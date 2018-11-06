package evoasm.x64

import kasm.x64.*
import kasm.x64.GpRegister8.*
import kasm.x64.GpRegister16.*
import kasm.x64.GpRegister32.*
import kasm.x64.GpRegister64.*
import kasm.x64.MmRegister.*
import kasm.x64.XmmRegister.*
import kasm.x64.YmmRegister.*

abstract class AbstractInterpreterInstruction {
    fun encode() {

    }
}

class Instrinsic