# Evoasm

## Description

*Evoasm* is an AIMGP (*Automatic Induction of Machine code by Genetic Programming*) engine.

You give it a set of examples, that is, several input/output pairs, that describe a program's behavior.
It will then try to come up with a short program (in the form of machine code) that follows your specification,
by means of genetic programming.
*Evoasm* contains a JIT that executes the generated machine code on the fly.

Currently, the only supported architecture is **x86-64**.

***NOTE**: Evoasm is currently WIP and unfinished. However, you
 should be able to import it into IDEA and run the examples.*

## Interpreter

*Evoasm* just-in-time compiles a fast interpreter at runtime. The interpreter instructions
map one-to-one to *x86-64* instructions. By using an interpreter, *Evoasm* avoids the need 
to re-JIT program candidates.

## Features

* Fast direct-threaded interpreter
* [x86-64](https://github.com/evoasm/kasm) up to AVX2 (no FPU)
* Parallel evaluation using OpenMP

## Examples

### Float Values

```kotlin
    val options = PopulationOptions(
            128_000,
            4,
            123456,
            12,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_SS_AVX_XMM_INSTRUCTIONS.instructions.filterNot { it == VroundssXmmXmmXmmm32Imm8 },
                    moveInstructions = listOf(),
                    compressOpcodes = false,
                    forceMultithreading = false,
                    threadCount = 5,
                    unsafe = false),
            0.01f,
            demeSize = 10,
            majorGenerationFrequency = 1,
            maxOffspringRatio = 0.05
    )

    // sqrt(x**3 + 2*x)
    val sampleSet = FloatSampleSet(1,
            0.0f, 0.0f,
            0.5f, 1.0606601717798212f,
            1.0f, 1.7320508075688772f,
            1.5f, 2.5248762345905194f,
            2.0f, 3.4641016151377544f,
            2.5f, 4.541475531146237f,
            3.0f, 5.744562646538029f,
            3.5f, 7.0622234459127675f,
            4.0f, 8.48528137423857f,
            4.5f, 10.00624804809475f,
            5.0f, 11.61895003862225f
    )
    val population = FloatPopulation(sampleSet, options)
    population.run()
```

Result will be:

```
vfmsub231ss xmm0, xmm0, xmm0
vfmadd231ss xmm2, xmm1, xmm0
vaddss xmm2, xmm1, xmm2
vfmadd132ss xmm1, xmm2, xmm1
vminss xmm2, xmm2, xmm1
vfnmsub132ss xmm2, xmm0, xmm1
vfnmsub213ss xmm2, xmm2, xmm1
vsqrtss xmm0, xmm2, xmm1
```

### Double Vector Values

```kotlin
    val options = PopulationOptions(
            2000,
            4,
            Random.nextLong(),//1234567,
            1,
            InterpreterOptions(instructions = InstructionGroup.ARITHMETIC_PD_AVX_YMM_INSTRUCTIONS.instructions,
                    moveInstructions = listOf(),
                    unsafe = true),
            0.01f,
            demeSize = 1,
            majorGenerationFrequency = 1
    )

    // simple vector addition
    val sampleSet = DoubleVectorSampleSet(2, VectorSize.BITS_256,
            0.0, 0.0, 0.0, 0.0,         1.0, 1.0, 1.0, 1.0,     1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0,         2.0, 2.0, 2.0, 2.0,     3.0, 3.0, 3.0, 3.0,
            2.0, 2.0, 2.0, 2.0,         2.0, 2.0, 2.0, 2.0,     4.0, 4.0, 4.0, 4.0)

    val population = DoubleVectorPopulation(sampleSet, options)
    population.run()
```

Result will be (program size was 1):
```kotlin
vaddpd ymm0, ymm2, ymm1
```

### Instruction Finder

```kotlin
    val finder = InstructionFinder<Long>(1,
           0b0111, 3,
           0b1, 1,
           0b1111, 4,
           0b11111, 5)
    finder.find()

    val finder2 = InstructionFinder<Long>(2,
                                       2, 2, 4,
                                       16, 4, 20,
                                       100, 10, 110,
                                       144, 12, 156)
    finder2.find()

```

Result will be:
```
FOUND 3 instructions:
	kasm.x64.PopcntR16Rm16@6bd61f98
	kasm.x64.PopcntR16Rm16@6bd61f98
	kasm.x64.PopcntR16Rm16@6bd61f98

FOUND 20 instructions:
	kasm.x64.AdcRm8R8@3059cbc
	kasm.x64.AdcR8Rm8@24fcf36f
	kasm.x64.AddRm8R8@ea6147e
	kasm.x64.AddR8Rm8@4d02f94e
	kasm.x64.AdcRm16R16@7e5afaa6
	kasm.x64.AdcR16Rm16@63a12c68
	kasm.x64.AddRm16R16@581ac8a8
	kasm.x64.AddR16Rm16@6d4e5011
	kasm.x64.AdcRm32R32@4d465b11
	kasm.x64.AdcR32Rm32@53fdffa1
	kasm.x64.AdcxR32Rm32@5562c41e
	kasm.x64.AddRm32R32@3232a28a
	kasm.x64.AddR32Rm32@73e22a3d
	kasm.x64.AdoxR32Rm32@47faa49c
	kasm.x64.AdcRm64R64@66d3eec0
	kasm.x64.AdcR64Rm64@1e04fa0a
	kasm.x64.AdcxR64Rm64@1af2d44a
	kasm.x64.AddRm64R64@543588e6
	kasm.x64.AddR64Rm64@f5acb9d
	kasm.x64.AdoxR64Rm64@4fb3ee4e
```


## Installation
First, install [kasm](https://github.com/evoasm/kasm).
Then import the project into IDEA.
Note that *kasm* and *evoasm* repositories should reside inside the same directory.

## License

[MPL-2.0][license]

