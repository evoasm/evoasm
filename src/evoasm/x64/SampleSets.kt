package evoasm.x64


class LongSampleSet(inputArity: Int, vararg values : Long) : NumberSampleSet<Long>(inputArity, values.size) {
    private val outputValues : LongArray
    private val inputValues : LongArray

    init {
        // programCount = values.programCount / rowLength
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


    companion object {
        fun generate(from: Float, to:Float, step: Float, action: (Float) -> Float): FloatSampleSet {
            val count = ((to - from) / step).toInt() + 1
            val array = FloatArray(2 * count)
            for(i in 0 until count) {
                val input = from + i * step
                array[2 * i] = input
                array[2 * i + 1] = action(input)
                require(array[i] <= to) {"${array[i]} <= $to"}
            }
            println(array.contentToString())
            return FloatSampleSet(1, *array)
        }
    }


    init {
        // programCount = values.programCount / rowLength
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

