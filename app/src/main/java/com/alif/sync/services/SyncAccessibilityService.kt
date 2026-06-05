package com.alif.sync.services

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class SyncAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d("SyncAccessibilityService", "onAccessibilityEvent: ${event?.eventType} - ${event?.packageName}")
    }

    override fun onInterrupt() {
        // Not yet implemented
    }
}
