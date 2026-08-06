package lang

sealed interface ErrorType {
    val display: String

    data object Syntax : ErrorType {
        override val display: String = "Syntax error"
    }

    data object Type : ErrorType {
        override val display: String = "Type error"
    }

    data object Runtime : ErrorType {
        override val display: String = "Runtime error"
    }
}

sealed interface ErrorMessage {
    val display: String

    // Tokenizer

    data object UnterminatedString : ErrorMessage {
        override val display: String = "Unterminated string"
    }

    data object UnexpectedToken : ErrorMessage {
        override val display: String = "Unexpected token"
    }

    data object InvalidNumber : ErrorMessage {
        override val display: String = "Invalid number"
    }

    data object StringContainsNewline : ErrorMessage {
        override val display: String = "String contains newline character"
    }

    // Parser

    data object UnexpectedEndOfInput : ErrorMessage {
        override val display: String = "Unexpected end of input"
    }

    // Analyzer

    data object AlreadyDeclared : ErrorMessage {
        override val display: String = "Already declared"
    }

    data object Undefined : ErrorMessage {
        override val display: String = "Undefined"
    }

    data object TypeNotSpecified : ErrorMessage {
        override val display: String = "Type not specified"
    }

    data object ConditionMustBeBoolean : ErrorMessage {
        override val display: String = "Condition must be a boolean"
    }

    data object BreakOutsideLoop : ErrorMessage {
        override val display: String = "Break outside loop"
    }

    data object ContinueOutsideLoop : ErrorMessage {
        override val display: String = "Continue outside loop"
    }

    data object FunctionParameterOutsideFunction : ErrorMessage {
        override val display: String = "Function parameter outside function"
    }

    data object ExpectedFunctionParameter : ErrorMessage {
        override val display: String = "Expected function parameter"
    }

    data object ReturnOutsideFunction : ErrorMessage {
        override val display: String = "Return outside function"
    }

    data object ReturnTypeMismatch : ErrorMessage {
        override val display: String = "Return type mismatch"
    }

    data object MissingReturn : ErrorMessage {
        override val display: String = "Missing return"
    }

    data object TypeMismatch : ErrorMessage {
        override val display: String = "Type mismatch"
    }

    // Interpreter

    data object DivisionByZero : ErrorMessage {
        override val display: String = "Division by zero"
    }

}

data class LangError(
    var line: Int,
    var col: Int,
    val type: ErrorType,
    override var message: String,
) : RuntimeException("${type.display}: $message") {
    constructor(
        line: Int,
        col: Int,
        type: ErrorType,
        em: ErrorMessage,
    ) : this(line, col, type, em.display)

    fun construct(source: String): String {
        // example:
        // Error at 3:11: Type mismatch in binary expression
        //
        // 3: x := 123.0 + true
        //                 ^

        val lines = source.split("\n")
        val lineText = lines[line - 1]

        return buildString {
            append("Error at $line:$col: ${type.display}\n\n")
            append("$line: $lineText\n")

            val lineNumLen = line.toString().length
            val offset = lineNumLen + 2 // line number + colon + space
            append(" ".repeat(offset + col - 1))
            append("^ -> $message\n")
        }
    }
}

fun createMessage(source: String, errors: List<LangError>): String {
    return errors.joinToString("\n") { it.construct(source) }
}
