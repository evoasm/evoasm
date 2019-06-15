package evoasm.x64


class LongSampleSet(inputArity: Int, vararg values : Long) : NumberSampleSet<Long>(inputArity, values.size) {
    private val outputValues : LongArray
    private val inputValues : LongArray

    init {
        // size = values.size / rowLength
        outputValues = LongArray(size){ values[elementsPerRow * it + inputArity] }
        inputValues = LongArray(inputArity * size) {
            val row = it / inputArity
            val column = it % inputArity
            values[row * elementsPerRow + column]
        }
    }

    override fun getOutputValue(rowIndex: Int): Long {
        return outputValues[rowIndex]
    }

    override fun getInputValue(rowIndex: Int, columnIndex: Int): Long {
        return inputValues[rowIndex * inputArity + columnIndex]
    }
}



class FloatSampleSet(inputArity: Int, vararg values : Float) : NumberSampleSet<Float>(inputArity, values.size) {
    private val outputValues : FloatArray
    private val inputValues : FloatArray

    init {
        // size = values.size / rowLength
        outputValues = FloatArray(size){ values[elementsPerRow * it + inputArity] }
        inputValues = FloatArray(inputArity * size) {
            val row = it / inputArity
            val column = it % inputArity
            values[row * elementsPerRow + column]
        }
    }

    override fun getOutputValue(rowIndex: Int): Float {
        return outputValues[rowIndex]
    }

    override fun getInputValue(rowIndex: Int, columnIndex: Int): Float {
        return inputValues[rowIndex * inputArity + columnIndex]
    }
}
