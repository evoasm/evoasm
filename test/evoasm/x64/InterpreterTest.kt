package evoasm.x64

import kasm.x64.AddRm64R64
import kasm.x64.AddsdXmm0To63Xmmm64
import kasm.x64.SubsdXmm0To63Xmmm64
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class InterpreterTest {

    @Test
    fun runAllInstructions() {
        val programInput = LongProgramInput(1, 2)
        programInput.set(0, 0, 0x1L)
        programInput.set(0,1, 0x2L)
        val defaultOptions = InterpreterOptions.DEFAULT
        val instructionCount = defaultOptions.instructions.size
        println(defaultOptions.instructions.size)
        val options = InterpreterOptions(instructions = defaultOptions.instructions.take(instructionCount), moveInstructions = defaultOptions.moveInstructions)
        println(options.instructions.last())
        val programSet = ProgramSet(1000, options.instructions.size)
        val programSetOutput = LongProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for(i in 0 until programSet.size) {
            for (j in 0 until programSet.programSize) {
                programSet.set(i, j, interpreter.getInterpreterInstruction(j))
            }
        }

//        val buffer = ShortArray(programSet.byteBuffer.bufferSize() / Short.SIZE_BYTES)
//        programSet.byteBuffer.asShortBuffer().get(buffer)
//        println("byte code")
//        println(buffer.contentToString())
//        println("/byte code")

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        println("Output: ${programSetOutput.getLong(0, 0)}")
    }

    @Test
    fun add() {
        val programSize = 10_000
        val expectedOutput = programSize.toLong()
        val programInput = LongProgramInput(1, 2)
        programInput.set(0, 0, 0x0L)
        programInput.set(0, 1, 0x1L)
        val options = InterpreterOptions.DEFAULT
        val programSet = ProgramSet(1, programSize)
        val programSetOutput = LongProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for (j in 0 until programSet.programSize) {
           programSet.set(0, j, interpreter.getInterpreterInstruction(AddRm64R64)!!)
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

    @Test
    fun addDouble() {
        val programSize = 100_000
        val factor = 4.0;
        val expectedOutput = programSize.toDouble()
        val programInput = DoubleProgramInput(2, 2)

        programInput[0, 0] = 0.0
        programInput[0, 1] = 1.0
        programInput[1, 0] = 0.0
        programInput[1, 1] = factor


        val options = InterpreterOptions.DEFAULT
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = DoubleProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        println("OUTPUT SIZE ${programSetOutput.size}")

        for (j in 0 until programSet.programSize) {
            programSet.set(0, j, interpreter.getInterpreterInstruction(AddsdXmm0To63Xmmm64)!!)
        }

        for (j in 0 until programSet.programSize) {
            programSet.set(1, j, interpreter.getInterpreterInstruction(SubsdXmm0To63Xmmm64)!!)
        }

        run {
            val output = programSetOutput.getDouble(0, 0)
            assertNotEquals(expectedOutput, output)
        }

        run {
            val output = programSetOutput.getDouble(1, 0)
            assertNotEquals(expectedOutput, -output)
        }


        val measurements = interpreter.runAndMeasure()
        println(measurements)

        run {
            assertEquals(expectedOutput, programSetOutput[0, 0])
            assertEquals(expectedOutput * factor, programSetOutput[0, 1])
        }

        run {
            assertEquals(-expectedOutput, programSetOutput[1, 0])
            assertEquals(-expectedOutput * factor, programSetOutput[1, 1])
        }
    }
}