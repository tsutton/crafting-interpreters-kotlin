data class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)

sealed class LoxValue {
    fun typeName() = this::class.simpleName?.substring(3)
}

data class LoxBoolean(val value: Boolean) : LoxValue() {
    override fun toString() = value.toString()
}

data class LoxNumber(val value: Double) : LoxValue() {
    operator fun plus(other: LoxNumber) = LoxNumber(value + other.value)
    override fun toString() = value.toString()

}

data class LoxString(val value: String) : LoxValue() {
    operator fun plus(other: LoxString) = LoxString(value + other.value)
    override fun toString() = value
}

class LoxNil : LoxValue() {
    override fun toString() = "nil"

    override fun equals(other: Any?) = other is LoxNil

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }
}

class Interpreter {
    fun interpret(statements: List<Stmt>) {
        for (statement in statements) {
            visitStatement(statement)
        }
    }

    private fun visitStatement(statement: Stmt) {
        when (statement) {
            is Expression -> visitExpr(statement.expr)
            is Print -> println(visitExpr(statement.expr))
        }
    }

    private fun visitExpr(expr: Expr): LoxValue {
        return when (expr) {
            is Literal -> expr.value
            is Binary -> {
                val lhs = visitExpr(expr.left)
                val rhs = visitExpr(expr.right)
                when (expr.operator.type) {
                    TokenType.STAR -> {
                        if (lhs !is LoxNumber || rhs !is LoxNumber) {
                            throw RuntimeError(
                                expr.operator,
                                "got '*' with invalid types; expected numbers, got ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                        LoxNumber(lhs.value * rhs.value)
                    }
                    TokenType.MINUS -> {
                        if (lhs !is LoxNumber || rhs !is LoxNumber) {
                            throw RuntimeError(
                                expr.operator,
                                "got '-' with invalid types; expected numbers, got ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                        LoxNumber(lhs.value - rhs.value)
                    }
                    TokenType.SLASH -> {
                        if (lhs !is LoxNumber || rhs !is LoxNumber) {
                            throw RuntimeError(
                                expr.operator,
                                "got '/' with invalid types; expected numbers, got ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                        LoxNumber(lhs.value / rhs.value)
                    }
                    TokenType.PLUS -> {
                        if (lhs is LoxNumber && rhs is LoxNumber) {
                            lhs + rhs
                        } else if (lhs is LoxString && rhs is LoxString) {
                            lhs + rhs
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "got '+' with invalid types: ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                    }
                    TokenType.GREATER -> {
                        if (lhs is LoxNumber && rhs is LoxNumber) {
                            LoxBoolean(lhs.value > rhs.value)
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "got '>' with invalid types: ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                    }
                    TokenType.GREATER_EQUAL -> {
                        if (lhs is LoxNumber && rhs is LoxNumber) {
                            LoxBoolean(lhs.value >= rhs.value)
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "got '>=' with invalid types: ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                    }
                    TokenType.LESS -> {
                        if (lhs is LoxNumber && rhs is LoxNumber) {
                            LoxBoolean(lhs.value < rhs.value)
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "got '<' with invalid types: ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                    }
                    TokenType.LESS_EQUAL -> {
                        if (lhs is LoxNumber && rhs is LoxNumber) {
                            LoxBoolean(lhs.value <= rhs.value)
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "got '<=' with invalid types: ${lhs.typeName()} and ${rhs.typeName()}"
                            )
                        }
                    }
                    TokenType.BANG_EQUAL -> LoxBoolean(!isEqual(lhs, rhs))
                    TokenType.EQUAL_EQUAL -> LoxBoolean(isEqual(lhs, rhs))
                    else -> throw RuntimeException("unimplemented binary operator: ${expr.operator.type}")
                }
            }
            is Grouping -> {
                visitExpr(expr.expr)
            }
            is Unary -> {
                val operand = visitExpr(expr.expr)
                when (expr.operator.type) {
                    TokenType.MINUS -> {
                        if (operand is LoxNumber) {
                            LoxNumber(-operand.value)
                        } else {
                            throw RuntimeError(
                                expr.operator,
                                "expected Number argument for unary '-', got ${operand.typeName()}"
                            )
                        }
                    }
                    TokenType.PLUS -> operand
                    TokenType.BANG -> LoxBoolean(!isTruthy(operand))
                    else -> throw RuntimeException("unimplemented unary operator: ${expr.operator.type}")
                }
            }
        }
    }

    companion object {
        private fun isTruthy(value: LoxValue): Boolean {
            if (value is LoxNil) return false
            return if (value is LoxBoolean) value.value else true
        }

        private fun isEqual(left: LoxValue, right: LoxValue): Boolean = left == right
    }
}