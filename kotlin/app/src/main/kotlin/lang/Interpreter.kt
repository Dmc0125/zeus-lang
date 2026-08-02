package lang

import java.io.PrintStream

class BufferedPrinter(val capacity: Int = 4096, val output: PrintStream = System.out) {
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
    val scopes: MutableList<MutableMap<String, VariableValue>> = mutableListOf(mutableMapOf())

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
        when (expression.operator) {
            is TokenValue.DoubleAmp -> {
                val left = interpretExpression(expression.left) as VariableValue.Bool
                if (!left.value) {
                    return VariableValue.Bool(false)
                }
                return interpretExpression(expression.right)
            }

            is TokenValue.DoublePipe -> {
                val left = interpretExpression(expression.left) as VariableValue.Bool
                if (left.value) {
                    return VariableValue.Bool(true)
                }
                return this.interpretExpression(expression.right)
            }

            else -> {
                val left = interpretExpression(expression.left)
                var right = interpretExpression(expression.right)

                return when (left) {
                    is VariableValue.Number -> {
                        right = right as VariableValue.Number
                        when (expression.operator) {
                            TokenValue.Plus -> VariableValue.Number(left.value + right.value)
                            TokenValue.Minus -> VariableValue.Number(left.value - right.value)
                            TokenValue.Star -> VariableValue.Number(left.value * right.value)
                            TokenValue.Slash -> {
                                check(right.value != 0.0) { throw RuntimeException("Division by zero") }
                                VariableValue.Number(left.value / right.value)
                            }

                            TokenValue.Lt -> VariableValue.Bool(left.value < right.value)
                            TokenValue.Gt -> VariableValue.Bool(left.value > right.value)
                            TokenValue.LtEqual -> VariableValue.Bool(left.value <= right.value)
                            TokenValue.GtEqual -> VariableValue.Bool(left.value >= right.value)
                            TokenValue.DoubleEqual -> VariableValue.Bool(left.value == right.value)
                            TokenValue.ExclEqual -> VariableValue.Bool(left.value != right.value)

                            else -> throw RuntimeException("Invalid operator")
                        }
                    }

                    is VariableValue.String -> {
                        right = right as VariableValue.String
                        when (expression.operator) {
                            TokenValue.DoubleEqual -> VariableValue.Bool(left.value == right.value)
                            TokenValue.ExclEqual -> VariableValue.Bool(left.value != right.value)
                            else -> throw RuntimeException("Invalid operator for string comparison")
                        }
                    }

                    is VariableValue.Bool -> {
                        right = right as VariableValue.Bool
                        when (expression.operator) {
                            TokenValue.DoubleEqual -> VariableValue.Bool(left.value == right.value)
                            TokenValue.ExclEqual -> VariableValue.Bool(left.value != right.value)
                            else -> throw RuntimeException("Invalid operator for boolean comparison")
                        }
                    }
                }
            }
        }
    }

    fun interpretExpression(expression: Expression): VariableValue {
        return when (expression) {
            is Expression.Ident -> {
                for (scope in this.scopes) {
                    if (scope.contains(expression.name)) {
                        return scope[expression.name]!!
                    }
                }
                throw RuntimeException("Unreachable")
            }

            is Expression.NumberLiteral -> VariableValue.Number(expression.value)
            is Expression.StringLiteral -> VariableValue.String(expression.value)
            is Expression.BoolLiteral -> VariableValue.Bool(expression.value)
            is Expression.Unary -> this.interpretUnary(expression)
            is Expression.Binary -> this.interpretBinary(expression)
        }
    }

    fun interpretStatement(statement: Statement) {
        when (statement) {
            is Statement.VariableDeclaration -> {
                val scope = this.scopes[this.scopes.size - 1]
                if (statement.value == null) {
                    scope[statement.name] = VariableValue.Number(0.0)
                } else {
                    scope[statement.name] = interpretExpression(statement.value)
                }
            }

            is Statement.VariableAssignment -> {
                for (scope in this.scopes) {
                    if (scope.contains(statement.name)) {
                        scope[statement.name] = interpretExpression(statement.value)
                        return
                    }
                }
                throw RuntimeException("Ureachable")
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

            is Statement.Block -> {
                this.scopes.add(mutableMapOf())
                for (statement in statement.statements) {
                    this.interpretStatement(statement)
                }
                this.scopes.removeAt(this.scopes.size - 1)
            }

            is Statement.If -> {
                var condition = this.interpretExpression(statement.condition)
                assert(condition is VariableValue.Bool) {
                    throw RuntimeException("Condition must be a boolean")
                }
                condition = condition as VariableValue.Bool

                if (condition.value && statement.thenBranch != null) {
                    this.interpretStatement(statement.thenBranch)
                } else if (!condition.value && statement.elseBranch != null) {
                    this.interpretStatement(statement.elseBranch)
                }
            }
        }
    }

    fun interpretProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.interpretStatement(statement)
        }
    }
}
