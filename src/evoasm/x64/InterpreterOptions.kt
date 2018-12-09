package evoasm.x64

import kasm.x64.Instruction
import kasm.x64.MovR64Rm64
import kasm.x64.MoveInstruction
import kasm.x64.MovsdXmmm64Xmm

class InterpreterOptions(instructions: List<Instruction> = defaultInstructions,
                         val allowUnsupportedInstructions: Boolean = false,
                         val safeDivision: Boolean = true,
                         val moveInstructions: List<MoveInstruction> = defaultMoveInstructions) {

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

        val defaultMoveInstructions: List<MoveInstruction> get() {
            return listOf(MovR64Rm64, MovsdXmmm64Xmm)
        }

        val DEFAULT = InterpreterOptions()
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