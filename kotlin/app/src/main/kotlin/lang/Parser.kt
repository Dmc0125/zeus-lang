package lang

sealed interface Expression {
    data class Ident(val name: String) : Expression
    data class NumberLiteral(val value: Double) : Expression
    data class Unary(val operator: TokenValue, val operand: Expression) : Expression
    data class Binary(val operator: TokenValue, val left: Expression, val right: Expression) : Expression
}

sealed interface Statement {
    data class VariableDeclaration(val name: String, val value: Expression?) : Statement
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
        // literal => NUMBER | ident

        val token = this.peek()
        check(token != null) {
            throw UnexpectedEndOfInputError
        }

        when (token.value) {
            is TokenValue.Number -> {
                this.idx += 1
                return Expression.NumberLiteral(token.value.value)
            }

            is TokenValue.Ident -> {
                this.idx += 1
                return Expression.Ident(token.value.name)
            }

            else -> {
                throw UnexpectedTokenError(token.line, token.col, "${token.value}", "number or variable")
            }
        }
    }

    fun parseUnary(): Expression {
        // unary => '-' unary | '+' unary | literal

        val token = this.peek()
        if (token != null && (token.value is TokenValue.Minus || token.value is TokenValue.Plus)) {
            this.idx += 1
            return Expression.Unary(
                operator = token.value,
                operand = this.parseUnary(),
            )
        }

        return this.parseLiteral()
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
        // variableDeclaration => ident ':=' expression | ident
        // variableAssignment => ident '=' expression

        this.idx += 1
        val next = this.peek() ?: throw UnexpectedEndOfInputError

        when (next.value) {
            is TokenValue.Semicolon -> {
                return Statement.VariableDeclaration(
                    name = ident.name,
                    value = null,
                )
            }

            is TokenValue.ColonEqual -> {
                this.idx += 1
                val value = this.parseExpression()
                return Statement.VariableDeclaration(
                    name = ident.name,
                    value = value,
                )
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
