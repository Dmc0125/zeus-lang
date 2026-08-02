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
    data object DoubleEqual : TokenValue
    data object ExclEqual : TokenValue
    data object Lt : TokenValue
    data object Gt : TokenValue
    data object LtEqual : TokenValue
    data object GtEqual : TokenValue
    data object DoubleAmp : TokenValue
    data object DoublePipe : TokenValue

    data object LParen : TokenValue
    data object RParen : TokenValue

    data object LBrace : TokenValue
    data object RBrace : TokenValue

    // keywords
    data class Print(val ln: Boolean) : TokenValue
    data class Type(val type: VariableType) : TokenValue
    data object If : TokenValue
    data object Else : TokenValue
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

    fun peek(n: Int = 0): Char? {
        if (idx + n >= input.length) return null
        return input[idx + n]
    }

    fun isNumeric(ch: Char): Boolean {
        return '0' <= ch && ch <= '9'
    }

    fun isAlpha(ch: Char): Boolean {
        return 'a' <= ch && ch <= 'z' || 'A' <= ch && ch <= 'Z' || ch == '_'
    }

    fun doubleChar(next: Char, single: TokenValue, double: TokenValue) {
        if (peek(1) == next) {
            idx += 1
            tokens.add(Token(double, line, col))
            col += 1
        } else {
            tokens.add(Token(single, line, col))
        }
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
            ':' -> tokens.add(Token(TokenValue.Colon, line, col))
            '(' -> tokens.add(Token(TokenValue.LParen, line, col))
            ')' -> tokens.add(Token(TokenValue.RParen, line, col))
            '{' -> tokens.add(Token(TokenValue.LBrace, line, col))
            '}' -> tokens.add(Token(TokenValue.RBrace, line, col))
            '=' -> doubleChar('=', TokenValue.Equal, TokenValue.DoubleEqual)
            '!' -> doubleChar('=', TokenValue.Excl, TokenValue.ExclEqual)
            '<' -> doubleChar('=', TokenValue.Lt, TokenValue.LtEqual)
            '>' -> doubleChar('=', TokenValue.Gt, TokenValue.GtEqual)
            '&' -> {
                idx += 1
                if (peek() == '&') {
                    tokens.add(Token(TokenValue.DoubleAmp, line, col))
                    col += 1
                    continue
                }
            }

            '|' -> {
                idx += 1
                if (peek() == '|') {
                    tokens.add(Token(TokenValue.DoublePipe, line, col))
                    col += 1
                    continue
                }
            }

            '"' -> {
                idx += 1
                val startIdx = idx

                val startCol = col
                col += 1

                // TODO: Can not have new lines

                while (true) {
                    val next = peek()
                    check(next != null) {
                        throw LangError(line, col, ErrorType.Syntax, ErrorMessage.UnterminatedString)
                    }

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
                        tokens.add(Token(TokenValue.NumberLiteral(value), line, startCol))
                    } catch (e: NumberFormatException) {
                        throw LangError(line, startCol, ErrorType.Syntax, ErrorMessage.InvalidNumber)
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

                        "if" -> tokens.add(Token(TokenValue.If, line, startCol))
                        "else" -> tokens.add(Token(TokenValue.Else, line, startCol))

                        else -> tokens.add(Token(TokenValue.Ident(value), line, startCol))
                    }

                    continue
                }

                throw LangError(line, col, ErrorType.Syntax, ErrorMessage.UnexpectedToken)
            }
        }

        col++
        idx++
    }

    return tokens
}
