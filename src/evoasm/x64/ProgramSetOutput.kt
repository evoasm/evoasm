package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.ext.log2
import kasm.x64.*

abstract class ProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput) {
    protected abstract val structure: Structure
    internal val buffer get() = structure.buffer
    val size = programSetInput.size
    val arity = programSetInput.arity

    fun zero() = structure.zero()

    internal abstract fun emitStore(assembler: Assembler)

    protected fun emitStore(assembler: Assembler, address: Address, elementSize: Int, block: (GpRegister64) -> Unit) {
        require(Integer.bitCount(elementSize) == 1)

        with(assembler) {

            if(size > 1) {
                mov(Interpreter.SCRATCH_REGISTER1, Interpreter.COUNTERS_REGISTER)
                shr(Interpreter.SCRATCH_REGISTER1, 16)
                Interpreter.emitMultiplication(assembler,
                                               Interpreter.SCRATCH_REGISTER1,
                                               size * elementSize)
                movzx(Interpreter.SCRATCH_REGISTER2, Interpreter.COUNTERS_REGISTER.subRegister16)
                sal(Interpreter.SCRATCH_REGISTER2, log2(elementSize).toByte())
                sub(Interpreter.SCRATCH_REGISTER1,
                    Interpreter.SCRATCH_REGISTER2)
                println("OUTPUT ADDRESS IS ${address}")
                mov(Interpreter.SCRATCH_REGISTER2, address.toLong() + size * elementSize)
                add(Interpreter.SCRATCH_REGISTER1,
                    Interpreter.SCRATCH_REGISTER2)
                block(Interpreter.SCRATCH_REGISTER1)
            } else {
                // TODO: if elementSize == 1, can use scale, if address is 32-bit can use displacement
                if(arity > 1) {
                    mov(Interpreter.SCRATCH_REGISTER1, Interpreter.COUNTERS_REGISTER)
                    sal(Interpreter.SCRATCH_REGISTER1, log2(elementSize).toByte())
                    mov(Interpreter.SCRATCH_REGISTER2, address)
                    add(Interpreter.SCRATCH_REGISTER1,
                        Interpreter.SCRATCH_REGISTER2)
                } else {
                    mov(Interpreter.SCRATCH_REGISTER1, address)
                }

                block(Interpreter.SCRATCH_REGISTER1)

            }
        }
    }
}

abstract class NumberProgramSetOutput<T : Number>(programSetSize: Int, programSetInput: ProgramSetInput) : ProgramSetOutput(programSetSize, programSetInput) {
    interface Factory<T:Number> {
        fun create(programSetSize: Int, programSetInput: ProgramSetInput) : NumberProgramSetOutput<T>
    }
    abstract operator fun get(programIndex: Int, outputIndex: Int): T
}

class LongProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput) : NumberProgramSetOutput<Long>(programSetSize, programSetInput) {

    companion object : NumberProgramSetOutput.Factory<Long> {
        override fun create(programSetSize: Int, programSetInput: ProgramSetInput): NumberProgramSetOutput<Long> {
                return LongProgramSetOutput(programSetSize, programSetInput)
        }
    }

    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = longField(programCount, inputSize)
    }

    init {
        storage = Storage(programSetSize, programSetInput.size)
        storage.allocate()
    }

    fun getLong(programIndex: Int, outputIndex: Int): Long {
        return storage.field.get(programIndex, outputIndex)
    }

    private fun setLong(programIndex: Int, inputIndex: Int, value: Long) {
        return storage.field.set(programIndex, inputIndex, value)
    }

    operator fun set(programIndex: Int, inputIndex: Int, value: Long) {
        return setLong(programIndex, inputIndex, value)
    }

    override operator fun get(programIndex: Int, outputIndex: Int): Long {
        return getLong(programIndex, outputIndex)
    }

    override fun emitStore(assembler: Assembler) {
        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.GP_REGISTERS.first()
            assembler.mov(AddressExpression64(base = it), outputRegister)
        }
    }
}

class DoubleProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput) : NumberProgramSetOutput<Double>(programSetSize, programSetInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = doubleField(programCount, inputSize, alignment = 16)
    }

    init {
        storage = Storage(programSetSize, programSetInput.size)
        storage.allocate()
    }

    fun getDouble(programIndex: Int, inputIndex: Int): Double {
        return storage.field.get(programIndex, inputIndex)
    }

    private fun setDouble(programIndex: Int, inputIndex: Int, value: Double) {
        return storage.field.set(programIndex, inputIndex, value)
    }

    override operator fun get(programIndex: Int, outputIndex: Int): Double {
        return getDouble(programIndex, outputIndex)
    }

    operator fun set(programIndex: Int, inputIndex: Int, value: Double) {
        return setDouble(programIndex, inputIndex, value)
    }

    override fun emitStore(assembler: Assembler) {
        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.XMM_REGISTERS.first()
            assembler.movDouble(AddressExpression64(it), outputRegister)
        }
    }

    override fun toString(): String {
        val elementCount = storage.field.dimensions.fold(1) { acc: Int, value: Int ->
            acc * value
        }
        val doubleBuffer = buffer.asDoubleBuffer()
        return DoubleArray(elementCount) { doubleBuffer.get(it)}.contentToString()
    }
}

class FloatProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput) : NumberProgramSetOutput<Float>(
        programSetSize, programSetInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = floatField(programCount, inputSize, alignment = 16)
    }

    init {
        storage = Storage(programSetSize, programSetInput.size)
        storage.allocate()
    }

    fun getFloat(programIndex: Int, inputIndex: Int) = storage.field.get(programIndex, inputIndex)
    private fun setFloat(programIndex: Int, inputIndex: Int, value: Float) = storage.field.set(programIndex, inputIndex, value)
    override operator fun get(programIndex: Int, outputIndex: Int): Float = getFloat(programIndex, outputIndex)
    operator fun set(programIndex: Int, inputIndex: Int, value: Float) = setFloat(programIndex, inputIndex, value)

    override fun emitStore(assembler: Assembler) {
        emitStore(assembler, storage.field.address, 4) {
            val outputRegister = Interpreter.XMM_REGISTERS.first()
            assembler.movFloat(AddressExpression32(it), outputRegister)
        }
    }

    override fun toString(): String {
        val elementCount = storage.field.dimensions.fold(1) { acc: Int, value: Int ->
            acc * value
        }
        val doubleBuffer = buffer.asFloatBuffer()
        return FloatArray(elementCount) { doubleBuffer.get(it)}.contentToString()
    }
}

abstract class VectorProgramSetOutput<T : Number>(programSetSize: Int, programSetInput: ProgramSetInput, val vectorSize: VectorSize) : ProgramSetOutput(
        programSetSize, programSetInput) {

    override val structure: Structure get() = storage
    protected val storage: Storage

    protected class Storage(programCount: Int,
                            inputSize: Int,
                            vectorRegisterType: VectorRegisterType) : Structure() {
        val field = vectorField(intArrayOf(programCount, inputSize), vectorRegisterType)
    }

    init {
        storage = Storage(programSetSize, programSetInput.size, vectorSize.vectorRegisterType)
        storage.allocate()
    }


    abstract operator fun get(programIndex: Int, outputIndex: Int, elementIndex: Int) : T
    abstract operator fun get(programIndex: Int, outputIndex: Int) : Array<T>


    /* pretty much the same for all integer sizes */
    internal fun emitIntegerStore(assembler: Assembler) {

        when(vectorSize) {
            VectorSize.BITS_64  -> {
                val outputRegister = Interpreter.MM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    assembler.movq(AddressExpression64(baseRegister), outputRegister)
                }
            }
            VectorSize.BITS_128 -> {
                val outputRegister = Interpreter.XMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    if (VmovdqaXmmXmmm128.isSupported()) {
                        assembler.vmovdqa(AddressExpression128(baseRegister), outputRegister)
                    } else {
                        assembler.movdqa(AddressExpression128(baseRegister), outputRegister)
                    }
                }
            }
            VectorSize.BITS_256 -> {
                val outputRegister = Interpreter.YMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    assembler.vmovdqa( AddressExpression256(baseRegister), outputRegister)
                }
            }
            VectorSize.BITS_512 -> TODO()
        }
    }

}

class ByteVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Byte>(
        programSetSize, programSetInput, vectorSize) {
    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Byte {
        return getByte(programIndex, outputIndex, elementIndex)
    }

    private fun getByte(programIndex: Int, outputIndex: Int, elementIndex: Int): Byte {
        return storage.field.getByte(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Byte> {
        return Array(vectorSize.byteSize) {
            storage.field.getByte(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitIntegerStore(assembler)
    }
}

class IntVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Int>(programSetSize, programSetInput, vectorSize) {
    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Int {
        return getInt(programIndex, outputIndex, elementIndex)
    }

    private fun getInt(programIndex: Int, outputIndex: Int, elementIndex: Int): Int {
        return storage.field.getInt(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Int> {
        return Array(vectorSize.byteSize / Int.SIZE_BYTES) {
            storage.field.getInt(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitIntegerStore(assembler)
    }
}

class LongVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Long>(programSetSize, programSetInput, vectorSize) {
    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Long {
        return getLong(programIndex, outputIndex, elementIndex)
    }

    private fun getLong(programIndex: Int, outputIndex: Int, elementIndex: Int): Long {
        return storage.field.getLong(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Long> {
        return Array(vectorSize.byteSize / Long.SIZE_BYTES) {
            storage.field.getLong(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitIntegerStore(assembler)
    }
}

class ShortVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Short>(programSetSize, programSetInput, vectorSize) {
    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Short {
        return getShort(programIndex, outputIndex, elementIndex)
    }

    private fun getShort(programIndex: Int, outputIndex: Int, elementIndex: Int): Short {
        return storage.field.getShort(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Short> {
        return Array(vectorSize.byteSize / Short.SIZE_BYTES) {
            storage.field.getShort(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitIntegerStore(assembler)
    }
}

class FloatVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Float>(programSetSize, programSetInput, vectorSize) {

    private fun emitSingleFloatStore(assembler: Assembler) {
        when(vectorSize) {
            VectorSize.BITS_64  -> throw IllegalArgumentException("invalid vector size $vectorSize")
            VectorSize.BITS_128 -> {
                val outputRegister = Interpreter.XMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    if (VmovapsXmmXmmm128.isSupported()) {
                        assembler.vmovaps(AddressExpression128(baseRegister), outputRegister)
                    } else {
                        assembler.movaps(AddressExpression128(baseRegister), outputRegister)
                    }
                }
            }
            VectorSize.BITS_256 -> {
                val outputRegister = Interpreter.YMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    assembler.vmovaps( AddressExpression256(baseRegister), outputRegister)
                }
            }
            VectorSize.BITS_512 -> TODO()
        }
    }

    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Float {
        return getFloat(programIndex, outputIndex, elementIndex)
    }

    private fun getFloat(programIndex: Int, outputIndex: Int, elementIndex: Int): Float {
        return storage.field.getFloat(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Float> {
        return Array(vectorSize.byteSize / 4) {
            storage.field.getFloat(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitSingleFloatStore(assembler)
    }
}


class DoubleVectorProgramSetOutput(programSetSize: Int, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Double>(programSetSize, programSetInput, vectorSize) {

    private fun emitDoubleFloatStore(assembler: Assembler) {
        when(vectorSize) {
            VectorSize.BITS_64  -> throw IllegalArgumentException("invalid vector size $vectorSize")
            VectorSize.BITS_128 -> {
                val outputRegister = Interpreter.XMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    if (VmovapdXmmXmmm128.isSupported()) {
                        assembler.vmovapd(AddressExpression128(baseRegister), outputRegister)
                    } else {
                        assembler.movapd(AddressExpression128(baseRegister), outputRegister)
                    }
                }
            }
            VectorSize.BITS_256 -> {
                val outputRegister = Interpreter.YMM_REGISTERS.first()
                emitStore(assembler, storage.field.address, vectorSize.byteSize) { baseRegister ->
                    assembler.vmovapd( AddressExpression256(baseRegister), outputRegister)
                }
            }
            VectorSize.BITS_512 -> TODO()
        }
    }

    override fun get(programIndex: Int, outputIndex: Int, elementIndex: Int): Double {
        return getDouble(programIndex, outputIndex, elementIndex)
    }

    private fun getDouble(programIndex: Int, outputIndex: Int, elementIndex: Int): Double {
        return storage.field.getDouble(programIndex, outputIndex, elementIndex)
    }

    override fun get(programIndex: Int, outputIndex: Int): Array<Double> {
        return Array(vectorSize.byteSize / 8) {
            storage.field.getDouble(programIndex, outputIndex, it)
        }
    }

    override fun emitStore(assembler: Assembler) {
        emitDoubleFloatStore(assembler)
    }
}
