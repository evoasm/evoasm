package evoasm.x64

import kotlin.random.Random

interface SelectionOperator {
    fun select()
}



class Population(val options: PopulationOptions) {
    private val wonTournamentsCounts = ByteArray(options.size)
    private val random = Random(options.seed)
    private val losses = FloatArray(options.size)
    private val programSet = ProgramSet(options.size, options.programSize)

    init {

    }


    private fun select(): Boolean {
        wonTournamentsCounts.fill(0)

        val tournamentSize = options.tournamentSize
        val populationSize = options.size

        var selectedCounter = 0;
        /* - 1 for the elite (currently only a single individual) */
        val toSelectCounter = populationSize;

//        if(major) {
//            /* on major we might */
//            if(migr) {
//                n_to_select -= (deme->params->n_demes);
//            } else {
//                n_to_select--;
//            }
//        }

        var iterationCounter = 0;

        while(selectedCounter < toSelectCounter) {
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

            if(!minLoss.isInfinite() && wonTournamentsCounts[minIndex].toUByte() < UByte.MAX_VALUE) {
                wonTournamentsCounts[minIndex]++
                selectedCounter++
            }

            iterationCounter++
            if(iterationCounter > populationSize && selectedCounter == 0) return false
        }

        return true
    }

    private fun reproduce() {
        val populationSize = options.size
        var deadIndex = 0

        for(i in 0 until populationSize) {
            for(j in 0 until wonTournamentsCounts[i]) {
                while(wonTournamentsCounts[deadIndex] != 0.toByte()) deadIndex++
                programSet.copyProgram(i, deadIndex)
                deadIndex++;
            }
        }

        for(i in 0 until populationSize) {
            mutateProgram(i)
            //!evoasm_deme_mutate_kernel(deme, i);
        }

//        while(true) {
//            while(survivorIndex < populationSize && wonTournamentsCounts[survivorIndex] <= 1) survivorIndex++;
//            if(survivorIndex >= populationSize) break;
//
//            while(deadIndex < populationSize && wonTournamentsCounts[deadIndex] != 0.toByte()) deadIndex++;
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
//            wonTournamentsCounts[survivorIndex]--;
//            deadIndex++;
//        }

    }

    private fun mutateProgram(programIndex: Int) {
        programSet.transform(programIndex) {

        }
    }

}

class PopulationOptions(val size: Int,
                        val tournamentSize: Int,
                        val seed: Long,
                        val programSize: Int) {

}
