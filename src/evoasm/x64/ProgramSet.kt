package evoasm.x64

import kasm.Address
import kasm.CodeModel
import kasm.NativeBuffer
import kasm.address
import kasm.x64.Assembler
import java.util.logging.Logger

class CompiledNumberProgram<T: Number> internal constructor(program: Program, interpreter: Interpreter, val programSetInput: NumberProgramSetInput<T>, val programSetOutput: NumberProgramSetOutput<T>) {

    val buffer = NativeBuffer(1024)

    companion object {
        val LOGGER = Logger.getLogger(CompiledNumberProgram::class.java.name)
    }

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
        LOGGER.finer(buffer.toByteString())
        buffer.setExecutable(true)
        LOGGER.fine("executing compiled program")
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

    inline fun forEachIndexed(action: (InterpreterOpcode, Int) -> Unit) {
        for (i in 0 until size) {
            action(this[i], i)
        }
    }

    operator fun get(index: Int): InterpreterOpcode {
        return InterpreterOpcode(code[index])
    }
}

class ProgramSet(val programCount: Int, val programSize: Int, val threadCount: Int) {

    constructor(size: Int, programSize: Int) : this(size, programSize, 1)

    internal val programSizeWithEnd = programSize + 1
    internal val perThreadProgramCount = programCount / threadCount
    private val perThreadInstructionCountWithHalt = perThreadProgramCount * programSizeWithEnd + 1
    val instructionCount = programCount * programSizeWithEnd
    private val instructionCountWithHalts = instructionCount + threadCount
    private val codeBuffer = NativeBuffer(instructionCountWithHalts.toLong() * UShort.SIZE_BYTES,
                                               CodeModel.LARGE)
    internal val byteBuffer = codeBuffer.byteBuffer
    private val shortBuffer = byteBuffer.asShortBuffer()
    val address: Address = codeBuffer.address
    internal var initialized : Boolean = false

    fun getAddress(threadIndex: Int): Address {
        val offset = (threadIndex * perThreadInstructionCountWithHalt * Short.SIZE_BYTES).toULong()
        val address = codeBuffer.address + offset
        LOGGER.finest("address: $address, offset: $offset, threadIndex: $threadIndex, bufferSize: ${codeBuffer.byteBuffer.capacity()}")
        return address
    }

    init {
        require(programCount % threadCount == 0)
    }

    companion object {
        val LOGGER = Logger.getLogger(ProgramSet::class.java.name)
    }

    internal fun initialize(haltOpcode: InterpreterOpcode, endOpcode: InterpreterOpcode) {
        check(!initialized) {"cannot reuse program set"}
        initialized = true

//        shortBuffer.put(0, startOpcode.code.toShort())

        require(perThreadInstructionCountWithHalt * threadCount == instructionCountWithHalts) {
            "${perThreadInstructionCountWithHalt * threadCount} == ${instructionCountWithHalts}"
        }
        for(threadIndex in 0 until threadCount) {
            for (threadProgramIndex in 0 until perThreadProgramCount) {
                val offset = calculateOffset(threadIndex, threadProgramIndex, programSize)
                require(offset == threadIndex * perThreadInstructionCountWithHalt + threadProgramIndex * programSizeWithEnd + programSize)

                shortBuffer.put(offset, endOpcode.code.toShort())
            }

            val haltOffset = threadIndex * perThreadInstructionCountWithHalt + perThreadInstructionCountWithHalt - 1
            LOGGER.finer("setting halt at $haltOffset");
            shortBuffer.put(haltOffset, haltOpcode.code.toShort())
        }
    }

    fun toString(interpreter: Interpreter): String {
        val builder = StringBuilder()

        for(i in 0 until instructionCountWithHalts) {
            val interpreterOpcode = InterpreterOpcode(shortBuffer.get(i).toUShort())
            val programIndex = i / programSizeWithEnd
            val instructionIndex = i % programSizeWithEnd
            val threadIndex = i / perThreadInstructionCountWithHalt
            val perThreadProgramIndex = i % perThreadInstructionCountWithHalt

            val instructionName = if(interpreterOpcode == interpreter.getHaltOpcode()) {
                "HALT"
            } else if(interpreterOpcode == interpreter.getEndOpcode()) {
                "END"
            } else {
                interpreter.getInstruction(interpreterOpcode).toString()
            }
            builder.append("$threadIndex:$perThreadProgramIndex:$instructionIndex:$i:$instructionName\t\n")
        }


        forEach { interpreterOpcode: InterpreterOpcode, threadIndex: Int, perThreadProgramIndex: Int, instructionIndex: Int, instructionOffset: Int ->
        }
        return builder.toString()
    }

    operator fun set(programIndex: Int, instructionIndex: Int, opcode: InterpreterOpcode) {
        val offset = calculateOffset(programIndex, instructionIndex)
        shortBuffer.put(offset, opcode.code.toShort())
    }

    private inline fun forEach(action: (InterpreterOpcode, Int, Int, Int, Int) -> Unit) {
        for(threadIndex in 0 until threadCount) {
            for (perThreadProgramIndex in 0 until perThreadProgramCount) {
                for (instructionIndex in 0 until programSize) {
                    val instructionOffset = calculateOffset(threadIndex, perThreadProgramIndex, instructionIndex);
                    val interpreterOpcode = InterpreterOpcode(shortBuffer.get(instructionOffset).toUShort())
                    action(interpreterOpcode, threadIndex, perThreadProgramIndex, instructionIndex, instructionOffset)
                }
            }
        }
    }

    internal inline fun transform(action: (InterpreterOpcode, Int, Int, Int) -> InterpreterOpcode)  {
        forEach { interpreterOpcode: InterpreterOpcode, threadIndex: Int, perThreadProgramIndex: Int, instructionIndex: Int, instructionOffset: Int ->
            shortBuffer.put(instructionOffset, action(interpreterOpcode, threadIndex, perThreadProgramIndex, instructionIndex).code.toShort())
        }
    }

    operator fun get(programIndex: Int, instructionIndex: Int): InterpreterOpcode {
        val offset = calculateOffset(programIndex, instructionIndex)
        return InterpreterOpcode(shortBuffer.get(offset).toUShort())
    }

    private fun calculateOffset(threadIndex: Int, perThreadProgramIndex: Int, instructionIndex: Int): Int {
        return (threadIndex * perThreadInstructionCountWithHalt + perThreadProgramIndex * programSizeWithEnd + instructionIndex)
    }

    private fun calculateOffset(programIndex: Int, instructionIndex: Int): Int {
        val threadIndex = programIndex / perThreadProgramCount
        val threadProgramIndex = programIndex % perThreadProgramCount
        return calculateOffset(threadIndex, threadProgramIndex, instructionIndex)
    }

    fun copyProgramTo(programIndex: Int, program: Program) {
        val programOffset = calculateOffset(programIndex, 0)

        for (i in 0 until programSize) {
            val instructionOffset = programOffset + i
            program[i] = InterpreterOpcode(shortBuffer.get(instructionOffset).toUShort())
        }
    }

    internal inline fun copyProgram(fromProgramIndex: Int, toProgramIndex: Int, transformer: (InterpreterOpcode, Int) -> InterpreterOpcode = { io, o -> io}) {
        val fromOffset = calculateOffset(fromProgramIndex, 0)
        val toOffset = calculateOffset(toProgramIndex, 0)

        for (i in 0 until programSizeWithEnd) {
            val relativeOffset = i
            val interpreterInstruction = InterpreterOpcode(shortBuffer.get(fromOffset + relativeOffset).toUShort())
            shortBuffer.put(toOffset + relativeOffset, transformer(interpreterInstruction, i).code.toShort())
        }
    }

}