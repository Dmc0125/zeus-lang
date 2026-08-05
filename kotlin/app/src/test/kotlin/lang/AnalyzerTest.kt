package lang

import kotlin.test.*

open class AnalyzerTest {
    fun runPipeline(program: String) {
        val tokens = tokenizerRun(program)
        val ast = Parser(tokens).parseProgram()
        Analyzer().analyzeProgram(ast)
    }
}

class AnalyzerLoop : AnalyzerTest() {
    @Test
    fun `throws at break outside loop`() {
        val program = "break;"
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(1, 1, ErrorType.Syntax, ErrorMessage.BreakOutsideLoop)
        assertEquals(expected, exception)
    }

    @Test
    fun `throws at continue outside loop`() {
        val program = "continue;"
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(1, 1, ErrorType.Syntax, ErrorMessage.ContinueOutsideLoop)
        assertEquals(expected, exception)
    }
}

class AnalyzerFunction : AnalyzerTest() {
    @Test
    fun `throws at duplicate function declaration`() {
        val program = """
            fun foo() {}
            fun foo() {}
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(3, 13, ErrorType.Type, ErrorMessage.AlreadyDeclared)
        assertEquals(expected, exception)
    }

    @Test
    fun `throws at return type mismatch`() {
        val input = funDecl(
            "foo",
            listOf(),
            VariableType.Number,
            block(listOf(returnStmt(str("123")))),
        )
        val exception = assertFailsWith(LangError::class) {
            Analyzer().analyzeProgram(listOf(input))
        }
        val expected = LangError(0, 0, ErrorType.Type, ErrorMessage.ReturnTypeMismatch)
        assertEquals(expected, exception)
    }

    @Test
    fun `handles nested return type`() {
        val program = """
            fun foo(): number {
                fun bar(): string {
                    return "123";
                }
                return 123;
            }
        """
        runPipeline(program)
    }

    @Test
    fun `throws on missing return`() {
        val program = """
            fun foo(): number {
            }
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(2, 13, ErrorType.Syntax, ErrorMessage.MissingReturn)
        assertEquals(expected, exception)
    }

    @Test
    fun `throws on unknown function call`() {
        val program = "x := foo();"
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(1, 6, ErrorType.Type, ErrorMessage.Undefined)
        assertEquals(expected, exception)
    }

    @Test
    fun `throws at invalid arg count`() {
        val program = """
            fun foo(x: number) { }
            x := foo();
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(3, 18, ErrorType.Type, "Expected 1 arguments")
        assertEquals(expected, exception)
    }

    @Test
    fun `throws at arg type mismatch`() {
        val program = """
            x := 1;
            fun foo(y: string) { }
            z := foo(x);
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(4, 22, ErrorType.Type, "Argument type mismatch")
        assertEquals(expected, exception)
    }

    @Test
    fun `throws at invalid return type`() {
        data class Input(
            val name: String,
            val input: String,
            val expected: LangError,
        )

        val inputs = listOf(
            Input(
                "declared type none",
                """
                    fun foo() {
                        return "123";
                    }
                """,
                LangError(3, 25, ErrorType.Type, ErrorMessage.ReturnTypeMismatch)
            ),
            Input(
                "return type none",
                """
                    fun foo(): number {
                        return;
                    }
                """,
                LangError(3, 25, ErrorType.Type, ErrorMessage.ReturnTypeMismatch)
            ),
        )

        for (input in inputs) {
            println("Running test: ${input.name}")
            val exception = assertFailsWith(LangError::class) {
                runPipeline(input.input)
            }
            assertEquals(input.expected, exception)
        }
    }

    @Test
    fun `does not throw when no return and return type is none`() {
        val program = """
            fun foo() {

            }
        """
        runPipeline(program)
    }

    @Test
    fun `throws if a branch is missing return`() {
        val program = """
            fun foo(): number {
                if (true) {
                    return 123;
                }
            }
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(2, 13, ErrorType.Syntax, ErrorMessage.MissingReturn)
        assertEquals(expected, exception)
    }

    @Test
    fun `does not throw if return is followed by other statements`() {
        val program = """
            fun foo(): number {
                x := 123;
                return 123;
                println x;
            }
        """
        runPipeline(program)
    }

    @Test
    fun `does not throw if all branches return`() {
        val program = """
            fun foo(): number {
                if (true) {
                    return 123;
                }
                return 456;
            }
        """
        runPipeline(program)
    }
}
