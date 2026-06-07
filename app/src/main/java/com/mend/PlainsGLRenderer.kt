package com.mend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PlainsGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val quadVertices = floatArrayOf(
        -1.0f,  1.0f,  // top left
        -1.0f, -1.0f,  // bottom left
         1.0f, -1.0f,  // bottom right
         1.0f,  1.0f   // top right
    )

    private val textureCoordinates = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer
    private lateinit var drawListBuffer: java.nio.ShortBuffer

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec2 a_texCoord;
        varying vec2 v_texCoord;
        void main() {
          gl_Position = uMVPMatrix * vPosition;
          v_texCoord = a_texCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D u_texture;
        uniform float u_alpha;
        varying vec2 v_texCoord;
        void main() {
          vec4 color = texture2D(u_texture, v_texCoord);
          gl_FragColor = vec4(color.rgb, color.a * u_alpha);
        }
    """.trimIndent()

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureUniformHandle: Int = 0
    private var alphaUniformHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    private var offsetX = 0f
    private var offsetY = 0f

    private var layer1Texture = 0
    private var layer2Texture = 0
    private var layer3Texture = 0
    private var layer4Texture = 0
    private var img1Texture = 0
    private var img2Texture = 0
    private var img3Texture = 0
    private var img4Texture = 0
    private var screenWidth = 0
    private var screenHeight = 0

    private var showImages = true
    private var showFps = false
    
    // UI controls
    private var uiTexture = 0
    private val uiQuadVertices = floatArrayOf(
        0.5f,  0.95f,  // top left of UI area (normalized to screen ratio later)
        0.5f,  0.80f,  // bottom left
        0.95f, 0.80f,  // bottom right
        0.95f, 0.95f   // top right
    )
    private lateinit var uiVertexBuffer: FloatBuffer

    // FPS counter variables
    private var frameCount = 0
    private var lastFpsUpdateTime = 0L
    private var currentFps = 0
    private var fpsTexture = 0
    private val fpsQuadVertices = floatArrayOf(
        -0.95f,  0.95f,  // top left
        -0.95f,  0.85f,  // bottom left
        -0.65f,  0.85f,  // bottom right
        -0.65f,  0.95f   // top right
    )
    private lateinit var fpsVertexBuffer: FloatBuffer

    private lateinit var sharedPreferences: android.content.SharedPreferences

    fun setOffset(x: Float, y: Float) {
        offsetX = x
        offsetY = y
    }

    fun updateSettings(showImages: Boolean, showFps: Boolean) {
        this.showImages = showImages
        this.showFps = showFps
        updateUiTexture()
    }

    fun handleTouch(x: Float, y: Float): Boolean {
        if (screenWidth == 0 || screenHeight == 0) return false
        
        val ratio = screenWidth.toFloat() / screenHeight.toFloat()
        val orthoX = (x / screenWidth * 2f - 1f) * ratio
        val orthoY = 1f - (y / screenHeight * 2f)
        
        // UI area in ortho: (0.5 * ratio, 0.80) to (0.95 * ratio, 0.95)
        if (orthoX > 0.5f * ratio && orthoX < 0.95f * ratio && orthoY > 0.80f && orthoY < 0.95f) {
            // Check if Left half (IMG) or Right half (FPS)
            if (orthoX < 0.725f * ratio) {
                showImages = !showImages
                sharedPreferences.edit().putBoolean("plains_show_images", showImages).apply()
            } else {
                showFps = !showFps
                sharedPreferences.edit().putBoolean("plains_show_fps", showFps).apply()
            }
            updateUiTexture()
            return true
        }
        return false
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f) // Transparent/Black base
        setupBuffers()
        setupShaders()
        
        sharedPreferences = context.getSharedPreferences("mend_prefs", android.content.Context.MODE_PRIVATE)
        showImages = sharedPreferences.getBoolean("plains_show_images", true)
        showFps = sharedPreferences.getBoolean("plains_show_fps", false)

        // Load the provided vector drawables into 2000x1600 bitmaps to maintain aspect ratio
        layer1Texture = loadVectorTexture(R.drawable.plains_layer_1)
        layer2Texture = loadVectorTexture(R.drawable.plains_layer_2)
        layer3Texture = loadVectorTexture(R.drawable.plains_layer_3)
        layer4Texture = loadVectorTexture(R.drawable.plains_layer_4)
        img1Texture = loadVectorTexture(R.drawable.img_1)
        img2Texture = loadVectorTexture(R.drawable.img_2)
        img3Texture = loadVectorTexture(R.drawable.img_3)
        img4Texture = loadVectorTexture(R.drawable.img_4)
        
        // Initialize extra buffers
        fpsVertexBuffer = ByteBuffer.allocateDirect(fpsQuadVertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(fpsQuadVertices); position(0) }
        }
        uiVertexBuffer = ByteBuffer.allocateDirect(uiQuadVertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(uiQuadVertices); position(0) }
        }
        
        updateUiTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        
        val ratio = width.toFloat() / height.toFloat()
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
        
        // Adjust UI buttons position based on ratio
        val uiVertices = floatArrayOf(
            0.5f * ratio,  0.95f,
            0.5f * ratio,  0.80f,
            0.95f * ratio, 0.80f,
            0.95f * ratio, 0.95f
        )
        uiVertexBuffer.clear()
        uiVertexBuffer.put(uiVertices)
        uiVertexBuffer.position(0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (layer1Texture == 0 || layer2Texture == 0 || layer3Texture == 0) return

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Calculate scaling for Center Crop
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val textureRatio = 2000f / 1600f
        
        var baseScaleX = 1f
        var baseScaleY = 1f
        if (screenRatio > textureRatio) {
            baseScaleX = screenRatio
            baseScaleY = screenRatio / textureRatio
        } else {
            baseScaleX = textureRatio
            baseScaleY = 1f
        }

        val maxOffset = baseScaleX - screenRatio

        // Draw Layers without Parallax (speed 0.0)
        drawLayer(layer1Texture, 0.0f, baseScaleX, baseScaleY, maxOffset)
        drawLayer(layer2Texture, 0.0f, baseScaleX, baseScaleY, maxOffset)
        drawLayer(layer3Texture, 0.0f, baseScaleX, baseScaleY, maxOffset)
        drawLayer(layer4Texture, 0.0f, baseScaleX, baseScaleY, maxOffset, 0f, 0f, 0.36f, 1f, 0.8f)

        if (showImages) {
            drawLayer(img1Texture, 0.0f, baseScaleX, baseScaleY, maxOffset, -0.137f, 0.855625f, 0.086f, 0.104375f)
            drawLayer(img2Texture, 0.0f, baseScaleX, baseScaleY, maxOffset, 0.1395f, 0.238125f, 0.3375f, 0.308125f)
            drawLayer(img3Texture, 0.0f, baseScaleX, baseScaleY, maxOffset, -0.018f, -0.3175f, 0.342f, 0.2475f)
            drawLayer(img4Texture, 0.0f, baseScaleX, baseScaleY, maxOffset, -0.0185f, -0.1725f, 0.0535f, 0.04375f)
        }
        
        drawUI()
        if (showFps) drawFps()

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawUI() {
        if (uiTexture == 0) return
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, uiVertexBuffer)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uiTexture)
        GLES20.glUniform1f(alphaUniformHandle, 0.8f)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
    }

    private fun updateUiTexture() {
        val bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = true; textSize = 40f; textAlign = Paint.Align.CENTER; style = Paint.Style.FILL }
        
        // IMG button
        paint.color = if (showImages) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        canvas.drawRect(10f, 10f, 120f, 118f, paint)
        paint.color = Color.WHITE; canvas.drawText("IMG", 65f, 75f, paint)
        
        // FPS button
        paint.color = if (showFps) Color.parseColor("#2196F3") else Color.parseColor("#9E9E9E")
        canvas.drawRect(136f, 10f, 246f, 118f, paint)
        paint.color = Color.WHITE; canvas.drawText("FPS", 191f, 75f, paint)
        
        if (uiTexture == 0) uiTexture = loadTexture(bitmap)
        else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, uiTexture); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) }
        bitmap.recycle()
    }

    private fun drawFps() {
        val currentTime = System.currentTimeMillis()
        frameCount++
        if (currentTime - lastFpsUpdateTime >= 500) {
            currentFps = (frameCount * 1000 / (currentTime - lastFpsUpdateTime).toInt())
            frameCount = 0; lastFpsUpdateTime = currentTime
            updateFpsTexture("FPS: $currentFps")
        }
        if (fpsTexture != 0) {
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, fpsVertexBuffer)
            Matrix.setIdentityM(viewMatrix, 0); Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fpsTexture); GLES20.glUniform1f(alphaUniformHandle, 1.0f)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        }
    }

    private fun updateFpsTexture(text: String) {
        val bitmap = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.WHITE; textSize = 40f; isAntiAlias = true; textAlign = Paint.Align.LEFT }
        canvas.drawText(text, 10f, 50f, paint)
        if (fpsTexture == 0) fpsTexture = loadTexture(bitmap)
        else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fpsTexture); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) }
        bitmap.recycle()
    }

    private fun drawLayer(textureId: Int, parallaxSpeed: Float, baseScaleX: Float, baseScaleY: Float, maxOffset: Float, posX: Float = 0f, posY: Float = 0f, scaleX: Float = 1f, scaleY: Float = 1f, alpha: Float = 1f) {
        Matrix.setIdentityM(viewMatrix, 0)
        // Set translationX to 0 to disable all horizontal scrolling/parallax
        val translationX = posX * baseScaleX
        Matrix.translateM(viewMatrix, 0, translationX, posY * baseScaleY, 0f)
        Matrix.scaleM(viewMatrix, 0, baseScaleX * scaleX, baseScaleY * scaleY, 1f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureUniformHandle, 0); GLES20.glUniform1f(alphaUniformHandle, alpha)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
    }

    private fun loadVectorTexture(resId: Int): Int {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return 0
        val width = 2000; val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); drawable.setBounds(0, 0, width, height); drawable.draw(canvas)
        val textureId = loadTexture(bitmap); bitmap.recycle()
        return textureId
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return textureIds[0]
    }

    private fun setupBuffers() {
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(quadVertices)
                position(0)
            }
        }

        textureBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(textureCoordinates)
                position(0)
            }
        }

        drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(drawOrder)
                position(0)
            }
        }
    }

    private fun setupShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_texCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "u_texture")
        alphaUniformHandle = GLES20.glGetUniformLocation(program, "u_alpha")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
