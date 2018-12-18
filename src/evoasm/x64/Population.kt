package evoasm.x64

import kasm.x64.VmovapdYmmYmmm256
import kotlin.math.absoluteValue
import kotlin.random.Random

interface SelectionOperator {
    fun select()
}

abstract class AbstractSampleSet<T: Number>() {
    abstract val size: Int
}

class DoubleSampleSet(val inputArity: Int, vararg values : Double) : AbstractSampleSet<Double>() {
    val outputValues : DoubleArray
    val inputValues : DoubleArray
    override val size: Int

    init {
        val rowLength = inputArity + 1
        require(values.size % rowLength == 0)
        size = values.size / rowLength
        outputValues = DoubleArray(size){ values[rowLength * it + inputArity] }
        inputValues = DoubleArray(inputArity * size) {
            val row = it / inputArity
            val column = it % inputArity
            values[row * rowLength + column]
        }
    }

    fun getOutputValue(rowIndex: Int): Double {
        return outputValues[rowIndex]
    }

    fun getInputValue(rowIndex: Int, columnIndex: Int): Double {
        return inputValues[rowIndex * inputArity + columnIndex]
    }

}


abstract class Population<T: Number>(sampleSet: AbstractSampleSet<T>, val lossFunction: LossFunction<T>, val options: PopulationOptions) {
    private val wonTournamentCounts = ByteArray(options.size)
    protected val random = Random(options.seed)
    protected val populationSize = options.size
    protected val losses = FloatArray(populationSize)
    protected val programSet = ProgramSet(populationSize, options.programSize)

    protected abstract val programSetInput : ProgramSetInput
    protected abstract val programSetOutput : ProgramSetOutput


    protected abstract val interpreter : Interpreter // = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)
    private val bestProgram = Program(programSet.programSize)

    fun nextGeneration() {
        if(select()) {
            reproduce()
        } else {
            //seed
        }
    }


    private fun select(): Boolean {
        wonTournamentCounts.fill(0)

        val tournamentSize = options.tournamentSize

        var selectedCounter = 0;
        /* - 1 for the elite (currently only a single individual) */
        //        if(major) {
//            /* on major we might */
//            if(migr) {
//                n_to_select -= (deme->params->n_demes);
//            } else {
//                n_to_select--;
//            }
//        }

        var iterationCounter = 0;

        while(selectedCounter < populationSize) {
            var minIndex = Int.MAX_VALUE
            var minLoss = Float.POSITIVE_INFINITY

            for(i in 0 until tournamentSize) {
                val index = random.nextInt(0, populationSize)
                val loss = losses[index]

                if(loss <= minLoss) {
                    minLoss = loss
                    minIndex = index
                }
            }

            if(!minLoss.isInfinite() && wonTournamentCounts[minIndex].toUByte() < UByte.MAX_VALUE) {
                wonTournamentCounts[minIndex]++
                selectedCounter++
            }

            iterationCounter++
            if(iterationCounter > populationSize && selectedCounter == 0) return false
        }

        return true
    }

    fun evaluate() {
        interpreter.run()
        calculateLosses()
        updateBest()

        println("-------------------------")
        println(bestLoss)
        println(losses.contentToString())
    }


    private var bestLoss: Float = 0f

    private fun updateBest() {
        var topLoss = Float.POSITIVE_INFINITY
        var topProgramIndex = Int.MAX_VALUE

        for (i in 0 until populationSize) {
            val programLoss = losses[i]
            if(programLoss < topLoss) {
                topLoss = programLoss
                topProgramIndex = i
            }
        }

        if(topLoss < bestLoss) {
            programSet.copyProgramTo(topProgramIndex, bestProgram)
            bestLoss = topLoss
        }
    }

    protected abstract fun calculateLosses()

    private fun reproduce() {
        var deadIndex = 0
        for(i in 0 until populationSize) {
            val childCount = wonTournamentCounts[i] - 1
            for(j in 0 until childCount) {
                while(wonTournamentCounts[deadIndex] != 0.toByte()) deadIndex++
                programSet.copyProgram(i, deadIndex)
                deadIndex++
            }
        }

        for(i in 0 until populationSize) {
            mutateProgram(i)
            //!evoasm_deme_mutate_kernel(deme, i);
        }

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

    protected val maxInstructionOpcode = options.interpreterOptions.instructions.size

    private fun mutateProgram(programIndex: Int) {
        programSet.transform(programIndex) {
            val opcode = random.nextInt(0, maxInstructionOpcode)
            interpreter.getInterpreterInstruction(opcode)
        }
    }

}

interface LossFunction<T : Number> {
    fun calculate(expectedValue: T, actualValue: T) : Float
}

class DoublePopulation(val sampleSet: DoubleSampleSet,
                       lossFunction: DoubleLossFunction,
                       options: PopulationOptions) : Population<Double>(sampleSet, lossFunction, options) {

    override val programSetInput = DoubleProgramSetInput(sampleSet.size, sampleSet.inputArity)
    override val programSetOutput = DoubleProgramSetOutput(programSet, programSetInput)
    override val interpreter = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)

    init {
        loadInput(sampleSet)
        seed()
    }

    private fun seed() {

        for (i in 0 until programSet.size) {
            for(j in 0 until programSet.programSize) {
                val interpreterInstruction = interpreter.getInterpreterInstruction(random.nextInt(maxInstructionOpcode))
                programSet[i, j] = interpreterInstruction
            }
        }
    }

    private fun loadInput(sampleSet: DoubleSampleSet) {
        println(sampleSet)
        for (i in 0 until sampleSet.size) {
            for(j in 0 until sampleSet.inputArity) {
                programSetInput[i, j] = sampleSet.getInputValue(i, j)
                println("SETTING ${sampleSet.getInputValue(i, j)}")

            }
        }


        for(k in 0 until programSet.size) {
            for (i in 0 until sampleSet.size) {
                programSetOutput.setDouble(k, i, -1.0)
            }
        }



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

interface DoubleLossFunction : LossFunction<Double> {

    fun calculateDouble(expectedValue: Double, actualValue: Double) : Float

    override fun calculate(expectedValue: Double, actualValue: Double) : Float {
        return calculateDouble(expectedValue, actualValue)
    }
}

class PopulationOptions(val size: Int,
                        val tournamentSize: Int,
                        val seed: Long,
                        val programSize: Int,
                        val interpreterOptions: InterpreterOptions) {

}

fun main() {

    val options = PopulationOptions(
            10_000,
            10,
            Random.nextLong(),//1234567,
            5,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SD_AVX_XMM_INSTRUCTIONS.instructions, moveInstructions = listOf(VmovapdYmmYmmm256))
                                   )

    val lossFunction = L1Norm()

    val sampleSet = DoubleSampleSet(1,
            0.0, 	0.0,
                        0.5, 	1.0606601717798212,
                    1.0, 	1.7320508075688772,
                    1.5, 	2.5248762345905194,
                2.0, 	3.4641016151377544,
                2.5, 	4.541475531146237,
                3.0, 	5.744562646538029,
                3.5, 	7.0622234459127675,
                4.0, 	8.48528137423857,
                4.5, 	10.00624804809475,
                5.0, 	11.61895003862225
                                   )


    val population = DoublePopulation(sampleSet, lossFunction, options)
    repeat(1000) {
        population.evaluate()
        population.nextGeneration()
    }

}

class L1Norm : DoubleLossFunction {
    override fun calculateDouble(expectedValue: Double, actualValue: Double): Float {
        return (actualValue - expectedValue).toFloat().absoluteValue
    }

}
