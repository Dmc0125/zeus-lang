package lang

data class FunctionSignature(
    val params: List<FunctionParameter>,
    val ret: VariableType?,

    var hasReturn: Boolean
)

class AnalyzerEnvironment() {
    val variables: MutableMap<String, VariableType> = mutableMapOf()
    val functions: MutableMap<String, FunctionSignature> = mutableMapOf()

    fun declare(name: String, type: VariableType): LangError? {
        if (variables.containsKey(name)) {
            return LangError(0, 0, ErrorType.Type, ErrorMessage.VariableAlreadyDefined)
        }
        variables[name] = type
        return null
    }

    fun declare(name: String, func: FunctionSignature): LangError? {
        if (functions.containsKey(name)) {
            return LangError(0, 0, ErrorType.Type, ErrorMessage.FunctionAlreadyDeclared)
        }
        functions[name] = func
        return null
    }

    fun assign(name: String, type: VariableType): LangError? {
        if (!variables.containsKey(name)) {
            return LangError(0, 0, ErrorType.Type, ErrorMessage.UndefinedVariable)
        }

        if (variables[name] != type) {
            return LangError(0, 0, ErrorType.Type, "Type mismatch : ${variables[name]} != ${type}")
        }

        return null
    }

    fun get(name: String): VariableType? {
        return variables[name]
    }

    fun getFunction(name: String): FunctionSignature? {
        return functions[name]
    }
}

class Analyzer {
    val stack: MutableList<AnalyzerEnvironment> = mutableListOf(AnalyzerEnvironment())

    fun analyzeUnary(expression: Node<ExpressionType.Unary>): VariableType {
        val unary = expression.type
        val type = this.analyzeExpression(unary.operand)

        return when (type) {
            VariableType.Number -> {
                when (unary.operator) {
                    is TokenValue.Minus, is TokenValue.Plus -> type
                    else -> throw LangError(
                        expression.line,
                        expression.col,
                        ErrorType.Syntax,
                        "Invalid operator; expected \"-\" or \"+\""
                    )
                }
            }

            VariableType.Bool -> {
                when (unary.operator) {
                    is TokenValue.Excl -> type
                    else -> throw LangError(
                        expression.line,
                        expression.col,
                        ErrorType.Syntax,
                        "Invalid operator; expected \"!\""
                    )
                }
            }

            else -> throw LangError(
                expression.line,
                expression.col,
                ErrorType.Syntax,
                "Invalid operand; expected number or bool"
            )
        }
    }

    fun analyzeBinary(expression: Node<ExpressionType.Binary>): VariableType {
        val binary = expression.type
        val leftType = this.analyzeExpression(binary.left)
        val rightType = this.analyzeExpression(binary.right)

        if (leftType != rightType) {
            throw LangError(
                expression.line,
                expression.col,
                ErrorType.Type,
                "Types mismatch: $leftType != $rightType"
            )
        }

        return when (binary.operator) {
            is TokenValue.Minus, is TokenValue.Plus,
            is TokenValue.Star, is TokenValue.Slash -> {
                check(leftType == VariableType.Number) {
                    throw LangError(
                        expression.line,
                        expression.col,
                        ErrorType.Type,
                        "Types mismatch: $leftType != $rightType; expected numbers"
                    )
                }
                VariableType.Number
            }

            is TokenValue.Lt, is TokenValue.Gt,
            is TokenValue.LtEqual, is TokenValue.GtEqual -> {
                check(leftType == VariableType.Number) {
                    throw LangError(
                        expression.line,
                        expression.col,
                        ErrorType.Type,
                        "Types mismatch: $leftType != $rightType; expected numbers"
                    )
                }
                VariableType.Bool
            }

            is TokenValue.DoubleAmp, is TokenValue.DoublePipe -> {
                check(leftType == VariableType.Bool) {
                    throw LangError(
                        expression.line,
                        expression.col,
                        ErrorType.Type,
                        "Types mismatch: $leftType != $rightType; expected bools"
                    )
                }
                VariableType.Bool
            }

            is TokenValue.DoubleEqual, is TokenValue.ExclEqual -> VariableType.Bool

            else -> throw LangError(
                expression.line,
                expression.col,
                ErrorType.Type,
                "Invalid operator"
            )
        }
    }

    fun analyzeExpression(expr: Expression): VariableType {
        return when (expr.type) {
            is ExpressionType.Ident -> {
                val ident = expr.type
                for (stackEnv in this.stack.asReversed()) {
                    stackEnv.get(ident.name)?.let { return it }
                }
                throw LangError(
                    expr.line,
                    expr.col,
                    ErrorType.Type,
                    ErrorMessage.UndefinedVariable
                )
            }

            is ExpressionType.NumberLiteral -> VariableType.Number
            is ExpressionType.StringLiteral -> VariableType.String
            is ExpressionType.BoolLiteral -> VariableType.Bool
            is ExpressionType.Unary -> this.analyzeUnary(expr as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.analyzeBinary(expr as Node<ExpressionType.Binary>)
            is ExpressionType.Call -> {
                val func = expr.type

                var signature: FunctionSignature? = null
                for (stackEnv in this.stack.asReversed()) {
                    signature = stackEnv.getFunction(func.name)
                }

                if (signature == null) {
                    throw LangError(expr.line, expr.col, ErrorType.Type, "Function not declared")
                }
                if (func.args.size != signature.params.size) {
                    throw LangError(expr.line, expr.col, ErrorType.Type, "Expected ${signature.params.size} arguments")
                }

                for ((i, callArg) in func.args.withIndex()) {
                    val argType = this.analyzeExpression(callArg)
                    val sigArg = signature.params[i]

                    if (sigArg.type != argType) {
                        throw LangError(
                            callArg.line, callArg.col, ErrorType.Type,
                            "Argument type mismatch",
                        )
                    }
                }

                check(signature.ret != null) {
                    "unimplmented"
                }
                signature.ret
            }
        }
    }

    fun analyzeBlock(block: Statement, insideLoop: Boolean = false, function: FunctionSignature? = null) {
        assert(block.type is StatementType.Block) { "Expected block, got ${block.type}" }

        val stmts = (block.type as StatementType.Block).statements
        if (stmts.isEmpty()) return

        this.stack.add(AnalyzerEnvironment())
        for (stmt in stmts) {
            this.analyzeStatement(stmt, insideLoop, function)
        }
        this.stack.removeLast()
    }

    fun analyzeStatement(stmt: Statement, insideLoop: Boolean = false, function: FunctionSignature? = null) {
        when (stmt.type) {
            is StatementType.VariableDeclaration -> {
                val decl = stmt.type
                val scope = this.stack[this.stack.size - 1]

                var valueType: VariableType?
                if (decl.value == null) {
                    valueType = decl.type
                } else {
                    valueType = this.analyzeExpression(decl.value)
                    if (decl.type != null) {
                        check(decl.type == valueType) {
                            throw LangError(
                                stmt.line, stmt.col, ErrorType.Type,
                                "Type mismatch: ${decl.type} != ${valueType}",
                            )
                        }
                    }
                }

                if (valueType == null) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.TypeNotSpecified)
                }

                scope.declare(decl.name, valueType)?.let {
                    it.line = stmt.line
                    it.col = stmt.col
                    throw it
                }
            }

            is StatementType.VariableAssignment -> {
                val assignment = stmt.type
                var found = false

                for (stackEnv in this.stack) {
                    if (stackEnv.variables.containsKey(assignment.name)) {
                        val valueType = this.analyzeExpression(assignment.value)

                        stackEnv.assign(assignment.name, valueType)?.let {
                            it.line = stmt.line
                            it.col = stmt.col
                            throw it
                        }

                        found = true
                    }
                }

                if (!found) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.UndefinedVariable)
                }
            }

            is StatementType.Print -> this.analyzeExpression(stmt.type.expression)
            is StatementType.Block -> this.analyzeBlock(stmt, insideLoop, function)

            is StatementType.If -> {
                val conditionType = this.analyzeExpression(stmt.type.condition)
                check(conditionType == VariableType.Bool) {
                    throw LangError(
                        stmt.type.condition.line,
                        stmt.type.condition.col,
                        ErrorType.Type,
                        ErrorMessage.ConditionMustBeBoolean,
                    )
                }

                this.analyzeBlock(stmt.type.thenBranch, insideLoop, function)
                if (stmt.type.elseBranch != null) {
                    this.analyzeStatement(stmt.type.elseBranch, insideLoop, function)
                }
            }

            is StatementType.For -> {
                val stmt = stmt.type
                if (stmt.condition != null) {
                    val conditionType = this.analyzeExpression(stmt.condition)
                    check(conditionType == VariableType.Bool) {
                        val cond = stmt.condition
                        throw LangError(cond.line, cond.col, ErrorType.Type, ErrorMessage.ConditionMustBeBoolean)
                    }
                }
                this.analyzeBlock(stmt.body, true, function)
            }

            is StatementType.CFor -> {
                val stmt = stmt.type

                // NOTE: declaration inside init should only be visible to for loop
                this.stack.add(AnalyzerEnvironment())

                if (stmt.init != null) {
                    check(stmt.init.type is StatementType.VariableDeclaration) {
                        throw LangError(
                            stmt.init.line,
                            stmt.init.col,
                            ErrorType.Syntax,
                            "Expected variable declaration",
                        )
                    }
                    this.analyzeStatement(stmt.init)
                }

                val cond = stmt.condition
                if (stmt.condition != null) {
                    val conditionType = this.analyzeExpression(cond)
                    if (conditionType != VariableType.Bool) {
                        throw LangError(cond.line, cond.col, ErrorType.Type, ErrorMessage.ConditionMustBeBoolean)
                    }
                }

                if (stmt.update != null) {
                    check(stmt.update.type is StatementType.VariableAssignment) {
                        throw LangError(
                            stmt.update.line,
                            stmt.update.col,
                            ErrorType.Syntax,
                            "Expected variable assignment",
                        )
                    }
                    this.analyzeStatement(stmt.update)
                }

                this.analyzeBlock(stmt.body, true, function)

                this.stack.removeLast()
            }

            StatementType.Break -> {
                check(insideLoop) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Syntax, ErrorMessage.BreakOutsideLoop)
                }
            }

            StatementType.Continue -> {
                check(insideLoop) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Syntax, ErrorMessage.ContinueOutsideLoop)
                }
            }

            is StatementType.FunctionDeclaration -> {
                val func = stmt.type

                if (func.params.size > 255) {
                    throw LangError(
                        stmt.line,
                        stmt.col,
                        ErrorType.Syntax,
                        "Can't have more than 255 parameters"
                    )
                }

                val signature = FunctionSignature(func.params, func.ret, false)
                this.analyzeBlock(func.body, false, signature)

                if (func.ret != null && !signature.hasReturn) {
                    throw LangError(
                        stmt.line,
                        stmt.col,
                        ErrorType.Syntax,
                        ErrorMessage.MissingReturn,
                    )
                }

                val scope = this.stack[this.stack.size - 1]
                scope.declare(func.name, signature)?.let {
                    it.line = stmt.line
                    it.col = stmt.col
                    throw it
                }
            }

            is StatementType.Return -> {
                check(function != null) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Syntax, ErrorMessage.ReturnOutsideFunction)
                }

                val value = this.analyzeExpression(stmt.type.value)
                check(value == function.ret) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.ReturnTypeMismatch)
                }

                function.hasReturn = true
            }
        }
    }

    fun analyzeProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.analyzeStatement(statement)
        }
    }
}
