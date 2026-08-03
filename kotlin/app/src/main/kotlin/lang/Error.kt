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

    // Parser
    data object UnexpectedEndOfInput : ErrorMessage {
        override val display: String = "Unexpected end of input"
    }

    // Analyzer
    data object UndefinedVariable : ErrorMessage {
        override val display: String = "Undefined variable"
    }

    data object VariableAlreadyDefined : ErrorMessage {
        override val display: String = "Variable already defined"
    }

    data object TypeNotSpecified : ErrorMessage {
        override val display: String = "Type not specified"
    }

    data object ConditionMustBeBoolean : ErrorMessage {
        override val display: String = "Condition must be a boolean"
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
