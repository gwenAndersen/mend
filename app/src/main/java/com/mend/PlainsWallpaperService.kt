package com.mend

import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

/*
 * DISABLED: This service depends on the 'Textures' folder which has been removed to save space.
 * To re-enable, restore the textures and uncomment the service in AndroidManifest.xml.
 */
class PlainsWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return PlainsWallpaperEngine()
    }

    inner class PlainsWallpaperEngine : Engine() {

        private var glSurfaceView: PlainsGLSurfaceView? = null
        private var renderer: PlainsGLRenderer? = null

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            glSurfaceView = PlainsGLSurfaceView(applicationContext).apply {
                setEGLContextClientVersion(2)
            }
            renderer = PlainsGLRenderer(applicationContext)
            glSurfaceView?.setRenderer(renderer)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                glSurfaceView?.onResume()
            } else {
                glSurfaceView?.onPause()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            glSurfaceView?.surfaceCreated(holder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            glSurfaceView?.surfaceChanged(holder, format, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            glSurfaceView?.surfaceDestroyed(holder)
        }

        override fun onDestroy() {
            super.onDestroy()
            glSurfaceView?.onPause()
            glSurfaceView = null
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixels: Int, yPixels: Int) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixels, yPixels)
            // Map xOffset (0.0 to 1.0) to a parallax range
            renderer?.setOffset(xOffset - 0.5f, 0f) 
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
        }

        inner class PlainsGLSurfaceView(context: android.content.Context) : GLSurfaceView(context) {
            override fun getHolder(): SurfaceHolder {
                return surfaceHolder
            }

            fun onDestroy() {
                super.onDetachedFromWindow()
            }
        }
    }
}
