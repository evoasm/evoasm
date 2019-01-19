package evoasm.x64

import kasm.x64.*

class InterpreterOptions(instructions: List<Instruction> = defaultInstructions,
                         val allowUnsupportedInstructions: Boolean = false,
                         val safeDivision: Boolean = true,
                         val compressOpcodes: Boolean = true,
                         val moveInstructions: List<MoveInstruction> = defaultMoveInstructions,
                         val movesGenerator: (MoveInstruction, List<Register>) -> Sequence<Pair<Register, Register>> = ::defaultMovesGenerator,
                         val xmmOperandRegisters: List<List<XmmRegister>> = DEFAULT_XMM_OPERAND_REGISTERS,
                         val ymmOperandRegisters: List<List<YmmRegister>> = DEFAULT_YMM_OPERAND_REGISTERS,
                         val mmOperandRegisters: List<List<MmRegister>> = DEFAULT_MM_OPERAND_REGISTERS,
                         val gp64OperandRegisters: List<List<GpRegister64>> = DEFAULT_GP64_OPERAND_REGISTERS,
                         val gp32OperandRegisters: List<List<GpRegister32>> = DEFAULT_GP32_OPERAND_REGISTERS,
                         val gp16OperandRegisters: List<List<GpRegister16>> = DEFAULT_GP16_OPERAND_REGISTERS,
                         val gp8OperandRegisters: List<List<GpRegister8>> = DEFAULT_GP8_OPERAND_REGISTERS,
                         val unsafe: Boolean) {

    companion object {
        private fun supportedInstruction(i0: Instruction,
                                         i1: Instruction,
                                         allowUnsupportedInstructions: Boolean): Instruction {
            if (allowUnsupportedInstructions || i1.isSupported()) return i1
            return i0
        }

        val defaultInstructions: List<Instruction> get() {
            return InstructionGroup.all
        }

        private fun <E> repeatList(count: Int, list: List<E>): List<List<E>> {
            return List(count) {list}
        }

        val DEFAULT_XMM_OPERAND_REGISTERS = repeatList(4, listOf(XmmRegister.XMM0, XmmRegister.XMM1, XmmRegister.XMM2))
        val DEFAULT_YMM_OPERAND_REGISTERS = repeatList(4, listOf(YmmRegister.YMM0, YmmRegister.YMM1, YmmRegister.YMM2))
        val DEFAULT_MM_OPERAND_REGISTERS = repeatList(3, listOf(MmRegister.MM0, MmRegister.MM1, MmRegister.MM2))
        val DEFAULT_GP64_OPERAND_REGISTERS = repeatList(3, Interpreter.GP_REGISTERS.take(3))
        val DEFAULT_GP32_OPERAND_REGISTERS = repeatList(3, Interpreter.GP_REGISTERS.take(3).map{it.subRegister32})
        val DEFAULT_GP16_OPERAND_REGISTERS = repeatList(3, Interpreter.GP_REGISTERS.take(3).map{it.subRegister16})
        val DEFAULT_GP8_OPERAND_REGISTERS = repeatList(3, Interpreter.GP_REGISTERS.take(3).map{it.subRegister8})



        val defaultMoveInstructions: List<MoveInstruction> get() {
            return listOf(MovR64Rm64, MovsdXmmm64Xmm)
        }




        val DEFAULT = InterpreterOptions(unsafe = false)

        fun defaultMovesGenerator(moveInstruction: MoveInstruction, registers: List<Register>): Sequence<Pair<Register, Register>> {
            val list = mutableListOf<Pair<Register, Register>>()

            val maxOperandRegisterCount = when(registers.first()) {
               is XmmRegister, is YmmRegister -> 3
               else -> 2
            }

            for (i in 0 until maxOperandRegisterCount) {
                val destinationRegister = registers[i]

                for(j in 0 until maxOperandRegisterCount) {
                    val sourceRegister = registers[j]
                    list.add(destinationRegister to sourceRegister)
                }
            }

            for (i in maxOperandRegisterCount until registers.lastIndex) {
               list.add(registers[i] to registers[i % maxOperandRegisterCount])
               list.add(registers[(i + 1) % maxOperandRegisterCount] to registers[i])
            }

            // Hmmm, this gives operandsRegisterCount**2 + 2 * (n - operandsRegisterCount) instructions
            // i.e. 9 + 2 * 13 = 33 for e.g. XMM registers

            return list.asSequence()
        }
    }


    val instructions: List<Instruction>

    init {
        this.instructions = if (!allowUnsupportedInstructions) {
            instructions.filter {
                val supported = it.isSupported()

                if (!supported) println("filtering out unsupported instruction ${it}")


                supported
            }
        } else {
            instructions
        }
    }

}

