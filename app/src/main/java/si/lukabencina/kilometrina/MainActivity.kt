package si.lukabencina.kilometrina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import si.lukabencina.kilometrina.ui.KilometrinaApp
import si.lukabencina.kilometrina.ui.theme.KilometrinaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            KilometrinaTheme {
                KilometrinaApp()
            }
        }
    }
}
