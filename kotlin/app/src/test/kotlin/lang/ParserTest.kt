package lang

import kotlin.test.*

class ParserTest {
    @Test
    fun `parses number`() {
        val tokens = listOf(Token(TokenValue.NumberLiteral(123.45), 1, 1))
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertTrue(expr is Expression.NumberLiteral)
        assertEquals(123.45, expr.value)
    }

    @Test
    fun `parses bool literal`() {
        val tokens = listOf(Token(TokenValue.BoolLiteral(true), 1, 1))
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertIs<Expression.BoolLiteral>(expr)
        assertEquals(true, expr.value)
    }

    @Test
    fun `parses unary number`() {
        val tokens = listOf(
            Token(TokenValue.Minus, 1, 1),
            Token(TokenValue.NumberLiteral(123.45), 1, 2),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertTrue(expr is Expression.Unary)
        assertEquals(TokenValue.Minus, expr.operator)
        assertTrue(expr.operand is Expression.NumberLiteral)
        assertEquals(123.45, expr.operand.value)
    }

    @Test
    fun `parses unary bool`() {
        val tokens = listOf(
            Token(TokenValue.Excl, 1, 1),
            Token(TokenValue.BoolLiteral(true), 1, 2),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertIs<Expression.Unary>(expr)
        assertEquals(TokenValue.Excl, expr.operator)
        assertIs<Expression.BoolLiteral>(expr.operand)
        assertEquals(true, expr.operand.value)
    }

    @Test
    fun `parses unary number multiple`() {
        // -(-(-123.45))
        val tokens = listOf(
            Token(TokenValue.Minus, 1, 1),
            Token(TokenValue.Minus, 1, 2),
            Token(TokenValue.Minus, 1, 3),
            Token(TokenValue.NumberLiteral(123.45), 1, 4),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        // -(-(-123.45))
        assertTrue(expr is Expression.Unary)
        assertEquals(TokenValue.Minus, expr.operator)
        // -(-123.45)
        assertTrue(expr.operand is Expression.Unary)
        assertEquals(TokenValue.Minus, expr.operand.operator)
        // -123.45
        assertTrue(expr.operand.operand is Expression.Unary)
        assertEquals(TokenValue.Minus, expr.operand.operand.operator)
        // 123.45
        assertTrue(expr.operand.operand.operand is Expression.NumberLiteral)
        assertEquals(123.45, expr.operand.operand.operand.value)
    }

    @Test
    fun `parses factor`() {
        // (123.45 + 67.89) - 3
        val tokens = listOf(
            Token(TokenValue.NumberLiteral(123.45), 1, 1),
            Token(TokenValue.Plus, 1, 7),
            Token(TokenValue.NumberLiteral(67.89), 1, 8),
            Token(TokenValue.Minus, 1, 13),
            Token(TokenValue.NumberLiteral(3.0), 1, 14),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        println("expr = $expr")

        // (123.45 + 67.89) - 3
        assertIs<Expression.Binary>(expr)
        assertIs<TokenValue.Minus>(expr.operator)

        // 123.45 + 67.89
        assertIs<Expression.Binary>(expr.left)
        assertIs<TokenValue.Plus>(expr.left.operator)

        assertIs<Expression.NumberLiteral>(expr.left.left)
        assertEquals(123.45, expr.left.left.value)

        assertIs<Expression.NumberLiteral>(expr.left.right)
        assertEquals(67.89, expr.left.right.value)

        // 3
        assertIs<Expression.NumberLiteral>(expr.right)
        assertEquals(3.0, expr.right.value)
    }

    @Test
    fun `parses term`() {
        // (123.45 * 25) / 5
        val tokens = listOf(
            Token(TokenValue.NumberLiteral(123.45), 1, 1),
            Token(TokenValue.Star, 1, 7),
            Token(TokenValue.NumberLiteral(25.0), 1, 8),
            Token(TokenValue.Slash, 1, 13),
            Token(TokenValue.NumberLiteral(5.0), 1, 14),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        // (123.45 * 25) / 5
        assertIs<Expression.Binary>(expr)
        assertEquals(TokenValue.Slash, expr.operator)

        // 123.45 * 25
        assertIs<Expression.Binary>(expr.left)
        assertEquals(TokenValue.Star, expr.left.operator)

        assertIs<Expression.NumberLiteral>(expr.left.left)
        assertEquals(123.45, expr.left.left.value)

        assertIs<Expression.NumberLiteral>(expr.left.right)
        assertEquals(25.0, expr.left.right.value)

        // 5
        assertIs<Expression.NumberLiteral>(expr.right)
        assertEquals(5.0, expr.right.value)
    }

    @Test
    fun `parses term with factor`() {
        // 12 * 25 + 5 / 3 => (12 * 25) + (5 / 3)
        val tokens = listOf(
            Token(TokenValue.NumberLiteral(12.0), 1, 1),
            Token(TokenValue.Star, 1, 3),
            Token(TokenValue.NumberLiteral(25.0), 1, 5),
            Token(TokenValue.Plus, 1, 6),
            Token(TokenValue.NumberLiteral(5.0), 1, 8),
            Token(TokenValue.Slash, 1, 9),
            Token(TokenValue.NumberLiteral(3.0), 1, 11),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        // (12 * 25) + (5 / 3)
        assertTrue(expr is Expression.Binary)
        assertEquals(TokenValue.Plus, expr.operator)

        run { // (12 * 25)
            assertTrue(expr.left is Expression.Binary)

            val left_expr = expr.left
            assertEquals(TokenValue.Star, left_expr.operator)

            assertTrue(left_expr.left is Expression.NumberLiteral)
            assertEquals(12.0, left_expr.left.value)

            assertTrue(left_expr.right is Expression.NumberLiteral)
            assertEquals(25.0, left_expr.right.value)
        }

        run { // (5 / 3)
            val right = expr.right

            assertTrue(right is Expression.Binary)
            assertEquals(TokenValue.Slash, right.operator)

            assertTrue(right.left is Expression.NumberLiteral)
            assertEquals(5.0, right.left.value)

            assertTrue(right.right is Expression.NumberLiteral)
            assertEquals(3.0, right.right.value)
        }
    }

    @Test
    fun `parses group`() {
        val tokens = listOf(
            Token(TokenValue.LParen, 1, 1),
            Token(TokenValue.NumberLiteral(123.45), 1, 2),
            Token(TokenValue.RParen, 1, 6)
        )

        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertIs<Expression.NumberLiteral>(expr)
        assertEquals(123.45, expr.value)
    }

    @Test
    fun `parses nested group`() {
        val tokens = listOf(
            Token(TokenValue.LParen, 1, 1),
            Token(TokenValue.LParen, 1, 2),
            Token(TokenValue.NumberLiteral(123.45), 1, 3),
            Token(TokenValue.RParen, 1, 7),
            Token(TokenValue.RParen, 1, 8),
        )

        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        assertIs<Expression.NumberLiteral>(expr)
        assertEquals(123.45, expr.value)
    }

    @Test
    fun `parses group with correct precedence`() {
        // 12 * (25 + 5 / 3) => 12 * (25 + (5 / 3))
        val tokens = listOf(
            Token(TokenValue.NumberLiteral(12.0), 1, 1),
            Token(TokenValue.Star, 1, 3),
            Token(TokenValue.LParen, 1, 4),
            Token(TokenValue.NumberLiteral(25.0), 1, 5),
            Token(TokenValue.Plus, 1, 6),
            Token(TokenValue.NumberLiteral(5.0), 1, 8),
            Token(TokenValue.Slash, 1, 9),
            Token(TokenValue.NumberLiteral(3.0), 1, 11),
            Token(TokenValue.RParen, 1, 12),
        )
        val parser = Parser(tokens)
        val expr = parser.parseExpression()

        // 12 * (25 + 5 / 3)
        assertIs<Expression.Binary>(expr)
        assertEquals(TokenValue.Star, expr.operator)

        assertIs<Expression.NumberLiteral>(expr.left)
        assertEquals(12.0, expr.left.value)

        // 25 + (5 / 3)
        val right = expr.right
        assertIs<Expression.Binary>(right)
        assertEquals(TokenValue.Plus, right.operator)

        assertIs<Expression.NumberLiteral>(right.left)
        assertEquals(25.0, right.left.value)

        // 5 / 3
        var rightRight = right.right
        assertIs<Expression.Binary>(rightRight)
        assertEquals(TokenValue.Slash, rightRight.operator)

        assertIs<Expression.NumberLiteral>(rightRight.left)
        assertEquals(5.0, rightRight.left.value)
        assertIs<Expression.NumberLiteral>(rightRight.right)
        assertEquals(3.0, rightRight.right.value)
    }

    @Test
    fun `parses variable declaration`() {
        val tokens = listOf(
            Token(TokenValue.Ident("foo"), 1, 1),
            Token(TokenValue.Colon, 1, 4),
            Token(TokenValue.Type(VariableType.Number), 1, 5),
            Token(TokenValue.Semicolon, 1, 6)
        )

        val parser = Parser(tokens)
        val stmt = parser.parseStatement()

        assertTrue(stmt is Statement.VariableDeclaration)
        assertEquals("foo", stmt.name)
        assertEquals(VariableType.Number, stmt.type)
        assertEquals(stmt.value, null)
    }

    @Test
    fun `parses variable declaration with expression`() {
        val tokens = listOf(
            Token(TokenValue.Ident("foo"), 1, 1),
            Token(TokenValue.Colon, 1, 4),
            Token(TokenValue.Equal, 1, 5),
            Token(TokenValue.NumberLiteral(12.0), 1, 6),
            Token(TokenValue.Semicolon, 1, 7),
        )

        val parser = Parser(tokens)
        val stmt = parser.parseStatement()

        assertTrue(stmt is Statement.VariableDeclaration)
        assertEquals("foo", stmt.name)
        assertTrue(stmt.value is Expression.NumberLiteral)
        assertEquals(12.0, stmt.value.value)
    }

    @Test
    fun `parses indentifier in expression`() {
        val tokens = listOf(
            Token(TokenValue.Ident("foo"), 1, 1),
            Token(TokenValue.Colon, 1, 4),
            Token(TokenValue.Equal, 1, 5),
            Token(TokenValue.Ident("bar"), 1, 6),
            Token(TokenValue.Semicolon, 1, 9),
        )

        val parser = Parser(tokens)
        val stmt = parser.parseStatement()

        assertTrue(stmt is Statement.VariableDeclaration)
        assertEquals("foo", stmt.name)
        assertTrue(stmt.value is Expression.Ident)
        assertEquals("bar", stmt.value.name)
    }

    @Test
    fun `parses multiple statements`() {
        val tokens = listOf(
            Token(TokenValue.Ident("foo"), 1, 1),
            Token(TokenValue.Colon, 1, 1),
            Token(TokenValue.Equal, 1, 1),
            Token(TokenValue.NumberLiteral(12.0), 1, 1),
            Token(TokenValue.Semicolon, 1, 1),

            Token(TokenValue.Ident("foo"), 2, 1),
            Token(TokenValue.Equal, 2, 1),
            Token(TokenValue.NumberLiteral(13.0), 2, 1),
            Token(TokenValue.Semicolon, 2, 1),
        )

        val parser = Parser(tokens)
        val statements = parser.parseProgram()
        assertEquals(2, statements.size)

        run {
            val foo = statements[0]
            assertIs<Statement.VariableDeclaration>(foo)
            assertEquals("foo", foo.name)
            assertIs<Expression.NumberLiteral>(foo.value)
            assertEquals(12.0, foo.value.value)
        }

        run {
            val foo = statements[1]
            assertIs<Statement.VariableAssignment>(foo)
            assertEquals("foo", foo.name)
            assertIs<Expression.NumberLiteral>(foo.value)
            assertEquals(13.0, foo.value.value)
        }
    }

    @Test
    fun `parses print statement`() {
        val tokens = listOf(
            Token(TokenValue.Print(false), 1, 1),
            Token(TokenValue.NumberLiteral(123.45), 1, 6),
            Token(TokenValue.Semicolon, 1, 11),
        )

        val parser = Parser(tokens)
        val stmt = parser.parseStatement()

        assertTrue(stmt is Statement.Print)
        assertTrue(stmt.expression is Expression.NumberLiteral)
        assertEquals(123.45, stmt.expression.value)
        assertFalse(stmt.ln)
    }

    @Test
    fun `parses block`() {
        val tokens = listOf(
            Token(TokenValue.LBrace, 1, 1),
            Token(TokenValue.Print(false), 1, 2),
            Token(TokenValue.NumberLiteral(123.45), 1, 7),
            Token(TokenValue.Semicolon, 1, 12),
            Token(TokenValue.RBrace, 1, 1),
        )

        val parser = Parser(tokens)
        val stmt = parser.parseStatement()

        assertIs<Statement.Block>(stmt)
        assertEquals(1, stmt.statements.size)
        assertIs<Statement.Print>(stmt.statements[0])
    }
}
