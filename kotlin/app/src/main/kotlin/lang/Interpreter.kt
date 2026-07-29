package lang

import java.io.PrintStream

class BufferedPrinter(
    val capacity: Int = 4096,
    val output: PrintStream = System.out
) {
    val sb = StringBuilder()

    fun print(text: String) {
        this.sb.append(text)
        if (this.sb.length >= this.capacity) {
            this.flush()
        }
    }

    fun println(text: String) {
        this.sb.append(text)
        this.sb.append("\n")
        if (this.sb.length >= this.capacity) {
            this.flush()
        }
    }

    fun flush() {
        output.print(this.sb)
        this.sb.clear()
    }
}

sealed interface VariableValue {
    data class Double(val value: kotlin.Double) : VariableValue {
        override fun toString(): String = this.value.toString()
    }
}

class Interpreter(
    val printer: BufferedPrinter? = null,
) {
    var variables: MutableMap<String, VariableValue> = mutableMapOf()

    fun interpretUnary(expression: Expression.Unary): VariableValue {
        val value = interpretExpression(expression.operand)
        return when (value) {
            is VariableValue.Double -> {
                when (expression.operator) {
                    TokenValue.Minus -> VariableValue.Double(-value.value)
                    TokenValue.Plus -> value
                    else -> throw RuntimeException("Invalid unary operator: ${expression.operator}")
                }
            }
        }
    }

    fun interpretBinary(expression: Expression.Binary): VariableValue {
        val left = interpretExpression(expression.left)
        check(left is VariableValue.Double) { throw RuntimeException("Expected a double value, got $left") }
        val right = interpretExpression(expression.right)
        check(right is VariableValue.Double) { throw RuntimeException("Expected a double value, got $right") }

        return when (expression.operator) {
            TokenValue.Plus -> VariableValue.Double(left.value + right.value)
            TokenValue.Minus -> VariableValue.Double(left.value - right.value)
            TokenValue.Star -> VariableValue.Double(left.value * right.value)
            TokenValue.Slash -> {
                check(right.value != 0.0) { throw RuntimeException("Division by zero") }
                VariableValue.Double(left.value / right.value)
            }

            else -> throw RuntimeException("Invalid operator: ${expression.operator}")
        }
    }

    fun interpretExpression(expression: Expression): VariableValue {
        return when (expression) {
            is Expression.Ident -> {
                val value = variables[expression.name]
                check(value != null) {
                    throw RuntimeException("Undefined variable: ${expression.name}")
                }
                value
            }

            is Expression.NumberLiteral -> VariableValue.Double(expression.value)
            is Expression.Unary -> this.interpretUnary(expression)
            is Expression.Binary -> this.interpretBinary(expression)
        }
    }

    fun interpretProgram(statements: List<Statement>) {
        for (statement in statements) {
            when (statement) {
                is Statement.VariableDeclaration -> {
                    if (statement.value == null) {
                        variables[statement.name] = VariableValue.Double(0.0)
                    } else {
                        variables[statement.name] = interpretExpression(statement.value)
                    }
                }

                is Statement.VariableAssignment -> {
                    if (variables.containsKey(statement.name)) {
                        variables[statement.name] = interpretExpression(statement.value)
                    } else {
                        throw IllegalArgumentException("variable ${statement.name} not declared")
                    }
                }

                is Statement.Print -> {
                    if (this.printer != null) {
                        val text = interpretExpression(statement.expression).toString()
                        if (statement.ln) {
                            this.printer.println(text)
                        } else {
                            this.printer.print(text)
                        }
                    }
                }
            }
        }
    }
}
