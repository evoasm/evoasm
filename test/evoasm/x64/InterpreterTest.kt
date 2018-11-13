package evoasm.x64

import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

internal class InterpreterTest {

    @Test
    fun run() {
        val programInput = ProgramInput(2)
        programInput.set(0, 0x1L)
        programInput.set(1, 0x2L)
        val x = 1200
        val options_ = InterpreterOptions.DEFAULT
        println(options_.instructions.size)
        val options = InterpreterOptions(instructions = options_.instructions)
        println(options.instructions.last())
        val programSet = ProgramSet(1, options.instructions.size)
        val programSetOutput = ProgramSetOutput(programSet)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for(i in 0 until programSet.size) {
            for (j in 0 until programSet.programSize) {
                programSet.setInstruction(i, j, interpreter.getInstruction(j))
            }
        }

        val nanoTime = measureNanoTime {
                interpreter.run()
        }

        val elapsedSeconds = nanoTime / 1E9.toDouble()
        println("$elapsedSeconds seconds")
        println("${programSet.instructionCount / elapsedSeconds} ips")

        println(programSetOutput.getLong(0, 0))

//        val program = ProgramSet()
//        val interpreter = Interpreter()
//        interpreter.run(program)
    }
}