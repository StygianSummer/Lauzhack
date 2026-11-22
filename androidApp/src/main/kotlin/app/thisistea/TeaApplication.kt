package app.thisistea

import android.app.Application
import tea.android.TeaAndroid

/** Gives :shared the application context (store, links, share sheet) before any screen opens. */
class TeaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TeaAndroid.init(this, envApi = BuildConfig.TEA_API)
    }
}
