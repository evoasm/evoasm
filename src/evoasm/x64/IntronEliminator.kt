package evoasm.x64

import kasm.x64.*

class IntronEliminator(val program: Program, val outputRegister: XmmRegister, val outputRange: BitRange, val interpreter: Interpreter) {

//    private val markedGpRegister64 = enumSetOf<GpRegister64>()
    private val uncoveredRegisters = mutableMapOf<Register, BitRange>()
    private var operandRegisters = emptyList<Register>()
    private val markedInstructionIndices = BooleanArray(program.size)
    private var instructionTracer = object : SimpleInstructionTracer() {
        override fun traceImplicitRead(addressExpression: AddressExpression) = throw NotImplementedError()
        override fun traceWrite(addressExpression: AddressExpression, index: Int) = throw NotImplementedError()
        override fun traceRead(addressExpression: AddressExpression, index: Int) = throw NotImplementedError()
        override fun traceImplicitWrite(addressExpression: AddressExpression) = throw NotImplementedError()

        override fun traceImplicitRead(immediate: Int) {}
        override fun traceRead(immediate: Long, index: Int, size: BitSize) {}

        //TODO: handle status fields (analogous to registers)
        override fun traceWrite(statusOrControlField: StatusOrControlField) {}
        override fun traceRead(statusOrControlField: StatusOrControlField) {}

        private val readRegisters = mutableMapOf<Register, BitRange>()
        var currentInstructionIndex : Int = 0

        override fun beginTracing() {
            readRegisters.clear()
        }

        private fun traceRead(register: Register, range: BitRange) {
            readRegisters[register] = range
        }

        private fun traceWrite(register: Register, range: BitRange) {
            val uncoveredRange = uncoveredRegisters[register]

            if(uncoveredRange != null) {
                if(range == uncoveredRange || range.contains(uncoveredRange)) {
                    uncoveredRegisters.remove(register)
                    uncoveredRegisters.putAll(readRegisters)
                    markedInstructionIndices[currentInstructionIndex] = true
                }
            /* FIXME: this does not handle e.g. uncovered 0..255 and 64..127 to be covered */
            } else if(uncoveredRange == BitRange.BITS_0_15 && range == BitRange.BITS_8_15) {
                uncoveredRegisters[register] = BitRange.BITS_0_7
                uncoveredRegisters.putAll(readRegisters)
                markedInstructionIndices[currentInstructionIndex] = true
            } else if(uncoveredRange == BitRange.BITS_0_127 && range == BitRange.BITS_64_127) {
                uncoveredRegisters[register] = BitRange.BITS_0_63
                uncoveredRegisters.putAll(readRegisters)
                markedInstructionIndices[currentInstructionIndex] = true
            }
        }

        override fun traceRead(register: Register, index: Int, range: BitRange) {
            traceRead(register, range)
        }

        override fun traceImplicitRead(register: Register, range: BitRange) {
            traceRead(register, range)
        }

        override fun traceImplicitWrite(register: Register, range: BitRange, always: Boolean) {
            traceWrite(register, range)
        }

        override fun traceWrite(register: Register, index: Int, range: BitRange, always: Boolean) {
            traceWrite(register, range)
        }
    }

    private var instructionParameters = object : InstructionParameters {
        override fun getXmmRegister(index: Int, isRead: Boolean, isWritten: Boolean): XmmRegister {
            return operandRegisters[index] as XmmRegister
        }

        override fun useSibd() = false
    }


    init {
        uncoveredRegisters.put(outputRegister, outputRange)
    }

    fun run() : Program {
        for(i in program.size - 1 downTo 0) {
            val opcode = program[i]
            println("handling opc ${opcode}")
            val interpreterInstruction = interpreter.getInstruction(opcode)!!
//            operandRegisters = interpreterInstruction.instructionParameters
            instructionTracer.currentInstructionIndex = i
            interpreterInstruction.instruction.trace(instructionTracer, interpreterInstruction.instructionParameters)
        }
        val intronFreeProgramSize = markedInstructionIndices.count { it }
        val p = Program(intronFreeProgramSize)
        markedInstructionIndices.foldIndexed(0) { sourceIndex, destinationIndex, marked ->
            if(marked) {
                p[destinationIndex] = program[sourceIndex]
                destinationIndex + 1
            } else {
                destinationIndex
            }
        }
        return p
    }
}