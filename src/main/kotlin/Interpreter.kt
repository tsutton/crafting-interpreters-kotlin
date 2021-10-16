data class RuntimeError(val token: Token, override val message: String) : RuntimeException(message)
data class ReturnException(val returnValue: LoxValue) : RuntimeException()

class Environment(internal val parent: Environment?) {
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

    fun getAt(depth: Int, name: String) = ancestor(depth).values[name]
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
            is ClassStatement -> {
                var superclass: LoxClass? = null
                if (statement.superclass != null) {
                    val superclassUnchecked = visitExpr(statement.superclass)
                    if (superclassUnchecked is LoxClass) {
                        superclass = superclassUnchecked
                    } else {
                        throw RuntimeError(
                            statement.superclass.name,
                            "can only inherit from classes, got type ${superclassUnchecked.typeName()}"
                        )
                    }
                    environment = Environment(environment)
                    // we put "super" as the value of the superclass, but only long enough to set up the methods
                    environment.define("super", superclass)
                }
                val methods = statement.methods.associate {
                    it.name.lexeme to LoxFunction(it, environment)
                }
                if (superclass != null)
                    environment = environment.parent!!
                environment.define(statement.name.lexeme, LoxClass(statement.name.lexeme, methods, superclass))
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
            is Variable -> resolveVariable(expr.name, expr.resolutionDepth)
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
                return callee.call(this, args)
            }
            is GetExpr -> {
                val base = visitExpr(expr.base)
                if (base !is LoxInstance) {
                    throw RuntimeError(expr.getter, "can only access properties of class instances")
                }
                return base.get(expr.getter)
            }
            is SetExpr -> {
                val base = visitExpr(expr.base)
                val value = visitExpr(expr.value)
                if (base !is LoxInstance) {
                    throw RuntimeError(expr.getter, "can only access properties of class instances")
                }
                base.set(expr.getter, value)
                return value
            }
            is This -> resolveVariable(expr.token, expr.resolutionDepth)
            is SuperExpr -> {
                val superclass = environment.getAt(expr.resolutionDepth!!, "super")
                val thisValue = environment.getAt(expr.resolutionDepth!! - 1, "this")
                (superclass as LoxClass).getMethod(expr.method.lexeme)?.bind(thisValue as LoxInstance)
                    ?: throw RuntimeError(
                        expr.token,
                        "method '${expr.method}' not found on super"
                    )
            }
        }
    }

    private fun resolveVariable(variableToken: Token, depth: Int?): LoxValue {
        return if (depth == null) {
            globals.get(variableToken.lexeme) ?: throw RuntimeError(
                variableToken,
                "Undefined variable '${variableToken.lexeme}'."
            )
        } else {
            environment.getAt(depth, variableToken.lexeme)!!
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