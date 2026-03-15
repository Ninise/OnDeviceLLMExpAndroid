package com.nikitanikitin.localllmexp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.WindowCompat
import com.nikitanikitin.localllmexp.ui.ChatScreen

class MainActivity : ComponentActivity() {

    // ─── Permission Launcher ──────────────────────────────────────────────────

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        android.util.Log.d("MainActivity", if (allGranted) "✅ Permissions granted" else "❌ Some permissions denied: $results")
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        requestCalendarPermissionsIfNeeded()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen()
                }
            }
        }
    }

    private fun requestCalendarPermissionsIfNeeded() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PermissionChecker.PERMISSION_GRANTED
        }

        if (needsRequest) {
            android.util.Log.d("MainActivity", "🔵 Requesting calendar permissions")
            requestPermissionsLauncher.launch(permissions)
        } else {
            android.util.Log.d("MainActivity", "✅ Calendar permissions already granted")
        }
    }
}