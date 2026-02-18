package io.apptolast.paparcar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.apptolast.paparcar.domain.permissions.PermissionManager
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val permissionManager: PermissionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        permissionManager.refreshPermissions()
    }
}
