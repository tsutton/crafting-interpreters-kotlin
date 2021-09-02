// import TokenType.*

data class ParseError(val unexpectedToken: Token, val parseMessage: String) : Throwable(
    if (unexpectedToken.type == TokenType.EOF) {
        "[line ${unexpectedToken.line}], Error at end: $parseMessage"
    } else {
        "[line ${unexpectedToken.line}], Error at '${unexpectedToken.lexeme}': $parseMessage"
    }
)

/*
Programs/statements:


program        → declaration* EOF ;

declaration    → varDecl
               | statement ;

statement      → exprStmt
               | printStmt ;
exprStmt       → expression ";" ;
printStmt      → "print" expression ";"

Expressions:

expression     → equality ;
equality       → comparison ( ( "!=" | "==" ) comparison )* ;
comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
term           → factor ( ( "-" | "+" ) factor )* ;
factor         → unary ( ( "/" | "*" ) unary )* ;
unary          → ( "!" | "-" ) unary
               | primary ;
primary        → NUMBER | STRING | "true" | "false" | "nil"
               | "(" expression ")" ;
               | IDENTIFIER
 */

sealed class ParseResult {
    data class Ok(val program: List<Stmt>) : ParseResult()
    data class Err(val err: ParseError) : ParseResult()
}

// Our grammar (after refactoring it into the above) is LL(1)
// i.e. we can parse it with recursive descent without backtracking (with a fixed, finite number of look ahead).
// And all the operators are left associate, so even though the grammar is ambiguous (a * b * c has two parse trees)
// that's ok.
// As such, from any given state, we use lookahead to decide which nonterminal is upcoming, and parse exactly that one.
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
            if (match(TokenType.VAR)) varDeclaration() else statement()
        } catch (error: ParseError) {
            synchronize()
            null
        }
    }

    private fun statement(): Stmt {
        return if (match(TokenType.PRINT)) printStatement() else expressionStatement()
    }

    private fun synchronize() {
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

    fun expression() = equality()

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

        return primary()
    }

    private fun primary(): Expr {
        if (match(TokenType.FALSE)) return Literal(LoxBoolean(false))
        if (match(TokenType.TRUE)) return Literal(LoxBoolean(true))
        if (match(TokenType.NIL)) return Literal(LoxNil())

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