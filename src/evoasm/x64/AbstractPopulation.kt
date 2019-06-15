package evoasm.x64

import evoasm.measureTimeSeconds
import kasm.x64.BitRange
import kasm.x64.XmmRegister
import evoasm.FastRandom
import kasm.x64.VroundssXmmXmmXmmm32Imm8

interface SelectionOperator {
    fun select()
}

abstract class SampleSet() {
    abstract val size: Int
}

abstract class NumberSampleSet<T : Number>(val inputArity: Int, valuesSize: Int) : SampleSet() {
    protected val elementsPerRow = inputArity + 1
    override val size: Int = valuesSize / elementsPerRow

    init {
        require(valuesSize % elementsPerRow == 0)
    }

    abstract fun getOutputValue(rowIndex: Int): T
    abstract fun getInputValue(rowIndex: Int, columnIndex: Int): T
}

abstract class VectorSampleSet<T : Number>(val inputArity: Int,
                                           val vectorSize: VectorSize,
                                           elementByteSize: Int,
                                           valuesSize: Int) : SampleSet() {
    val elementsInVector = (vectorSize.byteSize / elementByteSize)
    protected val elementsPerRow = ((inputArity + 1) * elementsInVector)
    override val size: Int = valuesSize / elementsPerRow

    init {
        require(valuesSize % elementsPerRow == 0) { "number of elements ($valuesSize) must be a mulitple of $elementsPerRow" }
    }

    abstract fun getOutputValue(rowIndex: Int, elementIndex: Int): T
    abstract fun getInputValue(rowIndex: Int, columnIndex: Int, elementIndex: Int): T
}


abstract class AbstractPopulation(val options: PopulationOptions) {
    private val wonTournamentCounts = ByteArray(options.size)
    private val winnerIndices = IntArray(options.size / options.tournamentSize)
    //    protected val random = Random(options.seed)
    protected val random = FastRandom(options.seed)
    protected val populationSize = options.size
    protected val losses = FloatArray(populationSize)
    protected val programSet = ProgramSet(populationSize, options.programSize)


    init {
        require(options.size % options.demeSize == 0)
    }

    protected abstract val interpreter: Interpreter // = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)
    private val bestProgram = Program(programSet.programSize)
    private var currentGeneration = 0

    private fun minorCycle() {
        majorSelect()
        majorReproduce()
    }

    private fun majorCycle() {
        majorSelect()
        majorReproduce()
    }


    fun nextGeneration() {
        val majorGeneration = currentGeneration.rem(options.majorGenerationFrequency) == 0

        if (majorGeneration) {
            majorCycle()
        } else {
            minorCycle()
        }
        currentGeneration++
    }


    protected fun seed() {
        val maxOpcodeIndex = interpreter.maxOpcodeIndex
        for (i in 0 until programSet.size) {
            for (j in 0 until programSet.programSize) {
                val interpreterInstruction = interpreter.getOpcode(this.random.nextInt(maxOpcodeIndex))
                programSet[i, j] = interpreterInstruction
            }
        }
    }

    private fun minorSelect(): Boolean {
        val tournamentSize = options.tournamentSize

        for (i in winnerIndices.indices) {
            val baseIndex = i * tournamentSize
            var minIndex = baseIndex
            var minLoss = losses[baseIndex]
            if(minLoss.isNaN()) {
                minLoss = Float.POSITIVE_INFINITY
            }

            for (j in 1 until tournamentSize) {
                val index = baseIndex + j
                val loss = losses[index]
                if (loss <= minLoss) {
                    minLoss = loss
                    minIndex = index
                }
            }
            winnerIndices[i] = minIndex
        }
        return true
    }


    private fun majorSelect() {
        wonTournamentCounts.fill(0)

        val tournamentSize = options.tournamentSize

        var selectedCounter = 0;
        var iterationCounter = 0;

        while (selectedCounter < populationSize) {
            var minIndex = Int.MAX_VALUE
            var minLoss = Float.POSITIVE_INFINITY

            for (i in 0 until tournamentSize) {
                val index = this.random.nextInt(populationSize)
//                require(index >= 0 && index < populationSize)
                val loss = losses[index]

                if (loss <= minLoss) {
                    minLoss = loss
                    minIndex = index
                }
            }

            if (!minLoss.isInfinite() && wonTournamentCounts[minIndex].toUByte() < UByte.MAX_VALUE) {
                wonTournamentCounts[minIndex]++
                selectedCounter++
            }

            iterationCounter++
            if (iterationCounter > populationSize && selectedCounter == 0) {
                throw RuntimeException()
            }
        }
    }

    private var runS: Double = 0.0
    private var gen = 0

    fun evaluate() {
        gen++

        val thisRunS = measureTimeSeconds {
            interpreter.run()
        }
        runS = (1.0 / gen) * thisRunS + (1.0 - (1.0 / gen)) * runS

        val lossS = measureTimeSeconds {
            calculateLosses()
            updateBest()
        }

//        println("Interpreter took $runS ($thisRunS)")
//        println("-------------------------")
//        println(bestLoss)
//        println("AVGLOSS: ${losses.filter { it.isFinite() }.average()}")
////        println("LOSSES: ${losses.toList()}")
    }


    var bestLoss: Float = Float.POSITIVE_INFINITY
        private set

    private fun updateBest() {
        var topLoss = Float.POSITIVE_INFINITY
        var topProgramIndex = Int.MAX_VALUE

        for (i in losses.indices) {
            val programLoss = losses[i]
            if (programLoss < topLoss) {
                topLoss = programLoss
                topProgramIndex = i
            }
        }

        if (topLoss < bestLoss) {
            programSet.copyProgramTo(topProgramIndex, bestProgram)
            bestLoss = topLoss
        }
    }

    fun printBest() {
        bestProgram.forEach {
            println("${it}: ${interpreter.getInstruction(it)}")
            println("${it}: ${interpreter.disassemble(it).contentDeepToString()}")
        }

        println()
        val ie = IntronEliminator(bestProgram, XmmRegister.XMM0, BitRange.BITS_0_63, interpreter)
        val iep = ie.run()
        iep.forEach {
            println("${it}: ${interpreter.getInstruction(it)}")
        }

        compile(iep)

    }

    fun compile(program: Program) {
        println(interpreter.buffer.toByteString())
        val programSetInput =  FloatProgramSetInput(1, interpreter.inputs[0].arity)
        val programSetOutput = FloatProgramSetOutput(1, programSetInput)
        val compiledProgram = CompiledNumberProgram(program, interpreter, programSetInput, programSetOutput)
        for (i in 1..5) {
            println("f($i) = ${compiledProgram.run(i.toFloat())}")
        }
    }

    fun compileBest() {
        compile(bestProgram)
    }

    protected abstract fun calculateLosses()


    private fun minorReproduce() {
        val tournamentSize = options.tournamentSize
//        println(winnerIndices.contentToString())
        for (i in winnerIndices.indices) {
            val winnerIndex = winnerIndices[i]
            val baseIndex = i * tournamentSize
            for (j in 0 until tournamentSize) {
                if (j != winnerIndex) {
                    programSet.copyProgram(winnerIndex, baseIndex + j)
                }
            }
        }
        mutatePrograms()
    }

    private fun majorReproduce() {
        var deadIndex = 0
        for (i in 0 until populationSize) {
            val childCount = wonTournamentCounts[i] - 1
            for (j in 0 until childCount) {
                while (wonTournamentCounts[deadIndex] != 0.toByte()) deadIndex++
                programSet.copyProgram(i, deadIndex)
                deadIndex++
            }
        }

        mutatePrograms()
        //!evoasm_deme_mutate_kernel(deme, i);

//        while(true) {
//            while(survivorIndex < populationSize && wonTournamentCounts[survivorIndex] <= 1) survivorIndex++;
//            if(survivorIndex >= populationSize) break;
//
//            while(deadIndex < populationSize && wonTournamentCounts[deadIndex] != 0.toByte()) deadIndex++;
//
//            // This check should not be necessary, mathematically,
//            // for each survivor there must be a corresponding dead
//            // as we only let individuals survive if they won > 1 tournaments and there
//            // are < deme_size tournaments in total
//            // just to be sure let's add an assert
//            //    if(dead_idx >= deme_size) break;
//            assert(deadIndex < populationSize);
//
//
//            //!!evoasm_deme_copy_kernel(deme, surviv_idx, dead_idx);
//            //!!evoasm_deme_mutate_kernel(deme, dead_idx);
//
//            wonTournamentCounts[survivorIndex]--;
//            deadIndex++;
//        }

    }

//    protected val maxInstructionOpcode = options.interpreterOptions.instructions.size

    private fun mutateOpcode(interpreterOpcode: InterpreterOpcode, random: FastRandom, mutationRate: Float, maxOpcodeIndex: Int): InterpreterOpcode {
        if (this.random.nextFloat() < mutationRate) {
            val opcodeIndex = this.random.nextInt(maxOpcodeIndex)
            return interpreter.getOpcode(opcodeIndex)
        } else {
            return interpreterOpcode
        }
    }

    private fun mutatePrograms() {

        val maxOpcodeIndex = interpreter.maxOpcodeIndex
        val mutationRate = options.mutationRate

        programSet.transform { interpreterOpcode: InterpreterOpcode, programIndex: Int, instructionIndex: Int ->
            mutateOpcode(interpreterOpcode, random, mutationRate, maxOpcodeIndex)
        }
    }

}

abstract class NumberPopulation<T : Number>(val sampleSet: NumberSampleSet<T>,
                                            options: PopulationOptions) : AbstractPopulation(options) {
    protected abstract val programSetInput: NumberProgramSetInput<T>
    protected abstract val programSetOutput: NumberProgramSetOutput<T>

    override fun calculateLosses() {
        for (programIndex in 0 until populationSize) {
            var loss = 0f
            for (inputIndex in 0 until programSetInput.size) {
                val actualOutput = programSetOutput[programIndex, inputIndex].toFloat()
                val expectedOutput = sampleSet.getOutputValue(inputIndex).toFloat()

                //println("ACTIAL OUTPUT: $actualOutput $expectedOutput")

                loss += Math.abs(expectedOutput - actualOutput)
            }

            if(loss == 0f) {
                for (inputIndex in 0 until programSetInput.size) {
                    val actualOutput = programSetOutput[programIndex, inputIndex].toFloat()
                    val expectedOutput = sampleSet.getOutputValue(inputIndex).toFloat()
                    println("ACTIAL OUTPUT: $actualOutput $expectedOutput")
                }
            }

            losses[programIndex] = loss
        }
        programSetOutput.zero()
    }

    protected fun loadInput(sampleSet: NumberSampleSet<T>) {
        println(sampleSet)
        for (i in 0 until sampleSet.size) {
            for (j in 0 until sampleSet.inputArity) {
                programSetInput[i, j] = sampleSet.getInputValue(i, j)
            }
        }
    }
}

abstract class VectorPopulation<T : Number>(val sampleSet: VectorSampleSet<T>,
                                            options: PopulationOptions) : AbstractPopulation(options) {
    protected abstract val programSetInput: VectorProgramSetInput<T>
    protected abstract val programSetOutput: VectorProgramSetOutput<T>

    override fun calculateLosses() {
        for (programIndex in 0 until populationSize) {
            var loss = 0f
            for (inputIndex in 0 until programSetInput.size) {

                for (elementIndex in 0 until sampleSet.elementsInVector) {
                    val actualOutput = programSetOutput[programIndex, inputIndex, elementIndex].toFloat()
                    val expectedOutput = sampleSet.getOutputValue(inputIndex, elementIndex).toFloat()
                    loss += Math.abs(expectedOutput - actualOutput)
                }
            }
            losses[programIndex] = loss
        }
    }

    protected fun loadInput(sampleSet: VectorSampleSet<T>) {
        println(sampleSet)
        for (i in 0 until sampleSet.size) {
            for (j in 0 until sampleSet.inputArity) {
                for (k in 0 until sampleSet.elementsInVector) {
                    programSetInput[i, j, k] = sampleSet.getInputValue(i, j, k)
                }
            }
        }
    }

}

class PopulationOptions(val size: Int,
                        val tournamentSize: Int,
                        val seed: Long,
                        val programSize: Int,
                        val interpreterOptions: InterpreterOptions,
                        val mutationRate: Float,
                        val demeSize: Int,
                        val majorGenerationFrequency: Int,
                        val maxOffspringRatio: Double = 0.3) {


}

fun main() {

    val options = PopulationOptions(
            120,
            4,
            1234567,
            12,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SS_AVX_XMM_INSTRUCTIONS.instructions.filterNot { it == VroundssXmmXmmXmmm32Imm8 },
                               moveInstructions = listOf(),
                               compressOpcodes = false,
                               unsafe = false),
            0.01f,
            demeSize = 10,
            majorGenerationFrequency = 1,
            maxOffspringRatio = 0.05
                                   )

    val lossFunction = L1Norm()

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


    val population = FloatPopulation(sampleSet, options)
    var found = false
    val seconds = measureTimeSeconds {

        for (i in 0 until 35_000) {
            population.evaluate()
            population.nextGeneration()
            if (population.bestLoss == 0f) {
                println("found 0, done after $i gens")
                population.compileBest()
                population.printBest()
                found = true
                break
            }
        }
    }

    if(found) println("Found after ${seconds}")

}

