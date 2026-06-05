package com.alif.sync.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.concurrent.TimeUnit

data class AppUsageInfo(
    val packageName: String,
    val totalTimeInForeground: Long
)

object AppUsageTracker {

    fun getUsageStats(context: Context): List<AppUsageInfo> {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return getUsageStats(context, cal.timeInMillis, System.currentTimeMillis())
    }

    fun getTodayUsageStats(context: Context): List<AppUsageInfo> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return getUsageStats(context, cal.timeInMillis, System.currentTimeMillis())
    }

    private fun getUsageStats(context: Context, startTime: Long, endTime: Long): List<AppUsageInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val queryUsageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        return queryUsageStats.map {
            AppUsageInfo(
                it.packageName,
                it.totalTimeInForeground
            )
        }.filter { it.totalTimeInForeground > 0 }
    }
}
