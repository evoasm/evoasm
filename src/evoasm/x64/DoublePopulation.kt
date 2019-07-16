package evoasm.x64

class DoublePopulation(sampleSet: DoubleSampleSet,
                       options: PopulationOptions) : NumberPopulation<Double>(sampleSet, options) {

    override val programSetInput =  DoubleProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = DoubleProgramSetOutput(programSet.programCount, programSetInput)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }
}