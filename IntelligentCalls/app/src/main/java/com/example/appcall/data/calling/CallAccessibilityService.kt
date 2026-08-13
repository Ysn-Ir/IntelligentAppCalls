package com.example.appcall.data.calling

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilityService"
        var instance: CallAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CallAccessibilityService connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only use this service for audio stream elevation
    }

    override fun onInterrupt() {
        Log.d(TAG, "CallAccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
