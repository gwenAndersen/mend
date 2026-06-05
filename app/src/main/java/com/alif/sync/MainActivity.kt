package com.alif.sync

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.alif.sync.ai.ChatScreen
import com.alif.sync.ui.ProcessListScreen
import com.alif.sync.ui.UsageStatsScreen
import com.alif.sync.services.SyncService
import com.alif.sync.ui.theme.SyncTheme
import com.alif.sync.util.AccessibilityServiceManager
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

class MainActivity : ComponentActivity(), Shizuku.OnBinderReceivedListener, Shizuku.OnBinderDeadListener {

    private val hasShizukuPermission = mutableStateOf(false)
    private val isShizukuRunning = mutableStateOf(false)
    private val isAccessibilityServiceEnabled = mutableStateOf(false)
    private val showChat = mutableStateOf(false)
    private val showProcessManager = mutableStateOf(false)
    private val showUsageStats = mutableStateOf(false)
    private val showMicManager = mutableStateOf(false)

    private val requestPermissionResultListener =
        OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1) {
                hasShizukuPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
            }
        }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // This is not used, Shizuku provides a callback
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.addBinderReceivedListenerSticky(this)
        Shizuku.addBinderDeadListener(this)
        createNotificationChannel()
        checkShizukuPermission() // Initial check
        checkAndRequestUsageStatsPermission()
        checkAndRequestStoragePermission()
        
        // Start SyncService automatically
        val serviceIntent = Intent(this, SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        setContent {
            SyncTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showChat.value) {
                        ChatScreen(onBack = { showChat.value = false })
                    } else if (showProcessManager.value) {
                        ProcessListScreen(onBack = { showProcessManager.value = false })
                    } else if (showUsageStats.value) {
                        UsageStatsScreen(onBack = { showUsageStats.value = false })
                    } else if (showMicManager.value) {
                        com.alif.sync.ui.MicManagerScreen(onBack = { showMicManager.value = false })
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(onClick = { showChat.value = true }) {
                                Text("Open AI Assistant")
                            }
                            Button(onClick = { showProcessManager.value = true }) {
                                Text("Process Manager (Shizuku)")
                            }
                            Button(onClick = { showUsageStats.value = true }) {
                                Text("App Usage Stats (Wellbeing)")
                            }
                            Button(onClick = { showMicManager.value = true }) {
                                Text("Microphone Manager (Shizuku)")
                            }
                            Button(onClick = {
                                val intent = Intent(this@MainActivity, SyncService::class.java)
                                startService(intent)
                            }) {
                                Text("Start Sync Service")
                            }
                            Button(onClick = {
                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                            }) {
                                Text("Open Accessibility Settings")
                            }
                            val context = LocalContext.current
                            Button(onClick = {
                                try {
                                    if (!Shizuku.pingBinder()) {
                                        Toast.makeText(context, "Shizuku is not running", Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                                            Shizuku.requestPermission(1)
                                        } else {
                                            Toast.makeText(context, "Permission already granted", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Request Shizuku Permission")
                            }
                            Text(text = "Shizuku Running: ${if (isShizukuRunning.value) "Yes" else "No"}")
                            Text(text = "Shizuku Permission: ${if (hasShizukuPermission.value) "Granted" else "Not Granted"}")
                            Text(text = "Accessibility Service: ${if (isAccessibilityServiceEnabled.value) "Enabled" else "Disabled"}")
                            Button(onClick = {
                                val success = AccessibilityServiceManager.enableAccessibilityService(context)
                                val message = if (success) "Accessibility Service Enabled" else "Failed to Enable Accessibility Service"
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                isAccessibilityServiceEnabled.value = success // Update the state
                            }) {
                                Text("Enable Accessibility Service via Shizuku")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isAccessibilityServiceEnabled.value = AccessibilityServiceManager.isAccessibilityServiceEnabled(this)
        checkShizukuPermission()
    }

    private fun checkShizukuPermission() {
        try {
            isShizukuRunning.value = Shizuku.pingBinder()
            if (isShizukuRunning.value) {
                hasShizukuPermission.value = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                hasShizukuPermission.value = false
            }
        } catch (e: Exception) {
            // Ignore errors when checking permission
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(this)
        Shizuku.removeBinderDeadListener(this)
    }

    override fun onBinderReceived() {
        isShizukuRunning.value = true
        checkShizukuPermission()
    }

    override fun onBinderDead() {
        isShizukuRunning.value = false
        hasShizukuPermission.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Sync Service Channel"
            val descriptionText = "Channel for Sync Service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("sync_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestUsageStatsPermission() {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        val hasUsageStatsPermission = mode == AppOpsManager.MODE_ALLOWED
        if (!hasUsageStatsPermission) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please grant Usage Access permission", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName()))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }
}
