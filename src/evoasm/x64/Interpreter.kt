package evoasm.x64

import kasm.*
import kasm.ext.log2
import kasm.ext.toEnumSet
import kasm.x64.*
import kasm.x64.GpRegister64.*
import kotlin.IllegalArgumentException
import kotlin.random.Random
import kotlin.system.measureNanoTime

inline class InterpreterInstruction(val index: UShort) {

}

abstract class ProgramInput(val size: Int, val arity: Int) {
    init {
        require(size > 0)
        require(size <= Short.MAX_VALUE)
        require(arity > 0)
    }
    abstract fun emitLoad(assembler: Assembler)

    protected fun <R: Register> emitLoad(assembler: Assembler, address: Address, registers: List<R>, elementSize: Int, block: (R, AddressExpression64) -> Unit) {
        with(assembler) {
            mov(Interpreter.SCRATCH_REGISTER1, address.toLong())

            if(size != 1) {
                movzx(Interpreter.SCRATCH_REGISTER2, Interpreter.COUNTERS_REGISTER.subRegister16)
                val multiplier = size * elementSize

                Interpreter.emitMultiplication(assembler, Interpreter.SCRATCH_REGISTER2, multiplier)
                add(Interpreter.SCRATCH_REGISTER1, Interpreter.SCRATCH_REGISTER2)

//                when {
//                    multiplier % 2 == 0 -> {
//                        val shiftSize = (31 - Integer.numberOfLeadingZeros(bufferSize)).toByte()
//                        sal(Interpreter.SCRATCH_REGISTER2, shiftSize)
//                    }
//                    multiplier <= Byte.MAX_VALUE -> {
//                        imul(Interpreter.SCRATCH_REGISTER2, Interpreter.SCRATCH_REGISTER2, multiplier.toByte())
//                    }
//                    else -> {
//                        imul(Interpreter.SCRATCH_REGISTER2, Interpreter.SCRATCH_REGISTER2, multiplier)
//                    }
//                }
            }

            registers.forEachIndexed { index, register ->
                block(register,AddressExpression64(base = Interpreter.SCRATCH_REGISTER1) )
                if(index > 0 && index % arity == 0) {
                    sub(Interpreter.SCRATCH_REGISTER1, arity * elementSize)
                } else {
                    lea(Interpreter.SCRATCH_REGISTER1, AddressExpression64(Interpreter.SCRATCH_REGISTER1, elementSize))
                }
            }
        }
    }
}

class LongProgramInput(size: Int, arity: Int) : ProgramInput(size, arity) {
    private val storage: Storage

    private class Storage(size: Int, arity: Int) : Structure() {
        val field = longField(size, arity)
    }

    init {
        require(arity < Interpreter.GP_REGISTERS.size)
        storage = Storage(size, arity)
        storage.allocate()
    }

    fun set(index: Int, value: Long) {
        storage.field.set(index, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address, Interpreter.GP_REGISTERS, Long.SIZE_BYTES) { register, addressExpression ->
            assembler.mov(register, addressExpression)
        }
    }
}

class DoubleProgramInput(size: Int, arity: Int) : ProgramInput(size, arity) {
    private val storage: Storage

    private class Storage(size: Int, arity: Int) : Structure() {
        val field = doubleField(size, arity)
    }

    init {
        require(arity < Interpreter.XMM_REGISTERS.size)
        storage = Storage(size, arity)
        storage.allocate()
    }

    fun set(index0: Int, index1: Int, value: Double) {
        storage.field.set(index0, index1, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address, Interpreter.XMM_REGISTERS, Long.SIZE_BYTES) { register, addressExpression ->
            assembler.movDouble(register, addressExpression)
        }

//        Interpreter.XMM_REGISTERS.forEachIndexed { index, register ->
//            with(assembler) {
//                mov(Interpreter.SCRATCH_REGISTER1, storage.field.getAddress(index % arity).toLong())
//                movsd(register, AddressExpression64(base = Interpreter.SCRATCH_REGISTER1))
//            }
//        }
    }
}

abstract class ProgramSetOutput(programSet: ProgramSet, programInput: ProgramInput) {
    protected abstract val structure: Structure
    internal val buffer get() = structure.buffer
    val size = programInput.size

    internal abstract fun emitStore(assembler: Assembler)

    protected fun emitStore(assembler: Assembler, address: Address, elementSize: Int, block: (AddressExpression64) -> Unit) {
        require(elementSize % 2 == 0)

        with(assembler) {
            mov(Interpreter.SCRATCH_REGISTER1, Interpreter.COUNTERS_REGISTER)

            if(size != 1) {
                shr(Interpreter.SCRATCH_REGISTER1, 16)
                Interpreter.emitMultiplication(assembler, Interpreter.SCRATCH_REGISTER1, size * elementSize)
                movzx(Interpreter.SCRATCH_REGISTER2, Interpreter.COUNTERS_REGISTER.subRegister16)
                sal(Interpreter.SCRATCH_REGISTER2, log2(elementSize).toByte())
                add(Interpreter.SCRATCH_REGISTER1, Interpreter.SCRATCH_REGISTER2)
                mov(Interpreter.SCRATCH_REGISTER2, address)
                add(Interpreter.SCRATCH_REGISTER1, Interpreter.SCRATCH_REGISTER2)
                block(AddressExpression64(Interpreter.SCRATCH_REGISTER1))
            } else {
                // TODO: if elementSize == 1, can use scale, if address is 32-bit can use displacement
                sal(Interpreter.SCRATCH_REGISTER1, log2(elementSize).toByte())
                mov(Interpreter.SCRATCH_REGISTER2, address)
                add(Interpreter.SCRATCH_REGISTER1, Interpreter.SCRATCH_REGISTER2)
                block(AddressExpression64(base = Interpreter.SCRATCH_REGISTER1))
            }
        }
    }
}

abstract class ValueProgramSetOutput<T>(programSet: ProgramSet, programInput: ProgramInput) : ProgramSetOutput(programSet, programInput) {
    abstract fun get(programIndex: Int, outputIndex: Int): T
}

class LongProgramSetOutput(programSet: ProgramSet, programInput: ProgramInput) : ValueProgramSetOutput<Long>(programSet, programInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = longField(programCount, inputSize)
    }

    init {
        storage = Storage(programSet.size, programInput.size)
        storage.allocate()
    }

    fun getLong(programIndex: Int, outputIndex: Int): Long {
        return storage.field.get(programIndex, outputIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Long {
        return getLong(programIndex, outputIndex)
    }

    override fun emitStore(assembler: Assembler) {

        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.GP_REGISTERS.first()
            assembler.mov(it, outputRegister)
        }

//        assembler.mov(AddressExpression64(base = null,
//                                          index = Interpreter.SCRATCH_REGISTER1,
//                                          scale = Scale.X8,
//                                          displacement = address), outputRegister)
    }
}

class DoubleProgramSetOutput(programSet: ProgramSet, programInput: ProgramInput) : ValueProgramSetOutput<Double>(programSet, programInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = doubleField(programCount, inputSize, alignment = 16)
    }

    init {
        storage = Storage(programSet.size, programInput.size)
        storage.allocate()
    }

    fun getDouble(programIndex: Int, outputIndex: Int): Double {
        return storage.field.get(programIndex, outputIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Double {
        return getDouble(programIndex, outputIndex)
    }

    override fun emitStore(assembler: Assembler) {

        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.XMM_REGISTERS.first()
            assembler.movDouble(it, outputRegister)
        }

//        val outputRegister = Interpreter.XMM_REGISTERS.first()
//        assembler.mov(Interpreter.SCRATCH_REGISTER1, Interpreter.COUNTERS_REGISTER)
//        assembler.sar(Interpreter.SCRATCH_REGISTER1, 16)
//        assembler.movsd(AddressExpression64(base = null,
//                                            index = Interpreter.SCRATCH_REGISTER1,
//                                            scale = Scale.X8,
//                                            displacement = address), outputRegister)
    }
}


class Interpreter(val programSet: ProgramSet,
                  val input: ProgramInput,
                  val output: ProgramSetOutput,
                  val options: InterpreterOptions = InterpreterOptions.DEFAULT) {

    companion object {
        fun emitMultiplication(assembler: Assembler, register: GpRegister64, multiplier: Int) {
            when {
                multiplier % 2 == 0 -> {
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
        internal val SCRATCH_REGISTER1 = R13
        internal val COUNTERS_REGISTER = R12
        internal val SCRATCH_REGISTER2 = R15
        internal val GP_REGISTERS = listOf(RAX, RBX, RCX, RDX, RDI, RSI, R8, R9, R10, R11)
        internal val XMM_REGISTERS = XmmRegister.values().filter { it.isSupported() }.toList()
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

            val register_ = when (register) {
                is GpRegister32 -> register.topLevelRegister
                is GpRegister16 -> register.topLevelRegister
                is GpRegister8  -> register.topLevelRegister
                else            -> register
            }
            check(register_ in gpRegisters || register in xmmRegisters || register in ymmRegisters || register in mmRegisters,
                  { "invalid register ${register} (${register_})" })
        }

        override fun traceWrite(register: Register, implicit: Boolean, range: BitRange?, always: Boolean) {
            checkRegister(register)
            if (implicit) useRegister(register)
        }

        override fun traceWrite(addressExpression: AddressExpression) {
            throw IllegalArgumentException()
        }

        override fun traceRead(register: Register, implicit: Boolean, range: BitRange?) {
            checkRegister(register)

            if (implicit) useRegister(register)
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

        override fun traceRead(rflag: RflagsField) {}
        override fun traceWrite(rflag: RflagsField, always: Boolean) {}
        override fun traceRead(mxcsrFlag: MxcsrField) {}
        override fun traceWrite(mxcsrFlag: MxcsrField, always: Boolean) {}
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

        private val random = Random.Default

        private fun <T : Register> findUnusedRegisterAndUse(registers: List<T>): T {
            return findUnusedRegister(registers).also { useRegister(it) }
        }

        private fun <T : Register> findUnusedRegister(registers: List<T>): T {
            registers.forEach {
                if (it !in usedRegisters) return it
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


    private var haltLinkPoint: Assembler.JumpLinkPoint? = null
    private var firstInstructionLinkPoint: Assembler.LongLinkPoint? = null
    private val buffer = NativeBuffer(1024 * 35, CodeModel.LARGE)
    private val assembler = Assembler(buffer)

    private var instructionCounter: Int = 0
    private var instructions = UShortArray(1024)


    private val usedRegisters = mutableSetOf<Register>()

    private var firstInstructionOffset: Int = -1

//    private val instructionParameters = InterpreterInstructionParameters(this)
//    private val instructionTracer = InterpreterInstructionTracer(this)


    init {
        emit()

        println(instructions.contentToString())
        check(getEndInterpreterInstruction() == ProgramSet.END_INSTRUCTION,
              { "invalid end instruction (${ProgramSet.END_INSTRUCTION} should be ${getEndInterpreterInstruction()})" })
        check(getHaltInterpreterInstruction() == ProgramSet.HALT_INSTRUCTION,
              { "invalid halt instruction (${ProgramSet.HALT_INSTRUCTION} should be ${getHaltInterpreterInstruction()})" })
//        programSet._init(this)
        println(buffer.toByteString())
    }

    internal fun getHaltInterpreterInstruction(): InterpreterInstruction {
        return InterpreterInstruction(instructions[0].toUShort());
    }

    internal fun getEndInterpreterInstruction(): InterpreterInstruction {
        return InterpreterInstruction(instructions[1].toUShort());
    }

    fun getInterpreterInstruction(opcode: Int): InterpreterInstruction {
        require(opcode < instructionCounter, { "invalid opcode $opcode (max opcode is $instructionCounter)" })
        return InterpreterInstruction(instructions[opcode + INTERNAL_INSTRUCTION_COUNT].toUShort());
    }

    fun getInterpreterInstruction(instruction: Instruction): InterpreterInstruction? {
        val index = options.instructions.indexOf(instruction)
        if (index == -1) return null;
        return getInterpreterInstruction(index)
    }

    fun getInstruction(interpreterInstruction: InterpreterInstruction): Instruction {
        val index = instructions.indexOf(interpreterInstruction.index.toUShort())
        return options.instructions[index - INTERNAL_INSTRUCTION_COUNT]
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
        val normalizedRegister = if (register is GpRegister && register is SubRegister<*, *>) {
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

    private var programStartLabel: Assembler.Label? = null

    private fun emitEndInstruction() {
        with(assembler) {
            emitInstruction {


                // extract program counter
//                mov(SCRATCH_REGISTER1, COUNTERS_REGISTER)

                //FIXME: if we have instruction counter
                // { shr(SCRATCH_REGISTER1, 16) }

                // store result
                emitOutputStore()
                // load inputs
                input.emitLoad(assembler)

                if(input.size != 1) {
                    val inputCounterSubRegister = COUNTERS_REGISTER.subRegister16
                    // decrease input count
                    dec(inputCounterSubRegister)
                    ifNotEqual(
                            {
                                //mov(IP_REGISTER, programSet.address.toLong())
                                sub(IP_REGISTER, OPCODE_SIZE * (programSet.programSize + 1))
                            },
                            {
                                // increment program counter
                                add(COUNTERS_REGISTER, 1 shl 16)
                                //inc(COUNTERS_REGISTER)

                                // reset input counter
                                mov(inputCounterSubRegister, input.size.toShort())
                            }
                              )
                } else {
                    inc(COUNTERS_REGISTER)
                }

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

            lea(SCRATCH_REGISTER1, AddressExpression64(FIRST_INSTRUCTION_ADDRESS_REGISTER, SCRATCH_REGISTER1, Scale.X8))
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

            if (dispatch) emitDispatch()
            align(INSTRUCTION_ALIGNMENT)
        }
    }


    private fun emitInterpreterProlog() {
        val interpreter = this
        with(assembler) {
            // let IP point to first opcode
            emitRflagsReset()
            mov(IP_REGISTER, programSet.address.toLong())
            firstInstructionLinkPoint = mov(FIRST_INSTRUCTION_ADDRESS_REGISTER)
            if(input.size == 1) {
                xor(COUNTERS_REGISTER, COUNTERS_REGISTER)
            } else {
                mov(COUNTERS_REGISTER, input.size)
            }
            input.emitLoad(assembler)

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
            emitMoveInstructions()
            emitInterpreterEpilog()
        }

        println("Average instruction bufferSize: ${assembler.buffer.position() / options.instructions.size.toDouble()}")
    }


    private fun emitInstructions() {
        println("emitting ${options.instructions.size} instructions")
        options.instructions.forEach {
            emitInstruction {
                if (it in DIV_INSTRUCTIONS && options.safeDivision) {
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
            is R8m8Instruction   -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister8)
            }
            is R16m16Instruction -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister16)
            }
            is R32m32Instruction -> {
                instruction.encode(assembler.buffer, divisorRegister.subRegister32)
            }
            is R64m64Instruction -> {
                instruction.encode(assembler.buffer, divisorRegister)
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
        val registers: List<Register> = when (instruction) {
            is R64m64R64Instruction, is R64R64m64Instruction                           -> GP_REGISTERS
            is XmmXmmm64Instruction, is XmmXmmm128Instruction, is Xmmm64XmmInstruction -> XMM_REGISTERS
            is YmmYmmm256Instruction                                                   -> YMM_REGISTERS
            else                                                                       -> throw IllegalArgumentException(
                    "invalid move instruction $instruction")
        }

        //NOTE: using the pd variant of the mov might cause a domain switch latency
        // i.e. if the register holds integer data (or possibly a single double?) and we use a pd mov
        // https://stackoverflow.com/questions/6678073/difference-between-movdqa-and-movaps-x86-instructions

        registers.take(3).forEach { destinationRegister ->
            registers.drop(3).forEach { sourceRegister ->
                emitInstruction {
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
                        is YmmYmmm256Instruction -> instruction.encode(buffer,
                                                                       destinationRegister as YmmRegister,
                                                                       sourceRegister as YmmRegister)
                    }
                }
            }
        }
    }

    private fun emitOutputStore() {
        output.emitStore(assembler)
    }

    private fun emitInstruction(dispatch: Boolean = true, block: () -> Unit) {
        addInstruction()
        encodeInstructionProlog()
        block()
        emitInstructionEpilog(dispatch)
    }

    private fun addInstruction() {
        val index = if (firstInstructionOffset > 0) {
            ((buffer.byteBuffer.position() - firstInstructionOffset) / 8).toUShort()
        } else {
            firstInstructionOffset = buffer.byteBuffer.position()
            0U
        }
        if (instructionCounter >= instructions.size) {
            val newInstructions = UShortArray(instructions.size * 2)
            instructions.copyInto(newInstructions)
            instructions = newInstructions
        }
        instructions[instructionCounter++] = index
    }

    fun run() {
//        println("instruction counter: $instructionCounter")
        println("running address is ${buffer.address}")
        println("byte code address is ${programSet.byteBuffer.address}")
        println("output address is ${output.buffer.address}/${output.buffer.capacity()}")
//        println("first instruction is ${programSet.byteBuffer.asShortBuffer().get(0)}")
//        println("sec instruction is ${programSet.byteBuffer.asShortBuffer().get(1)}")
//        println("third instruction is ${programSet.byteBuffer.asShortBuffer().get(2)}")
//        println("instruction address ${instructions.contentToString()}")
        buffer.execute()
    }

    data class RunMeasurements(var elapsedSeconds: Double, var instructionsPerSecond: Double)

    fun runAndMeasure(): RunMeasurements {
        val nanoTime = measureNanoTime {
            run()
        }.toDouble()

        val elapsedSeconds = nanoTime / 1E9
        val instructionsPerSecond = programSet.instructionCount / elapsedSeconds
        return RunMeasurements(elapsedSeconds, instructionsPerSecond)
    }


}


class ProgramSet(val size: Int, val programSize: Int) {
    private val actualProgramSize = programSize + 1
    val instructionCount = size * actualProgramSize
    private val codeBuffer = NativeBuffer((instructionCount.toLong() + 1) * UShort.SIZE_BYTES, CodeModel.LARGE)
    internal val byteBuffer = codeBuffer.byteBuffer
    val address: Address = codeBuffer.address

    companion object {
        val HALT_INSTRUCTION = InterpreterInstruction(0U)
        val END_INSTRUCTION = InterpreterInstruction(2U)
    }

    init {

        val shortBuffer = byteBuffer.asShortBuffer()
        for (i in 0 until size) {
            shortBuffer.put(i * actualProgramSize + programSize, END_INSTRUCTION.index.toShort())
        }
        println("putting halt at ${instructionCount}")
        shortBuffer.put(instructionCount, HALT_INSTRUCTION.index.toShort())
    }

    fun set(programIndex: Int, instructionIndex: Int, instruction: InterpreterInstruction) {
        val offset = (programIndex * actualProgramSize + Math.min(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        byteBuffer.putShort(offset, instruction.index.toShort())
    }

    fun get(programIndex: Int, instructionIndex: Int): InterpreterInstruction {
        val offset = (programIndex * actualProgramSize + Math.min(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
        return InterpreterInstruction(byteBuffer.getShort(offset).toUShort())
    }
}

fun main() {
//    val programInput = LongProgramInput(1, )
//    programInput.set(0, 0x1L)
//    val programSet = ProgramSet(1, 1)
//    val programSetOutput = LongProgramSetOutput(programSet)
//    val interpreter = Interpreter(programSet, programInput, programSetOutput)
//    for (i in 0 until programSet.programSize) {
//        programSet.set(0, i, interpreter.getInterpreterInstruction(0).also { println("setting instr ${it}") })
//    }
//    interpreter.run()
//    println(programSetOutput.getLong(0))

}