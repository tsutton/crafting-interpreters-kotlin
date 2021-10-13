sealed class Expr
data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
data class Grouping(val expr: Expr) : Expr()
data class Literal(val value: LoxValue) : Expr()
data class Unary(val operator: Token, val expr: Expr) : Expr()
data class Variable(val name: Token, var resolutionDepth: Int? = null) : Expr()
data class Assign(val name: Token, val value: Expr, var resolutionDepth: Int? = null) : Expr()
data class Logical(val left: Expr, val operator: Token, val right: Expr) : Expr()
data class CallExpr(val callee: Expr, val args: List<Expr>, val closeParen: Token) : Expr()
data class GetExpr(val base: Expr, val getter: Token) : Expr()
data class SetExpr(val base: Expr, val getter: Token, val value: Expr) : Expr()
data class This(val token: Token, var resolutionDepth: Int? = null) : Expr()

object ExprSexpPrinter {
    fun visit(expr: Expr): String {
        return when (expr) {
            is Literal -> expr.value.toString()
            is Binary -> "(${expr.operator.lexeme} ${visit(expr.left)} ${visit(expr.right)})"
            is Grouping -> "(group ${visit(expr.expr)})"
            is Unary -> "(${expr.operator.lexeme} ${visit(expr.expr)})"
            is Variable -> "(identifier ${expr.name.literal})"
            is Assign -> "(set ${expr.name.lexeme} ${visit(expr.value)})"
            is Logical -> "(${expr.operator.lexeme} ${visit(expr.left)} ${visit(expr.right)})"
            is CallExpr -> {
                val args = expr.args.joinToString(separator = " ", transform = ::visit)
                "(call ${visit(expr.callee)} ${args})"
            }
            is GetExpr -> TODO()
            is SetExpr -> TODO()
            is This -> TODO()
        }
    }
}