package io.github.staninna.warosso

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.staninna.warosso.ui.WarOssoNavigation
import io.github.staninna.warosso.ui.theme.WarOssoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = (application as WarOssoApp).container.settings.settings
        holdFirstFrameUntil { settings.value != null }

        setContent {
            val current by settings.collectAsStateWithLifecycle()
            current?.let { WarOssoTheme(it.theme) { WarOssoNavigation() } }
        }
    }

    /** Keeps the splash screen up, so the app never flashes in the wrong theme before the settings are read. */
    private fun holdFirstFrameUntil(ready: () -> Boolean) {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready()) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }
}
