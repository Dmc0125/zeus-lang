package lang

sealed interface ExpressionType {
    data class Ident(val name: String) : ExpressionType
    data class NumberLiteral(val value: Double) : ExpressionType
    data class StringLiteral(val value: String) : ExpressionType
    data class BoolLiteral(val value: Boolean) : ExpressionType
    data class Unary(val operator: TokenValue, val operand: Expression) : ExpressionType
    data class Binary(val operator: TokenValue, val left: Expression, val right: Expression) : ExpressionType
    data class Call(val name: String, val args: List<Expression>) : ExpressionType
}

data class FunctionParameter(
    val name: String,
    val type: VariableType,
    val line: Int,
    val col: Int,
)

sealed interface StatementType {
    data class VariableDeclaration(val name: String, val type: VariableType?, val value: Expression?) :
        StatementType

    data class VariableAssignment(val name: String, val value: Expression) : StatementType
    data class Print(val expression: Expression, val ln: Boolean) : StatementType
    data class Block(val statements: List<Statement>) : StatementType
    data class If(
        val condition: Expression,
        val thenBranch: Statement,
        val elseBranch: Statement?,
    ) : StatementType

    data class For(val condition: Expression?, val body: Statement) : StatementType
    data class CFor(
        val init: Statement?,
        val condition: Expression?,
        val update: Statement?,
        val body: Statement,
    ) : StatementType

    data object Break : StatementType
    data object Continue : StatementType

    data class FunctionDeclaration(
        val name: String,
        val params: List<FunctionParameter>,
        val ret: VariableType?,
        val body: Statement,
    ) : StatementType

    data class Return(val value: Expression?) : StatementType

    data class Call(val name: String, val args: List<Expression>) : StatementType
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

    fun peekOrThrow(offset: Int = 0): Token {
        val token = this.peek(offset)
        if (token != null) {
            return token
        }
        val current = this.tokens[this.idx - 1]
        throw LangError(
            current.line,
            current.col,
            ErrorType.Syntax,
            ErrorMessage.UnexpectedEndOfInput,
        )
    }

    fun parseCall(identToken: Token): Pair<String, List<Expression>> {
        assert(identToken.value is TokenValue.Ident)

        this.idx += 1 // skip ident and '('

        val lparen = this.peekOrThrow()
        check(lparen.value == TokenValue.LParen) {
            throw LangError(lparen.line, lparen.col, ErrorType.Syntax, "Expected '('")
        }
        this.idx += 1 // skip '('

        val args = mutableListOf<Expression>()

        if (this.peekOrThrow().value == TokenValue.RParen) {
            this.idx += 1
        } else {
            while (true) {
                val expr = this.parseExpression()
                args.add(expr)

                val next = this.peekOrThrow()
                when (next.value) {
                    TokenValue.Comma -> {
                        this.idx += 1
                    }

                    TokenValue.RParen -> {
                        this.idx += 1
                        break
                    }

                    else -> throw LangError(next.line, next.col, ErrorType.Syntax, "Expected ',' or ')'")
                }
            }
        }

        val ident = identToken.value as TokenValue.Ident
        return Pair(ident.name, args)
    }

    fun parsePrimary(): Expression {
        // primary => number | ident | string | bool | '(' expression ')' | call
        // call => ident '(' (expression (',' expression)*)? ')'

        val token = this.peekOrThrow()

        return when (token.value) {
            is TokenValue.NumberLiteral -> {
                this.idx += 1
                Expression(ExpressionType.NumberLiteral(token.value.value), token)
            }

            is TokenValue.Ident -> {
                if (this.peekOrThrow(1).value == TokenValue.LParen) {
                    val (name, args) = this.parseCall(token)
                    Expression(ExpressionType.Call(name, args), token)
                } else {
                    this.idx += 1 // skip ident
                    Expression(ExpressionType.Ident(token.value.name), token)
                }
            }

            is TokenValue.StringLiteral -> {
                this.idx += 1
                Expression(ExpressionType.StringLiteral(token.value.value), token)
            }

            is TokenValue.BoolLiteral -> {
                this.idx += 1
                Expression(ExpressionType.BoolLiteral(token.value.value), token)
            }

            is TokenValue.LParen -> {
                this.idx += 1
                val expr = this.parseExpression()
                val rparen = this.peekOrThrow()
                check(rparen.value == TokenValue.RParen) {
                    throw LangError(
                        rparen.line,
                        rparen.col,
                        ErrorType.Syntax,
                        "Expected ')'",
                    )
                }
                this.idx += 1
                expr
            }

            else -> throw LangError(
                token.line,
                token.col,
                ErrorType.Syntax,
                "Expected ident, string, bool, or number literal"
            )
        }
    }

    fun parseUnary(): Expression {
        // unary => ('-' | '+' | '!' unary) | literal

        val token = this.peekOrThrow()

        return when (token.value) {
            is TokenValue.Minus, is TokenValue.Plus, is TokenValue.Excl -> {
                this.idx += 1
                val operand = this.parseUnary()
                Expression(
                    ExpressionType.Unary(
                        operator = token.value,
                        operand = operand,
                    ),
                    token,
                )
            }

            else -> this.parsePrimary()
        }
    }

    fun parseTerm(): Expression {
        // term => unary (('*' | '/') unary)*

        var left = this.parseUnary()

        while (true) {
            val operator = this.peek()

            when (operator?.value) {
                TokenValue.Star, TokenValue.Slash -> {
                    this.idx += 1
                    val right = this.parseUnary()
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
                    val right = this.parseTerm()
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
                    val right = this.parseFactor()
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

    fun parseExpression(): Expression {
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

    fun parseIdentStatement(identToken: Token): Statement {
        // identStatement => variableDeclaration | variableAssignment
        // variableDeclaration => ident ':=' expression | ident ':' type | ident ':' type '=' expression
        // variableAssignment => ident '=' expression | ident ('+' | '-' | '*' | '/') '=' expression

        val ident = identToken.value as TokenValue.Ident

        fun parseDeclaration(): StatementType {
            this.idx += 1 // skip ':'
            val type = this.peekOrThrow()

            return when (type.value) {
                TokenValue.Equal -> {
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

                    val equal = this.peekOrThrow()
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

                else -> throw LangError(type.line, type.col, ErrorType.Syntax, "Expected type or '='")
            }
        }

        this.idx += 1 // skip ident
        val next = this.peekOrThrow()

        val stmtType = when (next.value) {
            TokenValue.Colon -> parseDeclaration()
            TokenValue.Equal -> {
                this.idx += 1
                val value = this.parseExpression()
                StatementType.VariableAssignment(
                    name = ident.name,
                    value = value,
                )
            }

            TokenValue.Plus, TokenValue.Minus, TokenValue.Star, TokenValue.Slash -> {
                this.idx += 1 // skip operator
                val equal = this.peekOrThrow()
                check(equal.value == TokenValue.Equal) {
                    throw LangError(equal.line, equal.col, ErrorType.Syntax, "Expected '='")
                }

                this.idx += 1
                val expr = ExpressionType.Binary(
                    next.value,
                    Expression(ExpressionType.Ident(ident.name), identToken.line, identToken.col),
                    this.parseExpression(),
                )

                StatementType.VariableAssignment(
                    name = ident.name,
                    value = Expression(expr, identToken.line, identToken.col),
                )
            }

            else -> throw LangError(
                next.line,
                next.col,
                ErrorType.Syntax,
                "Expected variable declaration or assignment",
            )
        }

        return Statement(stmtType, identToken.line, identToken.col)
    }

    fun parseBlockStatement(lBrace: Token): Statement {
        // block => '{' statement* '}'
        this.idx += 1 // skip '{'

        val statements = mutableListOf<Statement>()

        while (true) {
            val next = this.peekOrThrow()

            if (next.value == TokenValue.RBrace) {
                break
            }

            val stmt = this.parseStatement()
            statements.add(stmt)
        }

        this.idx += 1 // skip '}'

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

        val lBrace = this.peekOrThrow()
        check(lBrace.value == TokenValue.LBrace) {
            throw LangError(lBrace.line, lBrace.col, ErrorType.Syntax, "Expected block")
        }

        val thenBranch = this.parseBlockStatement(lBrace)

        val elseToken = this.peek()
        var elseBranch: Statement? = null

        if (elseToken?.value == TokenValue.Else) {
            this.idx += 1
            val next = this.peekOrThrow()
            elseBranch = when (next.value) {
                is TokenValue.If -> this.parseIfStatement(next)
                is TokenValue.LBrace -> this.parseBlockStatement(next)
                else -> throw LangError(next.line, next.col, ErrorType.Syntax, "Expected block or if statement")
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

    fun parseForStatement(forToken: Token): Statement {
        // for => 'for' expression block
        //      | 'for' varDecl? ';' expression? ';' assignment? block

        assert(forToken.value == TokenValue.For) {
            "Expected 'for' token, got ${forToken.value}"
        }

        this.idx += 1 // skip 'for'

        // valid:
        //
        // for ;; {}
        // for x := 1;; x = x + 1 {}
        // for ;; x < 10 {}
        // for x < 10 {}

        fun requireSemicolon() {
            val semicolon = this.peekOrThrow()
            if (semicolon.value != TokenValue.Semicolon) {
                throw LangError(semicolon.line, semicolon.col, ErrorType.Syntax, "Expected ';'")
            }
            this.idx += 1 // skip ';'
        }

        return when {
            this.peekOrThrow().value == TokenValue.Semicolon -> {
                // 'for' ';' expression? ';' assignment? block

                this.idx += 1 // skip ';'

                var cond: Expression? = null
                if (this.peekOrThrow().value != TokenValue.Semicolon) {
                    cond = this.parseExpression()
                }
                requireSemicolon() // skip ';'

                var assignment: Statement? = null
                run {
                    val ident = this.peekOrThrow()
                    if (ident.value == TokenValue.LBrace) {
                        return@run
                    }

                    if (ident.value !is TokenValue.Ident) {
                        throw LangError(ident.line, ident.col, ErrorType.Syntax, "Expected identifier")
                    }
                    assignment = this.parseIdentStatement(ident)
                }

                val lBrace = this.peekOrThrow()
                check(lBrace.value == TokenValue.LBrace) {
                    throw LangError(lBrace.line, lBrace.col, ErrorType.Syntax, "Expected '{'")
                }
                val body = this.parseBlockStatement(lBrace)

                Statement(
                    StatementType.CFor(null, cond, assignment, body),
                    forToken.line,
                    forToken.col,
                )
            }

            this.peekOrThrow(1).value == TokenValue.Colon -> {
                // 'for' x : ...
                // 'for' varDecl ';' expression? ';' expression? block

                var decl: Statement
                run {
                    val ident = this.peekOrThrow()
                    if (ident.value !is TokenValue.Ident) {
                        throw LangError(ident.line, ident.col, ErrorType.Syntax, "Expected identifier")
                    }
                    decl = this.parseIdentStatement(ident)
                }
                requireSemicolon() // skip ';'

                var cond: Expression? = null
                if (this.peekOrThrow().value != TokenValue.Semicolon) {
                    cond = this.parseExpression()
                }
                requireSemicolon() // skip ';'

                var assignment: Statement? = null
                run {
                    val ident = this.peekOrThrow()
                    if (ident.value == TokenValue.LBrace) {
                        return@run
                    }
                    if (ident.value !is TokenValue.Ident) {
                        throw LangError(ident.line, ident.col, ErrorType.Syntax, "Expected identifier")
                    }
                    assignment = this.parseIdentStatement(ident)
                }

                val lBrace = this.peekOrThrow()
                check(lBrace.value == TokenValue.LBrace) {
                    throw LangError(lBrace.line, lBrace.col, ErrorType.Syntax, "Expected block")
                }
                val body = this.parseBlockStatement(lBrace)

                Statement(
                    StatementType.CFor(decl, cond, assignment, body),
                    forToken.line,
                    forToken.col,
                )
            }

            else -> {
                // 'for' expression block

                var cond: Expression? = null
                if (this.peekOrThrow().value != TokenValue.LBrace) {
                    cond = this.parseExpression()
                }

                val lBrace = this.peekOrThrow()
                check(lBrace.value == TokenValue.LBrace) {
                    throw LangError(lBrace.line, lBrace.col, ErrorType.Syntax, "Expected block")
                }

                val body = this.parseBlockStatement(lBrace)
                Statement(
                    StatementType.For(cond, body),
                    forToken.line,
                    forToken.col,
                )
            }
        }
    }

    fun parseFunctionDeclaration(funToken: Token): Statement {
        // function => 'fun' ident '(' (parameter (',' parameter)*)? ')' (':' type)? block
        // parameter => ident ':' type

        this.idx += 1 // skip 'fun'

        val funName = this.peekOrThrow()
        check(funName.value is TokenValue.Ident) {
            throw LangError(funName.line, funName.col, ErrorType.Syntax, "Expected function identifier")
        }
        this.idx += 1 // skip function name

        // params
        val params = mutableListOf<FunctionParameter>()
        run {
            val lParen = this.peekOrThrow()
            check(lParen.value == TokenValue.LParen) {
                throw LangError(lParen.line, lParen.col, ErrorType.Syntax, "Expected '('")
            }
            this.idx += 1 // skip '('

            if (this.peekOrThrow().value == TokenValue.RParen) {
                this.idx += 1 // skip ')'
                return@run
            }

            while (true) {
                val next = this.peekOrThrow()
                check(next.value is TokenValue.Ident) {
                    throw LangError(next.line, next.col, ErrorType.Syntax, "Expected parameter identifier")
                }

                val paramIdent = next.value
                this.idx += 1 // skip ident

                val colon = this.peekOrThrow()
                check(colon.value == TokenValue.Colon) {
                    throw LangError(colon.line, colon.col, ErrorType.Syntax, "Expected ':'")
                }
                this.idx += 1 // skip ':'

                val type = this.peekOrThrow()
                check(type.value is TokenValue.Type) {
                    throw LangError(type.line, type.col, ErrorType.Syntax, "Expected type")
                }
                this.idx += 1 // skip type

                params.add(
                    FunctionParameter(paramIdent.name, type.value.type, next.line, next.col),
                )

                val comma = this.peekOrThrow()
                when (comma.value) {
                    TokenValue.Comma -> {
                        this.idx += 1 // skip ','
                    }

                    TokenValue.RParen -> {
                        this.idx += 1 // skip ')'
                        break
                    }

                    else -> throw LangError(comma.line, comma.col, ErrorType.Syntax, "Expected ',' or ')'")
                }
            }
        }

        // return type
        var retType: VariableType? = null
        run {
            val colon = this.peekOrThrow()
            if (colon.value == TokenValue.Colon) {
                this.idx += 1 // skip ':'

                val type = this.peekOrThrow()
                check(type.value is TokenValue.Type) {
                    throw LangError(type.line, type.col, ErrorType.Syntax, "Expected type")
                }
                this.idx += 1 // skip type

                retType = type.value.type
            }
        }

        // body
        val lBrace = this.peekOrThrow()
        check(lBrace.value is TokenValue.LBrace) {
            throw LangError(lBrace.line, lBrace.col, ErrorType.Syntax, "Expected '{'")
        }

        val body = this.parseBlockStatement(lBrace)

        return Statement(
            StatementType.FunctionDeclaration(funName.value.name, params, retType, body),
            funToken.line,
            funToken.col,
        )
    }

    fun parseStatement(): Statement {
        // statement => variableDeclaration | print

        val next = this.peekOrThrow()

        val (statement, requiresSemicolon) = when (next.value) {
            is TokenValue.Ident -> {
                if (this.peekOrThrow(1).value == TokenValue.LParen) {
                    val (name, args) = this.parseCall(next)
                    Pair(Statement(StatementType.Call(name, args), next.line, next.col), true)
                } else {
                    Pair(this.parseIdentStatement(next), true)
                }
            }

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

            TokenValue.LBrace -> Pair(this.parseBlockStatement(next), false)
            is TokenValue.If -> Pair(this.parseIfStatement(next), false)
            is TokenValue.For -> Pair(this.parseForStatement(next), false)
            is TokenValue.Fun -> Pair(this.parseFunctionDeclaration(next), false)
            is TokenValue.Return -> {
                this.idx += 1 // skip return

                val semi = this.peekOrThrow()
                var returnValue: Expression? = null
                if (this.peekOrThrow().value != TokenValue.Semicolon) {
                    returnValue = this.parseExpression()
                }

                Pair(Statement(StatementType.Return(returnValue), next.line, next.col), true)
            }

            TokenValue.Break -> {
                this.idx += 1
                Pair(Statement(StatementType.Break, next.line, next.col), true)
            }

            TokenValue.Continue -> {
                this.idx += 1
                Pair(Statement(StatementType.Continue, next.line, next.col), true)
            }

            else -> throw LangError(
                next.line,
                next.col,
                ErrorType.Syntax,
                "Expected identifier or block, if or print statement"
            )
        }

        if (requiresSemicolon) {
            val semicolon = this.peek()
            check(semicolon != null && semicolon.value == TokenValue.Semicolon) {
                val cur = this.tokens[this.idx - 1]
                throw LangError(cur.line, cur.col, ErrorType.Syntax, "Expect ';' after a statement")
            }
            this.idx += 1
        }

        return statement
    }

    fun parseProgram(): Pair<List<Statement>, List<LangError>> {
        var statements: MutableList<Statement> = mutableListOf()
        val errors = mutableListOf<LangError>()

        while (this.idx < this.tokens.size) {
            try {
                statements.add(this.parseStatement())
            } catch (e: LangError) {
                errors.add(e)
                // TODO: this may cause errors to be skipped
                this.idx += 1

                while (true) {
                    val next = this.peek()

                    when (next?.value) {
                        null -> return Pair(statements, errors)
                        TokenValue.Semicolon -> {
                            this.idx += 1
                            break
                        }

                        TokenValue.If,
                        TokenValue.Else,
                        TokenValue.For,
                        TokenValue.Fun -> {
                            break
                        }

                        is TokenValue.Ident -> {
                            when (this.peek(1)?.value) {
                                null -> {
                                    return Pair(statements, errors)
                                }

                                TokenValue.Colon, TokenValue.Equal -> {
                                    break
                                }

                                else -> {
                                    this.idx += 1
                                }
                            }
                        }

                        else -> {
                            this.idx += 1
                        }
                    }
                }
            }
        }

        return Pair(statements, errors)
    }
}
