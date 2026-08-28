package dev.achmad.finbox.util.permission

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

/**
 * POST_NOTIFICATIONS, or a stand-in below API 33, where notifications are
 * granted at install and asking would be denied forever.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberNotificationPermissionState(): PermissionState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        remember { AlreadyGranted("android.permission.POST_NOTIFICATIONS") }
    }

@OptIn(ExperimentalPermissionsApi::class)
private class AlreadyGranted(override val permission: String) : PermissionState {
    override val status: PermissionStatus = PermissionStatus.Granted
    override fun launchPermissionRequest() = Unit
}
