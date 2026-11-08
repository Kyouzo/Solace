package dev.kyouzo.solace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kyouzo.solace.data.remote.SyncScheduler
import dev.kyouzo.solace.ui.shell.SolaceShell
import dev.kyouzo.solace.ui.theme.SolaceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncScheduler.ensurePeriodic(this)
        setContent { SolaceTheme { SolaceShell() } }
    }
}
