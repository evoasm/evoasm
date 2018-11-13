package evoasm.x64

import kasm.*
import kasm.ext.toEnumSet
import kasm.x64.*
import kasm.x64.GpRegister64.*
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
import kotlin.random.Random

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
    val size get() = buffer.byteBuffer.capacity()

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


class Interpreter(val programSet: ProgramSet, val input: ProgramInput, val output: ProgramSetOutput, val options: InterpreterOptions = InterpreterOptions.DEFAULT)  {

    companion object {
        private const val INSTRUCTION_ALIGNMENT = 8 // bytes
        private const val OPCODE_SIZE = Short.SIZE_BYTES // bytes

        private const val INTERNAL_INSTRUCTION_COUNT = 2

        private val FIRST_INSTRUCTION_ADDRESS_REGISTER = R15
        private val IP_REGISTER = R14
        private val SCRATCH_REGISTER1 = R13
        private val COUNTERS_REGISTER = R12
        private val SCRATCH_REGISTER2 = R11
        private val GP_REGISTERS = listOf(RAX, RBX, RCX, RDX, RDI, RSI, R8, R9, R10)
        private val XMM_REGISTERS = XmmRegister.values().filter { it.isSupported() }.toList()
        private val YMM_REGISTERS = YmmRegister.values().filter { it.isSupported() }.toList()
        private val MM_REGISTERS = MmRegister.values().toList()
        private val DIV_INSTRUCTIONS = setOf(
                IdivRm8Ax,
                IdivRm16AxDx,
                IdivRm32EdxEax,
                IdivRm64RdxRax,
                DivRm8Ax,
                DivRm16AxDx,
                DivRm32EdxEax,
                DivRm64RdxRax)


    }

    private val instructionTracer = object : InstructionTracer {
        private val gpRegisters = GP_REGISTERS.toEnumSet()
        private val xmmRegisters = XMM_REGISTERS.toEnumSet()
        private val ymmRegisters = YMM_REGISTERS.toEnumSet()
        private val mmRegisters = MM_REGISTERS.toEnumSet()

        private fun checkRegister(register: Register) {

            val register_ = when(register) {
                is GpRegister32 -> register.topLevelRegister
                is GpRegister16 -> register.topLevelRegister
                is GpRegister8 -> register.topLevelRegister
                else -> register
            }
            check(register_ in gpRegisters || register in xmmRegisters || register in ymmRegisters || register in mmRegisters, {"invalid register ${register} (${register_})"})
        }

        override fun traceWrite(register: Register, implicit: Boolean, range: BitRange?, always: Boolean) {
            checkRegister(register)
            if(implicit) useRegister(register)
        }

        override fun traceWrite(addressExpression: AddressExpression) {
            throw IllegalArgumentException()
        }

        override fun traceRead(register: Register, implicit: Boolean, range: BitRange?) {
            checkRegister(register)

            if(implicit) useRegister(register)
        }

        override fun traceRead(addressExpression: AddressExpression) {
            throw IllegalArgumentException()
        }

        override fun traceRead(addressExpression: VectorAddressExpression) {
            throw IllegalArgumentException()
        }

        override fun traceRead(immediate: Long, implicit: Boolean, size: BitSize?) {

        }

        override fun beginTracing() {
            usedRegisters.clear()
        }

        override fun traceRead(rflag: Rflag) {}
        override fun traceWrite(rflag: Rflag, always: Boolean) {}
        override fun traceRead(mxcsrFlag: MxcsrFlag, always: Boolean) {}
        override fun traceWrite(mxcsrFlag: MxcsrFlag, always: Boolean) {}
    }


    private val traceInstructionParameters = object : InstructionParameters {


        override fun getGpRegister8(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister8 {
            return GP_REGISTERS[0].subRegister8
        }

        override fun getGpRegister16(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister16 {
            return GP_REGISTERS[0].subRegister16
        }

        override fun getGpRegister32(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister32 {
            return GP_REGISTERS[0].subRegister32
        }

        override fun getGpRegister64(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister64 {
            return GP_REGISTERS[0]
        }

        override fun getMmRegister(index: Int, isRead: Boolean, isWritten: Boolean): MmRegister {
            return MM_REGISTERS[0]
        }

        override fun getXmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): XmmRegister {
            return XMM_REGISTERS[0]
        }

        override fun getYmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): YmmRegister {
            return YMM_REGISTERS[0]
        }

        override fun getZmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): ZmmRegister {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress8(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression8 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress16(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression16 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress32(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression32 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress64(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression64 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress128(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression128 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress256(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression256 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress512(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression512 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getVectorAddress(index: Int, isRead: Boolean, isWritten: Boolean): VectorAddressExpression {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun useSibd(): Boolean {
            return false
        }

        override fun getByteImmediate(index: Int): Byte {
            return 0;
        }

        override fun getShortImmediate(index: Int): Short {
            return 0;
        }

        override fun getIntImmediate(index: Int): Int {
            return 0;
        }

        override fun getLongImmediate(index: Int): Long {
            return 0;
        }

    }


    private val instructionParameters = object : InstructionParameters {

        private val random = Random.Default

        private fun <T: Register> findUnusedRegisterAndUse(registers: List<T>) : T {
            return findUnusedRegister(registers).also { useRegister(it) }
        }

        private fun <T : Register> findUnusedRegister(registers: List<T>): T {
            registers.forEach {
                if(it !in usedRegisters) return it
            }
            throw RuntimeException()
        }

        override fun getGpRegister8(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister8 {
            return findUnusedRegisterAndUse(GP_REGISTERS).subRegister8
        }

        override fun getGpRegister16(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister16 {
            return findUnusedRegisterAndUse(GP_REGISTERS).subRegister16
        }

        override fun getGpRegister32(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister32 {
            return findUnusedRegisterAndUse(GP_REGISTERS).subRegister32
        }

        override fun getGpRegister64(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister64 {
            return findUnusedRegisterAndUse(GP_REGISTERS)
        }

        override fun getMmRegister(index: Int, isRead: Boolean, isWritten: Boolean): MmRegister {
            return findUnusedRegisterAndUse(MM_REGISTERS)
        }

        override fun getXmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): XmmRegister {
            return findUnusedRegisterAndUse(XMM_REGISTERS)
        }

        override fun getYmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): YmmRegister {
            return findUnusedRegisterAndUse(YMM_REGISTERS)
        }

        override fun getZmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): ZmmRegister {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress8(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression8 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress16(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression16 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress32(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression32 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress64(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression64 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress128(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression128 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress256(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression256 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddress512(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression512 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getVectorAddress(index: Int, isRead: Boolean, isWritten: Boolean): VectorAddressExpression {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun useSibd(): Boolean {
            return false
        }

        override fun getByteImmediate(index: Int): Byte {
            return (random.nextBits(8)).toByte();
        }

        override fun getShortImmediate(index: Int): Short {
            return (random.nextBits(16)).toShort();
        }

        override fun getIntImmediate(index: Int): Int {
            return random.nextInt()
        }

        override fun getLongImmediate(index: Int): Long {
            return random.nextLong()
        }

    }


    private var haltLinkPoint: Assembler.JumpLinkPoint? = null
    private var firstInstructionLinkPoint: Assembler.LongLinkPoint? = null
    private val buffer = NativeBuffer(1024 * 30, CodeModel.SMALL)
    private val assembler = Assembler(buffer)

    private var instructionCounter: Int = 0
    private var instructions = UShortArray(1024)


    private val usedRegisters = mutableSetOf<Register>()

    private var firstInstructionOffset: Int = -1
    private val firstOutputAddress: Int

//    private val instructionParameters = InterpreterInstructionParameters(this)
//    private val instructionTracer = InterpreterInstructionTracer(this)



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
        require(opcode < instructionCounter, {"invalid opcode $opcode (max opcode is $instructionCounter)"})
        return InterpreterInstruction(instructions[opcode + INTERNAL_INSTRUCTION_COUNT].toUShort());
    }

    private fun emitHaltInstruction() {
        emitInstruction(dispatch = false) {
            haltLinkPoint = assembler.jmp()
        }
    }

    private fun useRegister(register: Register) {
        // e.g. AX should use RAX
        // NOTE: XMM and YMM are kept separate, since there are no instruction (?) that mixes
        // XMM and YMM operands
        val normalizedRegister = if(register is GpRegister && register is SubRegister<*, *>) {
            register.topLevelRegister
        } else {
            register
        }

        usedRegisters.add(normalizedRegister)
    }

    private fun emitRflagsReset() {
        with(assembler) {
            pushfq()
            mov(AddressExpression64(RSP), 0)
            popfq()
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
            emitRflagsReset()
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

            emitInstructions()
//            emitAddInstructions()
//            emitSubInstructions()
//            emitMulInstructions()
//            emitMoveInstructions()
//            emitGpZeroAllInstruction()
            emitInterpreterEpilog()
        }

        println("Average instruction size: ${assembler.buffer.position() / options.instructions.size.toDouble()}")
    }


    private fun emitInstructions() {
        println("emitting ${options.instructions.size} instructions")
        options.instructions.forEach {
            emitInstruction {
                if(it in DIV_INSTRUCTIONS && options.safeDivision) {
                    emitDivInstruction(it)
                } else {
                    it.trace(instructionTracer, traceInstructionParameters)
                    it.encode(buffer.byteBuffer, instructionParameters)
                }
            }
        }
    }

    private fun emitDivInstruction(instruction: Instruction) {
        val divisorRegister = GP_REGISTERS[1]

        when (instruction) {
            is IdivRm8Ax, is DivRm8Ax           -> {
                assembler.xor(GpRegister16.AX, GpRegister16.AX)
                assembler.test(divisorRegister.subRegister8, divisorRegister.subRegister8)
            }
            is IdivRm16AxDx, is DivRm16AxDx     -> {
                assembler.xor(GpRegister16.AX, GpRegister16.AX)
                assembler.xor(GpRegister16.DX, GpRegister16.DX)
                assembler.test(divisorRegister.subRegister16, divisorRegister.subRegister16)
            }
            is IdivRm32EdxEax, is DivRm32EdxEax -> {
                assembler.xor(GpRegister32.EAX, GpRegister32.EAX)
                assembler.xor(GpRegister32.EDX, GpRegister32.EDX)
                assembler.test(divisorRegister.subRegister32, divisorRegister.subRegister32)
                divisorRegister.subRegister32
            }
            is IdivRm64RdxRax, is DivRm64RdxRax -> {
                assembler.xor(RAX, RAX)
                assembler.xor(RDX, RDX)
                assembler.test(divisorRegister, divisorRegister)
            }
            else                                -> {
                throw RuntimeException()
            }
        }

        val linkPoint = assembler.je()

        when (instruction) {
            is R8mInstruction  -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister8)
            }
            is R16mInstruction -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister16)
            }
            is R32mInstruction -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister32)
            }
            is R64mInstruction -> {
                instruction.encode(assembler.buffer, divisorRegister)
            }
        }
        assembler.link(linkPoint)
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
//            sal(scratchRegister, 6)
            mov(AddressExpression64(base = null, index = programCounterRegister, scale = Scale._8, displacement = firstOutputAddress), outputRegister)
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
        println("instruction counter: $instructionCounter")
        println("running address is ${buffer.address}")
        println("byte code address is ${programSet.byteBuffer.address}")
        println("output address is ${output.address}/${output.size}")
        println("first instruction is ${programSet.byteBuffer.asShortBuffer().get(0)}")
        println("sec instruction is ${programSet.byteBuffer.asShortBuffer().get(1)}")
        println("third instruction is ${programSet.byteBuffer.asShortBuffer().get(2)}")
        println("instruction address ${instructions.contentToString()}")
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