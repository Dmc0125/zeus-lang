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

    fun interpretUnary(expression: Node<ExpressionType.Unary>): VariableValue {
        val value = interpretExpression(expression.type.operand)

        return when (expression.type.operator) {
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

    fun interpretBinary(expression: Node<ExpressionType.Binary>): VariableValue {
        val binary = expression.type
        when (binary.operator) {
            is TokenValue.DoubleAmp -> {
                val left = interpretExpression(binary.left) as VariableValue.Bool
                if (!left.value) {
                    return VariableValue.Bool(false)
                }
                return interpretExpression(binary.right)
            }

            is TokenValue.DoublePipe -> {
                val left = interpretExpression(binary.left) as VariableValue.Bool
                if (left.value) {
                    return VariableValue.Bool(true)
                }
                return interpretExpression(binary.right)
            }

            else -> {
                val left = interpretExpression(binary.left)
                var right = interpretExpression(binary.right)

                return when (left) {
                    is VariableValue.Number -> {
                        right = right as VariableValue.Number
                        when (binary.operator) {
                            TokenValue.Plus -> VariableValue.Number(left.value + right.value)
                            TokenValue.Minus -> VariableValue.Number(left.value - right.value)
                            TokenValue.Star -> VariableValue.Number(left.value * right.value)
                            TokenValue.Slash -> {
                                check(right.value != 0.0) {
                                    throw LangError(
                                        expression.col,
                                        expression.line,
                                        ErrorType.Runtime,
                                        ErrorMessage.DivisionByZero,
                                    )
                                }
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
                        when (binary.operator) {
                            TokenValue.DoubleEqual -> VariableValue.Bool(left.value == right.value)
                            TokenValue.ExclEqual -> VariableValue.Bool(left.value != right.value)
                            else -> throw RuntimeException("Unreachable: Invalid operator for string comparison")
                        }
                    }

                    is VariableValue.Bool -> {
                        right = right as VariableValue.Bool
                        when (binary.operator) {
                            TokenValue.DoubleEqual -> VariableValue.Bool(left.value == right.value)
                            TokenValue.ExclEqual -> VariableValue.Bool(left.value != right.value)
                            else -> throw RuntimeException("Unreachable: Invalid operator for boolean comparison")
                        }
                    }
                }
            }
        }
    }

    fun interpretExpression(expression: Node<ExpressionType>): VariableValue {
        return when (expression.type) {
            is ExpressionType.Ident -> {
                val ident = expression.type
                for (scope in this.scopes) {
                    if (scope.contains(ident.name)) {
                        return scope[ident.name]!!
                    }
                }
                throw RuntimeException("Unreachable: variable undefined: ${ident.name}")
            }

            is ExpressionType.NumberLiteral -> VariableValue.Number(expression.type.value)
            is ExpressionType.StringLiteral -> VariableValue.String(expression.type.value)
            is ExpressionType.BoolLiteral -> VariableValue.Bool(expression.type.value)
            is ExpressionType.Unary -> this.interpretUnary(expression as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.interpretBinary(expression as Node<ExpressionType.Binary>)
        }
    }

    var insideLoop = false
    var shouldBreak = false
    var shouldContinue = false

    fun interpretBlock(statement: Statement) {
        assert(statement.type is StatementType.Block) {
            "Expected block statement, got ${statement.type}"
        }

        val block = statement.type as StatementType.Block

        this.scopes.add(mutableMapOf())
        for (statement in block.statements) {
            this.interpretStatement(statement)
            if (this.insideLoop && (this.shouldBreak || this.shouldContinue)) {
                break
            }
        }
        this.scopes.removeAt(this.scopes.size - 1)
    }

    fun interpretStatement(statement: Statement) {
        when (statement.type) {
            is StatementType.VariableDeclaration -> {
                val decl = statement.type
                val scope = this.scopes[this.scopes.size - 1]
                if (decl.value == null) {
                    scope[decl.name] = VariableValue.Number(0.0)
                } else {
                    scope[decl.name] = interpretExpression(decl.value)
                }
            }

            is StatementType.VariableAssignment -> {
                val assign = statement.type
                for (scope in this.scopes) {
                    if (scope.contains(assign.name)) {
                        scope[assign.name] = interpretExpression(assign.value)
                        return
                    }
                }
                throw RuntimeException("Unreachable: variable undefined: ${assign.name}")
            }

            is StatementType.Print -> {
                val print = statement.type
                if (this.printer != null) {
                    val text = interpretExpression(print.expression).toString()
                    if (print.ln) {
                        this.printer.println(text)
                    } else {
                        this.printer.print(text)
                    }
                }
            }

            is StatementType.Block -> this.interpretBlock(statement)

            is StatementType.If -> {
                val ifStmt = statement.type
                var condition = this.interpretExpression(ifStmt.condition)
                assert(condition is VariableValue.Bool) {
                    throw RuntimeException("Unreachable: Condition must be a boolean")
                }
                condition = condition as VariableValue.Bool

                if (condition.value) {
                    this.interpretBlock(ifStmt.thenBranch)
                } else if (!condition.value && ifStmt.elseBranch != null) {
                    this.interpretStatement(ifStmt.elseBranch)
                }
            }

            is StatementType.For -> {
                val forStmt = statement.type

                while (true) {
                    this.insideLoop = true

                    var cond = this.interpretExpression(forStmt.condition)
                    assert(cond is VariableValue.Bool) {
                        "Condition must be bool"
                    }
                    cond = cond as VariableValue.Bool

                    if (!cond.value) {
                        break
                    }

                    this.interpretBlock(forStmt.body)

                    if (this.shouldBreak) {
                        this.shouldBreak = false
                        break
                    }
                    if (this.shouldContinue) {
                        this.shouldContinue = false
                    }
                }

                this.insideLoop = false
            }

            is StatementType.CFor -> {
                val stmt = statement.type

                if (stmt.init != null) {
                    this.interpretStatement(stmt.init)
                }

                while (true) {
                    this.insideLoop = true

                    if (stmt.condition != null) {
                        var cond = this.interpretExpression(stmt.condition)
                        assert(cond is VariableValue.Bool) {
                            "Condition must be bool"
                        }
                        cond = cond as VariableValue.Bool
                        if (!cond.value) {
                            break
                        }
                    }

                    this.interpretBlock(stmt.body)

                    if (this.shouldBreak) {
                        this.shouldBreak = false
                        break
                    }
                    if (this.shouldContinue) {
                        this.shouldContinue = false
                    }

                    if (stmt.update != null) {
                        this.interpretStatement(stmt.update)
                    }
                }

                this.insideLoop = false
            }

            StatementType.Break -> {
                this.shouldBreak = true
            }

            StatementType.Continue -> {
                this.shouldContinue = true
            }
        }
    }

    fun interpretProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.interpretStatement(statement)
        }
    }
}
