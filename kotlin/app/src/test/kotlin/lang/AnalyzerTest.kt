package lang

import kotlin.test.*

open class AnalyzerTest {
    fun runPipeline(program: String) {
        val tokenizer = Tokenizer(program)
        val tokens = tokenizer.run()
        if (tokenizer.errors.isNotEmpty()) {
            throw Errors(tokenizer.errors)
        }
        val (ast, parserErrors) = Parser(tokens).parseProgram()
        if (parserErrors.isNotEmpty()) {
            throw Errors(parserErrors)
        }
        val analyzerErrors = Analyzer().analyzeProgram(ast)
        if (analyzerErrors.isNotEmpty()) {
            throw Errors(analyzerErrors)
        }
    }
}

class AnalyzerLoop : AnalyzerTest() {
    @Test
    fun `throws at break outside loop`() {
        val program = "break;"
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(1, 1, ErrorType.Syntax, ErrorMessage.BreakOutsideLoop))
            assertEquals(expected, e.errors)
        }
    }

    @Test
    fun `throws at continue outside loop`() {
        val program = "continue;"
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(1, 1, ErrorType.Syntax, ErrorMessage.ContinueOutsideLoop))
            assertEquals(expected, e.errors)
        }
    }
}

class AnalyzerFunction : AnalyzerTest() {
    @Test
    fun `throws at duplicate function declaration`() {
        val program = """
            fun foo() {}
            fun foo() {}
        """
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(3, 13, ErrorType.Type, ErrorMessage.AlreadyDeclared))
            assertEquals(expected, e.errors)
        }
    }

    @Test
    fun `throws at return type mismatch`() {
        val input = funDecl(
            "foo",
            listOf(),
            VariableType.Number,
            block(listOf(returnStmt(str("123")))),
        )
        val errors = Analyzer().analyzeProgram(listOf(input))
        val expected = listOf(LangError(0, 0, ErrorType.Type, ErrorMessage.ReturnTypeMismatch))
        assertEquals(expected, errors)
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
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(2, 13, ErrorType.Syntax, ErrorMessage.MissingReturn))
            assertEquals(expected, e.errors)
        }
    }

    @Test
    fun `throws on unknown function call`() {
        val program = "x := foo();"
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(1, 6, ErrorType.Type, ErrorMessage.Undefined))
            assertEquals(expected, e.errors)
        }
    }

    @Test
    fun `throws at invalid arg count`() {
        val program = """
            fun foo(x: number) { }
            x := foo();
        """
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(3, 18, ErrorType.Type, "Expected 1 arguments"))
            assertEquals(expected, e.errors)
        }
    }

    @Test
    fun `throws at arg type mismatch`() {
        val program = """
            x := 1;
            fun foo(y: string) { }
            z := foo(x);
        """
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = listOf(LangError(4, 22, ErrorType.Type, "Argument type mismatch"))
            assertEquals(expected, e.errors)
        }
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
            try {
                runPipeline(input.input)
                fail("Expected Errors to be thrown")
            } catch (e: Errors) {
                val expected = listOf(input.expected)
                assertEquals(expected, e.errors)
            }
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
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = LangError(2, 13, ErrorType.Syntax, ErrorMessage.MissingReturn)
            assertEquals(expected, e.errors[0])
        }
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

    @Test
    fun `does not throw if return is missing but loop is infinite`() {
        val program = """
            fun foo(): number {
                if true {
                    return 123;
                }
                for true {
                }
            }
        """
        runPipeline(program)
    }

    @Test
    fun `throws if return is missing after loop with break`() {
        val program = """
            fun foo(): number {
                for true {
                    break;
                }
            }
        """
        try {
            runPipeline(program)
            fail("Expected Errors to be thrown")
        } catch (e: Errors) {
            val expected = LangError(2, 13, ErrorType.Syntax, ErrorMessage.MissingReturn)
            assertEquals(expected, e.errors[0])
        }
    }

    @Test
    fun `should handle recursion`() {
        val program = """
            fun foo(): number {
                return foo();
            }
        """
        runPipeline(program)
    }
}
