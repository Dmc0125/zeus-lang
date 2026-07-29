package lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyzerTest {
    @Test
    fun `throws at invalid unary type`() {
        val u = Expression.Unary(
            TokenValue.Plus,
            Expression.StringLiteral("123"),
        )
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeUnary(u)
        }
    }

    @Test
    fun `throws at invalid unary operator`() {
        val u = Expression.Unary(
            TokenValue.Star,
            Expression.NumberLiteral(123.0),
        )
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeUnary(u)
        }
    }

    @Test
    fun `throws at invalid binary type`() {
        val b = Expression.Binary(
            TokenValue.Plus,
            Expression.StringLiteral("123"),
            Expression.NumberLiteral(123.0),
        )
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeBinary(b)
        }
    }

    @Test
    fun `throws at invalid binary operator`() {
        val b = Expression.Binary(
            TokenValue.Equal,
            Expression.NumberLiteral(123.0),
            Expression.NumberLiteral(123.0),
        )
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeBinary(b)
        }
    }

    @Test
    fun `throws at undefined variable`() {
        val statement = Statement.VariableAssignment("foo", Expression.NumberLiteral(123.0))
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeProgram(listOf(statement))
        }
    }

    @Test
    fun `throws at already defined variable`() {
        val statements = listOf(
            Statement.VariableDeclaration("foo", Expression.NumberLiteral(123.0)),
            Statement.VariableDeclaration("foo", Expression.NumberLiteral(345.0)),
        )
        val analyzer = Analyzer()

        assertFailsWith(RuntimeException::class) {
            analyzer.analyzeProgram(statements)
        }
    }
}
