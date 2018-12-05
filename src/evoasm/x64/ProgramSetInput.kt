package evoasm.x64

import kasm.Address
import kasm.Structure
import kasm.x64.AddressExpression64
import kasm.x64.Assembler
import kasm.x64.Register

abstract class ProgramSetInput(val size: Int, val arity: Int) {
    init {
        require(size > 0)
        require(size <= Short.MAX_VALUE)
        require(arity > 0)
    }
    abstract fun emitLoad(assembler: Assembler)

    protected fun <R: Register> emitLoad(assembler: Assembler, address: Address, registers: List<R>, elementSize: Int, block: (R, AddressExpression64) -> Unit) {
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
                block(register, AddressExpression64(base = Interpreter.SCRATCH_REGISTER1))
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

abstract class ValueProgramSetInput<T>(size: Int, arity: Int) : ProgramSetInput(size, arity) {
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
                 Interpreter.GP_REGISTERS, Long.SIZE_BYTES) { register, addressExpression ->
            assembler.mov(register, addressExpression)
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

    override operator fun set(rowIndex: Int, columnIndex: Int, value: Double) {
        storage.field.set(rowIndex, columnIndex, value)
    }

    override fun emitLoad(assembler: Assembler) {
        emitLoad(assembler, storage.field.address,
                 Interpreter.XMM_REGISTERS, Long.SIZE_BYTES) { register, addressExpression ->
            assembler.movDouble(register, addressExpression)
        }
    }
}