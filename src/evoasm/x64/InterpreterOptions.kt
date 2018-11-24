package evoasm.x64

import kasm.x64.Instruction

class InterpreterOptions(instructions: List<Instruction>,
                         val allowUnsupportedInstructions: Boolean = false,
                         val safeDivision: Boolean = true,
                         val divisionByZeroResult: Long = 0) {
    companion object {
        private fun supportedInstruction(i0: Instruction,
                                         i1: Instruction,
                                         allowUnsupportedInstructions: Boolean): Instruction {
            if (allowUnsupportedInstructions || i1.isSupported()) return i1
            return i0
        }

        val defaultInstructions: List<Instruction> get() {
            return InstructionGroup.values().fold(mutableListOf<Instruction>()) {acc, instructionGroup ->
                acc.addAll(instructionGroup.instructions)
                acc
            }
        }


        val DEFAULT = InterpreterOptions(instructions = defaultInstructions)
    }

    val instructions: List<Instruction>

    init {
        this.instructions = if (!allowUnsupportedInstructions) {
            instructions.filter {
                val supported = it.isSupported()

                if (!supported) println("filtering out unsupported instruction ${it}")


                supported
            }
        } else {
            instructions
        }
    }

}