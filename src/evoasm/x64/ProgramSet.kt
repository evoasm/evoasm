package evoasm.x64

import kasm.Address
import kasm.CodeModel
import kasm.NativeBuffer
import kasm.address
import kasm.x64.Assembler

//abstract class CompiledProgram() {
//
//}

//abstract class NumberCompiledProgram<T: Number>(val inputArity: Int) : CompiledProgram() {
//    abstract val programSetInput : NumberProgramSetInput<T>
//    abstract val programSetOutput : NumberProgramSetOutput<T>
//
//    operator fun invoke(vararg T: arguments) : T {
//
//    }
//}

class Compiler() {

}

class CompiledNumberProgram<T: Number> internal constructor(program: Program, interpreter: Interpreter, val programSetInput: NumberProgramSetInput<T>, val programSetOutput: NumberProgramSetOutput<T>) {

    val buffer = NativeBuffer(1024)

    init {
        compile(program, interpreter)
    }

    private fun  compile(program: Program, interpreter: Interpreter) {

        Assembler(buffer).apply {
            emitStackFrame {
                programSetInput.emitLoad(this)
                program.forEach {
                    val interpreterInstruction = interpreter.getInstruction(it)
                    interpreterInstruction!!.instruction.encode(buffer, interpreterInstruction.instructionParameters)
                }
                programSetOutput.emitStore(this)
            }
        }
    }

    fun run(vararg arguments: T) : T {
        arguments.forEachIndexed {index, argument ->
            programSetInput[0, index] = argument
        }
        println(buffer.toByteString())
        buffer.execute()
        return programSetOutput[0, 0]
    }
}

class Program(val size: Int) {
    private val code = UShortArray(size)

    operator fun set(index: Int, opcode: InterpreterOpcode) {
        code[index] = opcode.code
    }

    inline fun forEach(action: (InterpreterOpcode) -> Unit) {
        for (i in 0 until size) {
            action(this[i])
        }
    }

    operator fun get(index: Int): InterpreterOpcode {
        return InterpreterOpcode(code[index])
    }
}

class ProgramSet(val size: Int, val programSize: Int) {
    internal val actualProgramSize = programSize + 1
    val instructionCount = size * actualProgramSize
    private val codeBuffer = NativeBuffer((instructionCount.toLong() + 1) * UShort.SIZE_BYTES,
                                               CodeModel.LARGE)
    internal val byteBuffer = codeBuffer.byteBuffer
    val address: Address = codeBuffer.address
    private var initialized : Boolean = false

    companion object {
//        val HALT_INSTRUCTION = InterpreterOpcode(0U)
//        val END_INSTRUCTION = InterpreterOpcode(2U)
    }

    internal fun initialize(haltOpcode: InterpreterOpcode, endOpcode: InterpreterOpcode) {
        check(!initialized) {"cannot reuse program set"}
        initialized = true
        val shortBuffer = byteBuffer.asShortBuffer()
        for (i in 0 until size) {
            shortBuffer.put(i * actualProgramSize + programSize, endOpcode.code.toShort())
        }
        println("putting halt at ${instructionCount}")
        shortBuffer.put(instructionCount, haltOpcode.code.toShort())
    }

    operator fun set(programIndex: Int, instructionIndex: Int, opcode: InterpreterOpcode) {
        val offset = calculateOffset(programIndex, instructionIndex)
        byteBuffer.putShort(offset, opcode.code.toShort())
    }

    internal inline fun transform(action: (InterpreterOpcode, Int, Int) -> InterpreterOpcode)  {
        for(i in 0 until size) {
            val programOffset = i * actualProgramSize * Short.SIZE_BYTES
            for (j in 0 until programSize) {
                val instructionOffset = programOffset + j * Short.SIZE_BYTES
                val interpreterInstruction = InterpreterOpcode(byteBuffer.getShort(instructionOffset).toUShort())
                byteBuffer.putShort(instructionOffset, action(interpreterInstruction, i, j).code.toShort())
            }
        }
    }

    operator fun get(programIndex: Int, instructionIndex: Int): InterpreterOpcode {
        val offset = calculateOffset(programIndex, instructionIndex)
        return InterpreterOpcode(byteBuffer.getShort(offset).toUShort())
    }

    private fun calculateOffset(programIndex: Int, instructionIndex: Int): Int {
        return (programIndex * actualProgramSize + Math.min(programSize - 1, instructionIndex)) * Short.SIZE_BYTES
    }

    fun copyProgramTo(programIndex: Int, program: Program) {
        val programOffset = programIndex * actualProgramSize * Short.SIZE_BYTES

        for (i in 0 until programSize) {
            val instructionOffset = programOffset + i * Short.SIZE_BYTES
            program[i] = InterpreterOpcode(byteBuffer.getShort(instructionOffset).toUShort())
        }
    }

    internal inline fun copyProgram(fromProgramIndex: Int, toProgramIndex: Int, transformer: (InterpreterOpcode, Int) -> InterpreterOpcode = { io, o -> io}) {
        val fromOffset = fromProgramIndex * actualProgramSize * Short.SIZE_BYTES
        val toOffset = toProgramIndex * actualProgramSize * Short.SIZE_BYTES
        val byteBuffer = this.byteBuffer

        for (i in 0 until actualProgramSize) {
            val relativeOffset = i * Short.SIZE_BYTES
            val interpreterInstruction = InterpreterOpcode(byteBuffer.getShort(fromOffset + relativeOffset).toUShort())
            byteBuffer.putShort(toOffset + relativeOffset, transformer(interpreterInstruction, i).code.toShort())
        }
    }
}