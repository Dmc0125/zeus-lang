package lang

import java.io.PrintStream

class BufferedPrinter(
    val capacity: Int = 4096,
    val output: PrintStream = System.out
) {
    val sb = StringBuilder()
    var flushed = false

    fun print(text: String) {
        this.flushed = false

        this.sb.append(text)

        if (this.sb.length >= this.capacity) {
            this.flush()
        }
    }

    fun println(text: String) {
        this.flushed = false

        this.sb.append(text)
        this.sb.append("\n")

        if (this.sb.length >= this.capacity) {
            this.flush()
        }
    }

    fun flush() {
        if (this.sb.isNotEmpty()) {
            output.print(this.sb)
            this.sb.clear()
            this.flushed = true
        }
    }
}

sealed interface VariableValue {
    data class Number(val value: kotlin.Double) : VariableValue {
        override fun toString(): kotlin.String = this.value.toString()
    }

    data class String(val value: kotlin.String) : VariableValue {
        override fun toString(): kotlin.String = this.value
    }

    data class Bool(val value: kotlin.Boolean) : VariableValue {
        override fun toString(): kotlin.String = this.value.toString()
    }
}

class Interpreter(
    val printer: BufferedPrinter? = null,
) {
    var variables: MutableMap<String, VariableValue> = mutableMapOf()

    fun interpretUnary(expression: Expression.Unary): VariableValue {
        val value = interpretExpression(expression.operand)
        return when (expression.operator) {
            is TokenValue.Minus -> {
                val num = value as VariableValue.Number
                VariableValue.Number(-num.value)
            }

            is TokenValue.Excl -> {
                val bool = value as VariableValue.Bool
                VariableValue.Bool(!bool.value)
            }

            else -> value
        }
    }

    fun interpretBinary(expression: Expression.Binary): VariableValue {
        val left = interpretExpression(expression.left) as VariableValue.Number
        val right = interpretExpression(expression.right) as VariableValue.Number

        return when (expression.operator) {
            TokenValue.Plus -> VariableValue.Number(left.value + right.value)
            TokenValue.Minus -> VariableValue.Number(left.value - right.value)
            TokenValue.Star -> VariableValue.Number(left.value * right.value)
            TokenValue.Slash -> {
                check(right.value != 0.0) { throw RuntimeException("Division by zero") }
                VariableValue.Number(left.value / right.value)
            }

            else -> throw RuntimeException("Unreachable")
        }
    }

    fun interpretExpression(expression: Expression): VariableValue {
        return when (expression) {
            is Expression.Ident -> variables[expression.name]!!
            is Expression.NumberLiteral -> VariableValue.Number(expression.value)
            is Expression.StringLiteral -> VariableValue.String(expression.value)
            is Expression.BoolLiteral -> VariableValue.Bool(expression.value)
            is Expression.Unary -> this.interpretUnary(expression)
            is Expression.Binary -> this.interpretBinary(expression)
        }
    }

    fun interpretProgram(statements: List<Statement>) {
        for (statement in statements) {
            when (statement) {
                is Statement.VariableDeclaration -> {
                    if (statement.value == null) {
                        variables[statement.name] = VariableValue.Number(0.0)
                    } else {
                        variables[statement.name] = interpretExpression(statement.value)
                    }
                }

                is Statement.VariableAssignment -> {
                    variables[statement.name] = interpretExpression(statement.value)
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
