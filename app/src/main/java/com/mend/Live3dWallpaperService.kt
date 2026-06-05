package com.mend

import android.content.Context
import android.content.res.AssetManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import android.view.MotionEvent
import kotlin.math.sqrt

/*
 * DISABLED: This service depends on the 'Textures' folder which has been removed to save space.
 * To re-enable, restore the textures and uncomment the service in AndroidManifest.xml.
 */
class Live3dWallpaperService : WallpaperService() {

    companion object {
        init {
            System.loadLibrary("mend_native")
        }
    }

    override fun onCreateEngine(): Engine {
        return Live3dWallpaperEngine()
    }

    inner class Live3dWallpaperEngine : Engine() {

        private val handlerThread = HandlerThread("WallpaperRenderer")
        private lateinit var handler: Handler

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        private var eglConfig: EGLConfig? = null

        private var lastX = 0f
        private var lastY = 0f
        private var lastDist = 0f

        // --- Native Method Declarations ---
        private external fun initEngine(assetManager: AssetManager, filePath: String)
        private external fun onSurfaceCreated()
        private external fun onSurfaceChanged(width: Int, height: Int)
        private external fun onDrawFrame()
        private external fun updateCamera(dx: Float, dy: Float, dz: Float)

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        val dx = lastX - event.x
                        val dy = lastY - event.y
                        handler.post { updateCamera(dx, dy, 1.0f) }
                        lastX = event.x
                        lastY = event.y
                    } else if (event.pointerCount == 2) {
                        val dist = spacing(event)
                        if (dist > 10f) {
                            if (lastDist > 0f) {
                                val dz = lastDist / dist
                                handler.post { updateCamera(0f, 0f, dz) }
                            }
                            lastDist = dist
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastDist = spacing(event)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    lastDist = 0f
                }
            }
        }

        private fun spacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return sqrt((x * x + y * y).toDouble()).toFloat()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handlerThread.start()
            handler = Handler(handlerThread.looper)
            handler.post {
                initEGL(holder)
                onSurfaceCreated() // Call native onSurfaceCreated

                // Initialize the native engine with the asset manager and file path
                val sharedPreferences = applicationContext.getSharedPreferences("mend_prefs", Context.MODE_PRIVATE)
                val activeWld = sharedPreferences.getString("active_wld_file", "world1.wld") ?: "world1.wld"
                initEngine(applicationContext.assets, activeWld)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post {
                onSurfaceChanged(width, height) // Call native onSurfaceChanged
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            handler.post {
                destroyEGL()
            }
            handlerThread.quitSafely()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                handler.post { startRendering() }
            } else {
                handler.post { stopRendering() }
            }
        }

        private fun initEGL(holder: SurfaceHolder) {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
                EGL14.EGL_NONE
            )
            val numConfigs = IntArray(1)
            val configs = arrayOfNulls<EGLConfig>(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)
            eglConfig = configs[0]

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, // Request GLES 3.0 context
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, holder, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun destroyEGL() {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }

        private fun startRendering() {
            handler.post(object : Runnable {
                override fun run() {
                    if (isVisible) {
                        // LOGI is not available in Kotlin directly, use android.util.Log
                        // android.util.Log.d("WallpaperService", "Heartbeat: onDrawFrame")
                        onDrawFrame() // Call native onDrawFrame
                        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                             android.util.Log.e("WallpaperService", "eglSwapBuffers failed: " + EGL14.eglGetError())
                        }
                        handler.postDelayed(this, 16) // ~60 FPS
                    }
                }
            })
        }

        private fun stopRendering() {
            // The rendering loop will stop automatically when isVisible becomes false.
        }
    }
}
