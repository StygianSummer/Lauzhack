package tea

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SmokeTest {
    @Test
    fun arithmeticWorks() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun coroutinesTestWorks() = runTest {
        assertEquals("tea", "tea")
    }
}
