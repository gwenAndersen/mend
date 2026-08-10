package com.mend

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WallpaperPreviewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageView = ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(imageView)

        WallpaperUtils.updateCurrentWallpaper(this)
        val prefs = WallpaperUtils.getPrefs(this)
        val uriString = prefs.getString("current_wallpaper_uri", null)

        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = WallpaperUtils.openStreamFromUri(this, uri)
                if (inputStream != null) {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    imageView.setImageBitmap(bitmap)
                } else {
                    Toast.makeText(this, "Could not open wallpaper stream", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading wallpaper: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "No wallpaper scheduled", Toast.LENGTH_SHORT).show()
        }
    }
}
