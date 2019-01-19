package evoasm.x64

import kotlin.math.absoluteValue

interface NumberLossFunction<T : Number> {
    fun calculate(expectedValue: T, actualValue: T) : Float
}

interface DoubleLossFunction : NumberLossFunction<Double> {
    fun calculateDouble(expectedValue: Double, actualValue: Double) : Float
    override fun calculate(expectedValue: Double, actualValue: Double) : Float {
        return calculateDouble(expectedValue, actualValue)
    }
}

class L1Norm : DoubleLossFunction {
    override fun calculateDouble(expectedValue: Double, actualValue: Double): Float {
        return (actualValue - expectedValue).toFloat().absoluteValue
    }

}