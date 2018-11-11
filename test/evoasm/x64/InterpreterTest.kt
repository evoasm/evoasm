package evoasm.x64

import org.junit.jupiter.api.Test

internal class InterpreterTest {

    @Test
    fun run() {
        val programInput = ProgramInput(2)
        programInput.set(0, 0x1L)
        programInput.set(1, 0x2L)
        val x = 300
        println(InterpreterOptions.DEFAULT_INSTRUCTIONS[x - 1])
        val options = InterpreterOptions(instructions = InterpreterOptions.DEFAULT_INSTRUCTIONS.take(x))
        val programSet = ProgramSet(x, 1)
        val programSetOutput = ProgramSetOutput(programSet)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for(i in 0 until programSet.size) {
            for (j in 0 until programSet.programSize) {
                programSet.setInstruction(i, j, interpreter.getInstruction(i).also { println("setting instr ${it}") })
            }
        }
        interpreter.run()
        println(programSetOutput.getLong(0, 0))

//        val program = ProgramSet()
//        val interpreter = Interpreter()
//        interpreter.run(program)
    }
}