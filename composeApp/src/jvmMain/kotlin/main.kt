import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tea.ui.screens.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "ThisIsTea") {
        App()
    }
}
