package evoasm.x64

import kasm.x64.*
import org.junit.jupiter.api.Test


import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class InterpreterTest {

    @Test
    fun runAllInstructions() {
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x1L
        programInput[0, 1] = 0x2L
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
                programSet[i, j] = interpreter.getInterpreterInstruction(j)
            }
        }

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        println("Output: ${programSetOutput[0, 0]}")
    }


    @Test
    fun addLong() {
        val programSize = 5_500_000
        val expectedOutput = programSize.toLong()
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x0L
        programInput[0, 1] = 0x1L
        //val options = InterpreterOptions.DEFAULT
        val options = InterpreterOptions(moveInstructions = emptyList(), instructions = InstructionGroup.ARITHMETIC_GP64_INSTRUCTIONS.instructions)
        val programSet = ProgramSet(1, programSize)
        val programSetOutput = LongProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for (j in 0 until programSet.programSize) {
            programSet[0, j] = interpreter.getInterpreterInstruction(AddRm64R64)!!
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
    fun addSubLong() {
        addSubLong(InterpreterOptions.defaultInstructions)
    }

    @Test
    fun addSubLongOnlyGpInstructions() {
        addSubLong(InstructionGroup.ARITHMETIC_GP64_INSTRUCTIONS.instructions)
    }

    fun addSubLong(instructions: List<Instruction>) {
        val programSize = 10_000
        val expectedOutput = programSize.toLong()
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x0L
        programInput[0, 1] = 0x1L
        val options = InterpreterOptions(instructions = instructions)
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = LongProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        for (i in 0 until programSet.programSize) {
            programSet[0, i] = interpreter.getInterpreterInstruction(AddRm64R64)!!
        }

        for (i in 0 until programSet.programSize) {
            programSet[1, i] = interpreter.getInterpreterInstruction(SubRm64R64)!!
        }

        run {
            val output = programSetOutput.getLong(0, 0)
            assertNotEquals(expectedOutput, output)
        }

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        run {
            assertEquals(expectedOutput, programSetOutput.getLong(0, 0))
            assertEquals(-expectedOutput, programSetOutput.getLong(1, 0))
        }
    }

    @Test
    fun addSubByteVector() {
        val programSize = 10
        val programInput = ByteVectorProgramSetInput(1, 3, VectorSize.BITS_256)
        val expectedOutput = Array(programInput.vectorSize.byteSize) { ((it % 4) * programSize / 2).toByte() }
        programInput[0, 0] = Array(programInput.vectorSize.byteSize){0.toByte()}
        programInput[0, 1] = Array(programInput.vectorSize.byteSize){0.toByte()}
        programInput[0, 2] = Array(programInput.vectorSize.byteSize){(it % 4).toByte()}
        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_B_AVX_YMM_INSTRUCTIONS.instructions, moveInstructions = listOf(VmovapdYmmYmmm256))
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = ByteVectorProgramSetOutput(programSet, programInput, VectorSize.BITS_256)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        val interpreterMoveInstruction = interpreter.getInterpreterMoveInstruction(VmovapdYmmYmmm256, YmmRegister.YMM1, YmmRegister.YMM0)!!

        for (i in 0 until programSet.programSize step 2) {
            programSet[0, i] = interpreter.getInterpreterInstruction(VpaddbYmmYmmYmmm256)!!
            programSet[0, i + 1] = interpreterMoveInstruction
        }

        for (i in 0 until programSet.programSize step 2) {
            programSet[1, i] = interpreter.getInterpreterInstruction(VpsubbYmmYmmYmmm256)!!
            programSet[1, i + 1] = interpreterMoveInstruction
        }

        run {
            val output = programSetOutput[0, 0]
            assertNotEquals(expectedOutput, output)
        }

        val measurements = interpreter.runAndMeasure()
        println(measurements)

        run {
            assertEquals(expectedOutput.toList(), programSetOutput[0, 0].toList())
            assertEquals<List<Byte>>(expectedOutput.map{(-it).toByte()}, programSetOutput.get(1, 0).toList())
        }
    }

    @Test
    fun addDouble() {
        val programSize = 100_000
        val expectedOutput = programSize.toDouble()
        val programInput = DoubleProgramSetInput(1, 2)

        programInput[0, 0] = 0.0
        programInput[0, 1] = 1.0

        val options = InterpreterOptions.DEFAULT
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = DoubleProgramSetOutput(programSet, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        for (j in 0 until programSet.programSize) {
            programSet[0, j] = interpreter.getInterpreterInstruction(AddsdXmm0To63Xmmm64)!!
        }

        run {
            val output = programSetOutput.getDouble(0, 0)
            assertNotEquals(expectedOutput, output)
        }


        val measurements = interpreter.runAndMeasure()
        println(measurements)

        run {
            assertEquals(expectedOutput, programSetOutput[0, 0])
        }
    }

    @Test
    fun addSubDoubleWithMultipleInputs() {
        val programSize = 100_000
        val factor = 4.0;
        val expectedOutput = programSize.toDouble()
        val programInput = DoubleProgramSetInput(2, 2)

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
            programSet[0, j] = interpreter.getInterpreterInstruction(AddsdXmm0To63Xmmm64)!!
        }

        for (j in 0 until programSet.programSize) {
            programSet[1, j] = interpreter.getInterpreterInstruction(SubsdXmm0To63Xmmm64)!!
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