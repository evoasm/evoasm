package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.ext.log2
import kasm.x64.*

abstract class ProgramSetOutput(programSet: ProgramSet, programSetInput: ProgramSetInput) {
    protected abstract val structure: Structure
    internal val buffer get() = structure.buffer
    val size = programSetInput.size

    internal abstract fun emitStore(assembler: Assembler)

    protected fun emitStore(assembler: Assembler, address: Address, elementSize: Int, block: (GpRegister64) -> Unit) {
        require(elementSize % 2 == 0)

        with(assembler) {
            mov(Interpreter.SCRATCH_REGISTER1, Interpreter.COUNTERS_REGISTER)

            if(size != 1) {
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
                sal(Interpreter.SCRATCH_REGISTER1, log2(elementSize).toByte())
                mov(Interpreter.SCRATCH_REGISTER2, address)
                add(Interpreter.SCRATCH_REGISTER1,
                    Interpreter.SCRATCH_REGISTER2)
                block(Interpreter.SCRATCH_REGISTER1)
            }
        }
    }
}

abstract class ValueProgramSetOutput<T : Number>(programSet: ProgramSet, programSetInput: ProgramSetInput) : ProgramSetOutput(programSet, programSetInput) {
    abstract operator fun get(programIndex: Int, outputIndex: Int): T
}

class LongProgramSetOutput(programSet: ProgramSet, programSetInput: ProgramSetInput) : ValueProgramSetOutput<Long>(programSet, programSetInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = longField(programCount, inputSize)
    }

    init {
        storage = Storage(programSet.size, programSetInput.size)
        storage.allocate()
    }

    fun getLong(programIndex: Int, outputIndex: Int): Long {
        return storage.field.get(programIndex, outputIndex)
    }

    override operator fun get(programIndex: Int, outputIndex: Int): Long {
        return getLong(programIndex, outputIndex)
    }

    override fun emitStore(assembler: Assembler) {
        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.GP_REGISTERS.first()
            assembler.mov(it, outputRegister)
        }
    }
}

class DoubleProgramSetOutput(programSet: ProgramSet, programSetInput: ProgramSetInput) : ValueProgramSetOutput<Double>(programSet, programSetInput) {
    override val structure: Structure get() = storage
    private val storage: Storage

    private class Storage(programCount: Int, inputSize: Int) : Structure() {
        val field = doubleField(programCount, inputSize, alignment = 16)
    }

    init {
        storage = Storage(programSet.size, programSetInput.size)
        storage.allocate()
    }

    fun getDouble(programIndex: Int, columnIndex: Int): Double {
        return storage.field.get(programIndex, columnIndex)
    }

    override operator fun get(programIndex: Int, outputIndex: Int): Double {
        return getDouble(programIndex, outputIndex)
    }

    override fun emitStore(assembler: Assembler) {
        emitStore(assembler, storage.field.address, 8) {
            val outputRegister = Interpreter.XMM_REGISTERS.first()
            assembler.movDouble(AddressExpression64(it), outputRegister)
        }
    }
}

abstract class VectorProgramSetOutput<T : Number>(programSet: ProgramSet, programSetInput: ProgramSetInput, val vectorSize: VectorSize) : ProgramSetOutput(programSet, programSetInput) {

    override val structure: Structure get() = storage
    protected val storage: Storage

    protected class Storage(programCount: Int,
                            inputSize: Int,
                            vectorRegisterType: VectorRegisterType) : Structure() {
        val field = vectorField(intArrayOf(programCount, inputSize), vectorRegisterType)
    }

    init {
        storage = Storage(programSet.size, programSet.size, vectorSize.vectorRegisterType)
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

class ByteVectorProgramSetOutput(programSet: ProgramSet, programSetInput: ProgramSetInput, vectorSize: VectorSize) : VectorProgramSetOutput<Byte>(programSet, programSetInput, vectorSize) {
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
