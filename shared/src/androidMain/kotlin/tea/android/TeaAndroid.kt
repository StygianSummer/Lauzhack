package tea.android

import android.content.Context

/**
 * Android needs an application [Context] for the store, links and the share sheet. The Android
 * shell (`androidApp`, `TeaApplication.onCreate`) calls [init] once before any UI is shown.
 */
object TeaAndroid {
    @Volatile
    private var appContext: Context? = null

    /** Backend URL baked in at build time (`-Ptea.api=...` or env `TEA_API`), the Android "env var" of plan C5. */
    @Volatile
    var envApi: String? = null
        private set

    fun init(context: Context, envApi: String? = null) {
        appContext = context.applicationContext ?: context
        this.envApi = envApi?.trim()?.takeIf { it.isNotEmpty() }
    }

    val context: Context
        get() = checkNotNull(appContext) { "TeaAndroid.init(context) must be called before the app starts" }
}
