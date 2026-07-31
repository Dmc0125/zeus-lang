package lang

import kotlin.test.*

class TokenizerTest {
    @Test
    fun `tokenizes input successfully`() {
        val input = "+-* / :=;print println true false bool! (){}if else"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.Plus, 1, 1),
            Token(TokenValue.Minus, 1, 2),
            Token(TokenValue.Star, 1, 3),
            Token(TokenValue.Slash, 1, 5),
            Token(TokenValue.Colon, 1, 7),
            Token(TokenValue.Equal, 1, 8),
            Token(TokenValue.Semicolon, 1, 9),
            Token(TokenValue.Print(false), 1, 10),
            Token(TokenValue.Print(true), 1, 16),
            Token(TokenValue.BoolLiteral(true), 1, 24),
            Token(TokenValue.BoolLiteral(false), 1, 29),
            Token(TokenValue.Type(VariableType.Bool), 1, 35),
            Token(TokenValue.Excl, 1, 39),
            Token(TokenValue.LParen, 1, 41),
            Token(TokenValue.RParen, 1, 42),
            Token(TokenValue.LBrace, 1, 43),
            Token(TokenValue.RBrace, 1, 44),
            Token(TokenValue.If, 1, 45),
            Token(TokenValue.Else, 1, 48),
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes number`() {
        val input = "123.45"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.NumberLiteral(123.45), 1, 1)
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes string`() {
        val input = "\"hello\""
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.StringLiteral("hello"), 1, 1)
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes println with string`() {
        val input = "println \"hello\";"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.Print(true), 1, 1),
            Token(TokenValue.StringLiteral("hello"), 1, 9),
            Token(TokenValue.Semicolon, 1, 16)
        )

        assertEquals(expected.size, tokens.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `throws UnterminatedStringError on unterminated string`() {
        val input = "\"hello"
        val expection = assertFailsWith(UnterminatedStringError::class) {
            tokenizerRun(input)
        }
        assertEquals("Syntax error at 1:1: Unterminated string", expection.message)
    }

    @Test
    fun `tokenizes identifier`() {
        val input = "foo"
        val tokens = tokenizerRun(input)

        assertTrue(tokens.size == 1)
        assertEquals(Token(TokenValue.Ident("foo"), 1, 1), tokens[0])
    }

    @Test
    fun `throws SyntaxError on invalid number`() {
        val input = "123.45.67"
        val expection = assertFailsWith(SyntaxError::class) {
            tokenizerRun(input)
        }
        assertEquals("Syntax error at 1:10: Invalid number: 123.45.67", expection.message)
    }

    @Test
    fun `throws SyntaxError on unexpected character`() {
        val input = "@"
        val expection = assertFailsWith(SyntaxError::class) {
            tokenizerRun(input)
        }
        assertEquals("Syntax error at 1:1: Unexpected token: @", expection.message)
    }
}
