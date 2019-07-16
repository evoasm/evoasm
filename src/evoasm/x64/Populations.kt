package evoasm.x64


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
}
