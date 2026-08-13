package com.example.appcall.data.calling

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor() {
    companion object {
        private const val TAG = "ShizukuManager"
        const val SHIZUKU_REQ_CODE = 8001
    }

    /**
     * Returns true if Shizuku binder is running on the device (ADB service active).
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Checks whether our app has permission to use Shizuku.
     */
    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Requests Shizuku binder permission from the user.
     */
    fun requestShizukuPermission() {
        try {
            if (isShizukuAvailable() && !hasShizukuPermission()) {
                Shizuku.requestPermission(SHIZUKU_REQ_CODE)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to request Shizuku permission: ${e.message}")
        }
    }

    /**
     * Executes ADB shell command via Shizuku binder to grant privileged system permissions.
     */
    fun grantPrivilegedPermissions(context: Context): Boolean {
        if (!hasShizukuPermission()) {
            Log.w(TAG, "Shizuku permission not granted — cannot run grantPrivilegedPermissions")
            return false
        }

        val pkgName = context.packageName
        val commands = arrayOf(
            "cmd role add-role-holder android.app.role.DIALER $pkgName",
            "cmd appops set $pkgName RECORD_AUDIO allow",
            "cmd appops set $pkgName READ_PHONE_STATE allow",
            "pm grant $pkgName android.permission.RECORD_AUDIO"
        )

        return try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            for (cmd in commands) {
                Log.d(TAG, "Executing via Shizuku: $cmd")
                val process = method.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val exitCode = process.waitFor()
                Log.d(TAG, "Command '$cmd' exit code: $exitCode")
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing ADB command via Shizuku: ${e.message}")
            false
        }
    }
}
