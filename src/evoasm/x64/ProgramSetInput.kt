package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.x64.*

abstract class ProgramSetInput(val size: Int, val arity: Int) {
    init {
        require(size > 0)
        require(size <= Short.MAX_VALUE)
        require(arity > 0)
    }
    abstract fun emitLoad(assembler: Assembler)



    protected fun <R: Register> emitLoad(assembler: Assembler, address: Address, registers: List<R>,
                                               elementSize: Int,
                                               emitBlock: (R, GpRegister64) -> Unit) {
        with(assembler) {

            if(size != 1) {
                val rowSize = size * arity * elementSize
                mov(Interpreter.SCRATCH_REGISTER1, address.toLong() + rowSize)
                movzx(Interpreter.SCRATCH_REGISTER2, Interpreter.COUNTERS_REGISTER.subRegister16)

                Interpreter.emitMultiplication(assembler,
                                               Interpreter.SCRATCH_REGISTER2,
                                               arity * elementSize)
                sub(Interpreter.SCRATCH_REGISTER1,
                    Interpreter.SCRATCH_REGISTER2)

            } else {
                mov(Interpreter.SCRATCH_REGISTER1, address.toLong())
            }

            registers.forEachIndexed { index, register ->
                emitBlock(register, Interpreter.SCRATCH_REGISTER1)
                if(index < registers.lastIndex) {
                    if ((index + 1) % arity == 0) {
                        val immediate = (arity - 1) * elementSize
                        if(immediate <= Byte.MAX_VALUE) {
                            sub(Interpreter.SCRATCH_REGISTER1, immediate.toByte())
                        } else {
                            sub(Interpreter.SCRATCH_REGISTER1, immediate)
                        }
                    } else {
                        lea(Interpreter.SCRATCH_REGISTER1,
                            AddressExpression64(Interpreter.SCRATCH_REGISTER1,
                                                elementSize))
                    }
                }
            }
        }
    }
}

abstract class ValueProgramSetInput<T : Number>(size: Int, arity: Int) : ProgramSetInput(size, arity) {
    abstract operator fun set(rowIndex: Int, columnIndex: Int, value: T)
}


class LongProgramSetInput(size: Int, arity: Int) : ValueProgramSetInput<Long>(size, arity) {
    private val storage: Storage

    private class Storage(size: Int, arity: Int) : Structure() {
        val field = longField(size, arity)
    }

    init {
        require(arity < Interpreter.GP_REGISTERS.size)
        storage = Storage(size, arity)
        storage.allocate()
    }

    override operator fun set(rowIndex: Int, columnIndex: Int, value: Long) {
        storage.field.set(rowIndex, columnIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address,
                 Interpreter.GP_REGISTERS, Long.SIZE_BYTES) { register, baseRegister ->
            assembler.mov(register, AddressExpression64(base = baseRegister))
        }
    }
}

class DoubleProgramSetInput(size: Int, arity: Int) : ValueProgramSetInput<Double>(size, arity) {
    private val storage: Storage

    private class Storage(size: Int, arity: Int) : Structure() {
        val field = doubleField(size, arity)
    }

    init {
        require(arity < Interpreter.XMM_REGISTERS.size)
        storage = Storage(size, arity)
        storage.allocate()
    }

    override fun set(rowIndex: Int, columnIndex: Int, value: Double) {
        storage.field.set(rowIndex, columnIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address,
                 Interpreter.XMM_REGISTERS, Long.SIZE_BYTES) { register, baseRegister ->
            assembler.movDouble(register, AddressExpression64(base = baseRegister))
        }
    }
}

enum class VectorSize(val vectorRegisterType: VectorRegisterType, val byteSize: Int) {
    BITS_64(VectorRegisterType.MM, 8),
    BITS_128(VectorRegisterType.XMM, 16),
    BITS_256(VectorRegisterType.YMM, 32),
    BITS_512(VectorRegisterType.ZMM, 64),
}

abstract class VectorProgramSetInput<T : Number>(size: Int, arity: Int, val vectorSize: VectorSize) : ProgramSetInput(size, arity)  {

    abstract operator fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: T)
    abstract operator fun set(rowIndex: Int, columnIndex: Int, values: Array<T>)

    protected val storage: Storage

    protected class Storage(size: Int,
                            arity: Int,
                            vectorRegisterType: VectorRegisterType) : Structure() {
        val field = vectorField(intArrayOf(size, arity), vectorRegisterType)
    }

    init {
        require(arity < Interpreter.XMM_REGISTERS.size)
        storage = Storage(size, arity, vectorSize.vectorRegisterType)
        storage.allocate()
    }


    /* pretty much the same for all integer sizes */
    internal fun emitIntegerLoad(assembler: Assembler) {

        when(vectorSize) {
            VectorSize.BITS_64  -> {
                emitLoad(assembler, storage.field.address, Interpreter.MM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    assembler.movq(register, AddressExpression64(baseRegister))
                }
            }
            VectorSize.BITS_128 -> {
                emitLoad(assembler, storage.field.address, Interpreter.XMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    if (VmovdqaXmmXmmm128.isSupported()) {
                        assembler.vmovdqa(register, AddressExpression128(baseRegister))
                    } else {
                        assembler.movdqa(register, AddressExpression128(baseRegister))
                    }
                }
            }
            VectorSize.BITS_256 ->
                emitLoad(assembler, storage.field.address, Interpreter.YMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    assembler.vmovdqa(register, AddressExpression256(baseRegister))
                }
            VectorSize.BITS_512 -> TODO()
        }
    }
}

class ByteVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Byte>(size, arity, vectorSize)  {

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Byte>) {
        values.forEachIndexed { index, byte ->
            setByte(rowIndex, columnIndex, index, byte)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Byte) {
        setByte(rowIndex, columnIndex, elementIndex, value)
    }

    fun setByte(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Byte) {
        storage.field.setByte(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitIntegerLoad(assembler)
    }

}
