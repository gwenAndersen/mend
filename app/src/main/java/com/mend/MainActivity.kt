package com.mend

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }

        val btnSetWallpaper = Button(this).apply {
            text = "Set Terraria Live Wallpaper"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, Live3dWallpaperService::class.java))
                }
                startActivity(intent)
            }
        }

        val btnSettings = Button(this).apply {
            text = "Open Settings"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, MendSettingsActivity::class.java))
            }
        }

        layout.addView(btnSetWallpaper)
        layout.addView(btnSettings)
        setContentView(layout)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            // For Android 13+, these are more granular, but for simple file writing to /sdcard/mend
            // MANAGE_EXTERNAL_STORAGE might be better if the user wants it in a custom folder.
            // But we'll request what we can.
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), 101)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }
}
