package lang

import kotlin.test.*

class TokenizerTest {
    @Test
    fun `tokenizes expressions`() {
        val input = "5 + 2 / 10 *6 || -0 && == \"123\" != true && false"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.NumberLiteral(5.0), 1, 1),
            Token(TokenValue.Plus, 1, 3),
            Token(TokenValue.NumberLiteral(2.0), 1, 5),
            Token(TokenValue.Slash, 1, 7),
            Token(TokenValue.NumberLiteral(10.0), 1, 9),
            Token(TokenValue.Star, 1, 12),
            Token(TokenValue.NumberLiteral(6.0), 1, 13),
            Token(TokenValue.DoublePipe, 1, 15),
            Token(TokenValue.Minus, 1, 18),
            Token(TokenValue.NumberLiteral(0.0), 1, 19),
            Token(TokenValue.DoubleAmp, 1, 21),
            Token(TokenValue.DoubleEqual, 1, 24),
            Token(TokenValue.StringLiteral("123"), 1, 27),
            Token(TokenValue.ExclEqual, 1, 33),
            Token(TokenValue.BoolLiteral(true), 1, 36),
            Token(TokenValue.DoubleAmp, 1, 41),
            Token(TokenValue.BoolLiteral(false), 1, 44),
        )

        assertEquals(expected.size, tokens.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes literals`() {
        val input = "5 5.0 \"hello\" true false x"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.NumberLiteral(5.0), 1, 1),
            Token(TokenValue.NumberLiteral(5.0), 1, 3),
            Token(TokenValue.StringLiteral("hello"), 1, 7),
            Token(TokenValue.BoolLiteral(true), 1, 15),
            Token(TokenValue.BoolLiteral(false), 1, 20),
            Token(TokenValue.Ident("x"), 1, 26),
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes operators`() {
        val input = "- + / : = == != && || < <= > >= * ! ( ) {}"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.Minus, 1, 1),
            Token(TokenValue.Plus, 1, 3),
            Token(TokenValue.Slash, 1, 5),
            Token(TokenValue.Colon, 1, 7),
            Token(TokenValue.Equal, 1, 9),
            Token(TokenValue.DoubleEqual, 1, 11),
            Token(TokenValue.ExclEqual, 1, 14),
            Token(TokenValue.DoubleAmp, 1, 17),
            Token(TokenValue.DoublePipe, 1, 20),
            Token(TokenValue.Lt, 1, 23),
            Token(TokenValue.LtEqual, 1, 25),
            Token(TokenValue.Gt, 1, 28),
            Token(TokenValue.GtEqual, 1, 30),
            Token(TokenValue.Star, 1, 33),
            Token(TokenValue.Excl, 1, 35),
            Token(TokenValue.LParen, 1, 37),
            Token(TokenValue.RParen, 1, 39),
            Token(TokenValue.LBrace, 1, 41),
            Token(TokenValue.RBrace, 1, 42),
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `tokenizes keywords`() {
        val input = "if else for print println number string bool break continue"
        val tokens = tokenizerRun(input)

        val expected = listOf(
            Token(TokenValue.If, 1, 1),
            Token(TokenValue.Else, 1, 4),
            Token(TokenValue.For, 1, 9),
            Token(TokenValue.Print(false), 1, 13),
            Token(TokenValue.Print(true), 1, 19),
            Token(TokenValue.Type(VariableType.Number), 1, 27),
            Token(TokenValue.Type(VariableType.String), 1, 34),
            Token(TokenValue.Type(VariableType.Bool), 1, 41),
            Token(TokenValue.Break, 1, 46),
            Token(TokenValue.Continue, 1, 52),
        )

        assertTrue(tokens.size == expected.size)
        for (i in tokens.indices) {
            assertEquals(expected[i], tokens[i])
        }
    }

    @Test
    fun `throws on invalid number`() {
        val input = "123.45.67"
        val exception = assertFailsWith(LangError::class) {
            tokenizerRun(input)
        }
        val expected = LangError(1, 1, ErrorType.Syntax, ErrorMessage.InvalidNumber)
        assertEquals(expected, exception)
    }

    @Test
    fun `throws on unexpected character`() {
        val input = "@"
        val exception = assertFailsWith(LangError::class) {
            tokenizerRun(input)
        }
        val expected = LangError(1, 1, ErrorType.Syntax, ErrorMessage.UnexpectedToken)
        assertEquals(expected, exception)
    }
}
