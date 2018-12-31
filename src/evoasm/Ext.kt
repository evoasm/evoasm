package evoasm

import kotlin.system.measureNanoTime


inline fun measureTimeSeconds(block: () -> Unit): Double {
    val start = System.nanoTime()
    block()
    return (System.nanoTime() - start) / 10e9
}
