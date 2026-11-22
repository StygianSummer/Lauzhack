package app.thisistea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import tea.i18n.ProvideAndroidLanguage
import tea.ui.screens.App

/**
 * The single activity: draws edge-to-edge and keeps the shared UI inside the safe area (status
 * and navigation bars, cutouts and the keyboard), so text fields stay visible above the IME.
 * The window background (theme) matches the app's paper colour behind the bars.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAndroidLanguage {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    App()
                }
            }
        }
    }
}
