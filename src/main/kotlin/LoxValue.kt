abstract class LoxValue {
    open fun typeName() = this::class.simpleName?.substring(3)
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

class LoxFunction(
    private val declaration: FunctionDeclaration,
    private val closure: Environment,
    private val isInitializer: Boolean = false // initializers have special behavior
) : LoxValue(), LoxCallable {
    override fun call(interpreter: Interpreter, args: List<LoxValue>): LoxValue {
        val functionScope = Environment(closure)
        declaration.params.forEachIndexed { index, token ->
            functionScope.define(token.lexeme, args[index])
        }
        val returnValue: LoxValue = try {
            interpreter.visitBlock(declaration.body, functionScope)
            // implicit return of nil if we didn't see a return statement
            LoxNil()
        } catch (e: ReturnException) {
            e.returnValue
        }
        return if (!isInitializer) {
            returnValue
        } else {
            // resolver pass ensures that returnValue will be LoxNil here (can't return a value from init)
            closure.getAt(0, "this")!!
        }
    }

    override fun nArgs() = declaration.params.size

    override fun toString() = "<fn ${declaration.name.lexeme}>"

    // Construct a new function with the same code, but a different closure that has the value for "this
    fun bind(thisValue: LoxInstance): LoxFunction {
        val boundEnvironment = Environment(closure)
        boundEnvironment.define("this", thisValue)
        return LoxFunction(this.declaration, boundEnvironment)
    }
}

class LoxClass(val name: String, private val methods: Map<String, LoxFunction>) : LoxValue(), LoxCallable {
    override fun call(interpreter: Interpreter, args: List<LoxValue>): LoxInstance {
        val instance = LoxInstance(this)
        val initializer = getMethod("init") ?: return instance
        initializer.bind(instance).call(interpreter, args)
        return instance
    }

    override fun nArgs(): Int {
        val initializer = getMethod("init") ?: return 0
        return initializer.nArgs()
    }

    override fun toString() = "<class ${name}>"
    override fun typeName() = "<class>"

    fun getMethod(name: String) = methods[name]
}

class LoxInstance(private val type: LoxClass) : LoxValue() {
    private val properties = mutableMapOf<String, LoxValue>()
    override fun typeName() = type.name
    override fun toString() = "<instance of ${type.name}>"
    fun get(name: Token): LoxValue {
        val value = properties[name.lexeme]
        if (value != null) {
            return value
        }
        val method = type.getMethod(name.lexeme)
        if (method != null) {
            return method.bind(this)
        }
        throw RuntimeError(name, "Undefined property '${name.lexeme}")
    }

    fun set(name: Token, value: LoxValue) {
        properties[name.lexeme] = value
    }
}