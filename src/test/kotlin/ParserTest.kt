import org.junit.Test
import kotlin.test.assertEquals

internal class ParserTest {

    @Test
    fun expression() {
        val textExpr1 = Scanner("1 + 2 * 3").scanTokens().first
        assertEquals(
            Parser(textExpr1).expression(),
            Binary(
                Literal(1.0),
                Token(TokenType.PLUS, "+", null, 1),
                Binary(
                    Literal(2.0),
                    Token(
                        TokenType.STAR, "*", null, 1
                    ),
                    Literal(3.0),
                )
            )
        )
    }
}