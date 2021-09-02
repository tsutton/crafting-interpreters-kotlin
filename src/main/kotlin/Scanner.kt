import TokenType.*

sealed class ScanResult
data class ScanSuccess(val token: Token) : ScanResult()
data class ScanError(val line: Int, val message: String) : ScanResult()

class Scanner(private val source: String) {
    private var start: Int = 0
    private var current: Int = 0
    private var line: Int = 1

    fun scanTokens(): Pair<List<Token>, List<ScanError>> {
        val tokens = mutableListOf<Token>()
        val errors = mutableListOf<ScanError>()

        while (!isAtEnd()) {
            when (val result = scanToken()) {
                is ScanError -> errors.add(result)
                is ScanSuccess -> tokens.add(result.token)
            }
            start = current
        }
        val last = tokens.lastOrNull()
        if (last == null || last.type != EOF) {
            tokens.add(tokenAtPoint(EOF))
        }

        return tokens to errors
    }

    /**
     * Advances the scanner and returns the next token or next error.
     *
     * If not already at end of input, scanToken will always make progress (i.e. move forward along the input).
     * If called when the scanner is already at the end of input, it will always return success with an EOF token.
     * Thus, to avoid extraneous EOFs, a consumer should check if the return value was EOF and stop calling.
     * scanToken() may also return EOF in cases when not already at end of input, if there are no more tokens (that is,
     * if there are only comments and whitespace remaining in input).
     *
     * scanToken might advance start, but makes no promises. The caller should set start = current after calling this.
     */
    private tailrec fun scanToken(): ScanResult {
        if (isAtEnd()) return ScanSuccess(Token(EOF, "", null, line))

        val token: Token = when (val c = advance()) {
            '(' -> tokenAtPoint(LEFT_PAREN)
            ')' -> tokenAtPoint(RIGHT_PAREN)
            '{' -> tokenAtPoint(LEFT_BRACE)
            '}' -> tokenAtPoint(RIGHT_BRACE)
            ',' -> tokenAtPoint(COMMA)
            '.' -> tokenAtPoint(DOT)
            '-' -> tokenAtPoint(MINUS)
            '+' -> tokenAtPoint(PLUS)
            ';' -> tokenAtPoint(SEMICOLON)
            '*' -> tokenAtPoint(STAR)
            '!' -> tokenAtPoint(
                if (match('=')) BANG_EQUAL else BANG
            )
            '=' -> tokenAtPoint(
                if (match('=')) EQUAL_EQUAL else EQUAL
            )
            '>' -> tokenAtPoint(
                if (match('=')) GREATER_EQUAL else GREATER
            )
            '<' -> tokenAtPoint(
                if (match('=')) LESS_EQUAL else LESS
            )
            '/' -> {
                if (match('/')) {
                    // advance past comment and return next token
                    while (peek() != '\n' && !isAtEnd()) advance()
                    start = current
                    return scanToken()
                } else {
                    tokenAtPoint(SLASH)
                }
            }
            ' ', '\t', '\r' -> {
                start++
                return scanToken()
            }
            '\n' -> {
                start++
                line++
                return scanToken()
            }
            '"' -> return scanString()
            in '0'..'9' -> return scanNumber()
            else -> {
                if (canBeginIdentifier(c)) {
                    return scanIdentifier()
                }
                return ScanError(line, "Unexpected character '$c'")
            }
        }
        return ScanSuccess(token)
    }

    private fun tokenAtPoint(type: TokenType, literal: Any?): Token {
        val text = source.substring(start, current)
        return Token(type, text, literal, line)
    }

    private fun tokenAtPoint(type: TokenType) = tokenAtPoint(type, null)

    private fun isAtEnd() = current >= source.length

    private fun advance() = source[current++]

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun peek() = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext() = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    /**
     * scanString attempts to scan a string literal.
     * It assumes 'start' points to the double quote that begins the literal.
     */
    // Aside: It seems like Lox doesn't allow double quotes in string literals, not sure if it's possible to
    // have a double-quote in a string at all.
    // in fact, there are no escape sequences in the string! I guess escape sequences aren't much different
    // from supporting both = and == as separate tokens.
    private fun scanString(): ScanResult {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            return ScanError(line, "Unterminated string.")
        }

        // The closing ".
        advance()

        // Trim the surrounding quotes.
        val value = source.substring(start + 1, current - 1)
        return ScanSuccess(tokenAtPoint(STRING, value))
    }

    /**
     * scanString attempts to scan a numeric literal
     * It assumes 'start' points to the first character of the literal.
     */
    private fun scanNumber(): ScanResult {
        while (peek() in '0'..'9') advance()
        // if there's a '.' with digits after it, keep going
        if (peek() == '.' && peekNext() in '0'..'9') {
            advance()
            while (peek() in '0'..'9') advance()
        }
        return ScanSuccess(tokenAtPoint(NUMBER, source.substring(start until current).toDouble()))
    }

    /**
     * scanIdentifier attempts to scan an identifier (including reserved words)
     * It assumes 'start' points to the first character
     */
    private fun scanIdentifier(): ScanResult {
        while (canBeInIdentifier(peek())) advance()
        val identifier = source.substring(start until current)
        val keywordType = keywords[identifier]
        return if (keywordType == null) {
            ScanSuccess(tokenAtPoint(IDENTIFIER))
        } else {
            ScanSuccess(tokenAtPoint(keywordType))
        }
    }


    companion object {
        private fun canBeginIdentifier(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z')
        private fun canBeInIdentifier(c: Char) = canBeginIdentifier(c) || (c == '_') || (c in '0'..'9')
        private val keywords = mapOf(
            "and" to AND,
            "class" to CLASS,
            "else" to ELSE,
            "false" to FALSE,
            "for" to FOR,
            "fun" to FUN,
            "if" to IF,
            "nil" to NIL,
            "or" to OR,
            "print" to PRINT,
            "return" to RETURN,
            "super" to SUPER,
            "this" to THIS,
            "true" to TRUE,
            "var" to VAR,
            "while" to WHILE,
        )
    }
}