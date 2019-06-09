package evoasm.x64

class DoubleSampleSet(inputArity: Int, vararg values : Double) : NumberSampleSet<Double>(inputArity, values.size) {
    private val outputValues : DoubleArray
    private val inputValues : DoubleArray

    init {
        // size = values.size / rowLength
        outputValues = DoubleArray(size){ values[elementsPerRow * it + inputArity] }
        inputValues = DoubleArray(inputArity * size) {
            val row = it / inputArity
            val column = it % inputArity
            values[row * elementsPerRow + column]
        }
    }

    override fun getOutputValue(rowIndex: Int): Double {
        return outputValues[rowIndex]
    }

    override fun getInputValue(rowIndex: Int, columnIndex: Int): Double {
        return inputValues[rowIndex * inputArity + columnIndex]
    }
}
