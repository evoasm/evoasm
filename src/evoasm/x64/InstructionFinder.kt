package evoasm.x64

import kasm.x64.Instruction

class InstructionFinder(inputArity: Int, vararg values: Long) {

    private val rows = values.toList().chunked(inputArity + 1)
    private val programInput = LongProgramInput(rows.size, inputArity)
    private val interpreterOptions = InterpreterOptions.DEFAULT
    private val programSet = ProgramSet(interpreterOptions.instructions.size, 1)
    private val programOutput = LongProgramSetOutput(programSet, programInput)
    private val interpreter = Interpreter(programSet, programInput, programOutput, options = interpreterOptions)

    init {
        for(i in 0 until programSet.size) {
            programSet.set(i, 0, interpreter.getInterpreterInstruction(i))
        }
    }

    fun find() {
        val instructions = mutableSetOf<Instruction>()

        rows.forEachIndexed { rowIndex, row ->
            for (columnIndex in 0 until programInput.arity) {
                programInput[rowIndex, columnIndex] = row[columnIndex]
            }
        }

        interpreter.run()

        outerFor@
        for (i in 0 until programSet.size) {
            for(rowIndex in rows.indices) {
                if(programOutput.getLong(i, rowIndex) != rows[rowIndex].last()) {
                    continue@outerFor
                }
            }

            val instruction = interpreter.getInstruction(programSet.get(i, 0))
            instructions.add(instruction)
        }

        println("FOUND: $instructions")
    }

}

fun main() {
    val f = InstructionFinder(1,
                              0b0111, 3,
           0b1, 1,
           0b1111, 4,
           0b11111, 5)
    f.find()
}
