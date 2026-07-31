package lang

sealed interface VariableType {
    data object Number : VariableType
    data object String : VariableType
    data object Bool : VariableType
}

sealed interface TokenValue {
    // literal
    data class NumberLiteral(val value: Double) : TokenValue
    data class StringLiteral(val value: kotlin.String) : TokenValue
    data class BoolLiteral(val value: Boolean) : TokenValue
    data class Ident(val name: kotlin.String) : TokenValue

    // operator
    data object Plus : TokenValue
    data object Minus : TokenValue
    data object Star : TokenValue
    data object Slash : TokenValue
    data object Colon : TokenValue
    data object Semicolon : TokenValue
    data object Equal : TokenValue
    data object Excl : TokenValue

    data object LParen : TokenValue
    data object RParen : TokenValue

    // keywords
    data class Print(val ln: Boolean) : TokenValue
    data class Type(val type: VariableType) : TokenValue
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
            '=' -> tokens.add(Token(TokenValue.Equal, line, col))
            ':' -> tokens.add(Token(TokenValue.Colon, line, col))
            '!' -> tokens.add(Token(TokenValue.Excl, line, col))
            '(' -> tokens.add(Token(TokenValue.LParen, line, col))
            ')' -> tokens.add(Token(TokenValue.RParen, line, col))

            '"' -> {
                idx += 1
                val startIdx = idx

                val startCol = col
                col += 1

                while (true) {
                    val next = peek()
                    check(next != null) { throw UnterminatedStringError(line, startCol) }

                    if (next == '"') {
                        break
                    }

                    idx += 1
                    col += 1
                }

                val value = input.substring(startIdx, idx)
                idx += 1
                col += 1

                tokens.add(Token(TokenValue.StringLiteral(value), line, startCol))
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
                        tokens.add(Token(TokenValue.NumberLiteral(value), line, startCol))
                    } catch (e: NumberFormatException) {
                        throw SyntaxError(line, col, "Invalid number: ${valueStr}")
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

                        "number" -> tokens.add(Token(TokenValue.Type(VariableType.Number), line, startCol))
                        "string" -> tokens.add(Token(TokenValue.Type(VariableType.String), line, startCol))
                        "bool" -> tokens.add(Token(TokenValue.Type(VariableType.Bool), line, startCol))

                        "true" -> tokens.add(Token(TokenValue.BoolLiteral(true), line, startCol))
                        "false" -> tokens.add(Token(TokenValue.BoolLiteral(false), line, startCol))

                        else -> tokens.add(Token(TokenValue.Ident(value), line, startCol))
                    }

                    continue
                }

                throw SyntaxError(line, col, "Unexpected token: ${ch}")
            }
        }

        col++
        idx++
    }

    return tokens
}
