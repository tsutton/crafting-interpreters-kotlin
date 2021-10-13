data class ParseError(val unexpectedToken: Token, val parseMessage: String) : Throwable(
    if (unexpectedToken.type == TokenType.EOF) {
        "[line ${unexpectedToken.line}], Error at end: $parseMessage"
    } else {
        "[line ${unexpectedToken.line}], Error at '${unexpectedToken.lexeme}': $parseMessage"
    }
)

sealed class ParseResult {
    data class Ok(val program: List<Stmt>) : ParseResult()
    data class Err(val err: ParseError) : ParseResult()
}

// Our grammar (after refactoring it into the above) is LL(1)
// i.e. we can parse it with recursive descent without backtracking (with a fixed, finite number of look ahead).
// And all the operators are left associate, so even though the grammar is ambiguous (a * b * c has two parse trees)
// that's ok.
// As such, from any given state, we use lookahead to decide which non-terminal is upcoming, and parse exactly that one.
class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): ParseResult {
        return try {
            val statements = mutableListOf<Stmt>()
            while (!isAtEnd()) {
                val decl = declaration()
                if (decl != null) statements.add(decl)
            }
            ParseResult.Ok(statements)
        } catch (error: ParseError) {
            ParseResult.Err(error)
        }
    }

    private fun declaration(): Stmt? {
        return try {
            when {
                match(TokenType.VAR) -> varDeclaration()
                match(TokenType.FUN) -> functionDeclaration("function")
                match(TokenType.CLASS) -> classDeclaration()
                else -> statement()
            }

        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun classDeclaration(): ClassStatement {
        val name = consume(TokenType.IDENTIFIER, "expecting identifier after keyword 'class'")
        consume(TokenType.LEFT_BRACE, "expecting '{' after class name")
        val methods = mutableListOf<FunctionDeclaration>()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            methods.add(functionDeclaration("method"))
        }
        consume(TokenType.RIGHT_BRACE, "expecting '}' at end of class")
        return ClassStatement(name, methods)
    }

    private fun functionDeclaration(kind: String): FunctionDeclaration {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name.")
        consume(TokenType.LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    throw ParseError(peek(), "Can't have more than 255 parameters.")
                }
                parameters.add(
                    consume(TokenType.IDENTIFIER, "Expect parameter name.")
                )
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after parameters.")

        consume(TokenType.LEFT_BRACE, "Expect '{' before $kind body.")
        val body: List<Stmt> = statements()
        return FunctionDeclaration(name, parameters, body)
    }

    private fun statement(): Stmt {
        return when {
            match(TokenType.PRINT) -> printStatement()
            match(TokenType.LEFT_BRACE) -> Block(statements())
            match(TokenType.IF) -> ifStatement()
            match(TokenType.WHILE) -> whileStatement()
            match(TokenType.FOR) -> forStatement()
            match(TokenType.RETURN) -> returnStatement()
            else -> expressionStatement()
        }
    }

    private fun returnStatement(): Return {
        val keyword = previous()
        val value = if (!check(TokenType.SEMICOLON)) {
            expression()
        } else {
            null
        }
        consume(TokenType.SEMICOLON, "Expect ';' after return value.")
        return Return(keyword, value)
    }

    private fun whileStatement(): While {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'")
        val predicate = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after 'while' predicate")
        val body = statement()
        return While(predicate, body)
    }

    private fun forStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'")

        val initializer = if (match(TokenType.SEMICOLON)) {
            null
        } else if (match(TokenType.VAR)) {
            varDeclaration()
        } else {
            expressionStatement()
        }

        val condition = if (!check(TokenType.SEMICOLON)) {
            expression()
        } else {
            null
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(TokenType.RIGHT_PAREN)) {
            expression()
        } else {
            null
        }
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.")

        val body = statement()

        val desugared = mutableListOf<Stmt>()
        if (initializer != null) desugared.add(initializer)
        val augmentedBody = mutableListOf(body)
        if (increment != null) augmentedBody.add(Expression(increment))
        desugared.add(
            While(
                condition ?: Literal(LoxBoolean(true)),
                Block(augmentedBody)
            )
        )
        return Block(desugared)
    }

    private fun statements(): List<Stmt> {
        val statements: MutableList<Stmt> = mutableListOf()
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            val decl = declaration()
            if (decl != null) statements.add(decl)
        }
        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun ifStatement(): If {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()
        var elseBranch: Stmt? = null
        if (match(TokenType.ELSE)) {
            elseBranch = statement()
        }

        return If(condition, thenBranch, elseBranch)
    }

    private fun synchronize() {
        println("discarding unexpected token ${peek()}")
        advance()

        while (!isAtEnd()) {
            if (previous().type === TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.CLASS, TokenType.FUN, TokenType.VAR,
                TokenType.FOR, TokenType.IF, TokenType.WHILE,
                TokenType.PRINT, TokenType.RETURN -> return
                else -> advance()
            }
        }
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Print(value)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression.")
        return Expression(expr)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")
        var initializer: Expr? = null
        if (match(TokenType.EQUAL)) {
            initializer = expression()
        }
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Var(name, initializer)
    }

    private fun binary(component: () -> Expr, vararg operations: TokenType): Expr {
        var expr = component()

        while (match(*operations)) {
            val operator = previous()
            val right = component()
            expr = Binary(expr, operator, right)
        }
        return expr
    }

    fun expression() = assignment()

    private fun assignment(): Expr {
        val expr = orExpr()
        return if (match(TokenType.EQUAL)) {
            val equalsToken = previous()
            val value = assignment()
            when (expr) {
                is Variable -> Assign(expr.name, value)
                is GetExpr -> SetExpr(expr.base, expr.getter, value)
                else -> throw ParseError(equalsToken, "Cannot assign to this left side of equals")
            }
        } else {
            expr
        }
    }

    private fun orExpr(): Expr {
        var expr = andExpr()
        while (match(TokenType.OR)) {
            val operator = previous()
            val right: Expr = andExpr()
            expr = Logical(expr, operator, right)
        }
        return expr
    }

    private fun andExpr(): Expr {
        var expr = equality()
        while (match(TokenType.AND)) {
            val operator = previous()
            val right = equality()
            expr = Logical(expr, operator, right)
        }
        return expr
    }

    private fun equality(): Expr = binary(::comparison, TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)

    private fun comparison(): Expr = binary(
        ::term,
        TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL
    )

    private fun term(): Expr = binary(
        ::factor,
        TokenType.PLUS, TokenType.MINUS
    )

    private fun factor(): Expr = binary(
        ::unary,
        TokenType.STAR, TokenType.SLASH
    )

    private fun unary(): Expr {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expr {
        var expr = primary()
        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = extendCall(expr)
            } else if (match(TokenType.DOT)) {
                expr = GetExpr(
                    expr,
                    consume(TokenType.IDENTIFIER, "expecting identifier for property access after '.'")
                )
            } else {
                break
            }
        }
        return expr
    }

    private fun extendCall(base: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    throw ParseError(peek(), "Can't have more than 255 arguments.")
                }
                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val paren = consume(
            TokenType.RIGHT_PAREN,
            "Expect ')' after arguments."
        )

        return CallExpr(base, arguments, paren)
    }

    private fun primary(): Expr {
        if (match(TokenType.FALSE)) return Literal(LoxBoolean(false))
        if (match(TokenType.TRUE)) return Literal(LoxBoolean(true))
        if (match(TokenType.NIL)) return Literal(LoxNil())
        if (match(TokenType.THIS)) return This(previous())

        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return if (previous().type == TokenType.NUMBER) {
                Literal(LoxNumber(previous().literal as Double))
            } else {
                Literal(LoxString(previous().literal as String))
            }
        }

        if (match(TokenType.LEFT_PAREN)) {
            val expr: Expr = expression()
            consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
            return Grouping(expr)
        }

        if (match(TokenType.IDENTIFIER)) {
            return Variable(previous())
        }
        throw ParseError(tokens[current], "is this code reachable?")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        return if (isAtEnd()) false else peek().type === type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return peek().type === TokenType.EOF
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ParseError(peek(), message)
    }
}