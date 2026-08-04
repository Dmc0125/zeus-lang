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
