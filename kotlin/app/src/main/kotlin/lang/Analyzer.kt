package lang

sealed interface VariableType {
    data object Double : VariableType
    data object String : VariableType
}

class Analyzer {
    val variables: MutableMap<String, VariableType> = mutableMapOf()

    fun analyzeUnary(expression: Expression.Unary): VariableType {
        val type = this.analyzeExpression(expression.operand)

        if (type != VariableType.Double) {
            throw RuntimeException("Invalid operand type: $type; unary requires Double")
        }

        return when (expression.operator) {
            is TokenValue.Minus, is TokenValue.Plus -> type
            else -> throw RuntimeException("Invalid operator: ${expression.operator}; unary requires \"-\" or \"+\"")
        }
    }

    fun analyzeBinary(expression: Expression.Binary): VariableType {
        val leftType = this.analyzeExpression(expression.left)
        val rightType = this.analyzeExpression(expression.right)

        if (leftType != VariableType.Double || rightType != VariableType.Double) {
            throw RuntimeException("Invalid operand types: $leftType, $rightType; binary requires both Double")
        }

        return when (expression.operator) {
            is TokenValue.Minus, is TokenValue.Plus,
            is TokenValue.Star, is TokenValue.Slash -> VariableType.Double

            else -> throw RuntimeException("Invalid operator: ${expression.operator}; binary requires \"-\", \"+\", \"*\", or \"/\"")
        }
    }

    fun analyzeExpression(expression: Expression): VariableType {
        return when (expression) {
            is Expression.Ident -> {
                val type = this.variables[expression.name]
                check(type != null) {
                    throw RuntimeException("Undefined variable: ${expression.name}")
                }
                type!!
            }

            is Expression.NumberLiteral -> VariableType.Double
            is Expression.StringLiteral -> VariableType.String
            is Expression.Unary -> this.analyzeUnary(expression)
            is Expression.Binary -> this.analyzeBinary(expression)
        }
    }

    fun analyzeProgram(statements: List<Statement>) {
        for (statement in statements) {
            when (statement) {
                is Statement.VariableDeclaration -> {
                    if (this.variables.containsKey(statement.name)) {
                        throw RuntimeException("Variable already declared: ${statement.name}")
                    }

                    if (statement.value == null) {
                        // TODO: Introduce types
                        throw RuntimeException("unimplemented")
                    }

                    this.variables[statement.name] = this.analyzeExpression(statement.value)
                }

                is Statement.VariableAssignment -> {
                    if (!this.variables.containsKey(statement.name)) {
                        throw RuntimeException("Undefined variablel: ${statement.name}")
                    }

                    val variableType = this.variables[statement.name]
                    val valueType = this.analyzeExpression(statement.value)
                    check(variableType == valueType) {
                        throw RuntimeException("Type mismatch: ${statement.name}")
                    }
                }

                is Statement.Print -> this.analyzeExpression(statement.expression)
            }
        }
    }
}
