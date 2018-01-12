package evoasm.x64

import kasm.Buffer
import kasm.x64.*
import kasm.x64.GpRegister8.*
import kasm.x64.GpRegister16.*
import kasm.x64.GpRegister32.*
import kasm.x64.GpRegister64.*
import kasm.x64.MmRegister.*
import kasm.x64.XmmRegister.*
import kasm.x64.YmmRegister.*

enum class Instruction {
    IADD {
        override fun encode(buffer: Buffer) {
            AddR64Rm64.encode(buffer, RAX, RBX)
        }
    },

    ISUB {
        override fun encode(buffer: Buffer) {
            SubR64Rm64.encode(buffer, RAX, RBX)
        }
    },

    IMUL {
        override fun encode(buffer: Buffer) {
            ImulR64Rm64.encode(buffer, RAX, RBX)
        }
    },

    /* MUST STAY LAST */
    RET {
        override fun encode(buffer: Buffer) {
        }
    }
    ;

    abstract fun encode(buffer: Buffer)
}

