package com.stremio.mobile.presentation.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.stremio.mobile.core.theme.AccentPurple
import com.stremio.mobile.core.theme.MutedText
import com.stremio.mobile.server.LOCAL_NETWORK_PERMISSION

/**
 * Returns a function that explains, then asks for, the local network permission and passes on whether it was granted.
 * With [openSettingsIfBlocked], a denial without a dialog (Android stops asking after two) opens the app's system settings.
 */
@Composable
fun rememberLocalNetworkPermissionRequest(openSettingsIfBlocked: Boolean = false): (onResult: (granted: Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    var onResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var showExplanation by remember { mutableStateOf(false) }

    fun finish(granted: Boolean) {
        onResult?.invoke(granted)
        onResult = null
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && openSettingsIfBlocked && !canShowPermissionDialog(context)) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
        finish(granted)
    }

    if (showExplanation) {
        val notNow = { showExplanation = false; finish(false) }
        AlertDialog(
            onDismissRequest = notNow,
            title = { Text("Allow Nearby Devices", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "To reach Stremio on your PC over Wi-Fi, Android needs the Nearby devices permission. " +
                        "It's only used to connect to the server address you entered.",
                    color = MutedText,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showExplanation = false; launcher.launch(LOCAL_NETWORK_PERMISSION) }) {
                    Text("Continue", color = AccentPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = notNow) { Text("Not now", color = MutedText) }
            },
            containerColor = Color(0xFF2A2935),
            shape = RoundedCornerShape(20.dp),
        )
    }

    return { callback ->
        onResult = callback
        showExplanation = true
    }
}

private fun canShowPermissionDialog(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, LOCAL_NETWORK_PERMISSION)
}
