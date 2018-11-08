package evoasm.x64

import kasm.*
import kasm.x64.*
import kasm.x64.GpRegister64.*
import java.nio.ByteBuffer

inline class InterpreterInstruction(val index: UShort) {

}

class ProgramInput(val arity: Int) {
    private val addresses = IntArray(arity)
    private val fields : Fields
    private var buffer: NativeBuffer

    private class Fields(arity: Int, bufferAllocator: BufferAllocator) : Structure(bufferAllocator) {
        val longs = longField(arity)
    }

    fun getAddress(index: Int): Int {
        return addresses[index]
    }

    init {
        var buffer : NativeBuffer? = null

        fields = Fields(arity, object : Structure.BufferAllocator {
            override fun allocate(size: Int): ByteBuffer {
                buffer = NativeBuffer(size.toLong(), CodeModel.SMALL)
                return buffer!!.byteBuffer
            }
        })
        fields.allocate()
        this.buffer = buffer!!
    }

    fun set(index: Int, value: Long) {
        fields.longs.set(index, value)
        addresses[index] = fields.longs.getAddress(index).toInt()
    }
}

class ProgramSetOutput(programSet: ProgramSet, val arity: Int = 1) {
    private val addresses = IntArray(arity)
    private val fields : Fields
    private var buffer: NativeBuffer
    val address get() = buffer.address

    private class Fields(arity: Int, programCount: Int, bufferAllocator: BufferAllocator) : Structure(bufferAllocator) {
        val longs = longField(programCount, arity)
    }

    init {
        var buffer : NativeBuffer? = null

        fields = Fields(arity, programSet.size, object : Structure.BufferAllocator {
            override fun allocate(size: Int): ByteBuffer {
                buffer = NativeBuffer(size.toLong(), CodeModel.SMALL)
                return buffer!!.byteBuffer
            }
        })
        fields.allocate()
        this.buffer = buffer!!
    }

    fun getLong(programIndex: Int, outputIndex: Int): Long {
        return fields.longs.get(programIndex, outputIndex)
    }
}


class Interpreter(val programSet: ProgramSet, val input: ProgramInput, val output: ProgramSetOutput)  {


    companion object {
        private val INSTRUCTIONS = setOf(
                AddR64Rm64,
                SubR64Rm64,
                ImulR64Rm64
                                        )

        private const val INSTRUCTION_ALIGNMENT = 8 // bytes
        private const val OPCODE_SIZE = Short.SIZE_BYTES // bytes
        private const val INTERPRETER_PROLOG_SIZE = 32 // bytes
        private const val MAX_INSTRUCTIONS = 10240

        private const val INTERNAL_INSTRUCTION_COUNT = 2

        private val FIRST_INSTRUCTION_ADDRESS_REGISTER = R15
        private val IP_REGISTER = R14
        private val SCRATCH_REGISTER1 = R13
        private val COUNTERS_REGISTER = R12
        private val SCRATCH_REGISTER2 = R11
        private val GP_REGISTERS = listOf(RAX, RBX, RCX, RDI, RSI, R8, R9, R10)
        private val INSTRUCTION_COUNT = INSTRUCTIONS.size + GP_REGISTERS.size * GP_REGISTERS.size - GP_REGISTERS.size
        val RETURN = INSTRUCTION_COUNT.toShort()
    }

    private var haltLinkPoint: Assembler.JumpLinkPoint? = null
    private var firstInstructionLinkPoint: Assembler.LongLinkPoint? = null
    private val buffer = NativeBuffer(1024 * 4, CodeModel.SMALL)
    private val assembler = Assembler(buffer)

    private var instructionCounter: Int = 0
    private var instructions = UShortArray(1024)


    private var firstInstructionOffset: Int = -1
    private val firstOutputAddress: Int

    init {
        firstOutputAddress = output.address.toInt()

        emit()

        println(instructions.contentToString())
        check(getEndInstruction() == ProgramSet.END_INSTRUCTION, {"invalid end instruction (${ProgramSet.END_INSTRUCTION} should be ${getEndInstruction()})"})
        check(getHaltInstruction() == ProgramSet.HALT_INSTRUCTION, {"invalid halt instruction (${ProgramSet.HALT_INSTRUCTION} should be ${getHaltInstruction()})"})
//        programSet._init(this)
        println(buffer.toByteString())
    }

    internal fun getHaltInstruction(): InterpreterInstruction {
        return InterpreterInstruction(instructions[0].toUShort());
    }

    internal fun getEndInstruction(): InterpreterInstruction {
        return InterpreterInstruction(instructions[1].toUShort());
    }

    fun getInstruction(opcode: Int): InterpreterInstruction {
        return InterpreterInstruction(instructions[opcode + INTERNAL_INSTRUCTION_COUNT].toUShort());
    }

    private fun emitHaltInstruction() {
        emitInstruction(dispatch = false) {
            haltLinkPoint = assembler.jmp()
        }
    }

    private fun emitEndInstruction() {
        with(assembler) {
            emitInstruction {

                // extract program counter
//                mov(SCRATCH_REGISTER1, COUNTERS_REGISTER)

                //FIXME: if we have instruction counter
                // { shr(SCRATCH_REGISTER1, 16) }

                // store result
                emitOutputStore(programCounterRegister = COUNTERS_REGISTER, scratchRegister = SCRATCH_REGISTER2)
                // load inputs
                emitInputLoad()

                // increment program counter

                //FIXME: if we have instruction counter
                //add(COUNTERS_REGISTER, 1 shl 16)
                inc(COUNTERS_REGISTER)

                // reset instruction counter
                //FIXME: only if we keep instruction count
                //mov(COUNTERS_REGISTER.subRegister16, 0)
            }
        }
    }

    private fun emitDispatch() {
        val interpreter = this
        with(assembler) {
            // load next opcode
            movzx(SCRATCH_REGISTER1, AddressExpression16(IP_REGISTER))
//            if(firstInstructionAddress > 0) {
//                add(SCRATCH_REGISTER1.subRegister32, firstInstructionAddress)
//            } else {
//                add(SCRATCH_REGISTER1.subRegister32, 0xdeadbeef.toInt())
//                firstInstructionLinkPoint = Assembler.JumpLinkPoint(buffer, relative = false)
//            }

            lea(SCRATCH_REGISTER1, AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale._8))
            jmp(SCRATCH_REGISTER1)
//            sal(SCRATCH_REGISTER1, 3);
//            add(SCRATCH_REGISTER1, FIRST_INSTRUCTION_ADDRESS_REGISTER)
//            jmp(SCRATCH_REGISTER1)
//            jmp(AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale._4))
        }
    }

    private fun encodeInstructionProlog() {
        with(assembler) {
            lea(IP_REGISTER, AddressExpression64(IP_REGISTER, OPCODE_SIZE))
        }
    }

    private fun emitInstructionEpilog(dispatch: Boolean) {
        with(assembler) {
            // increment per program instruction counter
            // inc(COUNTERS_REGISTER)
            // extract lower counter
            // movzx(SCRATCH_REGISTER1, COUNTERS_REGISTER.subRegister16)

            if(dispatch) emitDispatch()
            align(INSTRUCTION_ALIGNMENT)
        }
    }


    private fun emitInterpreterProlog() {
        val interpreter = this
        with(assembler) {
            // let IP point to first opcode
            mov(IP_REGISTER, programSet.address.toInt())
            firstInstructionLinkPoint = mov(FIRST_INSTRUCTION_ADDRESS_REGISTER)
            mov(COUNTERS_REGISTER, 0)
            emitInputLoad()

//            mov(FIRST_INSTRUCTION_ADDRESS_REGISTER, instructionIndices.address.toInt())
            emitDispatch()
            align(INSTRUCTION_ALIGNMENT)
        }
    }

    private fun emitInterpreterEpilog() {
        assembler.link(haltLinkPoint!!)
    }

    private fun emit() {
        assembler.emitStackFrame {
            emitInterpreterProlog()
            // IMPORTANT: must be first two instructions

            val firstInstructionAddress = buffer.address.toLong() + buffer.position()
            println("First inst addr ${Address(firstInstructionAddress.toULong())}")
            firstInstructionLinkPoint!!.link(firstInstructionAddress)

            emitHaltInstruction()
            emitEndInstruction()

            emitAddInstructions()
            emitSubInstructions()
            emitMulInstructions()
            emitMoveInstructions()
            emitGpZeroAllInstruction()
            emitInterpreterEpilog()
        }
    }


    private fun emitGpZeroAllInstruction() {
                GP_REGISTERS.forEach {
                    assembler.mov(it, 0)
                }
    }

    private fun emitMoveInstructions() {
        emitGpInstructions { destinationRegister, sourceRegister ->
            assembler.mov(destinationRegister, sourceRegister)
        }
    }

    private fun emitAddInstructions() {
        emitGpInstructions { destinationRegister, sourceRegister ->
            assembler.add(destinationRegister, sourceRegister)
        }
    }

    private fun emitSubInstructions() {
        emitGpInstructions { destinationRegister, sourceRegister ->
            assembler.sub(destinationRegister, sourceRegister)
        }
    }

    private fun emitMulInstructions() {
        emitGpInstructions { destinationRegister, sourceRegister ->
            assembler.imul(destinationRegister, sourceRegister)
        }
    }

    private fun emitInputLoad() {
        val arity = input.arity
        GP_REGISTERS.forEachIndexed { index, register ->
            assembler.mov(register, AddressExpression64(input.getAddress(index % arity)))
        }
    }


    private fun emitOutputStore(programCounterRegister: GpRegister64, scratchRegister: GpRegister64) {
        // FIXME
        val outputRegister = GP_REGISTERS.first()

        with(assembler) {
            mov(scratchRegister, programCounterRegister)
            sal(scratchRegister, 6)
            mov(AddressExpression64(scratchRegister, displacement = firstOutputAddress), outputRegister)
        }
    }

    private fun emitInstruction(dispatch: Boolean = true, block: () -> Unit) {
        addInstruction()
        encodeInstructionProlog()
        block()
        emitInstructionEpilog(dispatch)
    }

    private fun addInstruction() {
        val index = if(firstInstructionOffset > 0) {
            ((buffer.byteBuffer.position() - firstInstructionOffset) / 8).toUShort()
        } else {
            firstInstructionOffset = buffer.byteBuffer.position()
            0U
        }
        if(instructionCounter >= instructions.size) {
            val newInstructions = UShortArray(instructions.size * 2)
            instructions.copyInto(newInstructions)
            instructions = newInstructions
        }
        instructions[instructionCounter++] = index
    }

    private fun emitGpInstructions(block: (GpRegister64, GpRegister64) -> Unit) {
        with(assembler) {
            GP_REGISTERS.forEachIndexed { index, destinationRegister ->
                for(i in index + 1..index + 3 ) {
                    val sourceRegister = GP_REGISTERS[i % GP_REGISTERS.size]
                    emitInstruction {
                        block(destinationRegister, sourceRegister)
                    }
                }
            }
        }
    }

    fun run() {
        println("running address is ${buffer.address}")
        println("first instruction is ${programSet.byteBuffer.asShortBuffer().get(0)}")
        println("sec instruction is ${programSet.byteBuffer.asShortBuffer().get(1)}")
        println("third instruction is ${programSet.byteBuffer.asShortBuffer().get(2)}")
        buffer.execute()
    }

}


class ProgramSet(val size: Int, val programSize: Int) {
    private val actualProgramSize = programSize + 1
    val instructionCount = size * actualProgramSize
    private val codeBuffer = NativeBuffer((instructionCount.toLong() + 1) * UShort.SIZE_BYTES, CodeModel.SMALL)
    internal val byteBuffer = codeBuffer.byteBuffer
    val address: Address = codeBuffer.address

    companion object {
        val HALT_INSTRUCTION = InterpreterInstruction(0U)
        val END_INSTRUCTION = InterpreterInstruction(2U)
    }

    init {

        val shortBuffer = byteBuffer.asShortBuffer()
        for(i in 0 until size) {
            shortBuffer.put(i * actualProgramSize + programSize, END_INSTRUCTION.index.toShort())
        }
        shortBuffer.put(instructionCount, HALT_INSTRUCTION.index.toShort())
    }

    fun setInstruction(programIndex: Int, instructionIndex: Int, instruction: InterpreterInstruction) {
        val offset = (programIndex * actualProgramSize + Math.min(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        byteBuffer.putShort(offset, instruction.index.toShort())
    }

    fun getInstruction(programIndex: Int, instructionIndex: Int): InterpreterInstruction {
        val offset = (programIndex * actualProgramSize + Math.min(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        return InterpreterInstruction(byteBuffer.getShort(offset).toUShort())
    }
}

fun main() {
    val programInput = ProgramInput(2)
    programInput.set(0, 0x1L)
    programInput.set(1, 0x2L)
    val programSet = ProgramSet(1, 1)
    val programSetOutput = ProgramSetOutput(programSet)
    val interpreter = Interpreter(programSet, programInput, programSetOutput)
    for(i in 0 until programSet.programSize) {
        programSet.setInstruction(0, i, interpreter.getInstruction(0).also{println("setting instr ${it}")})
    }
    interpreter.run()
    println(programSetOutput.getLong(0, 0))

}