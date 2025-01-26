package app.vitune.providers.innertube

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ScriptableObject

data class JavaScriptChallenge(
    val timestamp: String,
    val source: String,
    val functionName: String
) {
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    suspend fun decode(cipher: String) = mutex.withLock {
        cache.getOrPut(cipher) {
            with(Context.enter()) {
                languageVersion = Context.VERSION_ES6
                optimizationLevel = -1
                val scope = initSafeStandardObjects()
                scope.defineProperty(
                    "document",
                    Context.getUndefinedValue(),
                    ScriptableObject.EMPTY
                )
                scope.defineProperty(
                    "window",
                    Context.getUndefinedValue(),
                    ScriptableObject.EMPTY
                )
                scope.defineProperty(
                    "XMLHttpRequest",
                    Context.getUndefinedValue(),
                    ScriptableObject.EMPTY
                )
                evaluateString(scope, source, functionName, 1, null)
                val function = scope.get(functionName, scope) as Function
                function.call(this, scope, scope, arrayOf(cipher)).toString()
                    .also { Context.exit() }
            }
        }
    }
}
