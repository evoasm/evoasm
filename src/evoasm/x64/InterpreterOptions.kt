package evoasm.x64

import kasm.x64.*

class InterpreterOptions(instructions: List<Instruction> = defaultInstructions,
                         val allowUnsupportedInstructions: Boolean = false,
                         val safeDivision: Boolean = true,
                         val moveInstructions: List<MoveInstruction> = defaultMoveInstructions,
                         val movesGenerator: (MoveInstruction, List<Register>) -> Sequence<Pair<Register, Register>> = ::defaultMovesGenerator) {

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

        fun defaultMovesGenerator(moveInstruction: MoveInstruction, registers: List<Register>): Sequence<Pair<Register, Register>> {
            val list = mutableListOf<Pair<Register, Register>>()

            val maxOperandRegisterCount = when(registers.first()) {
               is XmmRegister, is YmmRegister -> 3
               else -> 2
            }

            for (i in 0 until maxOperandRegisterCount) {
                val destinationRegister = registers[i]

                for(j in 0 until maxOperandRegisterCount) {
                    val sourceRegister = registers[j]
                    list.add(destinationRegister to sourceRegister)
                }
            }

            for (i in maxOperandRegisterCount until registers.lastIndex) {
               list.add(registers[i] to registers[i % maxOperandRegisterCount])
               list.add(registers[(i + 1) % maxOperandRegisterCount] to registers[i])
            }

            // Hmmm, this gives operandsRegisterCount**2 + 2 * (n - operandsRegisterCount) instructions
            // i.e. 9 + 2 * 13 = 33 for e.g. XMM registers

            return list.asSequence()
        }
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

