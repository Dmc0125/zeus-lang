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
                Expression.Unary(
                    operator = token.value,
                    operand = this.parseUnary(),
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
                    val right = this.parseUnary()
                    left = Expression.Binary(operator = operator.value, left = left, right = right)
                }

                else -> return left
            }
        }
    }

    fun parseExpression(): Expression {
        // expression => term (('+' | '-') term)*

        var left = this.parseTerm()

        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                is TokenValue.Plus, TokenValue.Minus -> {
                    this.idx += 1
                    val right = this.parseTerm()
                    left = Expression.Binary(operator = operator.value, left = left, right = right)
                }

                else -> return left
            }
        }
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

    fun parseStatement(): Statement {
        // statement => variableDeclaration | print

        val next = this.peek()
        check(next != null) { throw UnexpectedEndOfInputError }

        val statement = when (next.value) {
            is TokenValue.Ident -> this.parseIdentStatement(next.value)
            is TokenValue.Print -> {
                // print => 'print' expression
                this.idx += 1
                Statement.Print(this.parseExpression(), next.value.ln)
            }

            else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}", "identifier or print")
        }

        val semicolon = this.peek()
        check(semicolon != null) { throw UnexpectedEndOfInputError }
        check(semicolon.value is TokenValue.Semicolon) {
            // TODO: tokenValue.toString()
            throw UnexpectedTokenError(semicolon.line, semicolon.col, "${semicolon.value}", ";")
        }
        this.idx += 1

        return statement
    }

    fun parseProgram(): MutableList<Statement> {
        var statements: MutableList<Statement> = mutableListOf()

        while (this.idx < this.tokens.size) {
            statements += this.parseStatement()
        }

        return statements
    }
}
