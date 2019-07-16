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

        println("FOUND: $instructions")
    }

    private fun accept(t: T, last: T): Boolean {
        return Math.abs(t.toDouble() - last.toDouble()) < 0.1;
    }

}

fun main() {
    val f = InstructionFinder<Long>(1,
                              0b0111, 3,
           0b1, 1,
           0b1111, 4,
           0b11111, 5)
    f.find()

    val f2 = InstructionFinder<Double>(1,
                                       4.0, 2.0,
                                       16.0, 4.0,
                                       100.0, 10.0,
                                       144.0, 12.0)
    f2.find()

    val f3 = InstructionFinder<Double>(2,
                                       2.0, 2.0, 4.0,
                                       16.0, 4.0, 20.0,
                                       100.0, 10.0, 110.0,
                                       144.0, 12.0, 156.0)
    f3.find()
}
