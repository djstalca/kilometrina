package si.lukabencina.kilometrina.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF285E4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F1D5),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4C6359),
    surface = Color(0xFFFAFDFB),
    surfaceContainer = Color(0xFFF0F4F1),
    surfaceContainerHigh = Color(0xFFEAEFEC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99D5B9),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF0F503B),
    onPrimaryContainer = Color(0xFFB5F1D5),
    secondary = Color(0xFFB3CCC0),
)

@Composable
fun KilometrinaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
