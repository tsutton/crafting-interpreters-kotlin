data class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)
data class ReturnException(val returnValue: LoxValue) : RuntimeException()

abstract class LoxValue {
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

interface LoxCallable {
    fun call(interpreter: Interpreter, args: List<LoxValue>): LoxValue
    fun nArgs(): Int
}

class LoxFunction(private val declaration: FunctionDeclaration, private val closure: Environment) : LoxValue(),
    LoxCallable {
    override fun call(interpreter: Interpreter, args: List<LoxValue>): LoxValue {
        val functionScope = Environment(closure)
        declaration.params.forEachIndexed { index, token ->
            functionScope.define(token.lexeme, args[index])
        }
        interpreter.visitBlock(declaration.body, functionScope)
        return LoxNil()
    }

    override fun nArgs() = declaration.params.size

    override fun toString() = "<fn ${declaration.name.lexeme}>"

}

class Environment(private val parent: Environment?) {
    private val values = mutableMapOf<String, LoxValue>()

    fun define(name: String, value: LoxValue) {
        values[name] = value
    }

    fun get(name: String): LoxValue? =
        values[name] ?: parent?.get(name)

    fun assign(name: Token, value: LoxValue) {
        if (values.containsKey(name.lexeme)) {
            values[name.lexeme] = value
        } else if (parent != null) {
            parent.assign(name, value)
        } else {
            throw RuntimeError(name, "Assignment to undefined variable '${name.lexeme}'.")
        }
    }

    private fun ancestor(depth: Int): Environment {
        var ret = this
        for (i in 1..depth) {
            ret = ret.parent!! // yikes
        }
        return ret
    }

    fun getAt(depth: Int, name: Token) = ancestor(depth).values[name.lexeme]
    fun assignAt(depth: Int, name: Token, value: LoxValue) {
        ancestor(depth).values[name.lexeme] = value
    }

    constructor() : this(null)
}

class Interpreter {
    private val globals = Environment()

    init {
        globals.define("clock", object : LoxValue(), LoxCallable {
            override fun call(interpreter: Interpreter, args: List<LoxValue>) =
                LoxNumber(System.currentTimeMillis().toDouble() / 1000.0)

            override fun nArgs() = 0
            override fun toString() = "<native fn clock>"
        })
    }

    private var environment = globals

    fun interpret(statements: List<Stmt>) {
        for (statement in statements) {
            visitStatement(statement)
        }
    }

    private fun visitStatement(statement: Stmt) {
        when (statement) {
            is Expression -> visitExpr(statement.expr)
            is Print -> println(visitExpr(statement.expr))
            is Var -> {
                val value = if (statement.initializer != null) {
                    visitExpr(statement.initializer)
                } else {
                    LoxNil()
                }
                environment.define(statement.name.lexeme, value)
            }
            is Block -> {
                visitBlock(statement.statements, Environment(environment))
            }
            is If -> {
                val predicate = visitExpr(statement.predicate)
                if (isTruthy(predicate)) {
                    visitStatement(statement.ifTrue)
                } else if (statement.ifFalse != null) {
                    visitStatement(statement.ifFalse)
                }
            }
            is While -> {
                while (isTruthy(visitExpr(statement.predicate))) {
                    visitStatement(statement.body)
                }
            }
            is FunctionDeclaration -> {
                environment.define(statement.name.lexeme, LoxFunction(statement, environment))
            }
            is Return -> {
                throw ReturnException(
                    if (statement.value == null) {
                        LoxNil()
                    } else {
                        visitExpr(statement.value)
                    }
                )
            }
        }
    }

    internal fun visitBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment
            for (statement in statements) {
                visitStatement(statement)
            }
        } finally {
            this.environment = previous
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
            is Variable -> {
                val depth = expr.resolutionDepth
                if (depth == null) {
                    globals.get(expr.name.lexeme) ?: throw RuntimeError(
                        expr.name,
                        "Undefined variable '${expr.name.lexeme}'."
                    )
                } else {
                    environment.getAt(depth, expr.name)!!
                }
            }
            is Assign -> {
                val value = visitExpr(expr.value)
                val depth = expr.resolutionDepth
                if (depth == null) {
                    globals.assign(expr.name, value)
                } else {
                    environment.assignAt(depth, expr.name, value)
                }
                value
            }
            is Logical -> {
                val left = visitExpr(expr.left)
                if (expr.operator.type == TokenType.OR && isTruthy(left)) {
                    left
                } else if (expr.operator.type == TokenType.AND && !isTruthy(left)) {
                    left
                } else {
                    visitExpr(expr.right)
                }
            }
            is CallExpr -> {
                val callee = visitExpr(expr.callee)
                if (callee !is LoxCallable) {
                    throw RuntimeError(
                        expr.closeParen,
                        "Can only call functions and classes."
                    )
                }
                if (expr.args.size != callee.nArgs()) {
                    throw RuntimeError(
                        expr.closeParen, "Expected " +
                                callee.nArgs().toString() + " arguments but got " +
                                expr.args.size.toString() + "."
                    )
                }
                val args = expr.args.map(::visitExpr)
                try {
                    callee.call(this, args)
                    return LoxNil()
                } catch (returned: ReturnException) {
                    returned.returnValue
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