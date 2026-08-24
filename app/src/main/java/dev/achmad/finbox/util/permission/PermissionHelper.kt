package dev.achmad.finbox.util.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class PermissionHelper(val context: Context) {

    fun arePermissionsAllowed(permissions: List<String>): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

}