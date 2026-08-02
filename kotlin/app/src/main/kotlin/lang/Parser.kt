package lang

sealed interface ExpressionType {
    data class Ident(val name: String) : ExpressionType
    data class NumberLiteral(val value: Double) : ExpressionType
    data class StringLiteral(val value: String) : ExpressionType
    data class BoolLiteral(val value: Boolean) : ExpressionType
    data class Unary(val operator: TokenValue, val operand: Expression) : ExpressionType
    data class Binary(val operator: TokenValue, val left: Expression, val right: Expression) : ExpressionType
}

sealed interface StatementType {
    data class VariableDeclaration(val name: String, val type: VariableType?, val value: Expression?) :
        StatementType

    data class VariableAssignment(val name: String, val value: Expression) : StatementType
    data class Print(val expression: Expression, val ln: Boolean) : StatementType
    data class Block(val statements: List<Statement>) : StatementType
    data class If(
        val condition: Expression,
        val thenBranch: Statement?,
        val elseBranch: Statement?,
    ) : StatementType
}

data class Node<T>(
    val type: T,
    val line: Int,
    val col: Int,
) {
    constructor(type: T, token: Token) : this(
        type,
        token.line,
        token.col,
    )
}
typealias Expression = Node<ExpressionType>
typealias Statement = Node<StatementType>

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

        return when (token.value) {
            is TokenValue.NumberLiteral -> {
                this.idx += 1
                Expression(ExpressionType.NumberLiteral(token.value.value), token)
            }

            is TokenValue.Ident -> {
                this.idx += 1
                Expression(ExpressionType.Ident(token.value.name), token)
            }

            is TokenValue.StringLiteral -> {
                this.idx += 1
                Expression(ExpressionType.StringLiteral(token.value.value), token)
            }

            is TokenValue.BoolLiteral -> {
                this.idx += 1
                Expression(ExpressionType.BoolLiteral(token.value.value), token)
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
                Expression(
                    ExpressionType.Unary(
                        operator = token.value,
                        operand = operand,
                    ),
                    token,
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
                    left = Expression(
                        ExpressionType.Binary(operator = operator.value, left = left, right = right),
                        left.line,
                        left.col,
                    )
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
                    left = Expression(
                        ExpressionType.Binary(operator = operator.value, left = left, right = right),
                        left.line,
                        left.col,
                    )
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
                    left = Expression(
                        ExpressionType.Binary(operator = operator.value, left = left, right = right),
                        left.line,
                        left.col,
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
                    left = Expression(
                        ExpressionType.Binary(operator = operator.value, left = left, right = right),
                        left.line,
                        left.col,
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

    fun parseIdentStatement(identToken: Token): Statement {
        // identStatement => variableDeclaration | variableAssignment
        // variableDeclaration => ident ':=' expression | ident ':' type | ident ':' type '=' expression
        // variableAssignment => ident '=' expression

        val ident = identToken.value as TokenValue.Ident

        this.idx += 1
        val next = this.peek() ?: throw UnexpectedEndOfInputError

        val stmtType = when (next.value) {
            is TokenValue.Colon -> {
                this.idx += 1
                val type = this.peek() ?: throw UnexpectedEndOfInputError

                when (type.value) {
                    is TokenValue.Equal -> {
                        this.idx += 1
                        val value = this.parseExpression()
                        StatementType.VariableDeclaration(
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
                            StatementType.VariableDeclaration(
                                name = ident.name,
                                type = variableType,
                                value = value,
                            )
                        } else {
                            StatementType.VariableDeclaration(
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
                StatementType.VariableAssignment(
                    name = ident.name,
                    value = value,
                )
            }

            else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}")
        }

        return Statement(stmtType, identToken.line, identToken.col)
    }

    fun parseBlockStatement(lBrace: Token): Statement? {
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

        // TODO: Don't do this
        if (statements.isEmpty()) {
            return null
        }

        return Statement(
            StatementType.Block(statements),
            lBrace.line,
            lBrace.col,
        )
    }

    fun parseIfStatement(ifToken: Token): Statement {
        // if => 'if' expression '{' statement* '}' ('else' '{' statement* '}')?

        this.idx += 1 // skip 'if'

        val cond = this.parseExpression()

        val lBrace = this.peek() ?: throw UnexpectedEndOfInputError
        check(lBrace.value == TokenValue.LBrace) {
            throw UnexpectedTokenError(lBrace.line, lBrace.col, "${lBrace.value}", "LBrace")
        }

        val thenBranch = this.parseBlockStatement(lBrace)

        val elseToken = this.peek()
        var elseBranch: Statement? = null

        if (elseToken?.value == TokenValue.Else) {
            this.idx += 1
            val next = this.peek() ?: throw UnexpectedEndOfInputError
            elseBranch = when (next.value) {
                is TokenValue.If -> this.parseIfStatement(next)
                is TokenValue.LBrace -> this.parseBlockStatement(next)
                else -> throw UnexpectedTokenError(next.line, next.col, "${next.value}", "LBrace or If")
            }
        }

        return Statement(
            StatementType.If(
                condition = cond,
                thenBranch = thenBranch,
                elseBranch = elseBranch,
            ),
            line = ifToken.line,
            col = ifToken.col,
        )
    }

    fun parseStatement(): Statement? {
        // statement => variableDeclaration | print

        val next = this.peek()
        check(next != null) { throw UnexpectedEndOfInputError }

        val (statement, requiresSemicolon) = when (next.value) {
            is TokenValue.Ident -> Pair(this.parseIdentStatement(next), true)
            is TokenValue.Print -> {
                // print => 'print' expression
                this.idx += 1
                val stmt = Statement(
                    StatementType.Print(this.parseExpression(), next.value.ln),
                    next.line,
                    next.col,
                )
                Pair(stmt, true)
            }

            is TokenValue.LBrace -> Pair(this.parseBlockStatement(next), false)
            is TokenValue.If -> Pair(this.parseIfStatement(next), false)

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
