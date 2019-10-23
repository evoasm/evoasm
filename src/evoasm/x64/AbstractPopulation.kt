package evoasm.x64

import evoasm.measureTimeSeconds
import evoasm.FastRandom
import kasm.x64.*
import java.util.logging.Logger
import kotlin.random.Random

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

    companion object {
        val LOGGER = Logger.getLogger(AbstractPopulation::class.java.name)
    }

    private val wonTournamentCounts = ByteArray(options.size)
    private val winnerIndices = IntArray(options.size / options.tournamentSize)
    //    protected val random = Random(options.seed)
    protected val random = FastRandom(options.seed)
    protected val populationSize = options.size
    protected val losses = FloatArray(populationSize)
    private val threadPopulationSize = populationSize / options.interpreterOptions.threadCount
    protected val programSet = ProgramSet(populationSize, options.programSize, options.interpreterOptions.threadCount)


    init {
        require(options.size % options.demeSize == 0)
        require(options.size % options.interpreterOptions.threadCount == 0)
    }

    protected abstract val interpreter: Interpreter // = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)
    internal val bestProgram = Program(options.programSize)
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
        val opcodeCount = interpreter.opcodeCount
        for (i in 0 until programSet.programCount) {
            for (j in 0 until programSet.programSize) {
                val opcodeIndex = this.random.nextInt(opcodeCount)
                val interpreterInstruction = interpreter.getOpcode(opcodeIndex)
                programSet[i, j] = interpreterInstruction
            }
        }
        LOGGER.finer(programSet.toString(interpreter))
    }

    private fun minorSelect(): Boolean {
        val tournamentSize = options.tournamentSize

        for (i in winnerIndices.indices) {
            val baseIndex = i * tournamentSize
            var minIndex = baseIndex
            var minLoss = losses[baseIndex]
            if (minLoss.isNaN()) {
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

    private var meanInterpreterRuntime: Double = 0.0
    private var generationCounter = 0

    fun evaluate() {
        generationCounter++
        var interpreterRuntime = 0.0
        if (interpreter.haveMultipleThreads) {
            interpreterRuntime = measureTimeSeconds {
                  interpreter.runParallel()
            }
        } else {
            interpreterRuntime = measureTimeSeconds {
                interpreter.run()
            }
//            println("single eval took $thisRunS")
        }
        meanInterpreterRuntime = (1.0 / generationCounter) * interpreterRuntime + (1.0 - (1.0 / generationCounter)) * meanInterpreterRuntime

        LOGGER.info("Interpreter took $interpreterRuntime (mean $meanInterpreterRuntime)")

        val lossCalculationRuntime = measureTimeSeconds {
            calculateLosses()
            updateBest()
        }

        LOGGER.info("loss calculations took $lossCalculationRuntime")
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
        LOGGER.info("BEFORE intron elimination")
        bestProgram.forEach {
            LOGGER.info(interpreter.disassemble(it)[1].joinToString(" "))
        }

        println()
        val outputRegister = when(this) {
            is FloatPopulation -> XmmRegister.XMM0
            is DoubleVectorPopulation -> YmmRegister.YMM0
            else -> throw RuntimeException()
        } as Register
        val intronEliminator = IntronEliminator(bestProgram, outputRegister, BitRange.BITS_0_63, interpreter)
        val intronEliminatedProgram = intronEliminator.run()
        LOGGER.info("AFTER intron elimination")
        intronEliminatedProgram.forEach {
            println(interpreter.disassemble(it)[1].joinToString(" "))
        }
        //  compile(intronEliminatedProgram)
    }

    protected abstract fun calculateLosses()


    private fun minorReproduce() {
        val tournamentSize = options.tournamentSize
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
    }

    private fun mutateOpcode(interpreterOpcode: InterpreterOpcode,
                             random: FastRandom,
                             mutationRate: Float,
                             opcodeCount: Int): InterpreterOpcode {
        if (this.random.nextFloat() < mutationRate) {
            val opcodeIndex = this.random.nextInt(opcodeCount)
            val newOpcode = this.interpreter.getOpcode(opcodeIndex)
            return newOpcode
        } else {
            return interpreterOpcode
        }
    }

    private fun mutatePrograms() {

        val opcodeCount = interpreter.opcodeCount
        val mutationRate = options.mutationRate

        programSet.transform { interpreterOpcode: InterpreterOpcode, threadIndex: Int, programIndex: Int, instructionIndex: Int ->
            if (interpreterOpcode == interpreter.getHaltOpcode()) {
                LOGGER.warning("${threadIndex} ${programIndex}, ${instructionIndex}, ${interpreterOpcode}");
                throw RuntimeException()
            }
            mutateOpcode(interpreterOpcode, random, mutationRate, opcodeCount)
        }
    }

    fun run(maxGenerations: Int = 35_000) : Boolean {
        var found = false
        var generations = 0
        val seconds = measureTimeSeconds {
            for (i in 0 until maxGenerations) {
                evaluate()
                nextGeneration()
                generations = i
                if (i % 100 == 0) {
                    LOGGER.info("best loss: ${bestLoss}");
                }
                if (bestLoss == 0f) {
                    found = true
                    break
                }
            }
        }

        if (found) {
            LOGGER.info("Found solution after ${seconds} ($generations generations)")
            printBest()
        } else {
            LOGGER.info("no solution found after ${seconds} ($generations generations)")
        }

        return found;
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

                loss += Math.abs(expectedOutput - actualOutput)
            }

            if (loss == 0f) {
                for (inputIndex in 0 until programSetInput.size) {
                    val actualOutput = programSetOutput[programIndex, inputIndex].toFloat()
                    val expectedOutput = sampleSet.getOutputValue(inputIndex).toFloat()
                    LOGGER.fine("output (actual, expected): $actualOutput, $expectedOutput")
                }
            }

            losses[programIndex] = loss
        }
        programSetOutput.zero()
    }

    protected fun loadInput(sampleSet: NumberSampleSet<T>) {
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

