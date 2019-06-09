package evoasm.x64

import kotlin.math.absoluteValue

class DoublePopulation(sampleSet: DoubleSampleSet,
                       options: PopulationOptions) : NumberPopulation<Double>(sampleSet, options) {

    override val programSetInput =  DoubleProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = DoubleProgramSetOutput(programSet.size, programSetInput)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }
}