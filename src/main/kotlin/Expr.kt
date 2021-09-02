sealed class Expr
data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
data class Grouping(val expr: Expr) : Expr()
data class Literal(val value: LoxValue) : Expr()
data class Unary(val operator: Token, val expr: Expr) : Expr()
data class Variable(val name: Token) : Expr()

object ExprSexpPrinter {
    fun visit(expr: Expr): String {
        return when (expr) {
            is Literal -> expr.value.toString()
            is Binary -> "(${expr.operator.lexeme} ${visit(expr.left)} ${visit(expr.right)})"
            is Grouping -> "(group ${visit(expr.expr)})"
            is Unary -> "(${expr.operator.lexeme} ${visit(expr.expr)})"
            is Variable -> "(identifier ${expr.name.literal})"
        }
    }
}