package evoasm.x64

import kasm.x64.*
import org.junit.jupiter.api.Test
import kasm.x64.GpRegister64.*
import kasm.x64.XmmRegister.*
import kasm.x64.YmmRegister.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class InterpreterTest {

    private fun <T> operandRegisters(size: Int, registers: List<T>) : List<List<T>> {
        return List(size){ listOf(registers[it]) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["false", "true"])
    fun runAllInstructions(compressOpcodes: Boolean) {
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x1L
        programInput[0, 1] = 0x2L
        val defaultOptions = InterpreterOptions.DEFAULT
        val instructionCount = defaultOptions.instructions.size
        println(defaultOptions.instructions.size)
        val options = InterpreterOptions(instructions = defaultOptions.instructions.take(instructionCount),
                                         compressOpcodes = compressOpcodes,
                                         moveInstructions = defaultOptions.moveInstructions,
                                         xmmOperandRegisters = operandRegisters(4, Interpreter.XMM_REGISTERS),
                                         ymmOperandRegisters = operandRegisters(4, Interpreter.YMM_REGISTERS),
                                         mmOperandRegisters = operandRegisters(3, Interpreter.MM_REGISTERS),
                                         gp64OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS),
                                         gp32OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister32}),
                                         gp16OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister16}),
                                         gp8OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister8}),
                                         unsafe = false
                                        )
        println(options.instructions.last())
        val programSet = ProgramSet(1000, options.instructions.size)
        val programSetOutput = LongProgramSetOutput(programSet.programCount, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for(i in 0 until programSet.programCount) {
            for (j in 0 until programSet.programSize) {
                programSet[i, j] = interpreter.getOpcode(j)
            }
        }

        val measurements = interpreter.runAndMeasure()
        println(measurements)
        println(interpreter.statistics)

        println("Output: ${programSetOutput[0, 0]}")
    }


    @ParameterizedTest
    @ValueSource(strings = ["false", "true"])
    fun addLong(compressOpcodes: Boolean) {
        val programSize = 5_500_000
        val expectedOutput = programSize.toLong()
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x0L
        programInput[0, 1] = 0x1L
        //val options = InterpreterOptions.DEFAULT
        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_GP64_INSTRUCTIONS.instructions,
                                         compressOpcodes = compressOpcodes,
                                         moveInstructions = emptyList(),
                                         gp64OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS),
                                         unsafe = false)
        val programSet = ProgramSet(1, programSize)
        val programSetOutput = LongProgramSetOutput(programSet.programCount, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)
        for (j in 0 until programSet.programSize) {
            programSet[0, j] = interpreter.getOpcode(AddRm64R64, RAX, RBX)!!
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

//    @TestFactory
//    fun addSubLong(): List<DynamicTest> {
//        return listOf(false, true).flatMap { compressOpcodes ->
//                    listOf(InterpreterOptions.defaultInstructions,InstructionGroup.ARITHMETIC_GP64_INSTRUCTIONS.instructions).map { instructions ->
//                        dynamicTest(listOf(compressOpcodes).toString()) {
//                            addSubLong(compressOpcodes, instructions)
//                        }
//                    }
//                }
//    }


    enum class TestInstructionGroup {
        ALL_INSTRUCTIONS {
            override val instructions: List<Instruction> get() = InstructionGroup.all
        },

        ARITHMETIC_GP64_INSTRUCTIONS {
            override val instructions: List<Instruction> get() = InstructionGroup.ARITHMETIC_GP64_INSTRUCTIONS.instructions
        }

        ;

        abstract val instructions : List<Instruction>
    }


    @ParameterizedTest
    @CsvSource(
            "false, ALL_INSTRUCTIONS",
            "true, ALL_INSTRUCTIONS",
            "false, ARITHMETIC_GP64_INSTRUCTIONS",
            "true, ARITHMETIC_GP64_INSTRUCTIONS"
               )
    fun addSubLong(compressOpcodes: Boolean, instructionGroup: TestInstructionGroup) {
        val programSize = 10_000
        val expectedOutput = programSize.toLong()
        val programInput = LongProgramSetInput(1, 2)
        programInput[0, 0] = 0x0L
        programInput[0, 1] = 0x1L
        val options = InterpreterOptions(instructions = instructionGroup.instructions,
                                         compressOpcodes = compressOpcodes,
                                         xmmOperandRegisters = operandRegisters(4, Interpreter.XMM_REGISTERS),
                                         ymmOperandRegisters = operandRegisters(4, Interpreter.YMM_REGISTERS),
                                         mmOperandRegisters = operandRegisters(3, Interpreter.MM_REGISTERS),
                                         gp64OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS),
                                         gp32OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister32}),
                                         gp16OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister16}),
                                         gp8OperandRegisters = operandRegisters(3, Interpreter.GP_REGISTERS.map{it.subRegister8}),
                                         unsafe = false
                                        )
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = LongProgramSetOutput(programSet.programCount, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        for (i in 0 until programSet.programSize) {
            programSet[0, i] = interpreter.getOpcode(AddRm64R64, RAX, RBX)!!
        }

        for (i in 0 until programSet.programSize) {
            programSet[1, i] = interpreter.getOpcode(SubRm64R64, RAX, RBX)!!
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
        val elementCount = programInput.vectorSize.byteSize
        val expectedOutput = Array(elementCount) { ((it % 4) * programSize / 2).toByte() }
        programInput[0, 0] = Array(elementCount){0.toByte()}
        programInput[0, 1] = Array(elementCount){0.toByte()}
        programInput[0, 2] = Array(elementCount){(it % 4).toByte()}
        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_B_AVX_YMM_INSTRUCTIONS.instructions,
                                         moveInstructions = listOf(VmovdqaYmmYmmm256),
                                         unsafe = false)
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = ByteVectorProgramSetOutput(programSet.programCount, programInput, VectorSize.BITS_256)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        val interpreterMoveInstruction = interpreter.getOpcode(VmovdqaYmmYmmm256, YMM1, YMM0)!!

        for (i in 0 until programSet.programSize step 2) {
            programSet[0, i] = interpreter.getOpcode(VpaddbYmmYmmYmmm256, YMM0, YMM1, YMM2)!!
            programSet[0, i + 1] = interpreterMoveInstruction
        }

        for (i in 0 until programSet.programSize step 2) {
            programSet[1, i] = interpreter.getOpcode(VpsubbYmmYmmYmmm256, YMM0, YMM1, YMM2)!!
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
            assertEquals(expectedOutput.map{(-it).toByte()}, programSetOutput.get(1, 0).toList())
        }
    }


    @Test
    fun addSubIntVector() {
        val programSize = 10_000_000
        val programInput = IntVectorProgramSetInput(1, 3, VectorSize.BITS_256)
        val elementCount = programInput.vectorSize.byteSize / Int.SIZE_BYTES
        val expectedOutput = Array(elementCount) { ((it % 4) * programSize / 2) }
        programInput[0, 0] = Array(elementCount){ 0 }
        programInput[0, 1] = Array(elementCount){ 0 }
        programInput[0, 2] = Array(elementCount){ (it % 4) }
        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_D_AVX_YMM_INSTRUCTIONS.instructions,
                                         moveInstructions = listOf(VmovdqaYmmYmmm256),
                                         unsafe = false)
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = IntVectorProgramSetOutput(programSet.programCount, programInput, VectorSize.BITS_256)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        val interpreterMoveInstruction = interpreter.getOpcode(VmovdqaYmmYmmm256, YMM1, YMM0)!!

        for (i in 0 until programSet.programSize step 2) {
            programSet[0, i] = interpreter.getOpcode(VpadddYmmYmmYmmm256, YMM0, YMM1, YMM2)!!
            programSet[0, i + 1] = interpreterMoveInstruction
        }

        for (i in 0 until programSet.programSize step 2) {
            programSet[1, i] = interpreter.getOpcode(VpsubdYmmYmmYmmm256, YMM0, YMM1, YMM2)!!
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
            assertEquals(expectedOutput.map{ (-it) }, programSetOutput.get(1, 0).toList())
        }
    }

    @Test
    fun addSubFloatVector() {
        val programSize = 1000
        val programInput = FloatVectorProgramSetInput(1, 3, VectorSize.BITS_256)
        val elementCount = programInput.vectorSize.byteSize / 4
        val expectedOutput = Array(elementCount) { ((it % 4) * programSize / 2).toFloat() }
        programInput[0, 0] = Array(elementCount){0.toFloat()}
        programInput[0, 1] = Array(elementCount){0.toFloat()}
        programInput[0, 2] = Array(elementCount){(it % 4).toFloat()}
        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_PS_AVX_YMM_INSTRUCTIONS.instructions,
                                         moveInstructions = listOf(VmovapsYmmYmmm256),
                                         unsafe = false)
        val programSet = ProgramSet(2, programSize)
        val programSetOutput = FloatVectorProgramSetOutput(programSet.programCount, programInput, VectorSize.BITS_256)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        val interpreterMoveInstruction = interpreter.getOpcode(VmovapsYmmYmmm256, YMM1, YMM0)!!

        val addOpcode = interpreter.getOpcode(VaddpsYmmYmmYmmm256, YMM0, YMM1, YMM2)!!
        val subOpcode = interpreter.getOpcode(VsubpsYmmYmmYmmm256, YMM0, YMM1, YMM2)!!

        for (i in 0 until programSet.programSize step 2) {
            programSet[0, i] = addOpcode
            programSet[0, i + 1] = interpreterMoveInstruction
        }

        for (i in 0 until programSet.programSize step 2) {
            programSet[1, i] = subOpcode
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
            assertEquals(expectedOutput.map{(-it + 0f).toFloat()}, programSetOutput.get(1, 0).toList())
        }
    }

    @Test
    fun addDouble() {
        val programSize = 100_000
        val expectedOutput = programSize.toDouble()
        val programInput = DoubleProgramSetInput(1, 2)

        programInput[0, 0] = 0.0
        programInput[0, 1] = 1.0

        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SD_SSE_XMM_INSTRUCTIONS.instructions,
                                         moveInstructions = listOf(),
                                         unsafe = false)

        val programSet = ProgramSet(2, programSize)
        val programSetOutput = DoubleProgramSetOutput(programSet.programCount, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        val opcode = interpreter.getOpcode(AddsdXmm0To63Xmmm64, XMM0, XMM1)!!

        for (j in 0 until programSet.programSize) {
            programSet[0, j] = opcode
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


        val options = InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SD_SSE_XMM_INSTRUCTIONS.instructions,
                                         moveInstructions = listOf(),
                                         unsafe = false)

        val programSet = ProgramSet(2, programSize)
        val programSetOutput = DoubleProgramSetOutput(programSet.programCount, programInput)
        val interpreter = Interpreter(programSet, programInput, programSetOutput, options = options)

        println("OUTPUT SIZE ${programSetOutput.size}")

        val addOpcode = interpreter.getOpcode(AddsdXmm0To63Xmmm64, XMM0, XMM1)!!
        val subOpcode = interpreter.getOpcode(SubsdXmm0To63Xmmm64, XMM0, XMM1)!!

        for (j in 0 until programSet.programSize) {
            programSet[0, j] = addOpcode
        }

        for (j in 0 until programSet.programSize) {
            programSet[1, j] = subOpcode
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