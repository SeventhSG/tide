package app.tide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tide.core.design.TideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The ocean runs to the edges, so the app draws behind the system bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TideTheme {
                TodayScreen()
            }
        }
    }
}
