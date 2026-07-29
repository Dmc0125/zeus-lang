package lang

open class SyntaxError : RuntimeException {
    constructor(message: String) : super("Syntax error: $message")
    constructor(line: Int, col: Int, message: String) : super("Syntax error at $line:$col: $message")
}

class UnexpectedTokenError(
    line: Int,
    col: Int,
    token: String,
    expected: String? = null,
) : SyntaxError(
    line,
    col,
    buildString {
        append("Unexpected token \"$token\"")
        if (expected != null) {
            append("; expected \"$expected\"")
        }
    }
)

object UnexpectedEndOfInputError : SyntaxError("Unexpected end of input")

class UnterminatedStringError(
    line: Int,
    col: Int,
) : SyntaxError(line, col, "Unterminated string")
