package com.example.visualization

import org.junit.Assert.*
import org.junit.Test

class RendererInitializationTest {
    @Test fun absentProviderReturnsFailureWithoutTryingToCleanNonexistentView() {
        var cleaned = false
        val result = RendererInitialization.create<Any, Any>({ throw IllegalStateException("No WebView provider") }, { it }, { cleaned = true })
        assertTrue(result.isFailure)
        assertFalse(cleaned)
    }
    @Test fun partialSettingsOrListenerFailureCleansAllocatedResourceAndPreservesFailure() {
        val allocated = Any()
        val cause = IllegalStateException("Provider initialization failed")
        var cleaned: Any? = null
        val result = RendererInitialization.create({ allocated }, { throw cause }, { cleaned = it; throw IllegalStateException("Cleanup also failed") })
        assertSame(cause, result.exceptionOrNull())
        assertSame(allocated, cleaned)
    }
    @Test fun oneTeardownFailureDoesNotPreventDestroyOrRemainingSteps() {
        val steps = mutableListOf<String>()
        RendererInitialization.cleanup({ steps += "listener"; throw IllegalStateException() }, { steps += "detach" }, { steps += "destroy" })
        assertEquals(listOf("listener", "detach", "destroy"), steps)
    }
}
