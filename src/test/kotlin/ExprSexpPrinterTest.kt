import kotlin.test.Test
import kotlin.test.assertEquals

internal class ExprSexpPrinterTest {
    @Test
    fun testPrinter() {
        val expr = Binary(
            Unary(
                Token(TokenType.MINUS, "-", null, 1),
                Literal(789)
            ),
            Token(TokenType.STAR, "*", null, 1),
            Grouping(Literal(12.34))
        )
        assertEquals(ExprSexpPrinter.visit(expr), "(* (- 789) (group 12.34))")
    }
}