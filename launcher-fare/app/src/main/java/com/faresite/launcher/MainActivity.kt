package com.faresite.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Sube cada vez que oprimes el botón de inicio estando ya en el launcher. */
    private val homePressed = MutableStateFlow(0)
    /** Sube cada vez que vuelves al launcher (para refrescar apps, hora y clima). */
    private val resumed = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp(homePressed, resumed) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.value++
    }

    override fun onResume() {
        super.onResume()
        visible = true
        resumed.value++
    }

    override fun onPause() {
        super.onPause()
        visible = false
    }

    companion object {
        /** Si el launcher está en pantalla (para no repetir encima los recibos y sonidos). */
        @Volatile var visible = false
    }
}
