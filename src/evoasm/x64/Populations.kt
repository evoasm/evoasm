package evoasm.x64

import kasm.x64.VroundssXmmXmmXmmm32Imm8


class LongPopulation(sampleSet: LongSampleSet,
                       options: PopulationOptions) : NumberPopulation<Long>(sampleSet, options) {

    override val programSetInput =  LongProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = LongProgramSetOutput(programSet.programCount, programSetInput)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }
}



class FloatPopulation(sampleSet: FloatSampleSet,
                       options: PopulationOptions) : NumberPopulation<Float>(sampleSet, options) {

    override val programSetInput =  FloatProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = FloatProgramSetOutput(programSet.programCount, programSetInput)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }

    fun compile(program: Program) {
        LOGGER.fine(interpreter.buffer.toByteString())
        val programSetInput = FloatProgramSetInput(1, interpreter.input.arity)
        val programSetOutput = FloatProgramSetOutput(1, programSetInput)
        val compiledProgram = CompiledNumberProgram(program, interpreter, programSetInput, programSetOutput)
        for (i in 1..5) {
            LOGGER.fine("f($i) = ${compiledProgram.run(i.toFloat())}")
        }
    }

    fun compileBest() {
        compile(bestProgram)
    }
}

fun main() {
    val options = PopulationOptions(
            128_000,
            4,
            123456,
            12,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SS_AVX_XMM_INSTRUCTIONS.instructions.filterNot { it == VroundssXmmXmmXmmm32Imm8 },
                    moveInstructions = listOf(),
                    compressOpcodes = false,
                    forceMultithreading = false,
                    threadCount = 5,
                    unsafe = false),
            0.01f,
            demeSize = 10,
            majorGenerationFrequency = 1,
            maxOffspringRatio = 0.05
    )

    val sampleSet = FloatSampleSet(1,
            0.0f, 0.0f,
            0.5f, 1.0606601717798212f,
            1.0f, 1.7320508075688772f,
            1.5f, 2.5248762345905194f,
            2.0f, 3.4641016151377544f,
            2.5f, 4.541475531146237f,
            3.0f, 5.744562646538029f,
            3.5f, 7.0622234459127675f,
            4.0f, 8.48528137423857f,
            4.5f, 10.00624804809475f,
            5.0f, 11.61895003862225f
    )

//    val sampleSet = FloatSampleSet.generate(0f, 5.0f, 0.5f) {
//        Math.sqrt(Math.pow(it.toDouble(), 3.0) + 4 * it).toFloat()
//    }

    val population = FloatPopulation(sampleSet, options)
    if(population.run()) {
        AbstractPopulation.LOGGER.fine("recompiling best solution...")
        population.compileBest()
    }
}
