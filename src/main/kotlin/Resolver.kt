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
    private var insideFunction: Boolean = false

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
                return i
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
                val originalInsideFunction = insideFunction
                insideFunction = true
                beginScope()
                for (param in stmt.params) {
                    declare(param)
                    define(param)
                }
                for (bodyStmt in stmt.body) {
                    visitStatement(bodyStmt)
                }
                insideFunction = originalInsideFunction
                endScope()
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
        }
    }

    private fun visitExpr(expr: Expr) {
        when (expr) {
            is Variable -> {
                val name = expr.name.lexeme
                val scope = scopes.peek()!!
                if (scope[name] == false) {
                    // I forget how to signal an error
                    throw ResolutionError(expr.name.line, "We can't reference an undefined var")
                }
                expr.resolutionDepth = depth(name)
                // if depth was null - not found - we'll end up with a runtime error, maybe that's okay?
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
        }
    }

}