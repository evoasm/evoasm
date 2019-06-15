package evoasm.x64

import evoasm.measureTimeSeconds
import kasm.*
import kasm.ext.enumSetOf
import kasm.ext.log2
import kasm.ext.toEnumSet
import kasm.x64.*
import kasm.x64.GpRegister64.*
import kotlin.IllegalArgumentException
import kotlin.random.Random

inline class InterpreterOpcode(val code: UShort) {

}


class InterpreterInstructionParameters(vararg val registers: Register) : InstructionParameters {
    override fun getGpRegister8(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as GpRegister8
    override fun getGpRegister16(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as GpRegister16
    override fun getGpRegister32(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as GpRegister32
    override fun getGpRegister64(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as GpRegister64
    override fun getMmRegister(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as MmRegister
    override fun getXmmRegister(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as XmmRegister
    override fun getYmmRegister(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as YmmRegister
    override fun getZmmRegister(index: Int, isRead: Boolean, isWritten: Boolean) = registers[index] as ZmmRegister
    override fun getX87Register(index: Int, isRead: Boolean, isWritten: Boolean): X87Register = registers[index] as X87Register
    override fun useSibd() = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InterpreterInstructionParameters

        if (!registers.contentEquals(other.registers)) return false

        return true
    }

    override fun hashCode(): Int {
        return registers.contentHashCode()
    }


}

data class InterpreterInstruction(val instruction: Instruction, val instructionParameters: InstructionParameters) //,val operandRegisters: List<Register>)


class Interpreter(val programSets: List<ProgramSet>,
                  val inputs: List<ProgramSetInput>,
                  val outputs: List<ProgramSetOutput>,
                  val options: InterpreterOptions = InterpreterOptions.DEFAULT) {

    constructor(programSet: ProgramSet, input: ProgramSetInput, output: ProgramSetOutput, options: InterpreterOptions = InterpreterOptions.DEFAULT) :
            this(listOf(programSet), listOf(input), listOf(output), options)

    companion object {
        fun emitMultiplication(assembler: Assembler, register: GpRegister64, multiplier: Int) {
            when {
                Integer.bitCount(multiplier) == 1 -> {
                    val shiftSize = log2(multiplier).toByte()
                    assembler.sal(register, shiftSize)
                }
                multiplier <= Byte.MAX_VALUE -> {
                    assembler.imul(register, register, multiplier.toByte())
                }
                else -> {
                    assembler.imul(register, register, multiplier)
                }
            }
        }

        private const val INSTRUCTION_ALIGNMENT = 8 // bytes
        private const val OPCODE_SIZE = Short.SIZE_BYTES // bytes

        private const val INTERNAL_INSTRUCTION_COUNT = 2

        private val FIRST_INSTRUCTION_ADDRESS_REGISTER = RBP
        private val IP_REGISTER = R14
        internal val SCRATCH_REGISTER1 = R12
        internal val COUNTERS_REGISTER = R13
        internal val SCRATCH_REGISTER2 = R15
        internal val GP_REGISTERS = listOf(RAX, RBX, RCX, RDX, RDI, RSI, R8, R9, R10, R11)
        internal val GP_REGISTERS_SET = GP_REGISTERS.toEnumSet()

        internal val XMM_REGISTERS = XmmRegister.values().filter { it.isSupported() }.toList()
        internal val XMM_REGISTERS_SET = XMM_REGISTERS.toEnumSet()

        internal val YMM_REGISTERS = YmmRegister.values().filter { it.isSupported() }.toList()
        internal val YMM_REGISTERS_SET = YMM_REGISTERS.toEnumSet()

        internal val MM_REGISTERS = MmRegister.values().toList()
        internal val MM_REGISTERS_SET = MM_REGISTERS.toEnumSet()


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
        
        private fun addOperandRegisters(index: Int, operandRegisters: List<Register>) {
            this.operandRegisters[index] = operandRegisters
            operandRegisterCount = Math.max(operandRegisterCount, index + 1)
        }
        
        override fun traceWrite(register: GpRegister8, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.gp8OperandRegisters[index])
        }

        override fun traceWrite(register: GpRegister16, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.gp16OperandRegisters[index])
        }

        override fun traceWrite(register: GpRegister32, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.gp32OperandRegisters[index])
        }

        override fun traceWrite(register: GpRegister64, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.gp64OperandRegisters[index])
        }

        override fun traceWrite(register: XmmRegister, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.xmmOperandRegisters[index])
        }

        override fun traceWrite(register: YmmRegister, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.ymmOperandRegisters[index])
        }

        override fun traceWrite(register: MmRegister, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            addOperandRegisters(index, options.mmOperandRegisters[index])
        }

        override fun traceWrite(register: X87Register, index: Int, range: BitRange, always: Boolean) {
            checkRegister(register)
            TODO()
        }

        override fun traceRead(register: GpRegister8, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.gp8OperandRegisters[index])
        }

        override fun traceRead(register: GpRegister16, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.gp16OperandRegisters[index])
        }

        override fun traceRead(register: GpRegister32, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.gp32OperandRegisters[index])
        }

        override fun traceRead(register: GpRegister64, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.gp64OperandRegisters[index])
        }

        override fun traceRead(register: XmmRegister, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.xmmOperandRegisters[index])
        }

        override fun traceRead(register: YmmRegister, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.ymmOperandRegisters[index])
        }

        override fun traceRead(register: MmRegister, index: Int, range: BitRange) {
            checkRegister(register)
            addOperandRegisters(index, options.mmOperandRegisters[index])
        }

        override fun traceRead(register: X87Register, index: Int, range: BitRange) {
            checkRegister(register)
            TODO()
        }

        private val gpRegisters = GP_REGISTERS_SET
        private val xmmRegisters = XMM_REGISTERS_SET
        private val ymmRegisters = YMM_REGISTERS_SET
        private val mmRegisters = MM_REGISTERS_SET

        private var operandRegisterCount = 0
        private val operandRegisters = Array(4) { emptyList<Register>()}

        private fun checkRegister(register: Register) {
            val normalizedRegister = normalizeRegister(register)
            checkNormalizedRegister(normalizedRegister, register)
        }

        private fun checkNormalizedRegister(normalizedRegister: Register, register: Register) {
            check(normalizedRegister in gpRegisters || register in xmmRegisters || register in ymmRegisters || register in mmRegisters
                 ) { "invalid register $register ($normalizedRegister)" }
        }

        private fun normalizeRegister(register: Register): Register {
            return when (register) {
                is GpRegister32 -> register.topLevelRegister
                is GpRegister16 -> register.topLevelRegister
                is GpRegister8  -> register.topLevelRegister
                else            -> register
            }
        }



//        override fun traceWrite(register: Register, implicit: Boolean, range: BitRange, always: Boolean) {
//            val normalizedRegister = normalizeRegister(register)
//            checkNormalizedRegister(normalizedRegister, register)
//            if (implicit) {
//                useRegister(register)
//            } else {
//                addRegisters(normalizedRegister)
//            }
//        }


        fun getOperandRegisterSequence() : Sequence<Array<Register>>  {
            val indices = IntArray(operandRegisterCount + 1)
            return generateSequence {
                if(indices.last() != 0) {
                    return@generateSequence null
                }

                val result = Array(operandRegisterCount) {
                        operandRegisters[it][indices[it]]
                }

                indices[0]++
                for (i in 0 until operandRegisterCount) {
                    if(indices[i] >= operandRegisters[i].size) {
                        indices[i] = 0
                        indices[i + 1]++
                    }
                }

                result
            }
        }

        internal var operandRegisterSequence: Sequence<Array<Register>>? = null

        override fun endTracing() {
            operandRegisterSequence = getOperandRegisterSequence()
//            getOperandRegisterSequence<Register>().forEach {
//                println(it)
//            }
        }

        override fun beginTracing() {
            operandRegisterCount = 0
            operandRegisters.fill(emptyList())
        }

        override fun traceRead(rflagsField: RflagsField) {}
        override fun traceWrite(rflagsField: RflagsField, always: Boolean) {}
        override fun traceRead(mxcsrField: MxcsrField) {}
        override fun traceWrite(mxcsrField: MxcsrField, always: Boolean) {}
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

        override fun getAddressExpression8(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression8 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression16(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression16 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression32(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression32 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression64(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression64 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression128(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression128 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression256(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression256 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression512(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression512 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getVectorAddress(index: Int, isRead: Boolean, isWritten: Boolean): VectorAddressExpression {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getX87Register(index: Int, isRead: Boolean, isWritten: Boolean): X87Register {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression80(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression80 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression28Bytes(index: Int,
                                                 isRead: Boolean,
                                                 isWritten: Boolean): AddressExpression28Bytes {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression108Bytes(index: Int,
                                                  isRead: Boolean,
                                                  isWritten: Boolean): AddressExpression108Bytes {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression512Bytes(index: Int,
                                                  isRead: Boolean,
                                                  isWritten: Boolean): AddressExpression512Bytes {
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

        internal var operandRegisters = emptyArray<Register>()
        private val random = Random.Default

        override fun getGpRegister8(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister8 {
            return operandRegisters[index] as GpRegister8
        }

        override fun getGpRegister16(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister16 {
            return operandRegisters[index] as GpRegister16
        }

        override fun getGpRegister32(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister32 {
            return operandRegisters[index] as GpRegister32
        }

        override fun getGpRegister64(index: Int, isRead: Boolean, isWritten: Boolean): GpRegister64 {
            return operandRegisters[index] as GpRegister64
        }

        override fun getMmRegister(index: Int, isRead: Boolean, isWritten: Boolean): MmRegister {
            return operandRegisters[index] as MmRegister
        }

        override fun getXmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): XmmRegister {
            return operandRegisters[index] as XmmRegister
        }

        override fun getYmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): YmmRegister {
            return operandRegisters[index] as YmmRegister
        }

        override fun getZmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): ZmmRegister {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression8(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression8 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression16(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression16 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression32(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression32 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression64(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression64 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression128(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression128 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression256(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression256 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression512(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression512 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getVectorAddress(index: Int, isRead: Boolean, isWritten: Boolean): VectorAddressExpression {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getX87Register(index: Int, isRead: Boolean, isWritten: Boolean): X87Register {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression80(index: Int, isRead: Boolean, isWritten: Boolean): AddressExpression80 {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression28Bytes(index: Int,
                                                 isRead: Boolean,
                                                 isWritten: Boolean): AddressExpression28Bytes {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression108Bytes(index: Int,
                                                  isRead: Boolean,
                                                  isWritten: Boolean): AddressExpression108Bytes {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun getAddressExpression512Bytes(index: Int,
                                                  isRead: Boolean,
                                                  isWritten: Boolean): AddressExpression512Bytes {
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


    val maxOpcodeIndex get() = instructionCounter
    private var haltLinkPoint: Assembler.JumpLinkPoint? = null
    private var firstInstructionLinkPoint: Assembler.LongLinkPoint? = null
    internal val buffer = NativeBuffer(1024 * 35, CodeModel.LARGE)
    private val assembler = Assembler(buffer)
    private var emittedNopBytes = 0

    private var instructionCounter: Int = 0
    private var instructions = UShortArray(1024)


    private var firstInstructionOffset: Int = -1
    private var interpreterEpilogOffset: Int = -1

    private val instructionMap = mutableMapOf<InterpreterInstruction, InterpreterOpcode>()

//    private val instructionParameters = InterpreterInstructionParameters(this)
//    private val instructionTracer = InterpreterInstructionTracer(this)


    init {
        emit()

        programSets.forEachIndexed { index, programSet ->
            programSet.initialize(getHaltOpcode(), getEndOpcode(index))
        }

//        println(instructions.contentToString())
//        check(getEndOpcode() == ProgramSet.END_INSTRUCTION
//             ) { "invalid end instruction (${ProgramSet.END_INSTRUCTION} should be ${getEndOpcode()})" }
//        check(getHaltOpcode() == ProgramSet.HALT_INSTRUCTION
//             ) { "invalid halt instruction (${ProgramSet.HALT_INSTRUCTION} should be ${getHaltOpcode()})" }
//        programSet._init(this)
        println(buffer.toByteString())

        if(options.unsafe) {
            buffer.makeExecutable()
        }

    }

//    internal fun getStartOpcode(threadIndex: Int): InterpreterOpcode {
//        return InterpreterOpcode(instructions[0 * options.threadCount + threadIndex].toUShort());
//    }

    internal fun getEndOpcode(threadIndex: Int): InterpreterOpcode {
        return InterpreterOpcode(instructions[0 * options.threadCount + threadIndex].toUShort());
    }

    internal fun getHaltOpcode(): InterpreterOpcode {
        return InterpreterOpcode(instructions[1 * options.threadCount].toUShort());
    }


    fun getOpcode(opcodeIndex: Int): InterpreterOpcode {
//        require(opcodeIndex < instructionCounter) { "invalid opcode index $opcodeIndex (max opcode index is $instructionCounter)" }
        return InterpreterOpcode(instructions[opcodeIndex + INTERNAL_INSTRUCTION_COUNT].toUShort());
    }

    fun getOpcode(instruction: Instruction, operandRegisters: List<Register>): InterpreterOpcode? {
        return getOpcode(instruction, *operandRegisters.toTypedArray())
    }

    fun getOpcode(instruction: Instruction, vararg operandRegisters: Register): InterpreterOpcode? {
        return instructionMap[InterpreterInstruction(instruction, InterpreterInstructionParameters(*operandRegisters))]
    }

    fun getInstruction(opcode: InterpreterOpcode): InterpreterInstruction? {
        return instructionMap.entries.find { it.value == opcode}?.key
    }

    private fun emitHaltInstruction() {
        emitInstruction(dispatch = false) {
            haltLinkPoint = assembler.jmp()
        }
    }

    private fun emitRflagsReset() {
        with(assembler) {
            pushfq()
            mov(AddressExpression64(RSP), 0)
            popfq()
        }
    }

    fun disassemble(opcode: InterpreterOpcode): Array<out Array<String>> {
        val instructionIndex = instructions.asShortArray().binarySearch(opcode.code.toShort(), 0, instructionCounter)
        println("found index ${instructions.indexOf(opcode.code)} for ${opcode.code}")
        require(instructionIndex >= 0) { "opcode ${opcode.code} was not found"}
        val instructionOffset = getInstructionOffset(opcode)

        println("$instructionIndex")

        val instructionEndOffset = if(instructionIndex + 1 < instructionCounter) {
            getInstructionOffset(instructions[instructionIndex + 1])
        } else {
            interpreterEpilogOffset
        }

        val codeArray = ByteArray(instructionEndOffset - instructionOffset) {
            buffer.byteBuffer.get(instructionOffset + it)
        }
        return Disassembler.disassemble(codeArray)
    }


    private fun emitEndInstructions() {
        repeat(options.threadCount) {
            emitEndInstruction(it)
        }
    }

    private fun emitEndInstruction(threadIndex: Int) {
        with(assembler) {
            emitInstruction {
                // store result
                emitOutputStore(threadIndex)
                if(inputs[threadIndex].size != 1) {
                    val inputCounterSubRegister = COUNTERS_REGISTER.subRegister16
                    // decrease input count
                    dec(inputCounterSubRegister)
                    ifNotEqual(
                            {
                                sub(IP_REGISTER, OPCODE_SIZE * (programSets[threadIndex].programSize + 1))
                            },
                            {
                                // increment program counter
                                add(COUNTERS_REGISTER, 1 shl 16)
                                //inc(COUNTERS_REGISTER)

                                // reset input counter
                                mov(inputCounterSubRegister, inputs[threadIndex].size.toShort())
                            }
                              )
                } else {
                    inc(COUNTERS_REGISTER.subRegister32)
                }

                // reset flags
                // NOTE: this includes only SF, ZF, AF, PF, and CF
                xor(GpRegister32.EAX, GpRegister32.EAX)
                sahfAh()

                // load inputs
                inputs[threadIndex].emitLoad(assembler)

            }
        }
    }

    private fun emitDispatch() {
        with(assembler) {
            // load next opcode
            movzx(SCRATCH_REGISTER1, AddressExpression16(IP_REGISTER))
            if(options.compressOpcodes) {
                lea(SCRATCH_REGISTER1,
                    AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale.X8))
            } else {
                lea(SCRATCH_REGISTER1, AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale.X1))
            }
            jmp(SCRATCH_REGISTER1)
        }
    }

    private fun encodeInstructionProlog() {
        with(assembler) {
            lea(IP_REGISTER, AddressExpression64(IP_REGISTER, OPCODE_SIZE))
        }
    }

    private fun emitInstructionEpilog(dispatch: Boolean) {
        with(assembler) {
            if (dispatch) emitDispatch()
            if(options.compressOpcodes) {
                emittedNopBytes += align(INSTRUCTION_ALIGNMENT)
            }
        }
    }

    private fun emitInterpreterProlog() {

        with(assembler) {
            // let IP point to first opcode
            emitRflagsReset()

            firstInstructionLinkPoint = mov(FIRST_INSTRUCTION_ADDRESS_REGISTER)

            var perThreadPrologLinkPoint: Assembler.LongLinkPoint? = null
            if(options.threadCount > 1) {
                //TODO: directly multiply RAX by size
                perThreadPrologLinkPoint = mov(SCRATCH_REGISTER1)
                imul(SCRATCH_REGISTER1, RAX)
                jmp(SCRATCH_REGISTER1)
            }

            var threadPrologSize = -1
            repeat(options.threadCount) { threadIndex ->
                val thisThreadPrologAddress = buffer.positionAddress

                mov(IP_REGISTER, programSets[threadIndex].address.toLong())
                if (inputs[threadIndex].size == 1) {
                    xor(COUNTERS_REGISTER, COUNTERS_REGISTER)
                } else {
                    mov(COUNTERS_REGISTER, inputs[threadIndex].size)
                }
                inputs[threadIndex].emitLoad(assembler)
                emitDispatch()

                val thisThreadPrologSize = (buffer.positionAddress - thisThreadPrologAddress).toInt()
                if(threadPrologSize == -1) {
                    threadPrologSize = thisThreadPrologSize
                } else {
                    check(threadPrologSize == thisThreadPrologSize)
                }
            }

            if(options.threadCount > 1) {
                perThreadPrologLinkPoint!!.link(threadPrologSize.toLong())
            }

            align(INSTRUCTION_ALIGNMENT)
        }
    }

    private fun emitInterpreterEpilog() {
        interpreterEpilogOffset = buffer.byteBuffer.position()
        assembler.link(haltLinkPoint!!)
    }

//    private fun emitStartInstructions() {
//        repeat(options.threadCount) {
//            emitStartInstruction(it)
//        }
//    }

//    private fun emitStartInstruction(threadIndex: Int) {
//        inputs[threadIndex].emitLoad(assembler)
//        emitDispatch()
//    }

    private fun emit() {
        val argumentRegisters = if(options.threadCount > 1) {
            enumSetOf(RAX)
        } else {
            enumSetOf()
        }
        assembler.emitStackFrame(argumentRegisters = argumentRegisters) {
            emitInterpreterProlog()

            val firstInstructionAddress = buffer.address.toLong() + buffer.position()
            println("First inst addr ${Address(firstInstructionAddress.toULong())}")
            firstInstructionLinkPoint!!.link(firstInstructionAddress)

            // IMPORTANT: must be first instructions, in this order
            emitEndInstructions()
            emitHaltInstruction()

            emitInstructions()
            emitMoveInstructions()
            emitInterpreterEpilog()
        }

        println("Average instruction bufferSize: ${assembler.buffer.position() / options.instructions.size.toDouble()}")
    }


    private fun emitInstructions() {
        println("emitting ${options.instructions.size} instructions")
        options.instructions.forEach {
            if (it in DIV_INSTRUCTIONS && options.safeDivision) {
                for(divisorInstruction in getDivisorInstructions(it)) {
                    emitInstruction { interpreterOpcode ->
                        emitDivInstruction(it, divisorInstruction)
                        instructionMap[InterpreterInstruction(it, InterpreterInstructionParameters(divisorInstruction))] = interpreterOpcode
                    }
                }
            } else {
                it.trace(instructionTracer, traceInstructionParameters)


                val operandRegisterSequence = instructionTracer.operandRegisterSequence!!

                println("EMITTING ${it}")
                operandRegisterSequence.forEach { operandRegisters ->
                    println("WITH ${operandRegisters}")
                    emitInstruction { interpreterOpcode ->
                        instructionParameters.operandRegisters = operandRegisters
                        it.encode(buffer.byteBuffer, instructionParameters)
                        instructionMap[InterpreterInstruction(it, InterpreterInstructionParameters(*operandRegisters))] = interpreterOpcode
                    }
                }

                println("")
                println("")
            }

        }
    }

    private fun getDivisorInstructions(divInstruction: Instruction) : List<GpRegister> {
        return when (divInstruction) {
            is IdivRm8Ax, is DivRm8Ax           -> options.gp8OperandRegisters[0]
            is IdivRm16AxDx, is DivRm16AxDx     -> options.gp16OperandRegisters[0]
            is IdivRm32EdxEax, is DivRm32EdxEax -> options.gp32OperandRegisters[0]
            is IdivRm64RdxRax, is DivRm64RdxRax -> options.gp64OperandRegisters[0]
            else                                -> {
                throw RuntimeException()
            }
        }
    }

    private fun emitDivInstruction(instruction: Instruction, divisorRegister: GpRegister) {

        when (instruction) {
            is IdivRm8Ax, is DivRm8Ax           -> {
                val divisorRegister8 = divisorRegister as GpRegister8
                assembler.xor(GpRegister16.AX, GpRegister16.AX)
                assembler.test(divisorRegister, divisorRegister8)
            }
            is IdivRm16AxDx, is DivRm16AxDx     -> {
                val divisorRegister16 = divisorRegister as GpRegister16
                assembler.xor(GpRegister16.AX, GpRegister16.AX)
                assembler.xor(GpRegister16.DX, GpRegister16.DX)
                assembler.test(divisorRegister16, divisorRegister16)
            }
            is IdivRm32EdxEax, is DivRm32EdxEax -> {
                val divisorRegister32 = divisorRegister as GpRegister32
                assembler.xor(GpRegister32.EAX, GpRegister32.EAX)
                assembler.xor(GpRegister32.EDX, GpRegister32.EDX)
                assembler.test(divisorRegister32, divisorRegister32)
            }
            is IdivRm64RdxRax, is DivRm64RdxRax -> {
                val divisorRegister64 = divisorRegister as GpRegister64
                assembler.xor(RAX, RAX)
                assembler.xor(RDX, RDX)
                assembler.test(divisorRegister64, divisorRegister64)
            }
            else                                -> {
                throw RuntimeException()
            }
        }

        val linkPoint = assembler.je()

        when (instruction) {
            is R8m8Instruction   -> {
                val divisorRegister8 = divisorRegister as GpRegister8
                instruction.encode(assembler.buffer, divisorRegister8)
            }
            is R16m16Instruction -> {
                val divisorRegister16 = divisorRegister as GpRegister16
                instruction.encode(assembler.buffer, divisorRegister16)
            }
            is R32m32Instruction -> {
                val divisorRegister32 = divisorRegister as GpRegister32
                instruction.encode(assembler.buffer, divisorRegister32)
            }
            is R64m64Instruction -> {
                val divisorRegister64 = divisorRegister as GpRegister64
                instruction.encode(assembler.buffer, divisorRegister64)
            }
        }
        assembler.link(linkPoint)
    }

    private fun emitMoveInstructions() {
        options.moveInstructions.forEach {
            emitMoveInstructions(it)
        }
    }

    private fun emitMoveInstructions(instruction: MoveInstruction) {

        val buffer = assembler.buffer
        val (registersList, registersSet) = when (instruction) {
            is R64m64R64Instruction, is R64R64m64Instruction                           -> GP_REGISTERS to GP_REGISTERS_SET
            is XmmXmmm64Instruction, is XmmXmmm128Instruction, is Xmmm64XmmInstruction -> XMM_REGISTERS to XMM_REGISTERS_SET
            is YmmYmmm256Instruction                                                   -> YMM_REGISTERS to YMM_REGISTERS_SET
            else                                                                       -> throw IllegalArgumentException(
                    "invalid move instruction $instruction")
        } as Pair<List<Register>, Set<Register>>

        val moves = options.movesGenerator(instruction, registersList)

        //NOTE: using the pd variant of the mov might cause a domain switch latency
        // i.e. if the register holds integer data (or possibly even packed vs single, single vs double float?) and we use a pd mov
        // https://stackoverflow.com/questions/6678073/difference-between-movdqa-and-movaps-x86-instructions

        moves.forEach { (destinationRegister, sourceRegister) ->
            require(destinationRegister in registersSet)
            require(sourceRegister in registersSet)

                emitInstruction {

                    instructionMap[InterpreterInstruction(instruction as Instruction, InterpreterInstructionParameters(destinationRegister, sourceRegister))] = it

                    when (instruction) {
                        is R64m64R64Instruction  -> instruction.encode(buffer,
                                                                       destinationRegister as GpRegister64,
                                                                       sourceRegister as GpRegister64)
                        is R64R64m64Instruction  -> instruction.encode(buffer,
                                                                       destinationRegister as GpRegister64,
                                                                       sourceRegister as GpRegister64)
                        is XmmXmmm64Instruction  -> instruction.encode(buffer,
                                                                       destinationRegister as XmmRegister,
                                                                       sourceRegister as XmmRegister)
                        is Xmmm64XmmInstruction  -> instruction.encode(buffer,
                                                                       destinationRegister as XmmRegister,
                                                                       sourceRegister as XmmRegister)
                        is XmmXmmm128Instruction -> instruction.encode(buffer,
                                                                       destinationRegister as XmmRegister,
                                                                       sourceRegister as XmmRegister)
                        is Xmmm128XmmInstruction -> instruction.encode(buffer,
                                                                       destinationRegister as XmmRegister,
                                                                       sourceRegister as XmmRegister)
                        is YmmYmmm256Instruction -> instruction.encode(buffer,
                                                                       destinationRegister as YmmRegister,
                                                                       sourceRegister as YmmRegister)
                        is Ymmm256YmmInstruction -> instruction.encode(buffer,
                                                                       destinationRegister as YmmRegister,
                                                                       sourceRegister as YmmRegister)
                    }
                }
        }
    }

    private fun emitOutputStore(threadIndex: Int) {
        outputs[threadIndex].emitStore(assembler)
    }

    private fun emitInstruction(dispatch: Boolean = true, block: (InterpreterOpcode) -> Unit) {
        val interpreterInstruction = addInstruction()
        encodeInstructionProlog()
        block(interpreterInstruction)
        emitInstructionEpilog(dispatch)
    }

    private fun addInstruction(): InterpreterOpcode {
        val opcode = if (firstInstructionOffset > 0) {
            val relativeInstructionOffset = buffer.byteBuffer.position() - firstInstructionOffset
            val opcodeInt = if(options.compressOpcodes) {
                relativeInstructionOffset / 8
            } else {
                relativeInstructionOffset
            }
            require(opcodeInt < UShort.MAX_VALUE.toInt()) {"16-bit opcode size exceeded"}
            opcodeInt.toUShort()
        } else {
            firstInstructionOffset = buffer.byteBuffer.position()
            0U
        }
        if (instructionCounter >= instructions.size) {
            val newInstructions = UShortArray(instructions.size * 2)
            instructions.copyInto(newInstructions)
            instructions = newInstructions
        }
        instructions[instructionCounter++] = opcode
        return InterpreterOpcode(opcode)
    }

    private fun getInstructionOffset(opcode: UShort) : Int {
        return firstInstructionOffset + if(options.compressOpcodes) {
            opcode.toInt() * 8
        } else {
            opcode.toInt()
        }
    }

    private fun getInstructionOffset(opcode: InterpreterOpcode) = getInstructionOffset(opcode.code)

    fun run() {
        run(0)
    }

    fun run(threadIndex: Int) {
//        println("instruction counter: $instructionCounter")
//        println("running address is ${buffer.address}")
//        println("byte code address is ${programSet.byteBuffer.address}")
//        println("output address is ${output.buffer.address}/${output.buffer.capacity()}")
//        println("first instruction is ${programSet.byteBuffer.asShortBuffer().get(0)}")
//        println("sec instruction is ${programSet.byteBuffer.asShortBuffer().get(1)}")
//        println("third instruction is ${programSet.byteBuffer.asShortBuffer().get(2)}")
//        println("instruction address ${instructions.contentToString()}")
        if(options.unsafe) {
            if(options.threadCount > 1) {
                buffer.executeUnsafe(threadIndex.toLong())
            } else {
                buffer.executeUnsafe()
            }
        } else {
            if(options.threadCount > 1) {
                buffer.execute(threadIndex.toLong())
            } else {
                buffer.execute()
            }
        }
    }

    data class RunMeasurements(var elapsedSeconds: Double, var instructionsPerSecond: Double)

    data class Statistics(val size: Int, val instructionCount: Int, val averageInstructionSize: Double, val nopRatio : Double)

    val statistics : Statistics get() {
        val emittedBytes = buffer.byteBuffer.position().toDouble()
        return Statistics(
                buffer.byteBuffer.position(),
                instructionCounter,
                          emittedBytes / instructionCounter,
                          emittedNopBytes / emittedBytes)
    }

    fun runAndMeasure() = runAndMeasure(0)

    fun runAndMeasure(threadIndex: Int): RunMeasurements {
        val elapsedSeconds = measureTimeSeconds {
            run(threadIndex)
        }
        val instructionsPerSecond = (programSets[threadIndex].instructionCount * inputs[threadIndex].size) / elapsedSeconds
        return RunMeasurements(elapsedSeconds, instructionsPerSecond)
    }

}


fun main() {
//    val programInput = LongProgramSetInput(1, )
//    programInput.set(0, 0x1L)
//    val programSet = ProgramSet(1, 1)
//    val programSetOutput = LongProgramSetOutput(programSet)
//    val interpreter = Interpreter(programSet, programInput, programSetOutput)
//    for (i in 0 until programSet.programSize) {
//        programSet.set(0, i, interpreter.getOpcode(0).also { println("setting instr ${it}") })
//    }
//    interpreter.run()
//    println(programSetOutput.getLong(0))

}