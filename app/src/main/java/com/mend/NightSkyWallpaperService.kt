package com.mend
import android.content.SharedPreferences
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class NightSkyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return NightSkyWallpaperEngine()
    }

    inner class NightSkyWallpaperEngine : Engine() {

        private var glSurfaceView: NightSkyGLSurfaceView? = null
        private var renderer: NightSkyGLRenderer? = null
        private lateinit var gestureDetector: android.view.GestureDetector

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)

            renderer = NightSkyGLRenderer(applicationContext)
            glSurfaceView = NightSkyGLSurfaceView(applicationContext).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
            }

            gestureDetector = android.view.GestureDetector(applicationContext, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    renderer?.toggleMenu()
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    return renderer?.handleTouch(e.x, e.y) ?: false
                }
            })
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                glSurfaceView?.onResume()
            } else {
                glSurfaceView?.onPause()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            glSurfaceView?.onPause()
            glSurfaceView = null
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            glSurfaceView?.surfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            glSurfaceView?.surfaceDestroyed(holder)
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }

        inner class NightSkyGLSurfaceView(context: android.content.Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder {
                return surfaceHolder
            }
        }
    }
}
