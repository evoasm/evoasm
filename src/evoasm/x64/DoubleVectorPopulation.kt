package evoasm.x64

import evoasm.measureTimeSeconds
import kotlin.random.Random

class DoubleVectorPopulation(sampleSet: DoubleVectorSampleSet,
                             options: PopulationOptions) : VectorPopulation<Double>(sampleSet, options) {

    override val programSetInput = DoubleVectorProgramSetInput(sampleSet.size, sampleSet.inputArity, sampleSet.vectorSize)
    override val programSetOutput = DoubleVectorProgramSetOutput(programSet.programCount, programSetInput, sampleSet.vectorSize)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }
}

fun main() {

    val options = PopulationOptions(
            100,
            4,
            Random.nextLong(),//1234567,
            1,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_PD_AVX_XMM_INSTRUCTIONS.instructions,
                               moveInstructions = listOf(),
                               unsafe = true),
            0.01f,
            demeSize = 1,
            majorGenerationFrequency = 1
                                   )

    val sampleSet = DoubleVectorSampleSet(2, VectorSize.BITS_256,
                                   0.0, 	0.0, 0.0, 0.0,         1.0, 1.0, 1.0, 1.0,     1.0, 1.0, 1.0, 1.0,
                                          1.0, 	1.0, 1.0, 1.0,         2.0, 2.0, 2.0, 2.0,     3.0, 3.0, 3.0, 3.0,
                                          2.0, 	2.0, 2.0, 2.0,         3.0, 3.0, 3.0, 3.0,     4.0, 4.0, 4.0, 4.0)

    val population = DoubleVectorPopulation(sampleSet, options)

    val seconds = measureTimeSeconds {
        for (i in 0 until 35_000) {
            population.evaluate()
            population.nextGeneration()
            if (population.bestLoss == 0f) {
                println("found 0, done")
                break
            }
        }
        population.printBest()
    }

    println("Found after ${seconds}")

}
