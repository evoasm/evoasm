package evoasm.x64

import kasm.Buffer
import kasm.NativeBuffer
import kasm.x64.*
import kasm.x64.GpRegister8.*
import kasm.x64.GpRegister16.*
import kasm.x64.GpRegister32.*
import kasm.x64.GpRegister64.*
import kasm.x64.MmRegister.*
import kasm.x64.XmmRegister.*
import kasm.x64.YmmRegister.*

class Interpreter {

    companion object {

        private const val INSTRUCTION_SIZE = 32 // bytes

        private val BUFFER_ADDRESS_REGISTER = R10
        private val IP_REGISTER = R11
    }

//    private val model = Model()
    private val buffer = NativeBuffer(1024, true)

    init {
        emit()
        println(buffer.toByteString())
    }

    private fun Assembler.encodeInstructionProlog(buffer: NativeBuffer) {
    }

    private fun Assembler.encodeInstructionEpilog(buffer: Buffer, offset: Int) {
        add(IP_REGISTER, 2)
        movzx(R12, Address16(IP_REGISTER))
        sal(R12, 5)
        jmp(Address64(BUFFER_ADDRESS_REGISTER, R12, Scale._1, offset))
    }

    private fun Assembler.emitInterpreterProlog() {
        mov(IP_REGISTER, RDI)
        mov(BUFFER_ADDRESS_REGISTER, RSI)
        movzx(R12, Address16(IP_REGISTER))
        jmp(Address64(BUFFER_ADDRESS_REGISTER, R12, Scale._8))
    }

    private fun Assembler.emitInterpreterEpilog() {
    }

    private fun emit() {
        Assembler(buffer).emitStackFrame {
            emitInterpreterProlog()

            align(INSTRUCTION_SIZE)

            val offset = buffer.position()
            enumValues<Instruction>().forEachIndexed {index, instruction ->
                if(instruction != Instruction.RET) {
                    this.encodeInstructionProlog(buffer as NativeBuffer)
                    instruction.encode(buffer)
                    this.encodeInstructionEpilog(buffer, offset)
                    align(INSTRUCTION_SIZE)
                }
            }
            Instruction.RET.encode(buffer)
            emitInterpreterEpilog()
        }
    }

    fun run(program: Program) {
        run(program.codeBuffer)
    }

    private fun run(programBuffer: NativeBuffer) {
        buffer.execute(programBuffer.address, buffer.address)
    }

}

class Program(code: ByteArray) {
    val codeBuffer = NativeBuffer(1024, false)

    init {
        codeBuffer.putBytes(code)
    }
}
