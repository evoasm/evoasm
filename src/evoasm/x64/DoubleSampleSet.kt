package evoasm.x64

class DoubleSampleSet(inputArity: Int, vararg values : Double) : NumberSampleSet<Double>(inputArity, values.size) {
    private val outputValues : DoubleArray
    private val inputValues : DoubleArray

    init {
        val rowLength = inputArity + 1
        require(values.size % rowLength == 0)
        // size = values.size / rowLength
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
