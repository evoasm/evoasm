package evoasm.x64

import kotlin.math.absoluteValue



class LongPopulation(val sampleSet: LongSampleSet,
                       options: PopulationOptions) : Population<Long>(sampleSet, options) {

    override val programSetInput = LongProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = LongProgramSetOutput(programSet, programSetInput)
    override val interpreter = Interpreter(programSet,
                                                      programSetInput,
                                                      programSetOutput,
                                                      options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }

    private fun seed() {
        val maxOpcodeIndex = interpreter.maxOpcodeIndex
        for (i in 0 until programSet.size) {
            for(j in 0 until programSet.programSize) {
                val interpreterInstruction = interpreter.getOpcode(this.random.nextInt(maxOpcodeIndex))
                programSet[i, j] = interpreterInstruction
            }
        }
    }

    private fun loadInput(sampleSet: LongSampleSet) {
        println(sampleSet)
        for (i in 0 until sampleSet.size) {
            for(j in 0 until sampleSet.inputArity) {
                programSetInput[i, j] = sampleSet.getInputValue(i, j)
                println("SETTING ${sampleSet.getInputValue(i, j)}")

            }
        }


//        for(k in 0 until programSet.size) {
//            for (i in 0 until sampleSet.size) {
//                programSetOutput[k, i] = -1.0
//            }
//        }



        println(sampleSet.size)
    }

    override fun calculateLosses() {
        for (programIndex in 0 until populationSize) {
            var loss = 0f
            for(inputIndex in 0 until programSetInput.size) {
                val actualOutput = programSetOutput.get(programIndex, inputIndex)
                val expectedOutput = sampleSet.getOutputValue(inputIndex)

                //println("ACTIAL OUTPUT: $actualOutput $expectedOutput")

                loss += (expectedOutput - actualOutput).toFloat().absoluteValue
            }
            losses[programIndex] = loss
        }
    }
}



class FloatPopulation(val sampleSet: FloatSampleSet,
                       options: PopulationOptions) : Population<Float>(sampleSet, options) {

    override val programSetInput = FloatProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = FloatProgramSetOutput(programSet, programSetInput)
    override val interpreter = Interpreter(programSet,
                                                      programSetInput,
                                                      programSetOutput,
                                                      options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }

    private fun seed() {
        val maxOpcodeIndex = interpreter.maxOpcodeIndex
        for (i in 0 until programSet.size) {
            for(j in 0 until programSet.programSize) {
                val interpreterInstruction = interpreter.getOpcode(this.random.nextInt(maxOpcodeIndex))
                programSet[i, j] = interpreterInstruction
            }
        }
    }

    private fun loadInput(sampleSet: FloatSampleSet) {
        println(sampleSet)
        for (i in 0 until sampleSet.size) {
            for(j in 0 until sampleSet.inputArity) {
                programSetInput[i, j] = sampleSet.getInputValue(i, j)
                println("SETTING ${sampleSet.getInputValue(i, j)}")

            }
        }


//        for(k in 0 until programSet.size) {
//            for (i in 0 until sampleSet.size) {
//                programSetOutput[k, i] = -1.0
//            }
//        }



        println(sampleSet.size)
    }

    override fun calculateLosses() {
        for (programIndex in 0 until populationSize) {
            var loss = 0f
            for(inputIndex in 0 until programSetInput.size) {
                val actualOutput = programSetOutput.get(programIndex, inputIndex)
                val expectedOutput = sampleSet.getOutputValue(inputIndex)

                //println("ACTIAL OUTPUT: $actualOutput $expectedOutput")

                loss += (expectedOutput - actualOutput).toFloat().absoluteValue
            }
            losses[programIndex] = loss
        }
    }
}
