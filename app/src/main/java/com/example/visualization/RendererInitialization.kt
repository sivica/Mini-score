package com.example.visualization

/** Provider creation may fail before a resource exists; partial initialization must still clean up. */
internal object RendererInitialization {
    fun <T, R> create(create: () -> T, initialize: (T) -> R, cleanup: (T) -> Unit): Result<R> {
        val resource = runCatching(create).getOrElse { return Result.failure(it) }
        return runCatching { initialize(resource) }.onFailure { runCatching { cleanup(resource) } }
    }

    /** One provider teardown failure must not prevent the remaining teardown steps. */
    fun cleanup(vararg actions: () -> Unit) { actions.forEach { runCatching(it) } }
}
