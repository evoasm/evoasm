package evoasm.x64

import kasm.*
import kasm.x64.*
import kasm.x64.GpRegister64.*
import java.nio.ByteBuffer
import kotlin.reflect.jvm.internal.impl.utils.CollectionsKt

inline class InterpreterInstruction(val index: UShort) {

}

class ProgramInput(val arity: Int) {
    private val addresses = IntArray(arity)
    private val inputFields : Fields
    private var inputBuffer: NativeBuffer

    private class Fields(arity: Int, bufferAllocator: BufferAllocator) : Structure(bufferAllocator) {
        val longs = longField(arity)
    }

    fun getAddress(index: Int): Int {
        return addresses[index]
    }

    init {
        var inputBuffer : NativeBuffer? = null

        inputFields = Fields(arity, object : Structure.BufferAllocator {
            override fun allocate(size: Int): ByteBuffer {
                inputBuffer = NativeBuffer(size.toLong(), CodeModel.SMALL)
                return inputBuffer!!.byteBuffer
            }
        })
        inputFields.allocate()
        this.inputBuffer = inputBuffer!!
    }

    fun set(index: Int, value: Long) {
        inputFields.longs.set(index, value)
        addresses[index] = inputFields.longs.getAddress(index).toInt()
    }
}

class Interpreter(val programSet: ProgramSet, val input: ProgramInput)  {


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

    private var haltLinkPoint: Assembler.IntLinkPoint? = null
    private var firstInstructionLinkPoint: Assembler.IntLinkPoint? = null
    private val buffer = NativeBuffer(1024 * 4, CodeModel.SMALL)
    private val assembler = Assembler(buffer)

    private var instructionCounter: Int = 0
    private var instructions = UShortArray(1024)


    private val outputBuffer: NativeBuffer


    private var firstInstructionOffset: Int = -1
    private val firstOutputAddress: Int

    init {
        this.outputBuffer = NativeBuffer((programSet.size * Long.SIZE_BYTES).toLong(), CodeModel.SMALL)
        firstOutputAddress = this.outputBuffer.address.toInt()

        emit()
        println(buffer.toByteString())
    }

    fun getInstruction(opcode: Int): InterpreterInstruction {
        return InterpreterInstruction(instructions[opcode + INTERNAL_INSTRUCTION_COUNT].toUShort());
    }

    private fun emitHaltInstruction() {
        emitInstruction(epilog = false) {
            haltLinkPoint = assembler.jmp()
        }
    }

    private fun emitEndInstruction() {
        with(assembler) {
            emitInstruction {

                // extract program counter
                mov(SCRATCH_REGISTER1, COUNTERS_REGISTER)

                //FIXME: if we have instruction counter
                // { shr(SCRATCH_REGISTER1, 16) }

                // store result
                emitOutputStore(programCounterRegister = SCRATCH_REGISTER1, scratchRegister = SCRATCH_REGISTER2)
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
//                firstInstructionLinkPoint = Assembler.IntLinkPoint(buffer, relative = false)
//            }

            lea(SCRATCH_REGISTER1, AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale._8))
//            sal(SCRATCH_REGISTER1, 3);
//            add(SCRATCH_REGISTER1, FIRST_INSTRUCTION_ADDRESS_REGISTER)
            jmp(SCRATCH_REGISTER1)
//            jmp(AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale._4))
        }
    }

    private fun encodeInstructionProlog() {
        with(assembler) {
            lea(IP_REGISTER, AddressExpression64(IP_REGISTER, OPCODE_SIZE))
        }
    }

    private fun emitInstructionEpilog() {
        with(assembler) {
            // increment per program instruction counter
            // inc(COUNTERS_REGISTER)
            // extract lower counter
            // movzx(SCRATCH_REGISTER1, COUNTERS_REGISTER.subRegister16)

            emitDispatch()
            align(INSTRUCTION_ALIGNMENT)
        }
    }


    private fun emitInterpreterProlog() {
        val interpreter = this
        with(assembler) {
            emitGpZeroAllInstruction()

            // let IP point to first opcode
            mov(IP_REGISTER, programSet.address.toInt())
//            mov(FIRST_INSTRUCTION_ADDRESS_REGISTER, instructionIndices.address.toInt())
            emitDispatch()
        }
    }

    private fun emitInterpreterEpilog() {
        assembler.link(haltLinkPoint!!)
    }

    private fun emit() {
        assembler.emitStackFrame {
            emitInterpreterProlog()
            align(INSTRUCTION_ALIGNMENT)
            // IMPORTANT: must be first two instructions
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

    private fun emitGpInputInstruction(register: GpRegister64, inputIndex: Int) {
        emitInstruction {
            with(assembler) {
                mov(register, AddressExpression64(calculateInputAddress(inputIndex).toInt()))
            }
        }
    }

    private fun calculateInputAddress(inputIndex: Int): Address {
        return outputBuffer.address + (inputIndex * Long.SIZE_BYTES).toULong()
    }

    private fun calculateOutputAddress(index: Int): Address {
        return outputBuffer.address + (index * Long.SIZE_BYTES).toULong()
    }

    private fun emitInstruction(epilog: Boolean = true, block: () -> Unit) {
        addInstruction()
        encodeInstructionProlog()
        block()
        if(epilog) emitInstructionEpilog()
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
        buffer.execute()
    }

}


class ProgramSet(val size: Int, val programSize: Int) {
    val instructionCount = size * programSize
    private val codeBuffer = NativeBuffer((instructionCount.toLong() + 1) * UShort.SIZE_BYTES, CodeModel.SMALL)
    private val byteBuffer = codeBuffer.byteBuffer
    val address: Address = codeBuffer.address

    companion object {
        const val HALT_OPCODE: Short = 0
        const val END_OPCODE: Short = 1
    }

    init {
        val shortBuffer = byteBuffer.asShortBuffer()
        for(i in 1 until size) {
            shortBuffer.put(i * programSize + 1, END_OPCODE)
        }
        shortBuffer.put(instructionCount, HALT_OPCODE)
    }

    fun setInstruction(programIndex: Int, instructionIndex: Int, instruction: InterpreterInstruction) {
        val offset = (programIndex * (programSize + 1) + Math.max(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        byteBuffer.putShort(offset, instruction.index.toShort())
    }

    fun getInstruction(programIndex: Int, instructionIndex: Int): InterpreterInstruction {
        val offset = (programIndex * (programSize + 1) + Math.max(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        return InterpreterInstruction(byteBuffer.getShort(offset).toUShort())
    }
}

fun main() {
    val programInput = ProgramInput(2)
    programInput.set(0, 0x1L)
    programInput.set(1, 0x2L)
    val programSet = ProgramSet(1, 5)
    val interpreter = Interpreter(programSet, programInput)
    for(i in 0 until programSet.programSize) {
        programSet.setInstruction(0, i, interpreter.getInstruction(0))
    }
//    interpreter.run()

}