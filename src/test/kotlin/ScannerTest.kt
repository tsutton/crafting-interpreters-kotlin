import kotlin.test.Test
import kotlin.test.assertContentEquals

internal class ScannerTest {
    @Test
    fun testScan() {
        val scanner = Scanner(
            """// this is a comment
(( )){} // grouping stuff
!*+-/=<> <= == // operators
1.234
var var_var_1
"""
        )
        val (tokens, errors) = scanner.scanTokens()
        assert(errors.isEmpty())
        val expectedTokens = listOf(
            Token(TokenType.LEFT_PAREN, "(", null, 2),
            Token(TokenType.LEFT_PAREN, "(", null, 2),
            Token(TokenType.RIGHT_PAREN, ")", null, 2),
            Token(TokenType.RIGHT_PAREN, ")", null, 2),
            Token(TokenType.LEFT_BRACE, "{", null, 2),
            Token(TokenType.RIGHT_BRACE, "}", null, 2),
            Token(TokenType.BANG, "!", null, 3),
            Token(TokenType.STAR, "*", null, 3),
            Token(TokenType.PLUS, "+", null, 3),
            Token(TokenType.MINUS, "-", null, 3),
            Token(TokenType.SLASH, "/", null, 3),
            Token(TokenType.EQUAL, "=", null, 3),
            Token(TokenType.LESS, "<", null, 3),
            Token(TokenType.GREATER, ">", null, 3),
            Token(TokenType.LESS_EQUAL, "<=", null, 3),
            Token(TokenType.EQUAL_EQUAL, "==", null, 3),
            Token(TokenType.NUMBER, "1.234", 1.234, 4),
            Token(TokenType.VAR, "var", null, 5),
            Token(TokenType.IDENTIFIER, "var_var_1", null, 5),
            Token(TokenType.EOF, "", null, 6)
        )
        assertContentEquals(expectedTokens, tokens)
    }
}