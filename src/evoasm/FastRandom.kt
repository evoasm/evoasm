package evoasm

class FastRandom(seed : Long) {

    constructor() : this(DEFAULT_SEED)

    companion object {
        val DEFAULT_SEED = 1234567891234L
    }

    private var state = seed

    fun nextLong() : Long {
        state = state xor (state shl  21)
        state = state xor (state ushr  35)
        state = state xor (state shl 4)
        return state - 1;
    }

    fun nextInt() : Int {
        return nextLong().toInt()
    }

    fun nextInt(min: Int, bounds: Int) = min + Math.abs(nextInt()).rem(bounds - min)
    fun nextInt(bounds: Int) = nextInt(0, bounds)
    fun nextFloat() = nextInt().toFloat() / Int.MAX_VALUE.toFloat()
}

