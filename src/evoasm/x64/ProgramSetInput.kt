package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.x64.*

abstract class ProgramSetInput(val size: Int, val arity: Int) {

    interface Factory {
        fun create(size: Int, arity: Int) : ProgramSetInput
    }

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

abstract class NumberProgramSetInput<T : Number>(size: Int, arity: Int) : ProgramSetInput(size, arity) {

    interface Factory<T: Number> : ProgramSetInput.Factory {
        override fun create(size: Int, arity: Int) : NumberProgramSetInput<T>
    }

    abstract operator fun set(rowIndex: Int, columnIndex: Int, value: T)
}


class LongProgramSetInput(size: Int, arity: Int) : NumberProgramSetInput<Long>(size, arity) {
    private val storage: Storage

    companion object : Factory<Long> {
        override fun create(size: Int, arity: Int): NumberProgramSetInput<Long> {
            return LongProgramSetInput(size, arity)
        }
    }

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

class DoubleProgramSetInput(size: Int, arity: Int) : NumberProgramSetInput<Double>(size, arity) {
    private val storage: Storage

    companion object : Factory<Double> {
        override fun create(size: Int, arity: Int): NumberProgramSetInput<Double> {
            return DoubleProgramSetInput(size, arity)
        }
    }

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
                 Interpreter.XMM_REGISTERS, 8) { register, baseRegister ->
            assembler.movDouble(register, AddressExpression64(base = baseRegister))
        }
    }
}

class FloatProgramSetInput(size: Int, arity: Int) : NumberProgramSetInput<Float>(size, arity) {
    private val storage: Storage


    companion object : Factory<Float> {
        override fun create(size: Int, arity: Int): NumberProgramSetInput<Float> {
            return FloatProgramSetInput(size, arity)
        }
    }

    private class Storage(size: Int, arity: Int) : Structure() {
        val field = floatField(size, arity)
    }

    init {
        require(arity < Interpreter.XMM_REGISTERS.size)
        storage = Storage(size, arity)
        storage.allocate()
    }

    override fun set(rowIndex: Int, columnIndex: Int, value: Float) {
        storage.field.set(rowIndex, columnIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address,
                 Interpreter.XMM_REGISTERS, 4) { register, baseRegister ->
            assembler.movFloat(register, AddressExpression32(base = baseRegister))
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
        values.forEachIndexed { index, value ->
            setByte(rowIndex, columnIndex, index, value)
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

class IntVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Int>(size, arity, vectorSize)  {

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Int>) {
        values.forEachIndexed { index, value ->
            setInt(rowIndex, columnIndex, index, value)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Int) {
        setInt(rowIndex, columnIndex, elementIndex, value)
    }

    fun setInt(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Int) {
        storage.field.setInt(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitIntegerLoad(assembler)
    }

}

class ShortVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Short>(size, arity, vectorSize)  {

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Short>) {
        values.forEachIndexed { index, value ->
            setShort(rowIndex, columnIndex, index, value)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Short) {
        setShort(rowIndex, columnIndex, elementIndex, value)
    }

    fun setShort(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Short) {
        storage.field.setShort(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitIntegerLoad(assembler)
    }
}

class LongVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Long>(size, arity, vectorSize)  {

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Long>) {
        values.forEachIndexed { index, value ->
            setLong(rowIndex, columnIndex, index, value)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Long) {
        setLong(rowIndex, columnIndex, elementIndex, value)
    }

    fun setLong(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Long) {
        storage.field.setLong(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitIntegerLoad(assembler)
    }
}


class FloatVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Float>(size, arity, vectorSize)  {
    private fun emitSingleFloatLoad(assembler: Assembler) {
        when(vectorSize) {
            VectorSize.BITS_128 -> {
                emitLoad(assembler, storage.field.address, Interpreter.XMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    if (VmovapsXmmXmmm128.isSupported()) {
                        assembler.vmovaps(register, AddressExpression128(baseRegister))
                    } else {
                        assembler.movaps(register, AddressExpression128(baseRegister))
                    }
                }
            }
            VectorSize.BITS_256 ->
                emitLoad(assembler, storage.field.address, Interpreter.YMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    assembler.vmovaps(register, AddressExpression256(baseRegister))
                }
            VectorSize.BITS_512 -> TODO()
            else -> throw IllegalArgumentException("invalid vector programCount $vectorSize")
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Float>) {
        values.forEachIndexed { index, byte ->
            setFloat(rowIndex, columnIndex, index, byte)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Float) {
        setFloat(rowIndex, columnIndex, elementIndex, value)
    }

    fun setFloat(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Float) {
        storage.field.setFloat(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitSingleFloatLoad(assembler)
    }

}

class DoubleVectorProgramSetInput(size: Int, arity: Int, vectorSize: VectorSize) : VectorProgramSetInput<Double>(size, arity, vectorSize)  {
    private fun emitSingleDoubleLoad(assembler: Assembler) {
        when(vectorSize) {
            VectorSize.BITS_128 -> {
                emitLoad(assembler, storage.field.address, Interpreter.XMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    if (VmovapdXmmXmmm128.isSupported()) {
                        assembler.vmovapd(register, AddressExpression128(baseRegister))
                    } else {
                        assembler.movapd(register, AddressExpression128(baseRegister))
                    }
                }
            }
            VectorSize.BITS_256 ->
                emitLoad(assembler, storage.field.address, Interpreter.YMM_REGISTERS, vectorSize.byteSize) { register, baseRegister ->
                    assembler.vmovapd(register, AddressExpression256(baseRegister))
                }
            VectorSize.BITS_512 -> TODO()
            else -> throw IllegalArgumentException("invalid vector programCount $vectorSize")
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, values: Array<Double>) {
        values.forEachIndexed { index, byte ->
            setDouble(rowIndex, columnIndex, index, byte)
        }
    }

    override fun set(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Double) {
        setDouble(rowIndex, columnIndex, elementIndex, value)
    }

    fun setDouble(rowIndex: Int, columnIndex: Int, elementIndex: Int, value: Double) {
        storage.field.setDouble(rowIndex, columnIndex, elementIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitSingleDoubleLoad(assembler)
    }

}
