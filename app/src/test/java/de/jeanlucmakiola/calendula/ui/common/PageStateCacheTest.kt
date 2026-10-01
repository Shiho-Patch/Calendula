package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class PageStateCacheTest {

    @Test
    fun `the same key shares one state`() = runTest {
        val cache = PageStateCache<Int, Int>(backgroundScope, 0, { a, b -> abs(a - b) <= 1 }) { flow { emit(it) } }
        assertThat(cache.get(5)).isSameInstanceAs(cache.get(5))
    }

    @Test
    fun `a dropped entry stops loading`() = runTest(UnconfinedTestDispatcher()) {
        var running = 0
        val cache = PageStateCache<Int, Int>(backgroundScope, 0, { a, b -> abs(a - b) <= 1 }) { key ->
            flow {
                running++
                try {
                    emit(key)
                    awaitCancellation()
                } finally {
                    running--
                }
            }
        }
        backgroundScope.launch { cache.get(0).collect {} }
        assertThat(running).isEqualTo(1)

        // Far enough away that 0 is let go, even though it is still collected.
        cache.get(10)
        assertThat(running).isEqualTo(0)
        assertThat(cache.size).isEqualTo(1)
    }

    @Test
    fun `nearby entries are kept`() = runTest {
        val cache = PageStateCache<Int, Int>(backgroundScope, 0, { a, b -> abs(a - b) <= 2 }) { flow { emit(it) } }
        cache.get(4)
        cache.get(5)
        cache.get(6)
        assertThat(cache.size).isEqualTo(3)
    }
}
