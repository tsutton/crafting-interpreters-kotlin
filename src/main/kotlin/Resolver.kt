import java.util.*


class ResolutionError(val line: Int, override val message: String?) : RuntimeException(message)

class Resolver {
    // Having this be a map of booleans, and splitting declare/define as two functions instead of one
    // is towards handling the case
    //   ```lox
    //   var x = 2
    //   {
    //      var x = x + 3
    //   }
    //   ```
    // In this case, the book says, don't allow shadowing via self-reference, instead just error
    // This seems to be consistent with how Javascript using `let` works, for example.
    // To handle this, we have var statements first set the variable as "declared" (put false in the map)
    // then resolve the RHS, then afterwards set the variable as "defined" (change it to true in the map).
    // This lets us detect, while resolving the RHS, we're in the middle of declaring x, thus using it is an error.
    // Also: Lox treats the global scope specially, so we initialize the stack to be *empty*
    // rather than initializing it to be the global scope.
    // The interpreter handles the globals itself.
    private val scopes: Stack<MutableMap<String, Boolean>> = Stack()

    private var insideFunction = false
    private var insideClass = false

    private fun beginScope() {
        scopes.push(mutableMapOf())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        val scope = if (scopes.empty()) {
            return
        } else {
            scopes.peek()
        }
        if (scope.containsKey(name.lexeme)) {
            throw ResolutionError(name.line, "redeclaration of variable '${name.lexeme}'")
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        val scope = if (scopes.empty()) {
            return
        } else {
            scopes.peek()
        }
        scope[name.lexeme] = true
    }

    private fun depth(name: String): Int? {
        for (i in scopes.size - 1 downTo 0) {
            if (scopes[i].containsKey(name)) {
                return scopes.size - 1 - i
            }
        }
        return null
    }

    fun visitStatement(stmt: Stmt) {
        when (stmt) {
            is Var -> {
                declare(stmt.name)
                if (stmt.initializer != null) {
                    visitExpr(stmt.initializer)
                }
                // since `var x;` (no initializer) defines x as nil, it's always defined now
                define(stmt.name)
            }
            is FunctionDeclaration -> {
                declare(stmt.name)
                define(stmt.name)
                resolveFunction(stmt)
            }
            is Block -> {
                beginScope()
                for (bodyStmt in stmt.statements) {
                    visitStatement(bodyStmt)
                }
                endScope()
            }
            is If -> {
                visitExpr(stmt.predicate)
                visitStatement(stmt.ifTrue)
                if (stmt.ifFalse != null) {
                    visitStatement(stmt.ifFalse)
                }
            }
            is Expression -> visitExpr(stmt.expr)
            is Print -> visitExpr(stmt.expr)
            is Return -> {
                if (!insideFunction) {
                    throw ResolutionError(stmt.returnToken.line, "can't return unless inside a function")
                }
                if (stmt.value != null) {
                    visitExpr(stmt.value)
                }
            }
            is While -> {
                visitExpr(stmt.predicate)
                visitStatement(stmt.body)
            }
            is ClassStatement -> {
                declare(stmt.name)
                define(stmt.name)
                beginScope()
                scopes.peek()["this"] = true
                val originalInsideClass = insideClass
                insideClass = true
                for (method in stmt.methods) {
                    resolveFunction(method)
                }
                insideClass = originalInsideClass
                endScope()
            }
        }
    }

    private fun resolveFunction(declaration: FunctionDeclaration) {
        val originalInsideFunction = insideFunction
        insideFunction = true
        beginScope()
        for (param in declaration.params) {
            declare(param)
            define(param)
        }
        for (bodyStmt in declaration.body) {
            visitStatement(bodyStmt)
        }
        insideFunction = originalInsideFunction
        endScope()
    }

    private fun visitExpr(expr: Expr) {
        when (expr) {
            is Variable -> {
                val name = expr.name.lexeme
                if (!scopes.empty() && scopes.peek()[name] == false) {
                    // I forget how to signal an error
                    throw ResolutionError(expr.name.line, "We can't reference an undefined var")
                }
                expr.resolutionDepth = depth(name)
                // if depth was null - not found - we might up with a runtime error, if there isn't a global
            }
            is Assign -> {
                // The order of visitExpr and setting depth shouldn't matter here
                visitExpr(expr.value)
                expr.resolutionDepth = depth(expr.name.lexeme)
            }
            is Binary -> {
                visitExpr(expr.left)
                visitExpr(expr.right)
            }
            is CallExpr -> {
                visitExpr(expr.callee)
                for (param in expr.args) {
                    visitExpr(param)
                }
            }
            is Grouping -> visitExpr(expr.expr)
            is Literal -> return
            is Logical -> {
                visitExpr(expr.left)
                visitExpr(expr.right)
            }
            is Unary -> visitExpr(expr.expr)
            is GetExpr -> {
                visitExpr(expr.base)
            }
            is SetExpr -> {
                visitExpr(expr.base)
                visitExpr(expr.value)
            }
            is This -> {
                if (!insideClass) {
                    throw ResolutionError(expr.token.line, "'this' is only available inside methods on classes.")
                }
                expr.resolutionDepth = depth(expr.token.lexeme)
            }
        }
    }

}