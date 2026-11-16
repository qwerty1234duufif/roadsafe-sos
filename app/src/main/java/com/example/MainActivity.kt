package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.firebase.configureAppCheck
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.RoadSafeTheme
import com.example.util.NotificationHelper

class MainActivity : ComponentActivity() {

    private var pendingAlertId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        pendingAlertId = intent?.getStringExtra(NotificationHelper.EXTRA_ALERT_ID)

        enableEdgeToEdge()
        setContent {
            RoadSafeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        pendingAlertId = pendingAlertId,
                        onConsumePendingAlertId = { pendingAlertId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_ALERT_ID)?.let { alertId ->
            pendingAlertId = alertId
        }
    }
}
