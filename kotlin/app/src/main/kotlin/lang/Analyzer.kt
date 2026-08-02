package lang

class AnalyzerScope {
    val variables: MutableMap<String, VariableType> = mutableMapOf()
}

class Analyzer {
    val stack: MutableList<AnalyzerScope> = mutableListOf(AnalyzerScope())

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

    fun analyzeExpression(expression: Expression): VariableType {
        return when (expression.type) {
            is ExpressionType.Ident -> {
                val ident = expression.type
                for (scope in this.stack) {
                    if (scope.variables.containsKey(ident.name)) {
                        return scope.variables[ident.name]!!
                    }
                }
                throw LangError(
                    expression.line,
                    expression.col,
                    ErrorType.Type,
                    ErrorMessage.UndefinedVariable
                )
            }

            is ExpressionType.NumberLiteral -> VariableType.Number
            is ExpressionType.StringLiteral -> VariableType.String
            is ExpressionType.BoolLiteral -> VariableType.Bool
            is ExpressionType.Unary -> this.analyzeUnary(expression as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.analyzeBinary(expression as Node<ExpressionType.Binary>)
        }
    }

    fun analyzeStatement(stmt: Statement) {
        when (stmt.type) {
            is StatementType.VariableDeclaration -> {
                val decl = stmt.type
                val scope = this.stack[this.stack.size - 1]

                if (scope.variables.containsKey(decl.name)) {
                    throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.VariableAlreadyDefined)
                }

                if (decl.value == null) {
                    check(decl.type != null) {
                        throw LangError(stmt.line, stmt.col, ErrorType.Type, ErrorMessage.TypeNotSpecified)
                    }
                    scope.variables[decl.name] = decl.type
                } else {
                    val valueType = this.analyzeExpression(decl.value)
                    if (decl.type != null) {
                        check(decl.type == valueType) {
                            throw LangError(
                                stmt.line, stmt.col, ErrorType.Type,
                                "Type mismatch: ${decl.type} != ${valueType}",
                            )
                        }
                    }
                    scope.variables[decl.name] = valueType
                }
            }

            is StatementType.VariableAssignment -> {
                val assignment = stmt.type
                var found = false
                for (scope in this.stack) {
                    if (scope.variables.containsKey(assignment.name)) {
                        val variableType = scope.variables[assignment.name]
                        val valueType = this.analyzeExpression(assignment.value)
                        check(variableType == valueType) {
                            throw LangError(
                                stmt.line, stmt.col, ErrorType.Type,
                                "Type mismatch: ${variableType} != ${valueType}",
                            )
                        }
                        found = true
                    }
                }
                check(found) {
                    throw LangError(
                        stmt.line,
                        stmt.col,
                        ErrorType.Type,
                        ErrorMessage.UndefinedVariable
                    )
                }
            }

            is StatementType.Print -> this.analyzeExpression(stmt.type.expression)
            is StatementType.Block -> {
                this.stack.add(AnalyzerScope())
                for (statement in stmt.type.statements) {
                    this.analyzeStatement(statement)
                }
                this.stack.removeAt(this.stack.size - 1)
            }

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

                if (stmt.type.thenBranch != null) {
                    this.analyzeStatement(stmt.type.thenBranch)
                }
                if (stmt.type.elseBranch != null) {
                    this.analyzeStatement(stmt.type.elseBranch)
                }
            }
        }
    }

    fun analyzeProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.analyzeStatement(statement)
        }
    }
}
