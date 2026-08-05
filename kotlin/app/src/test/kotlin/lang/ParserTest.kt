package lang

import kotlin.test.*

class ParserTest {
    @Test
    fun `parses literal`() {
        data class Input(
            val token: Token,
            val expected: Expression,
        )

        val inputs = listOf(
            Input(
                Token(TokenValue.NumberLiteral(123.45), 1, 1),
                num(123.45, 1, 1),
            ),
            Input(
                Token(TokenValue.StringLiteral("hello"), 1, 1),
                str("hello", 1, 1),
            ),
            Input(
                Token(TokenValue.BoolLiteral(true), 1, 1),
                bool(true, 1, 1)
            ),
        )

        for (input in inputs) {
            val parser = Parser(listOf(input.token))
            val expr = parser.parseExpression()
            assertEquals(input.expected, expr)
        }
    }

    @Test
    fun `parses unary`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Expression,
        )

        val inputs = listOf(
            // -123.45
            Input(
                "-123.45",
                listOf(
                    Token(TokenValue.Minus, 1, 1),
                    Token(TokenValue.NumberLiteral(123.45), 1, 2),
                ),
                unary(TokenValue.Minus, num(123.45, 1, 2), 1, 1),
            ),
            // !true
            Input(
                "!true",
                listOf(
                    Token(TokenValue.Excl, 1, 1),
                    Token(TokenValue.BoolLiteral(true), 1, 2),
                ),
                unary(TokenValue.Excl, bool(true, 1, 2), 1, 1),
            ),
            // --22
            Input(
                "--22",
                listOf(
                    Token(TokenValue.Minus, 1, 1),
                    Token(TokenValue.Minus, 1, 2),
                    Token(TokenValue.NumberLiteral(22.0), 1, 3),
                ),
                unary(TokenValue.Minus, unary(TokenValue.Minus, num(22.0, 1, 3), 1, 2), 1, 1),
            ),
            // +123.45
            Input(
                "+123.45",
                listOf(
                    Token(TokenValue.Plus, 1, 1),
                    Token(TokenValue.NumberLiteral(123.45), 1, 2),
                ),
                unary(TokenValue.Plus, num(123.45, 1, 2), 1, 1),
            ),
            // !!false
            Input(
                "!!false",
                listOf(
                    Token(TokenValue.Excl, 1, 1),
                    Token(TokenValue.Excl, 1, 2),
                    Token(TokenValue.BoolLiteral(false), 1, 3),
                ),
                unary(TokenValue.Excl, unary(TokenValue.Excl, bool(false, 1, 3), 1, 2), 1, 1),
            ),
        )

        for (input in inputs) {
            val parser = Parser(input.tokens)
            val expr = parser.parseExpression()
            assertEquals(input.expected, expr, "Failed to parse unary ${input.name}")
        }
    }

    @Test
    fun `parses binary arithmetic`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Expression,
        )

        val inputs = listOf(
            // 0.0 + 0.0
            Input(
                "0.0 + 0.0",
                listOf(
                    Token(TokenValue.NumberLiteral(0.0), 1, 1),
                    Token(TokenValue.Plus, 1, 2),
                    Token(TokenValue.NumberLiteral(0.0), 1, 3),
                ),
                binary(TokenValue.Plus, num(0.0, 1, 1), num(0.0, 1, 3), 1, 1),
            ),
            // left association
            // 123.45 + 67.89 - 3 => (123.45 + 67.89) - 3
            Input(
                "123.45 + 67.89 - 3",
                listOf(
                    Token(TokenValue.NumberLiteral(123.45), 1, 1),
                    Token(TokenValue.Plus, 1, 7),
                    Token(TokenValue.NumberLiteral(67.89), 1, 8),
                    Token(TokenValue.Minus, 1, 14),
                    Token(TokenValue.NumberLiteral(3.0), 1, 15),
                ),
                binary(
                    TokenValue.Minus,
                    binary(TokenValue.Plus, num(123.45, 1, 1), num(67.89, 1, 8), 1, 1),
                    num(3.0, 1, 15),
                    1, 1
                ),
            ),
            // precedence
            // 123.0 + 22.1 * 85.5 / 3.9 => 123.0 + ((22.1 * 85.5) / 3.9)
            Input(
                "123.0 + 22.1 * 85.5 / 3.9",
                listOf(
                    Token(TokenValue.NumberLiteral(123.0), 1, 1),
                    Token(TokenValue.Plus, 1, 7),
                    Token(TokenValue.NumberLiteral(22.1), 1, 13),
                    Token(TokenValue.Star, 1, 16),
                    Token(TokenValue.NumberLiteral(85.5), 1, 19),
                    Token(TokenValue.Slash, 1, 22),
                    Token(TokenValue.NumberLiteral(3.9), 1, 25),
                ),
                binary(
                    TokenValue.Plus,
                    num(123.0, 1, 1),
                    binary(
                        TokenValue.Slash,
                        binary(
                            TokenValue.Star,
                            num(22.1, 1, 13),
                            num(85.5, 1, 19),
                            1, 13,
                        ),
                        num(3.9, 1, 25),
                        1, 13,
                    ),
                    1, 1,
                ),
            ),
            // group
            // 22.0 + 85.0 * (3.0 / 3.0) => 22.0 + (85.0 * (3.0 / 3.0))
            Input(
                "22.0 + 85.0 * (3.0 / 3.0)",
                listOf(
                    Token(TokenValue.NumberLiteral(22.0), 1, 1),
                    Token(TokenValue.Plus, 1, 2),
                    Token(TokenValue.NumberLiteral(85.0), 1, 3),
                    Token(TokenValue.Star, 1, 4),
                    Token(TokenValue.LParen, 1, 5),
                    Token(TokenValue.NumberLiteral(3.0), 1, 6),
                    Token(TokenValue.Slash, 1, 7),
                    Token(TokenValue.NumberLiteral(3.0), 1, 8),
                    Token(TokenValue.RParen, 1, 9),
                ),
                binary(
                    TokenValue.Plus,
                    num(22.0, 1, 1),
                    binary(
                        TokenValue.Star,
                        num(85.0, 1, 3),
                        binary(
                            TokenValue.Slash,
                            num(3.0, 1, 6),
                            num(3.0, 1, 8),
                            1, 6,
                        ),
                        1, 3,
                    ),
                    1, 1,
                )
            ),
            // nested group
            // ((22.0)) => 22.0
            Input(
                "((22.0))",
                listOf(
                    Token(TokenValue.LParen, 1, 1),
                    Token(TokenValue.LParen, 1, 2),
                    Token(TokenValue.NumberLiteral(22.0), 1, 3),
                    Token(TokenValue.RParen, 1, 4),
                    Token(TokenValue.RParen, 1, 5),
                ),
                num(22.0, 1, 3),
            ),
            // group followed by binary
            // (22.0) + 3.0 => 22.0 + 3.0
            Input(
                "(22.0) + 3.0",
                listOf(
                    Token(TokenValue.LParen, 1, 1),
                    Token(TokenValue.NumberLiteral(22.0), 1, 2),
                    Token(TokenValue.RParen, 1, 3),
                    Token(TokenValue.Plus, 1, 4),
                    Token(TokenValue.NumberLiteral(3.0), 1, 5),
                ),
                binary(TokenValue.Plus, num(22.0, 1, 2), num(3.0, 1, 5), 1, 2)
            ),
        )

        for (input in inputs) {
            println("Starting test: ${input.name}")
            val parser = Parser(input.tokens)
            try {
                val expr = parser.parseExpression()
                assertEquals(input.expected, expr, input.name)
            } catch (e: LangError) {
                fail("Unexpected exception: ${input.name}", e)
            }
        }
    }

    @Test
    fun `parses binary comparison`() {
        data class Input(
            val tokens: List<Token>,
            val expected: Expression,
        )

        val inputs = listOf(
            // true == false
            Input(
                listOf(
                    Token(TokenValue.BoolLiteral(true), 1, 1),
                    Token(TokenValue.DoubleEqual, 1, 5),
                    Token(TokenValue.BoolLiteral(false), 1, 7),
                ),
                binary(
                    TokenValue.DoubleEqual,
                    bool(true, 1, 1),
                    bool(false, 1, 7),
                    1, 1
                ),
            ),
            // precendce
            // 22.0 + 5.0 != 67.0
            Input(
                listOf(
                    Token(TokenValue.NumberLiteral(22.0), 1, 1),
                    Token(TokenValue.Plus, 1, 5),
                    Token(TokenValue.NumberLiteral(5.0), 1, 7),
                    Token(TokenValue.ExclEqual, 1, 11),
                    Token(TokenValue.NumberLiteral(67.0), 1, 13),
                ),
                binary(
                    TokenValue.ExclEqual,
                    binary(
                        TokenValue.Plus,
                        num(22.0, 1, 1),
                        num(5.0, 1, 7),
                        1, 1,
                    ),
                    num(67.0, 1, 13),
                    1, 1
                ),
            ),
        )

        for (input in inputs) {
            val parser = Parser(input.tokens)
            val expr = parser.parseExpression()
            assertEquals(input.expected, expr)
        }
    }

    @Test
    fun `parses logical binary`() {
        data class Input(
            val tokens: List<Token>,
            val expected: Expression,
        )

        val inputs = listOf(
            // true && false
            Input(
                listOf(
                    Token(TokenValue.BoolLiteral(true), 1, 1),
                    Token(TokenValue.DoubleAmp, 1, 2),
                    Token(TokenValue.BoolLiteral(false), 1, 3),
                ),
                binary(
                    TokenValue.DoubleAmp,
                    bool(true, 1, 1),
                    bool(false, 1, 3),
                    1, 1
                ),
            ),
            // "123" && true || 123.0 => ("123" && true) || 123.0
            Input(
                listOf(
                    Token(TokenValue.StringLiteral("123"), 1, 1),
                    Token(TokenValue.DoubleAmp, 1, 2),
                    Token(TokenValue.BoolLiteral(true), 1, 3),
                    Token(TokenValue.DoublePipe, 1, 4),
                    Token(TokenValue.NumberLiteral(123.0), 1, 5),
                ),
                binary(
                    TokenValue.DoublePipe,
                    binary(
                        TokenValue.DoubleAmp,
                        str("123", 1, 1),
                        bool(true, 1, 3),
                        1, 1
                    ),
                    num(123.0, 1, 5),
                    1, 1
                ),
            ),
        )

        for (input in inputs) {
            val expr = Parser(input.tokens).parseExpression()
            assertEquals(input.expected, expr)
        }
    }

    @Test
    fun `parses variable declaration`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Statement,
        )

        val inputs = listOf(
            // foo: number;
            Input(
                "foo: number;",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Colon, 1, 4),
                    Token(TokenValue.Type(VariableType.Number), 1, 5),
                    Token(TokenValue.Semicolon, 1, 6)
                ),
                varDecl("foo", VariableType.Number, null, 1, 1),
            ),
            // foo: number = 42;
            Input(
                "foo: number = 42;",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Colon, 1, 4),
                    Token(TokenValue.Type(VariableType.Number), 1, 5),
                    Token(TokenValue.Equal, 1, 6),
                    Token(TokenValue.NumberLiteral(42.0), 1, 7),
                    Token(TokenValue.Semicolon, 1, 8)
                ),
                varDecl("foo", VariableType.Number, num(42.0, 1, 7), 1, 1),
            ),
            // foo := 42;
            Input(
                "foo := 42;",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Colon, 1, 4),
                    Token(TokenValue.Equal, 1, 5),
                    Token(TokenValue.NumberLiteral(42.0), 1, 6),
                    Token(TokenValue.Semicolon, 1, 7)
                ),
                varDecl("foo", null, num(42.0, 1, 6), 1, 1),
            ),
            // foo := x;
            Input(
                "foo := x;",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Colon, 1, 4),
                    Token(TokenValue.Equal, 1, 5),
                    Token(TokenValue.Ident("bar"), 1, 6),
                    Token(TokenValue.Semicolon, 1, 7)
                ),
                varDecl("foo", null, ident("bar", 1, 6), 1, 1),
            ),
        )

        for (input in inputs) {
            val stmt = Parser(input.tokens).parseStatement()
            assertEquals(input.expected, stmt, "Failed to parse ${input.name}")
        }
    }

    @Test
    fun `parses variable assignment`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Statement,
        )

        val inputs = listOf(
            // foo = "123";
            Input(
                "foo = \"123\"",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Equal, 1, 5),
                    Token(TokenValue.StringLiteral("123"), 1, 6),
                    Token(TokenValue.Semicolon, 1, 9),
                ),
                varAssignment("foo", str("123", 1, 6), 1, 1),
            ),
            // foo = x;
            Input(
                "foo = x",
                listOf(
                    Token(TokenValue.Ident("foo"), 1, 1),
                    Token(TokenValue.Equal, 1, 5),
                    Token(TokenValue.Ident("x"), 1, 6),
                    Token(TokenValue.Semicolon, 1, 9),
                ),
                varAssignment("foo", ident("x", 1, 6), 1, 1),
            ),
            Input(
                "x += 1",
                listOf(
                    Token(TokenValue.Ident("x"), 1, 1),
                    Token(TokenValue.Plus, 1, 2),
                    Token(TokenValue.Equal, 1, 3),
                    Token(TokenValue.NumberLiteral(1.0), 1, 5),
                    Token(TokenValue.Semicolon, 1, 6),
                ),
                varAssignment(
                    "x",
                    binary(TokenValue.Plus, ident("x", 1, 1), num(1.0, 1, 5), 1, 1),
                    1, 1,
                )
            ),
        )

        for (input in inputs) {
            val stmt = Parser(input.tokens).parseStatement()
            assertEquals(input.expected, stmt, "Failed to parse ${input.name}")
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

        val expected = printStmt(num(123.45, 1, 6), false, 1, 1)
        assertEquals(expected, stmt)
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

        val expected = block(
            listOf(
                printStmt(num(123.45, 1, 7), false, 1, 2),
            ), 1, 1
        )
        assertEquals(expected, stmt)
    }

    @Test
    fun `parses if statement`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Statement,
        )

        val inputs = listOf(
            // if true { print 123.45; }
            Input(
                "if true { print 123.45; }",
                listOf(
                    Token(TokenValue.If, 1, 1),
                    Token(TokenValue.BoolLiteral(true), 1, 2),
                    Token(TokenValue.LBrace, 1, 4),
                    Token(TokenValue.Print(false), 1, 5),
                    Token(TokenValue.NumberLiteral(123.45), 1, 10),
                    Token(TokenValue.Semicolon, 1, 15),
                    Token(TokenValue.RBrace, 1, 1),
                ),
                ifStmt(
                    bool(true, 1, 2),
                    block(
                        listOf(
                            printStmt(num(123.45, 1, 10), false, 1, 5),
                        ), 1, 4
                    ),
                    null,
                    1, 1,
                ),
            ),
            // if false { x: number; } else { y: string; }
            Input(
                "if false { x: number; } else { y: string; }",
                listOf(
                    Token(TokenValue.If, 1, 1),
                    Token(TokenValue.BoolLiteral(false), 1, 2),
                    Token(TokenValue.LBrace, 1, 4),
                    Token(TokenValue.Ident("x"), 1, 5),
                    Token(TokenValue.Colon, 1, 6),
                    Token(TokenValue.Type(VariableType.Number), 1, 7),
                    Token(TokenValue.Semicolon, 1, 8),
                    Token(TokenValue.RBrace, 1, 9),
                    Token(TokenValue.Else, 1, 10),
                    Token(TokenValue.LBrace, 1, 11),
                    Token(TokenValue.Ident("y"), 1, 12),
                    Token(TokenValue.Colon, 1, 13),
                    Token(TokenValue.Type(VariableType.String), 1, 14),
                    Token(TokenValue.Semicolon, 1, 15),
                    Token(TokenValue.RBrace, 1, 16),
                ),
                ifStmt(
                    bool(false, 1, 2),
                    block(
                        listOf(
                            varDecl("x", VariableType.Number, null, 1, 5),
                        ), 1, 4
                    ),
                    block(
                        listOf(
                            varDecl("y", VariableType.String, null, 1, 12),
                        ), 1, 11
                    ),
                    1, 1
                ),
            ),
        )

        for (input in inputs) {
            val stmt = Parser(input.tokens).parseStatement()
            assertEquals(input.expected, stmt, "Failed to parse if statement ${input.name}")
        }
    }

    @Test
    fun `parses for statement`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Statement,
        )

        val inputs = listOf(
            Input(
                "for true { println true; }",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.BoolLiteral(true), 1, 2),
                    Token(TokenValue.LBrace, 1, 3),
                    Token(TokenValue.Print(true), 1, 4),
                    Token(TokenValue.BoolLiteral(true), 1, 5),
                    Token(TokenValue.Semicolon, 1, 6),
                    Token(TokenValue.RBrace, 1, 7),
                ),
                forStmt(
                    bool(true, 1, 2),
                    block(listOf(printStmt(bool(true, 1, 5), true, 1, 4)), 1, 3),
                    1, 1
                )
            ),
            Input(
                "for { x = x + 1; }",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.LBrace, 1, 2),
                    Token(TokenValue.Ident("x"), 1, 3),
                    Token(TokenValue.Equal, 1, 4),
                    Token(TokenValue.Ident("x"), 1, 5),
                    Token(TokenValue.Plus, 1, 6),
                    Token(TokenValue.NumberLiteral(1.0), 1, 7),
                    Token(TokenValue.Semicolon, 1, 8),
                    Token(TokenValue.RBrace, 1, 9),
                ),
                forStmt(
                    null,
                    block(
                        listOf(
                            varAssignment(
                                "x",
                                binary(
                                    TokenValue.Plus,
                                    ident("x", 1, 5),
                                    num(1.0, 1, 7),
                                    1, 5,
                                ),
                                1, 3
                            ),
                        ), 1, 2
                    ),
                    1, 1
                )
            ),
            Input(
                "for 123 > x { x = x + 1; }",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.NumberLiteral(123.0), 1, 2),
                    Token(TokenValue.Gt, 1, 3),
                    Token(TokenValue.Ident("x"), 1, 4),
                    Token(TokenValue.LBrace, 1, 5),
                    Token(TokenValue.Ident("x"), 1, 6),
                    Token(TokenValue.Equal, 1, 7),
                    Token(TokenValue.Ident("x"), 1, 8),
                    Token(TokenValue.Plus, 1, 9),
                    Token(TokenValue.NumberLiteral(1.0), 1, 10),
                    Token(TokenValue.Semicolon, 1, 11),
                    Token(TokenValue.RBrace, 1, 12),
                ),
                forStmt(
                    binary(TokenValue.Gt, num(123.0, 1, 2), ident("x", 1, 4), 1, 2),
                    block(
                        listOf(
                            varAssignment(
                                "x",
                                binary(TokenValue.Plus, ident("x", 1, 8), num(1.0, 1, 10), 1, 8),
                                1, 6
                            )
                        ), 1, 5
                    ),
                    1, 1
                )
            ),
            Input(
                "for i := 0; i < 10; i = i + 1 {}",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.Ident("i"), 1, 2),
                    Token(TokenValue.Colon, 1, 3),
                    Token(TokenValue.Equal, 1, 4),
                    Token(TokenValue.NumberLiteral(0.0), 1, 5),
                    Token(TokenValue.Semicolon, 1, 6),
                    Token(TokenValue.Ident("i"), 1, 7),
                    Token(TokenValue.Lt, 1, 8),
                    Token(TokenValue.NumberLiteral(10.0), 1, 9),
                    Token(TokenValue.Semicolon, 1, 10),
                    Token(TokenValue.Ident("i"), 1, 11),
                    Token(TokenValue.Equal, 1, 12),
                    Token(TokenValue.Ident("i"), 1, 13),
                    Token(TokenValue.Plus, 1, 14),
                    Token(TokenValue.NumberLiteral(1.0), 1, 15),
                    Token(TokenValue.LBrace, 1, 16),
                    Token(TokenValue.RBrace, 1, 17)
                ),
                cForStmt(
                    varDecl("i", null, num(0.0, 1, 5), 1, 2),
                    binary(TokenValue.Lt, ident("i", 1, 7), num(10.0, 1, 9), 1, 7),
                    varAssignment(
                        "i",
                        binary(TokenValue.Plus, ident("i", 1, 13), num(1.0, 1, 15), 1, 13),
                        1, 11
                    ),
                    block(listOf(), 1, 16),
                    1, 1,
                ),
            ),
            Input(
                "for ;; {}",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.Semicolon, 1, 2),
                    Token(TokenValue.Semicolon, 1, 3),
                    Token(TokenValue.LBrace, 1, 4),
                    Token(TokenValue.RBrace, 1, 5),
                ),
                cForStmt(null, null, null, block(listOf(), 1, 4), 1, 1),
            ),
            Input(
                "for ;x < 10; { println true; }",
                listOf(
                    Token(TokenValue.For, 1, 1),
                    Token(TokenValue.Semicolon, 1, 2),
                    Token(TokenValue.Ident("x"), 1, 3),
                    Token(TokenValue.Lt, 1, 4),
                    Token(TokenValue.NumberLiteral(10.0), 1, 5),
                    Token(TokenValue.Semicolon, 1, 6),
                    Token(TokenValue.LBrace, 1, 7),
                    Token(TokenValue.Print(true), 1, 8),
                    Token(TokenValue.BoolLiteral(true), 1, 9),
                    Token(TokenValue.Semicolon, 1, 10),
                    Token(TokenValue.RBrace, 1, 11),
                ),
                cForStmt(
                    null,
                    binary(TokenValue.Lt, ident("x", 1, 3), num(10.0, 1, 5), 1, 3),
                    null,
                    block(
                        listOf(
                            printStmt(bool(true, 1, 9), true, 1, 8)
                        ), 1, 7
                    ),
                    1, 1,
                ),
            ),
        )

        for (input in inputs) {
            try {
                val stmt = Parser(input.tokens).parseStatement()
                assertEquals(input.expected, stmt, "Failed to parse for statement ${input.name}")
            } catch (e: Exception) {
                fail("Failed to parse for statement ${input.name}", e)
            }
        }
    }

    @Test
    fun `parses function declaration`() {
        data class Input(
            val name: String,
            val tokens: List<Token>,
            val expected: Statement,
        )

        val inputs = listOf(
            Input(
                "fun add(a: number, b: number) { }",
                listOf(
                    Token(TokenValue.Fun, 1, 1),
                    Token(TokenValue.Ident("add"), 1, 2),
                    Token(TokenValue.LParen, 1, 3),
                    Token(TokenValue.Ident("a"), 1, 4),
                    Token(TokenValue.Colon, 1, 5),
                    Token(TokenValue.Type(VariableType.Number), 1, 6),
                    Token(TokenValue.Comma, 1, 7),
                    Token(TokenValue.Ident("b"), 1, 8),
                    Token(TokenValue.Colon, 1, 9),
                    Token(TokenValue.Type(VariableType.Number), 1, 10),
                    Token(TokenValue.RParen, 1, 11),
                    Token(TokenValue.LBrace, 1, 12),
                    Token(TokenValue.RBrace, 1, 13),
                ),
                funDecl(
                    "add",
                    listOf(
                        funParam("a", VariableType.Number, 1, 4),
                        funParam("b", VariableType.Number, 1, 8),
                    ),
                    null,
                    block(listOf(), 1, 12),
                    1, 1,
                )
            ),
            Input(
                "fun foo(): number { return 123; }",
                listOf(
                    Token(TokenValue.Fun, 1, 1),
                    Token(TokenValue.Ident("foo"), 1, 2),
                    Token(TokenValue.LParen, 1, 3),
                    Token(TokenValue.RParen, 1, 4),
                    Token(TokenValue.Colon, 1, 5),
                    Token(TokenValue.Type(VariableType.Number), 1, 6),
                    Token(TokenValue.LBrace, 1, 7),
                    Token(TokenValue.Return, 1, 8),
                    Token(TokenValue.NumberLiteral(123.0), 1, 9),
                    Token(TokenValue.Semicolon, 1, 10),
                    Token(TokenValue.RBrace, 1, 11),
                ),
                funDecl(
                    "foo",
                    listOf(),
                    VariableType.Number,
                    block(listOf(returnStmt(num(123.0, 1, 9), 1, 8)), 1, 7),
                    1, 1,
                )
            )
        )

        for (input in inputs) {
            println("Starting test for ${input.name}")
            val result = Parser(input.tokens).parseProgram()
            assertEquals(result.size, 1)
            assertEquals(input.expected, result[0])
        }
    }

    @Test
    fun `parses function call as expression`() {
        // x := foo(bar);
        val tokens = listOf(
            Token(TokenValue.Ident("x"), 1, 1),
            Token(TokenValue.Colon, 1, 2),
            Token(TokenValue.Equal, 1, 3),
            Token(TokenValue.Ident("foo"), 1, 4),
            Token(TokenValue.LParen, 1, 5),
            Token(TokenValue.Ident("bar"), 1, 6),
            Token(TokenValue.RParen, 1, 7),
            Token(TokenValue.Semicolon, 1, 8),
        )
        val ast = Parser(tokens).parseProgram()
        val expected = varDecl(
            "x",
            null,
            call("foo", listOf(Expression(ExpressionType.Ident("bar"), 1, 6)), 1, 4),
            1, 1,
        )

        assertEquals(ast.size, 1)
        assertEquals(expected, ast[0])
    }

    @Test
    fun `parses function call as statement`() {
        // foo(bar);
        val tokens = listOf(
            Token(TokenValue.Ident("foo"), 1, 1),
            Token(TokenValue.LParen, 1, 2),
            Token(TokenValue.Ident("bar"), 1, 3),
            Token(TokenValue.RParen, 1, 4),
            Token(TokenValue.Semicolon, 1, 5),
        )
        val ast = Parser(tokens).parseProgram()
        val expected = callStmt("foo", listOf(Expression(ExpressionType.Ident("bar"), 1, 3)), 1, 1)

        assertEquals(ast.size, 1)
        assertEquals(expected, ast[0])
    }

    @Test
    fun `parses return statement without value`() {
        // return
        val tokens = listOf(
            Token(TokenValue.Return, 1, 1),
            Token(TokenValue.Semicolon, 1, 2),
        )
        val ast = Parser(tokens).parseProgram()
        val expected = Statement(StatementType.Return(null), 1, 1)

        assertEquals(ast.size, 1)
        assertEquals(expected, ast[0])
    }

    @Test
    fun `parses return statement with value`() {
        // return 42;
        val tokens = listOf(
            Token(TokenValue.Return, 1, 1),
            Token(TokenValue.NumberLiteral(42.0), 1, 2),
            Token(TokenValue.Semicolon, 1, 3),
        )
        val ast = Parser(tokens).parseProgram()
        val expected = Statement(
            StatementType.Return(
                Expression(ExpressionType.NumberLiteral(42.0), 1, 2),
            ),
            1, 1
        )

        assertEquals(ast.size, 1)
        assertEquals(expected, ast[0])
    }
}
