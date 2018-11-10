package evoasm.x64

import kasm.x64.*
import kasm.x64.Instruction

data class InterpreterOptions(val instructions: List<Instruction>, val allowUnsupportedInstructions: Boolean = false) {
    companion object {
        private val DEFAULT_INSTRUCTIONS = listOf(
                AddR64Rm64,
                SubR64Rm64,
                ImulR64Rm64,
                HsubpdXmmXmmm128)

        val DEFAULT = InterpreterOptions(instructions = DEFAULT_INSTRUCTIONS)
    }

    init {
        if(!allowUnsupportedInstructions) {
            instructions.forEach {
                require(it.isSupported(), {"${it} is not supported"})
            }
        }
    }
}