package evoasm.x64

import kasm.x64.AddRm64R64
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
        val programSet = ProgramSet(1000, options.instructions.size)
        val programSetOutput = ProgramSetOutput(programSet)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for(i in 0 until programSet.size) {
            for (j in 0 until programSet.programSize) {
                programSet.setInstruction(i, j, interpreter.getInstruction(j))
            }
        }

        val buffer = ShortArray(programSet.byteBuffer.capacity() / Short.SIZE_BYTES)
        programSet.byteBuffer.asShortBuffer().get(buffer)
        println("byte code")
        println(buffer.contentToString())
        println("/byte code")

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        println("Output: ${programSetOutput.getLong(0, 0)}")
    }

    @Test
    fun add() {
        val programSize = 10_000
        val expectedOutput = programSize.toLong()
        val programInput = ProgramInput(2)
        programInput.set(0, 0x0L)
        programInput.set(1, 0x1L)
        val options = InterpreterOptions.DEFAULT
        val programSet = ProgramSet(1, programSize)
        val programSetOutput = ProgramSetOutput(programSet)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for (j in 0 until programSet.programSize) {
           programSet.setInstruction(0, j, interpreter.getInstruction(AddRm64R64)!!)
        }

        run {
            val output = programSetOutput.getLong(0, 0)
            assertNotEquals(expectedOutput, output)
        }

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        run {
            val output = programSetOutput.getLong(0, 0)
            assertEquals(expectedOutput, output)
        }
    }
}