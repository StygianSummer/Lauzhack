package tea.server

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigTest {
    @Test
    fun publicUrlSetsLinkDefaultsAndCors() {
        val c = Config.fromEnv(mapOf("PUBLIC_URL" to "https://tea.example/", "ALLOWED_ORIGINS" to "https://org.github.io"))
        assertEquals("https://tea.example/", c.appPublicUrl)
        assertEquals("https://tea.example/api", c.apiPublicUrl)
        assertEquals(listOf("https://tea.example", "https://org.github.io"), c.allowedOrigins)
    }

    @Test
    fun explicitUrlsWinOverPublicUrl() {
        val c = Config.fromEnv(
            mapOf("PUBLIC_URL" to "https://tea.example", "APP_PUBLIC_URL" to "https://app.example/", "API_PUBLIC_URL" to "https://api.example"),
        )
        assertEquals("https://app.example/", c.appPublicUrl)
        assertEquals("https://api.example", c.apiPublicUrl)
    }

    @Test
    fun withoutPublicUrlTheLocalDefaultsStay() {
        val c = Config.fromEnv(emptyMap())
        assertEquals("http://localhost:8081/", c.appPublicUrl)
        assertEquals("http://localhost:8080", c.apiPublicUrl)
        assertEquals(emptyList(), c.allowedOrigins)
        assertEquals(300, c.fieldRemindDelayS)
        assertEquals(0, Config.fromEnv(mapOf("FIELD_REMIND_DELAY_S" to "0")).fieldRemindDelayS)
    }
}
