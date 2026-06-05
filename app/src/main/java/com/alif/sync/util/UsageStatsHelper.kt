package com.alif.sync.util

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.util.Calendar

object UsageStatsHelper {

    fun grantUsageStatsPermission(context: Context): Boolean {
        val packageName = context.packageName
        val cmd = "appops set $packageName GET_USAGE_STATS allow"
        
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e("UsageStatsHelper", "Failed to grant usage stats permission via Shizuku", e)
            false
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    data class AppUsageInfo(
        val packageName: String,
        val appName: String,
        val totalTimeInForeground: Long,
        val lastTimeUsed: Long,
        val icon: android.graphics.drawable.Drawable?
    )

    fun getUsageStats(context: Context): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -1) // Last 24 hours
        val startTime = calendar.timeInMillis

        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (queryUsageStats == null || queryUsageStats.isEmpty()) {
            return emptyList()
        }

        val packageManager = context.packageManager
        val appUsageList = mutableListOf<AppUsageInfo>()

        for (usageStats in queryUsageStats) {
            if (usageStats.totalTimeInForeground > 0) {
                try {
                    val appInfo = packageManager.getApplicationInfo(usageStats.packageName, 0)
                    // Filter out system apps if needed, but for now show user apps or updated system apps
                    /*
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                        continue
                    }
                    */
                    
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    
                    appUsageList.add(
                        AppUsageInfo(
                            packageName = usageStats.packageName,
                            appName = appName,
                            totalTimeInForeground = usageStats.totalTimeInForeground,
                            lastTimeUsed = usageStats.lastTimeUsed,
                            icon = icon
                        )
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    // App might be uninstalled
                    Log.w("UsageStatsHelper", "Package not found: ${usageStats.packageName}")
                }
            }
        }
        
        return appUsageList.sortedByDescending { it.lastTimeUsed }
    }
}
