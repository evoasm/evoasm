package evoasm.x64

import kasm.x64.Instruction

class InstructionFinder(inputArity: Int) {

    private val programInput = LongProgramInput(inputArity, 1)
    private val interpreterOptions = InterpreterOptions.DEFAULT
    private val programSet = ProgramSet(interpreterOptions.instructions.size, 1)
    private val programOutput = LongProgramSetOutput(programSet, programInput)
    private val interpreter = Interpreter(programSet, programInput, programOutput, options = interpreterOptions)

    init {
        for(i in 0 until programSet.size) {
            programSet.set(i, 0, interpreter.getInterpreterInstruction(i))
        }
    }

    fun find(vararg values: Long) {
        val rows = values.toList().chunked(programInput.arity + 1)
        val instructions = mutableSetOf<Instruction>()

        for(row in rows) {
            val rowInstructions = mutableSetOf<Instruction>()
            val output = row.last()
            for(i in 0 until programInput.arity) {
                programInput.set(i, row[i])
            }
            interpreter.run()
            for (i in 0 until programSet.size) {
                if(programOutput.getLong(i, 0) == output) {
                    val instruction = interpreter.getInstruction(programSet.get(i, 0))
                    rowInstructions.add(instruction)
                }
            }
            if(instructions.isEmpty()) {
                instructions.addAll(rowInstructions)
            } else {
                instructions.retainAll(rowInstructions)
            }
        }

        println("FOUND: $instructions")
    }

}

fun main() {
    val f = InstructionFinder(1)
    f.find(0b0111, 3,
           0b1, 1,
           0b1111, 4,
           0b11111, 5)
}
