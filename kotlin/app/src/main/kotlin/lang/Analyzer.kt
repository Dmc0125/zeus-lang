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
                    else ->
                        throw RuntimeException(
                            "Invalid operator: ${unary.operator}; unary requires \"-\" or \"+\""
                        )
                }
            }

            VariableType.Bool -> {
                when (unary.operator) {
                    is TokenValue.Excl -> type
                    else ->
                        throw RuntimeException(
                            "Invalid operator: ${unary.operator}; unary requires \"!\""
                        )
                }
            }

            else -> throw RuntimeException("Invalid operand type: $type; unary requires Double")
        }
    }

    fun analyzeBinary(expression: Node<ExpressionType.Binary>): VariableType {
        val binary = expression.type
        val leftType = this.analyzeExpression(binary.left)
        val rightType = this.analyzeExpression(binary.right)

        if (leftType != rightType) {
            throw RuntimeException(
                "Invalid operand types: $leftType, $rightType"
            )
        }

        return when (binary.operator) {
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
                "Invalid operator: ${binary.operator}"
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
                throw RuntimeException("Undefined variable: ${ident.name}")
            }

            is ExpressionType.NumberLiteral -> VariableType.Number
            is ExpressionType.StringLiteral -> VariableType.String
            is ExpressionType.BoolLiteral -> VariableType.Bool
            is ExpressionType.Unary -> this.analyzeUnary(expression as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.analyzeBinary(expression as Node<ExpressionType.Binary>)
        }
    }

    fun analyzeStatement(statement: Statement) {
        when (statement) {
            is StatementType.VariableDeclaration -> {
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

            is StatementType.VariableAssignment -> {
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

            is StatementType.Print -> this.analyzeExpression(statement.expression)
            is StatementType.Block -> {
                this.stack.add(AnalyzerScope())
                for (statement in statement.statements) {
                    this.analyzeStatement(statement)
                }
                this.stack.removeAt(this.stack.size - 1)
            }

            is StatementType.If -> {
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
