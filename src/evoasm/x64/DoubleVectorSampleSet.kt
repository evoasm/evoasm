package evoasm.x64

class DoubleVectorSampleSet(inputArity: Int, vectorSize: VectorSize, vararg values : Double) : VectorSampleSet<Double>(inputArity, vectorSize, 8, values.size) {
    private val outputValues : DoubleArray
    private val inputValues : DoubleArray

    init {
        // programCount = values.programCount / rowLength
        outputValues = DoubleArray(size * elementsInVector){
            val row = it / elementsInVector
            val column = it % elementsInVector
            values[elementsPerRow * row + inputArity * elementsInVector + column]
        }
        inputValues = DoubleArray(inputArity * elementsInVector * size) {
            val row = it / (inputArity * elementsInVector)
            val column = it % (inputArity * elementsInVector)
            values[row * elementsPerRow + column]
        }
    }

    override fun getOutputValue(rowIndex: Int, elementIndex: Int): Double {
        return outputValues[rowIndex * elementsInVector + elementIndex]
    }

    override fun getInputValue(rowIndex: Int, columnIndex: Int, elementIndex: Int): Double {
        return inputValues[(rowIndex * inputArity + columnIndex) * elementsInVector + elementIndex]
    }
}
