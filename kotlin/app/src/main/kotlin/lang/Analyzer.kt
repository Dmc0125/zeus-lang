package lang

sealed interface ConditionFlow {
    data object FallsThrough : ConditionFlow
    data object Returns : ConditionFlow
}

data class FunctionSignature(
    val params: List<FunctionParameter>,
    val ret: VariableType?,
    var flow: ConditionFlow = ConditionFlow.FallsThrough
)

class Analyzer {
    var varEnv = Environment<VariableType>()
    var funcEnv = Environment<FunctionSignature>()

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

    fun analyzeCall(line: Int, col: Int, name: String, args: List<Expression>): VariableType? {
        var signature = this.funcEnv.get(name)
        if (signature == null) {
            throw LangError(line, col, ErrorType.Type, ErrorMessage.Undefined)
        }
        if (args.size != signature.params.size) {
            throw LangError(line, col, ErrorType.Type, "Expected ${signature.params.size} arguments")
        }

        for ((i, callArg) in args.withIndex()) {
            val argType = this.analyzeExpression(callArg)
            val sigArg = signature.params[i]

            if (sigArg.type != argType) {
                throw LangError(
                    callArg.line, callArg.col, ErrorType.Type,
                    "Argument type mismatch",
                )
            }
        }
        return signature.ret
    }

    fun analyzeExpression(expr: Expression): VariableType {
        return when (expr.type) {
            is ExpressionType.Ident -> {
                val ident = expr.type
                this.varEnv.get(ident.name)?.let {
                    return it
                }
                throw LangError(expr.line, expr.col, ErrorType.Type, ErrorMessage.Undefined)
            }

            is ExpressionType.NumberLiteral -> VariableType.Number
            is ExpressionType.StringLiteral -> VariableType.String
            is ExpressionType.BoolLiteral -> VariableType.Bool
            is ExpressionType.Unary -> this.analyzeUnary(expr as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.analyzeBinary(expr as Node<ExpressionType.Binary>)
            is ExpressionType.Call -> {
                val func = expr.type
                val retType = this.analyzeCall(expr.line, expr.col, func.name, func.args)
                check(retType != null) {
                    throw LangError(
                        expr.line, expr.col, ErrorType.Type,
                        "Call as an expression must return a value",
                    )
                }
                retType
            }
        }
    }

    fun analyzeBlock(
        block: Statement,
        insideLoop: Boolean = false,
        function: FunctionSignature? = null
    ) {
        assert(block.type is StatementType.Block) { "Expected block, got ${block.type}" }

        val stmts = (block.type as StatementType.Block).statements
        if (stmts.isEmpty()) return

        this.varEnv = this.varEnv.child()
        this.funcEnv = this.funcEnv.child()

        for (stmt in stmts) {
            this.analyzeStatement(stmt, insideLoop, function)
        }

        this.varEnv = this.varEnv.parent!!
        this.funcEnv = this.funcEnv.parent!!
    }

    fun analyzeStatement(
        stmt: Statement,
        insideLoop: Boolean = false,
        function: FunctionSignature? = null
    ) {
        when (stmt.type) {
            is StatementType.VariableDeclaration -> {
                val decl = stmt.type

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

                this.varEnv.declare(decl.name, valueType)?.let {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, it)
                }
            }

            is StatementType.VariableAssignment -> {
                val assignment = stmt.type
                this.varEnv.assign(assignment.name, this.analyzeExpression(assignment.value))?.let {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, it)
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
                val elseBranch = stmt.type.elseBranch

                if (elseBranch == null) {
                    if (function != null) {
                        function.flow = ConditionFlow.FallsThrough
                    }
                } else {
                    // if one of the branches is fall through, the flow is fall through
                    var thenFlow = function?.flow ?: ConditionFlow.FallsThrough
                    this.analyzeStatement(stmt.type.elseBranch, insideLoop, function)

                    if (function != null) {
                        if (thenFlow == ConditionFlow.FallsThrough || function.flow == ConditionFlow.FallsThrough) {
                            function.flow = ConditionFlow.FallsThrough
                        }
                    }
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

                this.varEnv = this.varEnv.child()

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

                this.varEnv = this.varEnv.parent!!
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

                val oldVarEnv = this.varEnv
                val oldFuncEnv = this.funcEnv

                // Create a new scope for the function - do not allow closure
                this.varEnv = Environment<VariableType>()
                this.funcEnv = this.funcEnv.child()

                // Declare function parameters in the new scope
                for (param in func.params) {
                    this.varEnv.declare(param.name, param.type)?.let {
                        throw LangError(param.line, param.col, ErrorType.Syntax, it)
                    }
                }

                val signature = FunctionSignature(func.params, func.ret)

                // validate body statements

                val body = func.body.type as StatementType.Block
                for (stmt in body.statements) {
                    this.analyzeStatement(stmt, false, signature)
                }

                // validate return

                if (func.ret != null && signature.flow != ConditionFlow.Returns) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Syntax, ErrorMessage.MissingReturn)
                }

                this.varEnv = oldVarEnv
                this.funcEnv = oldFuncEnv

                this.funcEnv.declare(func.name, signature)?.let {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, it)
                }
            }

            is StatementType.Return -> {
                check(function != null) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Syntax, ErrorMessage.ReturnOutsideFunction)
                }

                val ret = stmt.type
                var retType: VariableType? = null
                if (ret.value != null) {
                    retType = this.analyzeExpression(ret.value)
                }

                check(retType == function.ret) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.ReturnTypeMismatch)
                }

                function.flow = ConditionFlow.Returns
            }

            is StatementType.Call -> {
                val call = stmt.type
                this.analyzeCall(stmt.line, stmt.col, call.name, call.args)
            }
        }
    }

    fun analyzeProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.analyzeStatement(statement)
        }
    }
}
