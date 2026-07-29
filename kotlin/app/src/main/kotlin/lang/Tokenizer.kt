package lang

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
    data object Print : TokenValue
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

    fun isKeyword(keyword: String): Boolean {
        if (idx + keyword.length > input.length) return false

        for (i in 0 until keyword.length) {
            if (input[idx + i] != keyword[i]) return false
        }

        if (idx + keyword.length == input.length) {
            return true
        }
        if (!isAlpha(input[idx + keyword.length])) {
            return true
        }

        return false
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

            '+' -> tokens.add(Token(TokenValue.Plus, line, col))
            '-' -> tokens.add(Token(TokenValue.Minus, line, col))
            '*' -> tokens.add(Token(TokenValue.Star, line, col))
            '/' -> tokens.add(Token(TokenValue.Slash, line, col))

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

            ';' -> tokens.add(Token(TokenValue.Semicolon, line, col))

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

                    val value = input.substring(startIdx, idx).toDouble()
                    tokens.add(Token(TokenValue.Number(value), line, startCol))
                    continue
                }

                if (isKeyword("print")) {
                    tokens.add(Token(TokenValue.Print, line, col))
                    idx += 5
                    col += 5
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
                    tokens.add(Token(TokenValue.Ident(value), line, startCol))
                    continue
                }
            }
        }

        col++
        idx++
    }

    return tokens
}
