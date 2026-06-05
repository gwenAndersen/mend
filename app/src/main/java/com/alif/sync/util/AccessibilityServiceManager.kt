package com.alif.sync.util

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.alif.sync.services.SyncAccessibilityService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object AccessibilityServiceManager {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (accessibilityEnabled == 0) {
            return false
        }

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val componentName = context.packageName + "/" + SyncAccessibilityService::class.java.canonicalName
        return enabledServices?.let {
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(it)
            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(componentName, ignoreCase = true)) {
                    return true
                }
            }
            false
        } ?: false
    }

    fun enableAccessibilityService(context: Context): Boolean {
        val componentName = context.packageName + "/" + SyncAccessibilityService::class.java.canonicalName
        Log.d("AccessibilityServiceMgr", "Attempting to enable service: $componentName")

        if (!Shizuku.pingBinder()) {
            Log.e("AccessibilityServiceMgr", "Shizuku binder is not available.")
            return false
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.e("AccessibilityServiceMgr", "Shizuku permission not granted.")
            return false
        }

        try {
            // Step 1: Ensure accessibility_enabled is 1
            val accessibilityEnabledCmd = arrayOf("settings", "put", "secure", "accessibility_enabled", "1")
            var process = Shizuku.newProcess(accessibilityEnabledCmd, null, null)
            
            var stdoutBuilder = StringBuilder()
            var stderrBuilder = StringBuilder()

            var reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stdoutBuilder.append(line).append("\n")
            }
            reader = BufferedReader(InputStreamReader(process.errorStream))
            while (reader.readLine().also { line = it } != null) {
                stderrBuilder.append(line).append("\n")
            }
            process.waitFor()

            Log.d("AccessibilityServiceMgr", "accessibility_enabled stdout: $stdoutBuilder")
            Log.e("AccessibilityServiceMgr", "accessibility_enabled stderr: $stderrBuilder")

            if (process.exitValue() != 0) {
                Log.e("AccessibilityServiceMgr", "Failed to set accessibility_enabled to 1. Exit code: ${process.exitValue()}")
                return false
            }
            Log.d("AccessibilityServiceMgr", "accessibility_enabled set to 1 successfully.")

            // Step 2: Get current enabled_accessibility_services
            val getEnabledServicesCmd = arrayOf("settings", "get", "secure", "enabled_accessibility_services")
            process = Shizuku.newProcess(getEnabledServicesCmd, null, null)
            
            stdoutBuilder = StringBuilder()
            stderrBuilder = StringBuilder()

            reader = BufferedReader(InputStreamReader(process.inputStream))
            var currentServicesString = reader.readLine() // Read only the first line for the setting value
            while (reader.readLine().also { line = it } != null) {
                stdoutBuilder.append(line).append("\n") // Append any extra output
            }
            reader = BufferedReader(InputStreamReader(process.errorStream))
            while (reader.readLine().also { line = it } != null) {
                stderrBuilder.append(line).append("\n")
            }
            process.waitFor()

            Log.d("AccessibilityServiceMgr", "Current enabled_accessibility_services raw: $currentServicesString")
            if (stdoutBuilder.isNotEmpty()) Log.d("AccessibilityServiceMgr", "getEnabledServices stdout extra: $stdoutBuilder")
            if (stderrBuilder.isNotEmpty()) Log.e("AccessibilityServiceMgr", "getEnabledServices stderr: $stderrBuilder")


            val enabledServices = currentServicesString?.split(":")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()

            if (!enabledServices.contains(componentName)) {
                enabledServices.add(componentName)
                val newServicesString = enabledServices.joinToString(":")

                val setEnabledServicesCmd = arrayOf("settings", "put", "secure", "enabled_accessibility_services", newServicesString)
                process = Shizuku.newProcess(setEnabledServicesCmd, null, null)

                stdoutBuilder = StringBuilder()
                stderrBuilder = StringBuilder()

                reader = BufferedReader(InputStreamReader(process.inputStream))
                while (reader.readLine().also { line = it } != null) {
                    stdoutBuilder.append(line).append("\n")
                }
                reader = BufferedReader(InputStreamReader(process.errorStream))
                while (reader.readLine().also { line = it } != null) {
                    stderrBuilder.append(line).append("\n")
                }
                process.waitFor()
                
                Log.d("AccessibilityServiceMgr", "setEnabledServices stdout: $stdoutBuilder")
                Log.e("AccessibilityServiceMgr", "setEnabledServices stderr: $stderrBuilder")

                if (process.exitValue() != 0) {
                    Log.e("AccessibilityServiceMgr", "Failed to set enabled_accessibility_services. Exit code: ${process.exitValue()}")
                    return false
                }
                Log.d("AccessibilityServiceMgr", "Service $componentName added to enabled_accessibility_services.")
            } else {
                Log.d("AccessibilityServiceMgr", "Service $componentName is already enabled.")
            }
            return true
        } catch (e: Exception) {
            Log.e("AccessibilityServiceMgr", "Error enabling accessibility service via Shizuku", e)
            return false
        }
    }
}
