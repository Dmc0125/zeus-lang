package lang

import kotlin.test.*


class AnalyzerTest {
    fun runPipeline(program: String) {
        val tokens = tokenizerRun(program)
        val ast = Parser(tokens).parseProgram()
        Analyzer().analyzeProgram(ast)
    }

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

    @Test
    fun `throws at duplicate function declaration`() {
        val program = """
            fun foo() {}
            fun foo() {}
        """
        val exception = assertFailsWith(LangError::class) {
            runPipeline(program)
        }
        val expected = LangError(3, 13, ErrorType.Type, ErrorMessage.FunctionAlreadyDeclared)
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
        val expected = LangError(1, 6, ErrorType.Type, "Function not declared")
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


// @Test
// fun `throws at invalid unary type`() {
//     val u = Expression.Unary(
//         TokenValue.Plus,
//         Expression.StringLiteral("123"),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeUnary(u)
//     }
// }

// @Test
// fun `throws at invalid unary operator`() {
//     val u = Expression.Unary(
//         TokenValue.Star,
//         Expression.NumberLiteral(123.0),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeUnary(u)
//     }
// }

// @Test
// fun `does not throw at valid plus unary operator`() {
//     val u = Expression.Unary(
//         TokenValue.Plus,
//         Expression.NumberLiteral(123.0),
//     )
//     val analyzer = Analyzer()
//     analyzer.analyzeUnary(u)
// }

// @Test
// fun `does not throw at valid minus unary operator`() {
//     val u = Expression.Unary(
//         TokenValue.Minus,
//         Expression.NumberLiteral(123.0),
//     )
//     val analyzer = Analyzer()
//     analyzer.analyzeUnary(u)
// }

// @Test
// fun `does not throw at valid excl unary operator`() {
//     val u = Expression.Unary(
//         TokenValue.Excl,
//         Expression.BoolLiteral(true),
//     )
//     val analyzer = Analyzer()
//     analyzer.analyzeUnary(u)
// }

// @Test
// fun `throws at invalid binary type`() {
//     val binaries = listOf(
//         Expression.Binary(
//             TokenValue.Plus,
//             Expression.StringLiteral("123"),
//             Expression.NumberLiteral(123.0),
//         ),
//         Expression.Binary(
//             TokenValue.DoubleEqual,
//             Expression.BoolLiteral(true),
//             Expression.StringLiteral("123"),
//         ),
//         Expression.Binary(
//             TokenValue.Plus,
//             Expression.StringLiteral("222"),
//             Expression.StringLiteral("123"),
//         ),
//     )

//     for (b in binaries) {
//         val analyzer = Analyzer()
//         assertFailsWith(RuntimeException::class) {
//             analyzer.analyzeBinary(b)
//         }
//     }
// }

// @Test
// fun `throws at invalid binary operator`() {
//     val b = Expression.Binary(
//         TokenValue.Equal,
//         Expression.NumberLiteral(123.0),
//         Expression.NumberLiteral(123.0),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeBinary(b)
//     }
// }

// @Test
// fun `throws at undefined variable`() {
//     val statement = Statement.VariableAssignment("foo", Expression.NumberLiteral(123.0))
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeProgram(listOf(statement))
//     }
// }

// @Test
// fun `throws at already defined variable`() {
//     val statements = listOf(
//         Statement.VariableDeclaration("foo", null, Expression.NumberLiteral(123.0)),
//         Statement.VariableDeclaration("foo", null, Expression.NumberLiteral(345.0)),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeProgram(statements)
//     }
// }

// @Test
// fun `throws at variable declaration type mismatch`() {
//     val statements = listOf(
//         Statement.VariableDeclaration("foo", VariableType.String, Expression.NumberLiteral(123.0)),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeProgram(statements)
//     }
// }

// @Test
// fun `throws at variable assignment type mismatch`() {
//     val statements = listOf(
//         Statement.VariableDeclaration("foo", VariableType.String, null),
//         Statement.VariableAssignment("foo", Expression.NumberLiteral(345.0)),
//     )
//     val analyzer = Analyzer()

//     assertFailsWith(RuntimeException::class) {
//         analyzer.analyzeProgram(statements)
//     }
// }

// @Test
// fun `does not throw at valid variable assignment in block`() {
//     val statements = listOf(
//         Statement.VariableDeclaration("foo", VariableType.Number, null),
//         Statement.Block(
//             listOf(
//                 Statement.VariableAssignment("foo", Expression.NumberLiteral(345.0)),
//             )
//         )
//     )
//     val analyzer = Analyzer()
//     analyzer.analyzeProgram(statements)
// }
}
