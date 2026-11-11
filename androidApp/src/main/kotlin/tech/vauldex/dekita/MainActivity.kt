package tech.vauldex.dekita

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import tech.vauldex.dekita.ui.checkin.LocalMapStyleUrl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(LocalMapStyleUrl provides BuildConfig.MAP_STYLE_URL) {
                App(isDebug = BuildConfig.DEBUG)
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(isDebug = true)
}
