package lang

sealed interface Expression {
    data class Ident(val name: String) : Expression
    data class NumberLiteral(val value: Double) : Expression
    data class Unary(val operator: TokenValue, val operand: Expression) : Expression
    data class Binary(val operator: TokenValue, val left: Expression, val right: Expression) : Expression
}

sealed interface Statement {
    data class VariableDeclaration(val name: String, val value: Expression?) : Statement
    data class Print(val expression: Expression) : Statement
}

class Parser(
    val tokens: List<Token>,
    var idx: Int = 0,
) {
    fun peek(): Token? {
        return if (idx < tokens.size) tokens[idx] else null
    }

    fun parseLiteral(): Expression {
        // literal => NUMBER | ident

        val token = this.peek()
        check(token != null) { "Unexpected end of input" }

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
                throw IllegalArgumentException("Unexpected token: ${token.value}")
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

        val left = this.parseUnary()

        val operator = this.peek()
        if (operator != null && (operator.value is TokenValue.Star || operator.value is TokenValue.Slash)) {
            this.idx += 1

            val right = this.parseTerm()
            return Expression.Binary(operator = operator.value, left = left, right = right)
        }

        return left
    }

    fun parseExpression(): Expression {
        // expression => term (('+' | '-') term)*

        val left = this.parseTerm()

        val operator = this.peek()
        if (operator != null && (operator.value is TokenValue.Plus || operator.value is TokenValue.Minus)) {
            this.idx += 1

            val right = this.parseExpression()
            return Expression.Binary(operator = operator.value, left = left, right = right)
        }

        return left
    }

    fun parseVariableDeclaration(ident: TokenValue.Ident): Statement {
        // variableDeclaration => (ident ':=' expression | ident)

        this.idx += 1
        val operator = this.peek()

        if (operator != null && operator.value is TokenValue.ColonEqual) {
            this.idx += 1

            return Statement.VariableDeclaration(
                name = ident.name,
                value = this.parseExpression(),
            )
        } else {
            return Statement.VariableDeclaration(
                name = ident.name,
                value = null
            )
        }
    }

    fun parseStatement(): Statement {
        // statement => variableDeclaration | print

        val next = this.peek()
        check(next != null) { "unexpected end of input" }

        val statement = when (next.value) {
            is TokenValue.Ident -> this.parseVariableDeclaration(next.value)
            is TokenValue.Print -> {
                // print => 'print' expression
                this.idx += 1
                Statement.Print(this.parseExpression())
            }

            else -> throw IllegalArgumentException("unexpected token: ${next.value}")
        }

        val semicolon = this.peek()
        check(semicolon != null) { "unexpected end of input" }
        check(semicolon.value is TokenValue.Semicolon) { "expected semicolon" }
        this.idx += 1

        return statement
    }

    fun parseProgram(): List<Statement> {
        var statements: List<Statement> = listOf()

        while (this.idx < this.tokens.size) {
            statements += this.parseStatement()
        }

        return statements
    }
}
