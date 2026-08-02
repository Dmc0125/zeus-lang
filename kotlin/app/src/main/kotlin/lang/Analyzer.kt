package lang

class AnalyzerScope {
    val variables: MutableMap<String, VariableType> = mutableMapOf()
}

class Analyzer {
    val stack: MutableList<AnalyzerScope> = mutableListOf(AnalyzerScope())

    fun analyzeUnary(expression: Expression.Unary): VariableType {
        val type = this.analyzeExpression(expression.operand)

        return when (type) {
            VariableType.Number -> {
                when (expression.operator) {
                    is TokenValue.Minus, is TokenValue.Plus -> type
                    else ->
                        throw RuntimeException(
                            "Invalid operator: ${expression.operator}; unary requires \"-\" or \"+\""
                        )
                }
            }

            VariableType.Bool -> {
                when (expression.operator) {
                    is TokenValue.Excl -> type
                    else ->
                        throw RuntimeException(
                            "Invalid operator: ${expression.operator}; unary requires \"!\""
                        )
                }
            }

            else -> throw RuntimeException("Invalid operand type: $type; unary requires Double")
        }
    }

    fun analyzeBinary(expression: Expression.Binary): VariableType {
        val leftType = this.analyzeExpression(expression.left)
        val rightType = this.analyzeExpression(expression.right)

        if (leftType != rightType) {
            throw RuntimeException(
                "Invalid operand types: $leftType, $rightType"
            )
        }

        return when (expression.operator) {
            is TokenValue.Minus, is TokenValue.Plus,
            is TokenValue.Star, is TokenValue.Slash -> {
                check(leftType == VariableType.Number) {
                    throw RuntimeException(
                        "Invalid operand types: $leftType, $rightType; binary requires both Double"
                    )
                }
                VariableType.Number
            }

            is TokenValue.Lt, is TokenValue.Gt,
            is TokenValue.LtEqual, is TokenValue.GtEqual -> {
                check(leftType == VariableType.Number) {
                    throw RuntimeException(
                        "Invalid operand types: $leftType, $rightType; binary requires both Double"
                    )
                }
                VariableType.Bool
            }

            is TokenValue.DoubleAmp, is TokenValue.DoublePipe -> {
                check(leftType == VariableType.Bool) {
                    throw RuntimeException(
                        "Invalid operand types: $leftType, $rightType; binary requires both Bool"
                    )
                }
                VariableType.Bool
            }

            is TokenValue.DoubleEqual, is TokenValue.ExclEqual -> VariableType.Bool
            else -> throw RuntimeException(
                "Invalid operator: ${expression.operator}"
            )
        }
    }

    fun analyzeExpression(expression: Expression): VariableType {
        return when (expression) {
            is Expression.Ident -> {
                for (scope in this.stack) {
                    if (scope.variables.containsKey(expression.name)) {
                        return scope.variables[expression.name]!!
                    }
                }
                throw RuntimeException("Undefined variable: ${expression.name}")
            }

            is Expression.NumberLiteral -> VariableType.Number
            is Expression.StringLiteral -> VariableType.String
            is Expression.BoolLiteral -> VariableType.Bool
            is Expression.Unary -> this.analyzeUnary(expression)
            is Expression.Binary -> this.analyzeBinary(expression)
        }
    }

    fun analyzeStatement(statement: Statement) {
        when (statement) {
            is Statement.VariableDeclaration -> {
                val scope = this.stack[this.stack.size - 1]

                if (scope.variables.containsKey(statement.name)) {
                    throw RuntimeException("Variable already declared: ${statement.name}")
                }

                if (statement.value == null) {
                    check(statement.type != null) {
                        throw RuntimeException("Type not specified for variable: ${statement.name}")
                    }
                    scope.variables[statement.name] = statement.type
                } else {
                    val valueType = this.analyzeExpression(statement.value)
                    if (statement.type != null) {
                        check(statement.type == valueType) {
                            throw RuntimeException("Type mismatch: ${statement.name}")
                        }
                    }
                    scope.variables[statement.name] = valueType
                }
            }

            is Statement.VariableAssignment -> {
                var found = false
                for (scope in this.stack) {
                    if (scope.variables.containsKey(statement.name)) {
                        val variableType = scope.variables[statement.name]
                        val valueType = this.analyzeExpression(statement.value)
                        check(variableType == valueType) {
                            throw RuntimeException("Type mismatch: ${statement.name}")
                        }
                        found = true
                    }
                }
                check(found) { throw RuntimeException("Undefined variablel: ${statement.name}") }
            }

            is Statement.Print -> this.analyzeExpression(statement.expression)
            is Statement.Block -> {
                this.stack.add(AnalyzerScope())
                for (statement in statement.statements) {
                    this.analyzeStatement(statement)
                }
                this.stack.removeAt(this.stack.size - 1)
            }

            is Statement.If -> {
                val conditionType = this.analyzeExpression(statement.condition)
                check(conditionType == VariableType.Bool) {
                    throw RuntimeException("Condition must be a boolean")
                }

                if (statement.thenBranch != null) {
                    this.analyzeStatement(statement.thenBranch)
                }
                if (statement.elseBranch != null) {
                    this.analyzeStatement(statement.elseBranch)
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
