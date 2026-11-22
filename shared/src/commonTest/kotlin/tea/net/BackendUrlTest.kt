package tea.net

import kotlinx.coroutines.test.runTest
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackendUrlTest {
    private fun state(saved: String? = null) = LocalState(InMemoryKeyValueStore()).also { it.apiOverride = saved }

    @Test
    fun queryParamWinsAndIsSaved() = runTest {
        val s = state(saved = "https://old.example")
        val r = BackendUrl.resolveWeb("https://tunnel.example/", s) { "https://config.example" }
        assertEquals(BackendUrl.Resolved("https://tunnel.example", BackendUrl.Source.QUERY), r)
        assertEquals("https://tunnel.example", s.apiOverride)
    }

    @Test
    fun savedValueBeatsConfig() = runTest {
        val r = BackendUrl.resolveWeb(null, state(saved = "https://saved.example")) { "https://config.example" }
        assertEquals(BackendUrl.Source.SAVED, r.source)
        assertEquals("https://saved.example", r.url)
    }

    @Test
    fun configJsonThenDefault() = runTest {
        assertEquals(
            BackendUrl.Resolved("https://config.example", BackendUrl.Source.CONFIG),
            BackendUrl.resolveWeb(null, state()) { "https://config.example" },
        )
        assertEquals(
            BackendUrl.Resolved("http://localhost:8080", BackendUrl.Source.DEFAULT),
            BackendUrl.resolveWeb(null, state()) { null },
        )
    }

    @Test
    fun resetClearsSavedAndFallsThrough() = runTest {
        val s = state(saved = "https://saved.example")
        val r = BackendUrl.resolveWeb("reset", s) { "https://config.example" }
        assertNull(s.apiOverride)
        assertEquals(BackendUrl.Source.CONFIG, r.source)
    }

    @Test
    fun invalidQueryIsIgnored() = runTest {
        val s = state(saved = "https://saved.example")
        val r = BackendUrl.resolveWeb("javascript:alert(1)", s) { null }
        assertEquals("https://saved.example", r.url)
        assertEquals("https://saved.example", s.apiOverride)
    }

    @Test
    fun configFetchedOnlyWhenNeeded() = runTest {
        var fetched = 0
        BackendUrl.resolveWeb("https://q.example", state()) { fetched++; null }
        BackendUrl.resolveWeb(null, state(saved = "https://s.example")) { fetched++; null }
        assertEquals(0, fetched)
    }

    @Test
    fun desktopOrder() {
        assertEquals(BackendUrl.Source.ENV, BackendUrl.resolveDesktop("https://env.example", state("https://s.example")).source)
        assertEquals("https://s.example", BackendUrl.resolveDesktop(null, state("https://s.example")).url)
        assertEquals("http://localhost:8080", BackendUrl.resolveDesktop("  ", state()).url)
        assertEquals("http://10.0.2.2:8080", BackendUrl.resolveDesktop(null, state(), BackendUrl.ANDROID_EMULATOR).url)
    }

    @Test
    fun normalise() {
        assertEquals("https://a.example", BackendUrl.normalise(" https://a.example// "))
        assertEquals("http://localhost:8080", BackendUrl.normalise("http://localhost:8080"))
        assertNull(BackendUrl.normalise("ftp://a.example"))
        assertNull(BackendUrl.normalise("https://"))
        assertNull(BackendUrl.normalise("a.example"))
        assertNull(BackendUrl.normalise(null))
    }
}
