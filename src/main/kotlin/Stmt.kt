sealed class Stmt
data class Expression(val expr: Expr) : Stmt()
data class Print(val expr: Expr) : Stmt()
data class Var(val name: Token, val initializer: Expr?) : Stmt()
data class Block(val statements: List<Stmt>) : Stmt()
data class If(val predicate: Expr, val ifTrue: Stmt, val ifFalse: Stmt?) : Stmt()
data class While(val predicate: Expr, val body: Stmt) : Stmt()
data class FunctionDeclaration(val name: Token, val params: List<Token>, val body: List<Stmt>) : Stmt()
data class Return(val returnToken: Token, val value: Expr?) : Stmt()
data class ClassStatement(val name: Token, val methods: List<FunctionDeclaration>): Stmt()