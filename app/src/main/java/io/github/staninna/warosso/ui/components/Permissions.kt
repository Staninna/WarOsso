package io.github.staninna.warosso.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class PermissionState(val granted: Boolean, val request: () -> Unit)

/**
 * Tracks whether [permission] is held and offers a way to ask for it. [alongside] goes into the
 * same system dialog (fine location needs coarse next to it). Once Android stops showing the
 * dialog, [PermissionState.request] opens the app's settings page instead.
 */
@Composable
fun rememberPermissionState(permission: String, vararg alongside: String): PermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(context.holds(permission)) }

    // The user can change permissions in system settings while the app sits in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.holds(permission) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[permission] == true
        if (!granted && activity?.shouldShowRequestPermissionRationale(permission) == false) {
            context.openAppSettings()
        }
    }
    return PermissionState(granted) { launcher.launch(arrayOf(permission, *alongside)) }
}

private fun Context.holds(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
