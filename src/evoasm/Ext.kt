package evoasm

inline fun measureTimeSeconds(block: () -> Unit): Double {
    val start = System.nanoTime()
    block()
    return (System.nanoTime() - start).toDouble() / 1e9
}
