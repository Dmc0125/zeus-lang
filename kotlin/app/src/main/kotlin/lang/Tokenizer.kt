package lang

class TokenizerError(
    val line: Int,
    val col: Int,
    message: String,
) : RuntimeException("Tokenizer error at $line:$col: $message")

sealed interface TokenValue {
    // literal
    data class Number(val value: Double) : TokenValue
    data class Ident(val name: String) : TokenValue

    // operator
    data object Plus : TokenValue
    data object Minus : TokenValue
    data object Star : TokenValue
    data object Slash : TokenValue
    data object ColonEqual : TokenValue
    data object Semicolon : TokenValue

    // keywords
    data class Print(val ln: Boolean) : TokenValue
}

data class Token(
    val value: TokenValue,
    val line: Int,
    val col: Int,
)

fun tokenizerRun(input: String): List<Token> {
    val tokens = mutableListOf<Token>()

    var line = 1
    var col = 1
    var idx = 0

    fun peek(): Char? {
        if (idx >= input.length) return null
        return input[idx]
    }

    fun isNumeric(ch: Char): Boolean {
        return '0' <= ch && ch <= '9'
    }

    fun isAlpha(ch: Char): Boolean {
        return 'a' <= ch && ch <= 'z' || 'A' <= ch && ch <= 'Z' || ch == '_'
    }

    while (idx < input.length) {
        var ch = input[idx]

        when (ch) {
            '\n' -> {
                line += 1
                col = 1
                idx += 1
                continue
            }

            ' ', '\t', '\r' -> {
                col += 1
                idx += 1
                continue
            }

            // singlechar
            '+' -> tokens.add(Token(TokenValue.Plus, line, col))
            '-' -> tokens.add(Token(TokenValue.Minus, line, col))
            '*' -> tokens.add(Token(TokenValue.Star, line, col))
            '/' -> tokens.add(Token(TokenValue.Slash, line, col))
            ';' -> tokens.add(Token(TokenValue.Semicolon, line, col))

            // multichar
            ':' -> {
                idx += 1

                val next = peek()
                if (next == '=') {
                    tokens.add(Token(TokenValue.ColonEqual, line, col))
                    col += 2
                    idx += 1
                } else {
                    // Colon
                    col += 1
                }
                continue
            }

            else -> {
                if (isNumeric(ch)) {
                    val startIdx = idx
                    val startCol = col

                    idx += 1
                    col += 1

                    while (idx < input.length && (isNumeric(input[idx]) || input[idx] == '.')) {
                        idx += 1
                        col += 1
                    }

                    val valueStr = input.substring(startIdx, idx)
                    try {
                        val value = valueStr.toDouble()
                        tokens.add(Token(TokenValue.Number(value), line, startCol))
                    } catch (e: NumberFormatException) {
                        throw TokenizerError(line, col, "Invalid number: ${valueStr}")
                    }
                    continue
                }

                if (isAlpha(ch)) {
                    val startIdx = idx
                    val startCol = col

                    idx += 1
                    col += 1

                    while (idx < input.length && (isAlpha(input[idx]) || isNumeric(input[idx]))) {
                        idx += 1
                        col += 1
                    }

                    val value = input.substring(startIdx, idx)

                    when (value) {
                        "print" -> tokens.add(Token(TokenValue.Print(false), line, startCol))
                        "println" -> tokens.add(Token(TokenValue.Print(true), line, startCol))
                        else -> tokens.add(Token(TokenValue.Ident(value), line, startCol))
                    }

                    continue
                }

                throw TokenizerError(line, col, "Unexpected token: ${ch}")
            }
        }

        col++
        idx++
    }

    return tokens
}
