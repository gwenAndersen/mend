package com.mend

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences

/*
 * DISABLED: This service depends on the 'Textures' folder which has been removed to save space.
 * To re-enable, restore the textures and uncomment the service in AndroidManifest.xml.
 */
class ScheduledImageWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return ImageEngine()
    }

    inner class ImageEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {

        private val handler = Handler(Looper.getMainLooper())
        private var currentBitmap: Bitmap? = null
        private lateinit var prefs: SharedPreferences
        private val scheduler = WallpaperScheduler()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs = WallpaperUtils.getPrefs(this@ScheduledImageWallpaperService)
            prefs.registerOnSharedPreferenceChangeListener(this)
            
            // Register scheduler to update URI automatically
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(scheduler, filter)
            
            // Trigger an immediate check to set the first wallpaper
            WallpaperUtils.updateCurrentWallpaper(this@ScheduledImageWallpaperService)
            
            updateBitmap()
        }

        override fun onDestroy() {
            super.onDestroy()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            try {
                unregisterReceiver(scheduler)
            } catch (e: Exception) {
                // Ignore
            }
            currentBitmap?.recycle()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) {
                draw()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == "current_wallpaper_uri") {
                updateBitmap()
            }
        }

        private fun updateBitmap() {
            val uriString = prefs.getString("current_wallpaper_uri", null)
            android.util.Log.d("ImageWallpaper", "updateBitmap called. current_wallpaper_uri: $uriString")
            if (uriString != null) {
                try {
                    val uri = Uri.parse(uriString)
                    android.util.Log.d("ImageWallpaper", "Parsing URI: $uri")
                    val inputStream = WallpaperUtils.openStreamFromUri(this@ScheduledImageWallpaperService, uri)
                    if (inputStream == null) {
                        android.util.Log.e("ImageWallpaper", "InputStream is null for URI: $uri")
                        return
                    }
                    val newBitmap = BitmapFactory.decodeStream(inputStream)
                    if (newBitmap == null) {
                        android.util.Log.e("ImageWallpaper", "Failed to decode bitmap for URI: $uri")
                    } else {
                        android.util.Log.d("ImageWallpaper", "Successfully decoded bitmap: ${newBitmap.width}x${newBitmap.height}")
                    }
                    handler.post {
                        currentBitmap?.recycle()
                        currentBitmap = newBitmap
                        draw()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImageWallpaper", "Error loading bitmap", e)
                }
            } else {
                android.util.Log.w("ImageWallpaper", "current_wallpaper_uri is null in SharedPreferences")
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val bitmap = currentBitmap
                    if (bitmap != null) {
                        android.util.Log.d("ImageWallpaper", "Drawing bitmap...")
                        drawBitmapCentred(canvas, bitmap)
                    } else {
                        android.util.Log.d("ImageWallpaper", "Drawing fallback text (bitmap is null)")
                        canvas.drawColor(Color.BLACK)
                        val paint = Paint().apply {
                            color = Color.WHITE
                            textSize = 40f
                            textAlign = Paint.Align.CENTER
                        }
                        val uriString = prefs.getString("current_wallpaper_uri", "null")
                        canvas.drawText("No image selected (URI: $uriString)", canvas.width / 2f, canvas.height / 2f, paint)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ImageWallpaper", "Error during draw", e)
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawBitmapCentred(canvas: Canvas, bitmap: Bitmap) {
            val canvasWidth = canvas.width.toFloat()
            val canvasHeight = canvas.height.toFloat()
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            val scale = Math.max(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
            val scaledWidth = bitmapWidth * scale
            val scaledHeight = bitmapHeight * scale

            val left = (canvasWidth - scaledWidth) / 2
            val top = (canvasHeight - scaledHeight) / 2

            val destRect = Rect(left.toInt(), top.toInt(), (left + scaledWidth).toInt(), (top + scaledHeight).toInt())
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}
