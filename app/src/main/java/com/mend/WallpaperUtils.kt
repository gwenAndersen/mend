package com.mend

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar

object WallpaperUtils {
    private const val PREFS_NAME = "mend_prefs"
    private const val KEY_SCHEDULES = "wallpaper_schedules"
    private const val KEY_CURRENT_URI = "current_wallpaper_uri"
    private const val SCHEDULES_FILE_PATH = "/sdcard/mend/schedules.json"

    private val gson = GsonBuilder()
        .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
        .create()

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadSchedules(context: Context): List<WallpaperSchedule> {
        val file = File(SCHEDULES_FILE_PATH)
        if (file.exists()) {
            try {
                val json = file.readText()
                if (json.isNotBlank()) {
                    android.util.Log.d("WallpaperUtils", "Loading schedules from file: $json")
                    val type = object : TypeToken<List<WallpaperSchedule>>() {}.type
                    val list: List<WallpaperSchedule>? = gson.fromJson(json, type)
                    if (list != null) return list
                }
            } catch (e: Exception) {
                android.util.Log.e("WallpaperUtils", "Error reading schedules from file", e)
            }
        }

        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_SCHEDULES, null)
        android.util.Log.d("WallpaperUtils", "Loading schedules from JSON: $json")
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<WallpaperSchedule>>() {}.type
            val list: List<WallpaperSchedule>? = gson.fromJson(json, type)
            list ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("WallpaperUtils", "Error parsing schedules", e)
            emptyList()
        }
    }

    fun saveSchedules(context: Context, schedules: List<WallpaperSchedule>) {
        val json = gson.toJson(schedules)
        
        // Save to file
        try {
            val file = File(SCHEDULES_FILE_PATH)
            file.parentFile?.mkdirs()
            file.writeText(json)
            android.util.Log.d("WallpaperUtils", "Saved schedules to file: $SCHEDULES_FILE_PATH")
        } catch (e: Exception) {
            android.util.Log.e("WallpaperUtils", "Error saving schedules to file", e)
        }

        val prefs = getPrefs(context)
        android.util.Log.d("WallpaperUtils", "Saving schedules to prefs: $json")
        prefs.edit().putString(KEY_SCHEDULES, json).commit()
        updateCurrentWallpaper(context)
    }

    fun updateCurrentWallpaper(context: Context) {
        val schedules = loadSchedules(context)
        if (schedules.isEmpty()) {
            android.util.Log.w("WallpaperUtils", "No schedules found to update.")
            return
        }

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        android.util.Log.d("WallpaperUtils", "Updating wallpaper. Current time: $currentHour:$currentMinute")

        var bestSchedule: WallpaperSchedule? = null
        for (schedule in schedules) {
            if (schedule.hour < currentHour || (schedule.hour == currentHour && schedule.minute <= currentMinute)) {
                if (bestSchedule == null || schedule.hour > bestSchedule.hour || (schedule.hour == bestSchedule.hour && schedule.minute > bestSchedule.minute)) {
                    bestSchedule = schedule
                }
            }
        }

        if (bestSchedule == null) {
            android.util.Log.d("WallpaperUtils", "No schedule passed today. Wrapping around.")
            bestSchedule = schedules.maxWithOrNull(compareBy({ it.hour }, { it.minute }))
        }

        bestSchedule?.let {
            val prefs = getPrefs(context)
            val currentUri = prefs.getString(KEY_CURRENT_URI, null)
            if (currentUri != it.uri.toString()) {
                android.util.Log.d("WallpaperUtils", "Setting current wallpaper URI to: ${it.uri}")
                prefs.edit().putString(KEY_CURRENT_URI, it.uri.toString()).commit()
            } else {
                android.util.Log.d("WallpaperUtils", "Wallpaper URI already matches: $currentUri")
            }
        }
    }
}
