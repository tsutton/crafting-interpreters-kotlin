import java.io.File
import java.nio.charset.Charset
import kotlin.system.exitProcess

class Lox {
    private var hadError: Boolean = false
    private var hadRuntimeError: Boolean = false

    private var interpreter = Interpreter()

    // for debugging
    private val showTokens = false
    //  private val showAst = false

    fun runFile(filename: String) {
        val bytes = File(filename).readBytes()
        run(String(bytes, Charset.defaultCharset()))
        if (hadError) {
            exitProcess(65)
        } else if (hadRuntimeError) {
            exitProcess(70)
        }
    }

    fun runPrompt() {
        while (true) {
            print("> ")
            val line = readLine() ?: break
            run(line)
            hadError = false
        }
    }

    fun run(source: String) {
        val scanner = Scanner(source)
        val (tokens, errors) = scanner.scanTokens()
        if (errors.isNotEmpty()) {
            println("Scan error(s) found:")
            for (scanError in errors) {
                error(scanError.line, scanError.message)
            }
            return
        }
        if (showTokens) {
            println(tokens)
        }

        val program = when (val result = Parser(tokens).parse()) {
            is ParseResult.Ok -> {
                result.program
            }
            is ParseResult.Err -> {
                error(result.err.unexpectedToken.line, result.err.message ?: result.err.parseMessage)
                return
            }
        }
        try {
            interpreter.interpret(program)
        } catch (e: RuntimeError) {
            System.err.println(e.message)
            hadRuntimeError = true
        }

    }

    private fun error(line: Int, message: String) {
        report(line, "", message)
    }

    private fun report(
        line: Int, where: String,
        message: String
    ) {
        System.err.println(
            "[line $line] Error$where: $message"
        )
        hadError = true
    }
}