package lang

sealed interface Expression {
    data class Ident(val name: String) : Expression
    data class NumberLiteral(val value: Double) : Expression
    data class StringLiteral(val value: String) : Expression
    data class BoolLiteral(val value: Boolean) : Expression
    data class Unary(val operator: TokenValue, val operand: Expression) : Expression
    data class Binary(val operator: TokenValue, val left: Expression, val right: Expression) : Expression
}

sealed interface Statement {
    data class VariableDeclaration(val name: String, val type: VariableType?, val value: Expression?) : Statement
    data class VariableAssignment(val name: String, val value: Expression) : Statement
    data class Print(val expression: Expression, val ln: Boolean) : Statement
    data class Block(val statements: List<Statement>) : Statement
    data class If(
        val condition: Expression,
        val thenBranch: Statement.Block?,
        val elseBranch: Statement?,
    ) : Statement
}

class Parser(
    val tokens: List<Token>,
    var idx: Int = 0,
) {
    fun peek(offset: Int = 0): Token? {
        return if (idx + offset < tokens.size) tokens[idx + offset] else null
    }

    fun parseLiteral(): Expression {
        // literal => number | ident | string | bool

        val token = this.peek()
        check(token != null) {
            throw UnexpectedEndOfInputError
        }

        when (token.value) {
            is TokenValue.NumberLiteral -> {
                this.idx += 1
                return Expression.NumberLiteral(token.value.value)
            }

            is TokenValue.Ident -> {
                this.idx += 1
                return Expression.Ident(token.value.name)
            }

            is TokenValue.StringLiteral -> {
                this.idx += 1
                return Expression.StringLiteral(token.value.value)
            }

            is TokenValue.BoolLiteral -> {
                this.idx += 1
                return Expression.BoolLiteral(token.value.value)
            }

            else -> {
                throw UnexpectedTokenError(token.line, token.col, "${token.value}", "number or variable")
            }
        }
    }

    fun parseUnary(): Expression {
        // unary => ('-' | '+' | '!' unary) | literal

        val token = this.peek()
        check(token != null) {
            throw UnexpectedEndOfInputError
        }

        return when (token.value) {
            is TokenValue.Minus, is TokenValue.Plus, is TokenValue.Excl -> {
                this.idx += 1
                val operand = this.parseGroup(this::parseUnary)
                Expression.Unary(
                    operator = token.value,
                    operand = operand,
                )
            }

            else -> this.parseLiteral()
        }
    }

    fun parseTerm(): Expression {
        // term => unary (('*' | '/') unary)*

        var left = this.parseUnary()

        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                is TokenValue.Star, TokenValue.Slash -> {
                    this.idx += 1
                    val right = this.parseGroup(this::parseUnary)
                    left = Expression.Binary(operator = operator.value, left = left, right = right)
                }

                else -> return left
            }
        }
    }

    fun parseFactor(): Expression {
        // expression => term (('+' | '-') term)*

        var left = this.parseTerm()

        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                is TokenValue.Plus, is TokenValue.Minus -> {
                    this.idx += 1
                    val right = this.parseGroup(this::parseTerm)
                    left = Expression.Binary(operator = operator.value, left = left, right = right)
                }

                else -> return left
            }
        }
    }

    fun parseComparison(): Expression {
        // comparison => factor (('==' | '!=' | '<' | '>' | '<=' | '>=') factor)*

        var left = this.parseFactor()

        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                is TokenValue.DoubleEqual, is TokenValue.ExclEqual,
                is TokenValue.Lt, is TokenValue.Gt,
                is TokenValue.LtEqual, is TokenValue.GtEqual -> {
                    this.idx += 1
                    val right = this.parseGroup(this::parseFactor)
                    left = Expression.Binary(
                        operator = operator.value,
                        left = left,
                        right = right,
                    )
                }

                else -> return left
            }
        }
    }

    fun parseLogical(): Expression {
        // logical => comparison (('&&' | '||') comparison)*

        var left = this.parseComparison()
        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                is TokenValue.DoubleAmp, is TokenValue.DoublePipe -> {
                    this.idx += 1
                    val right = this.parseComparison()
                    left = Expression.Binary(
                        operator = operator.value,
                        left = left,
                        right = right,
                    )
                }

                else -> return left
            }
        }
    }

    fun parseGroup(otherwise: () -> Expression): Expression {
        val left = this.peek() ?: throw UnexpectedEndOfInputError
        return when (left.value) {
            is TokenValue.LParen -> {
                this.idx += 1
                val expr = this.parseExpression()
                val rparen = this.peek() ?: throw UnexpectedEndOfInputError
                check(rparen.value == TokenValue.RParen) {
                    throw UnexpectedTokenError(rparen.line, rparen.col, "")
                }
                this.idx += 1
                expr
            }

            else -> otherwise()
        }
    }

    fun parseExpression(): Expression {
        // expression => '(' logical ')' | logical
        return this.parseGroup(this::parseLogical)
    }

    fun parseIdentStatement(ident: TokenValue.Ident): Statement {
        // identStatement => variableDeclaration | variableAssignment
        // variableDeclaration => ident ':=' expression | ident ':' type | ident ':' type '=' expression
        // variableAssignment => ident '=' expression

        this.idx += 1
        val next = this.peek() ?: throw UnexpectedEndOfInputError

        return when (next.value) {
            is TokenValue.Colon -> {
                this.idx += 1
                val type = this.peek() ?: throw UnexpectedEndOfInputError

                when (type.value) {
                    is TokenValue.Equal -> {
                        this.idx += 1
                        val value = this.parseExpression()
                        Statement.VariableDeclaration(
                            name = ident.name,
                            type = null,
                            value = value,
                        )
                    }

                    is TokenValue.Type -> {
                        this.idx += 1
                        val variableType = type.value.type

                        val equal = this.peek() ?: throw UnexpectedEndOfInputError
                        if (equal.value == TokenValue.Equal) {
                            this.idx += 1
                            val value = this.parseExpression()
                            Statement.VariableDeclaration(
                                name = ident.name,
                                type = variableType,
                                value = value,
                            )
                        } else {
                            Statement.VariableDeclaration(
                                name = ident.name,
                                type = variableType,
                                value = null,
                            )
                        }
                    }

                    else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}")
                }
            }

            is TokenValue.Equal -> {
                this.idx += 1
                val value = this.parseExpression()
                return Statement.VariableAssignment(
                    name = ident.name,
                    value = value,
                )
            }

            else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}")
        }
    }

    fun parseBlockStatement(): Statement.Block? {
        // block => '{' statement* '}'

        this.idx += 1 // skip '{'

        val statements = mutableListOf<Statement>()

        while (true) {
            val next = this.peek() ?: throw UnexpectedEndOfInputError

            if (next.value == TokenValue.RBrace) {
                break
            }

            val stmt = this.parseStatement()
            if (stmt != null) {
                statements.add(stmt)
            }
        }

        this.idx += 1 // skip '}'

        if (statements.isEmpty()) {
            return null
        }

        return Statement.Block(statements)
    }

    fun parseIfStatement(): Statement {
        // if => 'if' expression '{' statement* '}' ('else' '{' statement* '}')?

        this.idx += 1 // skip 'if'

        val cond = this.parseExpression()

        val lBrace = this.peek() ?: throw UnexpectedEndOfInputError
        check(lBrace.value == TokenValue.LBrace) {
            throw UnexpectedTokenError(lBrace.line, lBrace.col, "${lBrace.value}", "LBrace")
        }

        val thenBranch = this.parseBlockStatement()

        val elseToken = this.peek()
        var elseBranch: Statement? = null

        if (elseToken?.value == TokenValue.Else) {
            this.idx += 1
            val ifToken = this.peek() ?: throw UnexpectedEndOfInputError
            elseBranch = when (ifToken.value) {
                is TokenValue.If -> this.parseIfStatement()
                is TokenValue.LBrace -> this.parseBlockStatement()
                else -> throw UnexpectedTokenError(ifToken.line, ifToken.col, "${ifToken.value}", "LBrace or If")
            }
        }

        return Statement.If(
            condition = cond,
            thenBranch = thenBranch,
            elseBranch = elseBranch,
        )
    }

    fun parseStatement(): Statement? {
        // statement => variableDeclaration | print

        val next = this.peek()
        check(next != null) { throw UnexpectedEndOfInputError }

        val (statement, requiresSemicolon) = when (next.value) {
            is TokenValue.Ident -> Pair(this.parseIdentStatement(next.value), true)
            is TokenValue.Print -> {
                // print => 'print' expression
                this.idx += 1
                Pair(Statement.Print(this.parseExpression(), next.value.ln), true)
            }

            is TokenValue.LBrace -> Pair(this.parseBlockStatement(), false)
            is TokenValue.If -> Pair(this.parseIfStatement(), false)

            else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}", "identifier or print")
        }

        if (requiresSemicolon) {
            val semicolon = this.peek()
            check(semicolon != null) { throw UnexpectedEndOfInputError }
            check(semicolon.value is TokenValue.Semicolon) {
                // TODO: tokenValue.toString()
                throw UnexpectedTokenError(semicolon.line, semicolon.col, "${semicolon.value}", ";")
            }
            this.idx += 1
        }

        return statement
    }

    fun parseProgram(): MutableList<Statement> {
        var statements: MutableList<Statement> = mutableListOf()

        while (this.idx < this.tokens.size) {
            val stmt = this.parseStatement()
            if (stmt != null) {
                statements += stmt
            }
        }

        return statements
    }
}
