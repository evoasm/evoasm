package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.ext.log2
import kasm.x64.AddressExpression64
import kasm.x64.Assembler

abstract class ProgramSetOutput(programSet: ProgramSet, programSetInput: ProgramSetInput) {
    protected abstract val structure: Structure
    internal val buffer get() = structure.buffer
    val size = programSetInput.size

    internal abstract fun emitStore(assembler: Assembler)

    protected fun emitStore(assembler: Assembler, address: Address, elementSize: Int, block: (AddressExpression64) -> Unit) {
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
                block(AddressExpression64(Interpreter.SCRATCH_REGISTER1))
            } else {
                // TODO: if elementSize == 1, can use scale, if address is 32-bit can use displacement
                sal(Interpreter.SCRATCH_REGISTER1, log2(elementSize).toByte())
                mov(Interpreter.SCRATCH_REGISTER2, address)
                add(Interpreter.SCRATCH_REGISTER1,
                    Interpreter.SCRATCH_REGISTER2)
                block(AddressExpression64(base = Interpreter.SCRATCH_REGISTER1))
            }
        }
    }
}

abstract class ValueProgramSetOutput<T>(programSet: ProgramSet, programSetInput: ProgramSetInput) : ProgramSetOutput(programSet, programSetInput) {
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
            assembler.movDouble(it, outputRegister)
        }
    }
}