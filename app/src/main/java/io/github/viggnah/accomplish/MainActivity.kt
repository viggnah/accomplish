package io.github.viggnah.accomplish

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import io.github.viggnah.accomplish.notify.Notifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.viggnah.accomplish.ui.Phase
import io.github.viggnah.accomplish.ui.Root

class MainActivity : ComponentActivity() {
    private val state get() = (application as App).state

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (state.phase == Phase.Loading) state.start()
        openFrom(intent)
        setContent { Root(state) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A tapped notification says where to go. */
    private fun openFrom(intent: android.content.Intent?) {
        val where = intent?.getStringExtra(Notifier.EXTRA_SCREEN) ?: return
        intent.removeExtra(Notifier.EXTRA_SCREEN)
        state.openFromNotification(where)
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
        askForNotificationsOnce()
    }

    override fun onPause() {
        super.onPause()
        state.onPause()
        Notifier.runAsync(this)
    }

    /** Android 13 needs a yes before any reminder can show; ask once, after setup is done. */
    private fun askForNotificationsOnce() {
        if (state.phase != Phase.Ready || !state.settings.notify) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("reminders", MODE_PRIVATE)
        if (prefs.getBoolean("asked", false)) return
        prefs.edit().putBoolean("asked", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
