package evoasm.x64

import evoasm.x64.Interpreter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class InterpreterTest {

    @Test
    fun run() {
        val program = Program(byteArrayOf(3,3,2,1))
        val interpreter = Interpreter()
//        interpreter.run(program)
    }
}