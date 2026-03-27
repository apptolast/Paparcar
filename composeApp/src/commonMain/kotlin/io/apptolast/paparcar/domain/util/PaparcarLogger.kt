package io.apptolast.paparcar.domain.util

/**
 * Minimal structured logger for Paparcar.
 *
 * All output goes through [println] so it works across all KMP targets without
 * platform-specific dependencies. Replace the body of each function with a
 * Napier/Kermit call when added to the project — call sites will not change.
 */
object PaparcarLogger {

    fun d(tag: String, message: String) {
        println("D/Paparcar[$tag]: $message")
    }

    fun i(tag: String, message: String) {
        println("I/Paparcar[$tag]: $message")
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        println("W/Paparcar[$tag]: $message${throwable.suffix()}")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("E/Paparcar[$tag]: $message${throwable.suffix()}")
    }

    private fun Throwable?.suffix(): String =
        this?.message?.let { " — $it" } ?: ""
}
