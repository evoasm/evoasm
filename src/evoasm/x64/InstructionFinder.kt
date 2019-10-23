package evoasm.x64

class InstructionFinder<T : Number>(inputArity: Int, vararg values: T) {

    private val rows = values.toList().chunked(inputArity + 1)
    private val programInput : NumberProgramSetInput<T> = when(val value = values.first()) {
        is Long -> LongProgramSetInput(rows.size, inputArity) as NumberProgramSetInput<T>
        is Double -> DoubleProgramSetInput(rows.size, inputArity) as NumberProgramSetInput<T>
        else -> throw IllegalArgumentException("invalid value class ${value}")
    }
    private val interpreterOptions = InterpreterOptions.DEFAULT
    private val programSet = ProgramSet(interpreterOptions.instructions.size, 1)
    private val programOutput : NumberProgramSetOutput<T> = when(values.first()) {
        is Long -> LongProgramSetOutput(programSet.programCount, programInput) as NumberProgramSetOutput<T>
        is Double -> DoubleProgramSetOutput(programSet.programCount, programInput) as NumberProgramSetOutput<T>
        else -> throw IllegalArgumentException()
    }
    private val interpreter = Interpreter(programSet, programInput, programOutput, options = interpreterOptions)

    init {
        for(i in 0 until programSet.programCount) {
            programSet.set(i, 0, interpreter.getOpcode(i))
        }
    }

    fun find() {
        val instructions = mutableSetOf<InterpreterInstruction>()

        rows.forEachIndexed { rowIndex, row ->
            for (columnIndex in 0 until programInput.arity) {
                programInput[rowIndex, columnIndex] = row[columnIndex]
            }
        }

        interpreter.run()

        outerFor@
        for (i in 0 until programSet.programCount) {
            for(rowIndex in rows.indices) {
                if(!accept(programOutput[i, rowIndex], rows[rowIndex].last())) {
                    continue@outerFor
                }
            }

            val instruction = interpreter.getInstruction(programSet.get(i, 0))!!
            instructions.add(instruction)
        }

        println("FOUND ${instructions.size} instructions:")
        instructions.forEach {
            println("\t${it.instruction.toString()}")
        }
    }

    private fun accept(t: T, last: T): Boolean {
        return Math.abs(t.toDouble() - last.toDouble()) < 0.1;
    }

}

fun main() {
    val finder = InstructionFinder<Long>(1,
                              0b0111, 3,
           0b1, 1,
           0b1111, 4,
           0b11111, 5)
    finder.find()

    val finder2 = InstructionFinder<Long>(2,
                                       2, 2, 4,
                                       16, 4, 20,
                                       100, 10, 110,
                                       144, 12, 156)
    finder2.find()
}
