package com.mend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WallpaperScheduler : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("WallpaperScheduler", "onReceive: action=${intent.action}")
        if (intent.action == Intent.ACTION_TIME_TICK || 
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            
            WallpaperUtils.updateCurrentWallpaper(context)
        }
    }
}
