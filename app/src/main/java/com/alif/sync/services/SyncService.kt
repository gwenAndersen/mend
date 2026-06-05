package com.alif.sync.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alif.sync.ExternalTool
import com.alif.sync.ExternalToolAction
import com.alif.sync.ExternalToolContent
import com.alif.sync.R
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncService : Service() {

    private val launchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "${packageName}.LAUNCH_TOOL" -> {
                    val toolId = intent.getStringExtra("tool_id")
                    when (toolId) {
                        "sync_tool" -> sendToolContent()
                        "app_usage_tool" -> sendAppUsageContent()
                        "audio_input_tool" -> sendAudioInputContent()
                        else -> sendToolContent() // Default
                    }
                }
                "${packageName}.LAUNCH_APP_USAGE_TOOL" -> sendAppUsageContent()
                "${packageName}.SET_AUDIO_INPUT_DEVICE" -> {
                    val deviceIdStr = intent.getStringExtra("extra")
                    val deviceId = deviceIdStr?.toIntOrNull()
                    if (deviceId != null) {
                        Log.d("SyncService", "Setting audio input to device ID: $deviceId")
                        com.alif.sync.util.AudioInputManager.forceSetAudioInput(context!!, deviceId)
                        // Refresh the tool content to show updated status
                        sendAudioInputContent()
                    }
                }
                "${packageName}.TRIGGER_BEHAVIOR_LOG" -> {
                    Log.d("SyncService", "Manually triggering behavior log!")
                    val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.alif.sync.workers.BehaviorLogWorker>().build()
                    androidx.work.WorkManager.getInstance(context!!).enqueue(workRequest)
                }
                "${packageName}.TRIGGER_REMINDER" -> {
                    Log.d("SyncService", "Manually triggering reminder check!")
                    val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.alif.sync.workers.ReminderWorker>().build()
                    androidx.work.WorkManager.getInstance(context!!).enqueue(workRequest)
                }
                "${packageName}.TRIGGER_SYNC" -> {
                    Log.d("SyncService", "Triggering sync from overlay!")
                    // Implement actual sync logic here
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction("${packageName}.LAUNCH_TOOL")
            addAction("${packageName}.LAUNCH_APP_USAGE_TOOL")
            addAction("${packageName}.SET_AUDIO_INPUT_DEVICE")
            addAction("${packageName}.TRIGGER_BEHAVIOR_LOG")
            addAction("${packageName}.TRIGGER_REMINDER")
            addAction("${packageName}.TRIGGER_SYNC")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(launchReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(launchReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(launchReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("Sync Service")
            .setContentText("Sync is running.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        registerWithObserver()
        registerAppUsageTool()
        registerAudioInputTool()
        scheduleBehaviorLogging()
        scheduleProactiveReminders()

        return START_STICKY
    }

    private fun scheduleProactiveReminders() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.alif.sync.workers.ReminderWorker>(
            30, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ReminderWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleBehaviorLogging() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.alif.sync.workers.BehaviorLogWorker>(
            4, java.util.concurrent.TimeUnit.HOURS
        ).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BehaviorLogWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun registerWithObserver() {
        val tool = ExternalTool(
            id = "sync_tool",
            name = "Sync Manager",
            subtitle = "Control your data sync",
            iconName = "Sync",
            packageName = packageName
        )
        val intent = Intent("com.fahim.alyfobserver.REGISTER_TOOL")
        intent.putExtra("tool_json", Json.encodeToString(tool))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }

    private fun registerAppUsageTool() {
        val tool = ExternalTool(
            id = "app_usage_tool",
            name = "App Usage",
            subtitle = "See your daily app usage",
            iconName = "BarChart", // A generic icon name
            packageName = packageName
        )
        val intent = Intent("com.fahim.alyfobserver.REGISTER_TOOL")
        intent.putExtra("tool_json", Json.encodeToString(tool))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }

    private fun registerAudioInputTool() {
        val tool = ExternalTool(
            id = "audio_input_tool",
            name = "Audio Input",
            subtitle = "Choose microphone sources",
            iconName = "Settings", // Default to settings for now
            packageName = packageName
        )
        val intent = Intent("com.fahim.alyfobserver.REGISTER_TOOL")
        intent.putExtra("tool_json", Json.encodeToString(tool))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }

    private fun sendAudioInputContent() {
        val currentDevice = com.alif.sync.util.AudioInputManager.getCurrentInputDeviceName(this)
        val devices = com.alif.sync.util.AudioInputManager.getAudioInputDevices(this)
        val actions = devices.map { device ->
            ExternalToolAction(
                label = "Use ${device.name}",
                action = "${packageName}.SET_AUDIO_INPUT_DEVICE",
                extra = device.id.toString()
            )
        }

        val content = ExternalToolContent(
            toolId = "audio_input_tool",
            title = "Microphone Selection",
            description = "Current active device: $currentDevice\nSelect a microphone to use.",
            actions = actions
        )
        val intent = Intent("com.fahim.alyfobserver.UPDATE_TOOL_CONTENT")
        intent.putExtra("content_json", Json.encodeToString(content))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }

    private fun sendToolContent() {
        val content = ExternalToolContent(
            toolId = "sync_tool",
            title = "Sync Tools",
            description = "Manage your background synchronization and data updates.",
            actions = listOf(
                ExternalToolAction("Force Sync Now", "${packageName}.TRIGGER_SYNC"),
                ExternalToolAction("Analyze Behavior", "${packageName}.TRIGGER_BEHAVIOR_LOG"),
                ExternalToolAction("Check Reminders", "${packageName}.TRIGGER_REMINDER"),
                ExternalToolAction("View Logs", "${packageName}.VIEW_LOGS"),
                ExternalToolAction("Settings", "${packageName}.OPEN_SETTINGS")
            )
        )
        val intent = Intent("com.fahim.alyfobserver.UPDATE_TOOL_CONTENT")
        intent.putExtra("content_json", Json.encodeToString(content))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }

    private fun sendAppUsageContent() {
        val usageData = com.alif.sync.util.AppUsageTracker.getUsageStats(this)
        val description = usageData.sortedByDescending { it.totalTimeInForeground }
            .take(10)
            .joinToString("\n") {
                val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(it.totalTimeInForeground)
                val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(it.totalTimeInForeground) % 60
                "${it.packageName}: ${hours}h ${minutes}m"
            }

        val content = ExternalToolContent(
            toolId = "app_usage_tool",
            title = "App Usage",
            description = description,
            actions = emptyList()
        )
        val intent = Intent("com.fahim.alyfobserver.UPDATE_TOOL_CONTENT")
        intent.putExtra("content_json", Json.encodeToString(content))
        intent.setPackage("com.fahim.alyfobserver")
        sendBroadcast(intent)
    }
}
