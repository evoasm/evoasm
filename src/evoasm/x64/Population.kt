package evoasm.x64

import evoasm.measureTimeSeconds
import kasm.x64.BitRange
import kasm.x64.XmmRegister
import kotlin.random.Random
import evoasm.FastRandom

interface SelectionOperator {
    fun select()
}

abstract class SampleSet() {
    abstract val size: Int
}

abstract class NumberSampleSet<T: Number>(val inputArity: Int, valuesSize : Int) : SampleSet() {

    override val size: Int = valuesSize / (inputArity + 1)

    init {
        require(valuesSize % (inputArity + 1) == 0)
    }
}


abstract class Population<T: Number>(sampleSet: NumberSampleSet<T>, val options: PopulationOptions) {
    private val wonTournamentCounts = ByteArray(options.size)
    private val winnerIndices = IntArray(options.size / options.tournamentSize)
//    protected val random = Random(options.seed)
    protected val random = FastRandom(options.seed)
    protected val populationSize = options.size
    protected val losses = FloatArray(populationSize)
    protected val programSet = ProgramSet(populationSize, options.programSize)

    protected abstract val programSetInput : ProgramSetInput
    protected abstract val programSetOutput : ProgramSetOutput

    init {
        require(options.size % options.demeSize == 0)
    }

    protected abstract val interpreter : Interpreter // = Interpreter(programSet, programSetInput, programSetOutput, options = options.interpreterOptions)
    private val bestProgram = Program(programSet.programSize)
    private var currentGeneration = 0

    fun nextGeneration() {
        var select: Boolean = false
        val majorGeneration = currentGeneration.rem(options.majorGenerationFrequency) == 0

        val selectT = measureTimeSeconds {
            select = if(majorGeneration) {
                majorSelect()
            } else {
                minorSelect()
            }
        }

        println("Select took $selectT")

        if (select) {
            val reprodT = measureTimeSeconds {
                if(majorGeneration) {
                    majorReproduce()
                } else {
                    minorReproduce()
                }
            }
            println("Reprod took $reprodT")

        } else {
              //seed
          }

        currentGeneration++
    }



    private fun minorSelect() : Boolean {
        val tournamentSize = options.tournamentSize

        for (i in winnerIndices.indices)  {
            val baseIndex = i * tournamentSize
            var minIndex = baseIndex
            var minLoss = losses[baseIndex]
            for(j in 1 until tournamentSize) {
                val index = baseIndex + j
                val loss = losses[index]
                if(loss <= minLoss) {
                    minLoss = loss
                    minIndex = index
                }
            }
            winnerIndices[i] = minIndex
        }
        return true
    }



    private fun majorSelect(): Boolean {
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
                val index = this.random.nextInt(populationSize)
//                require(index >= 0 && index < populationSize)
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
            if(iterationCounter > populationSize && selectedCounter == 0) {
                throw RuntimeException()
            }
        }

        return true
    }

    private var runS: Double = 0.0
    private var gen = 0

    fun evaluate() {
        gen++

        val thisRunS = measureTimeSeconds {
            interpreter.run()
        }
        runS = (1.0/gen) * thisRunS + (1.0 - (1.0/gen)) * runS

        val lossS = measureTimeSeconds {
            calculateLosses()
            updateBest()
        }

        println("Interpreter took $runS")
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

    fun printBest() {
        bestProgram.forEach {
            println("${it}: ${interpreter.getInstruction(it)}")
            println("${it}: ${interpreter.disassemble(it).contentDeepToString()}")
        }

        println()
        val ie = IntronEliminator(bestProgram, XmmRegister.XMM0, BitRange.BITS_0_63, interpreter)
        ie.run().forEach {
            println("${it}: ${interpreter.getInstruction(it)}")
        }

    }

    protected abstract fun calculateLosses()


    private fun minorReproduce() {
        val tournamentSize = options.tournamentSize
//        println(winnerIndices.contentToString())
        for(i in winnerIndices.indices) {
            val winnerIndex = winnerIndices[i]
            val baseIndex = i * tournamentSize
            for(j in 0 until tournamentSize) {
                if(j != winnerIndex) {
                    programSet.copyProgram(winnerIndex, baseIndex + j)
                }
            }
        }
        mutatePrograms()
    }

    private fun majorReproduce() {
        var deadIndex = 0
        for(i in 0 until populationSize) {
            val childCount = wonTournamentCounts[i] - 1
            for(j in 0 until childCount) {
                while(wonTournamentCounts[deadIndex] != 0.toByte()) deadIndex++
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

    private fun mutatePrograms() {
        val maxOpcodeIndex = interpreter.maxOpcodeIndex
        val mutationRate = options.mutationRate
        programSet.transform { interpreterOpcode: InterpreterOpcode, programIndex: Int, instructionIndex: Int ->
            if(this.random.nextFloat() < mutationRate) {
                val opcodeIndex = this.random.nextInt(maxOpcodeIndex)
                interpreter.getOpcode(opcodeIndex)
            } else {
                interpreterOpcode
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
                        val majorGenerationFrequency: Int) {

}

fun main() {

    val options = PopulationOptions(
            100,
            4,
            Random.nextLong(),//1234567,
            5,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SS_AVX_XMM_INSTRUCTIONS.instructions,
                               moveInstructions = listOf(),
                               unsafe = true),
            0.01f,
            demeSize = 10,
            majorGenerationFrequency = 10
                                   )

    val lossFunction = L1Norm()

    val sampleSet = FloatSampleSet(1,
            0.0f, 	0.0f,
                        0.5f, 	1.0606601717798212f,
                    1.0f, 	1.7320508075688772f,
                    1.5f, 	2.5248762345905194f,
                2.0f, 	3.4641016151377544f,
                2.5f, 	4.541475531146237f,
                3.0f, 	5.744562646538029f,
                3.5f, 	7.0622234459127675f,
                4.0f, 	8.48528137423857f,
                4.5f, 	10.00624804809475f,
                5.0f, 	11.61895003862225f
                                   )


    val population = FloatPopulation(sampleSet, options)

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

