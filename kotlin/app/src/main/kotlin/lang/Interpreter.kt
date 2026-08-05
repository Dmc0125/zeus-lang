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

class Environment<T>(val parent: Environment<T>? = null) {
    val scope = mutableMapOf<String, T>()

    fun get(name: String): T? {
        val value = scope[name]
        if (value == null && parent != null) {
            return parent.get(name)
        }
        return value
    }

    fun declare(name: String, value: T): ErrorMessage? {
        if (scope.contains(name)) {
            return ErrorMessage.AlreadyDeclared
        }
        scope[name] = value
        return null
    }

    fun assign(name: String, value: T): ErrorMessage? {
        if (!scope.contains(name)) {
            if (parent != null) {
                return parent.assign(name, value)
            }
            return ErrorMessage.Undefined
        }
        scope[name] = value
        return null
    }

    fun child(): Environment<T> {
        return Environment(this)
    }
}

data class Function(
    val params: List<FunctionParameter>,
    val body: StatementType.Block,
    val funcEnv: Environment<Function>,
)

class Return(val value: VariableValue) : Throwable("")
class Break() : Throwable("")
class Continue() : Throwable("")

class Interpreter(
    val printer: BufferedPrinter? = null,
) {
    var varEnv = Environment<VariableValue>()
    var funcEnv = Environment<Function>()

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

    fun interpretExpression(expr: Node<ExpressionType>): VariableValue {
        return when (expr.type) {
            is ExpressionType.Ident -> {
                val ident = expr.type
                val value = this.varEnv.get(ident.name)
                if (value == null) {
                    throw LangError(expr.line, expr.col, ErrorType.Type, ErrorMessage.Undefined)
                }
                value
            }

            is ExpressionType.NumberLiteral -> VariableValue.Number(expr.type.value)
            is ExpressionType.StringLiteral -> VariableValue.String(expr.type.value)
            is ExpressionType.BoolLiteral -> VariableValue.Bool(expr.type.value)
            is ExpressionType.Unary -> this.interpretUnary(expr as Node<ExpressionType.Unary>)
            is ExpressionType.Binary -> this.interpretBinary(expr as Node<ExpressionType.Binary>)
            is ExpressionType.Call -> {
                val call = expr.type

                var func = this.funcEnv.get(call.name)
                assert(func != null) { "Function not declared" }

                func = func!!

                val prevVarEnv = this.varEnv
                val prevFuncEnv = this.funcEnv

                this.funcEnv = func.funcEnv
                this.varEnv = Environment<VariableValue>()
                for ((i, callArg) in call.args.withIndex()) {
                    val sigParam = func.params[i]
                    val value = this.interpretExpression(callArg)
                    this.varEnv.assign(sigParam.name, value)
                }

                var ret: VariableValue? = null

                try {
                    for (stmt in func.body.statements) {
                        this.interpretStatement(stmt)
                    }
                } catch (e: Return) {
                    ret = e.value
                }

                this.varEnv = prevVarEnv
                this.funcEnv = prevFuncEnv

                check(ret != null) { "unimplemented" }

                ret
            }
        }
    }

    fun interpretBlock(statement: Statement) {
        assert(statement.type is StatementType.Block) {
            "Expected block statement, got ${statement.type}"
        }

        val block = statement.type as StatementType.Block

        this.varEnv = this.varEnv.child()
        this.funcEnv = this.funcEnv.child()

        for (statement in block.statements) {
            this.interpretStatement(statement)
        }

        this.varEnv = this.varEnv.parent!!
        this.funcEnv = this.funcEnv.parent!!
    }

    fun interpretStatement(statement: Statement) {
        when (statement.type) {
            is StatementType.VariableDeclaration -> {
                val decl = statement.type
                var value: VariableValue? = null

                if (decl.value == null) {
                    value = decl.type!!.defaultValue()
                } else {
                    value = this.interpretExpression(decl.value)
                }

                this.varEnv.declare(decl.name, value!!)?.let {
                    throw RuntimeException(it.display)
                }
            }

            is StatementType.VariableAssignment -> {
                val assign = statement.type
                val value = this.interpretExpression(assign.value)
                this.varEnv.assign(assign.name, value)?.let {
                    throw RuntimeException(it.display)
                }
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
                    if (forStmt.condition != null) {
                        var cond = this.interpretExpression(forStmt.condition)
                        assert(cond is VariableValue.Bool) {
                            "Condition must be bool"
                        }
                        cond = cond as VariableValue.Bool
                        if (!cond.value) {
                            break
                        }
                    }

                    try {
                        this.interpretBlock(forStmt.body)
                    } catch (e: Break) {
                        break
                    } catch (e: Continue) {
                    }
                }
            }

            is StatementType.CFor -> {
                val stmt = statement.type

                this.varEnv = this.varEnv.child()

                if (stmt.init != null) {
                    this.interpretStatement(stmt.init)
                }

                while (true) {
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

                    try {
                        this.interpretBlock(stmt.body)
                    } catch (e: Break) {
                        break
                    } catch (e: Continue) {
                    }

                    if (stmt.update != null) {
                        this.interpretStatement(stmt.update)
                    }
                }

                this.varEnv = this.varEnv.parent!!
                this.funcEnv = this.funcEnv.parent!!
            }

            StatementType.Break -> throw Break()
            StatementType.Continue -> throw Continue()

            is StatementType.FunctionDeclaration -> {
                val decl = statement.type
                val funcEnv = this.funcEnv
                val body = decl.body.type as StatementType.Block

                val func = Function(
                    params = decl.params,
                    body = body,
                    funcEnv = funcEnv.child(),
                )

                funcEnv.declare(decl.name, func)?.let {
                    throw LangError(statement.line, statement.col, ErrorType.Syntax, it)
                }
            }

            is StatementType.Return -> {
                val ret = this.interpretExpression(statement.type.value)
                throw Return(ret)
            }
        }
    }

    fun interpretProgram(statements: List<Statement>) {
        for (statement in statements) {
            this.interpretStatement(statement)
        }
    }
}
