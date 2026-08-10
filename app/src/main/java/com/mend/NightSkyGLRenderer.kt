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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class NightSkyGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Particle system
    private var particleSystem: AtmosphericParticleSystem? = null
    private var particleProgram: Int = 0
    private var particlePositionHandle: Int = 0
    private var particleDataHandle: Int = 0 // x, y, alpha, state
    private var particleMvpMatrixHandle: Int = 0
    private lateinit var particleBuffer: FloatBuffer

    private val particleVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec2 vPosition;
        attribute vec2 aData; // x: alpha, y: state (ignored for color now)
        varying float vAlpha;
        void main() {
          gl_Position = uMVPMatrix * vec4(vPosition, 0.0, 1.0);
          gl_PointSize = 5.0; // Fixed size like 2side's strokeWidth
          vAlpha = aData.x;
        }
    """.trimIndent()

    private val particleFragmentShaderCode = """
        precision mediump float;
        varying float vAlpha;
        void main() {
          float dist = distance(gl_PointCoord, vec2(0.5));
          if (dist > 0.5) discard;
          
          // Pure white like 2side
          gl_FragColor = vec4(1.0, 1.0, 1.0, vAlpha);
        }
    """.trimIndent()

    private val particleProjectionMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var sharedPreferences: android.content.SharedPreferences

    // UI controls
    private var menuVisible = false
    private var menuTexture = 0
    private val menuQuadVertices = floatArrayOf(
        -0.4f,  0.4f,  // top left
        -0.4f, -0.4f,  // bottom left
         0.4f, -0.4f,  // bottom right
         0.4f,  0.4f   // top right
    )
    private lateinit var menuVertexBuffer: FloatBuffer
    private val textureCoordinates = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )
    private lateinit var textureBuffer: FloatBuffer
    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)
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

    private var buffersInitialized = false
    private var menuNeedsUpdate = false

    fun toggleMenu() {
        android.util.Log.d("NightSkyGL", "toggleMenu called. Previous state: $menuVisible")
        menuVisible = !menuVisible
        if (menuVisible) menuNeedsUpdate = true
    }

    fun handleTouch(x: Float, y: Float): Boolean {
        particleSystem?.triggerRipple(x, y)

        if (!menuVisible || screenWidth == 0 || screenHeight == 0) return false
        
        val ratio = screenWidth.toFloat() / screenHeight.toFloat()
        val orthoX = (x / screenWidth * 2f - 1f) * ratio
        val orthoY = 1f - (y / screenHeight * 2f)
        
        android.util.Log.d("NightSkyGL", "Menu touch attempt at: $orthoX, $orthoY")

        if (orthoX > -0.4f && orthoX < 0.4f && orthoY > -0.4f && orthoY < 0.4f) {
            val canvasTouchY = (0.4f - orthoY) / 0.8f * 1024f
            when {
                canvasTouchY in 250f..440f -> {
                    val current = sharedPreferences.getBoolean("dark_wave_enabled", true)
                    val next = !current
                    sharedPreferences.edit().putBoolean("dark_wave_enabled", next).apply()
                    particleSystem?.isDarkWaveEnabled = next
                    if (next) {
                        sharedPreferences.edit().putBoolean("cosmic_wave_enabled", false).apply()
                        particleSystem?.isCosmicWaveEnabled = false
                    }
                    menuNeedsUpdate = true
                }
                canvasTouchY in 440f..620f -> {
                    val current = sharedPreferences.getBoolean("cosmic_wave_enabled", false)
                    val next = !current
                    sharedPreferences.edit().putBoolean("cosmic_wave_enabled", next).apply()
                    particleSystem?.isCosmicWaveEnabled = next
                    if (next) {
                        sharedPreferences.edit().putBoolean("dark_wave_enabled", false).apply()
                        particleSystem?.isDarkWaveEnabled = false
                    }
                    menuNeedsUpdate = true
                }
                canvasTouchY in 620f..800f -> {
                    val current = sharedPreferences.getBoolean("ripple_enabled", true)
                    val next = !current
                    sharedPreferences.edit().putBoolean("ripple_enabled", next).apply()
                    particleSystem?.isRippleEnabled = next
                    menuNeedsUpdate = true
                }
                canvasTouchY > 800f -> {
                    menuVisible = false
                }
            }
            return true
        }
        return false
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0117f, 0.0274f, 0.0705f, 1.0f) // Exact #030712
        setupBuffers()
        setupShaders()
        setupParticleShaders()
        sharedPreferences = context.getSharedPreferences("mend_prefs", Context.MODE_PRIVATE)
        buffersInitialized = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        
        particleSystem = AtmosphericParticleSystem(width, height).apply {
            isRippleEnabled = sharedPreferences.getBoolean("ripple_enabled", true)
            isDarkWaveEnabled = sharedPreferences.getBoolean("dark_wave_enabled", true)
            isCosmicWaveEnabled = sharedPreferences.getBoolean("cosmic_wave_enabled", false)
        }
        
        particleBuffer = ByteBuffer.allocateDirect(particleSystem!!.particleCount * 4 * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer()
        }
        
        Matrix.orthoM(particleProjectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
        
        val ratio = width.toFloat() / height.toFloat()
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
        
        val menuVertices = floatArrayOf(
            -0.4f,  0.4f,
            -0.4f, -0.4f,
             0.4f, -0.4f,
             0.4f,  0.4f
        )
        menuVertexBuffer.clear()
        menuVertexBuffer.put(menuVertices)
        menuVertexBuffer.position(0)
        
        menuNeedsUpdate = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawParticles()
        if (menuVisible) {
            if (menuNeedsUpdate) {
                updateMenuTexture()
                menuNeedsUpdate = false
            }
            drawMenu()
        }
    }

    private fun drawMenu() {
        if (menuTexture == 0) return
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, menuVertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, menuTexture)
        GLES20.glUniform1f(alphaUniformHandle, 0.9f)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
        
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun updateMenuTexture() {
        val size = 1024
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint().apply { isAntiAlias = true }
        
        // 1. Background (Glassmorphism look)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#EE111827") // Deep dark blue-grey
        canvas.drawRoundRect(20f, 20f, size - 20f, size - 20f, 80f, 80f, paint)
        
        // 2. Glowing Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = Color.parseColor("#44FFFFFF")
        canvas.drawRoundRect(24f, 24f, size - 24f, size - 24f, 76f, 76f, paint)
        
        // 3. Title
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("Mend Particles", (size / 2).toFloat(), 180f, paint)
        
        // Divider under title
        paint.color = Color.parseColor("#33FFFFFF")
        paint.strokeWidth = 4f
        canvas.drawLine(100f, 220f, size - 100f, 220f, paint)
        
        // 4. Menu Items
        val darkEnabled = sharedPreferences.getBoolean("dark_wave_enabled", true)
        val cosmicEnabled = sharedPreferences.getBoolean("cosmic_wave_enabled", false)
        val rippleEnabled = sharedPreferences.getBoolean("ripple_enabled", true)
        
        val items = listOf(
            Pair("Dark Wave", darkEnabled),
            Pair("Cosmic Wave", cosmicEnabled),
            Pair("Ripples", rippleEnabled),
            Pair("Close Menu", null)
        )
        
        paint.isFakeBoldText = false
        paint.textSize = 60f
        
        for (i in items.indices) {
            val yBase = 350f + i * 180f
            val item = items[i]
            
            // Text Alignment Left
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.WHITE
            canvas.drawText(item.first, 120f, yBase, paint)
            
            // Status Indicator or Action
            if (item.second != null) {
                // Draw a stylized toggle switch
                val toggleX = size - 250f
                val toggleY = yBase - 25f
                
                // Track
                paint.style = Paint.Style.FILL
                paint.color = if (item.second!!) Color.parseColor("#1E3A8A") else Color.parseColor("#374151")
                canvas.drawRoundRect(toggleX, toggleY, toggleX + 130f, toggleY + 60f, 30f, 30f, paint)
                
                // Thumb
                paint.color = if (item.second!!) Color.parseColor("#60A5FA") else Color.LTGRAY
                val thumbX = if (item.second!!) toggleX + 85f else toggleX + 15f
                canvas.drawCircle(thumbX + 15f, toggleY + 30f, 25f, paint)
            } else {
                // "Close Menu" styled as a button
                paint.textAlign = Paint.Align.CENTER
                paint.color = Color.parseColor("#9CA3AF")
                paint.textSize = 50f
                canvas.drawText(item.first, (size / 2).toFloat(), yBase + 100f, paint)
            }
        }
        
        if (menuTexture == 0) menuTexture = loadTexture(bitmap)
        else { GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, menuTexture); GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0) }
        bitmap.recycle()
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
        menuVertexBuffer = ByteBuffer.allocateDirect(menuQuadVertices.size * 4).run {
            order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(menuQuadVertices); position(0) }
        }
        textureBuffer = ByteBuffer.allocateDirect(textureCoordinates.size * 4).run {
            order(ByteOrder.nativeOrder()); asFloatBuffer().apply { put(textureCoordinates); position(0) }
        }
        drawListBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2).run {
            order(ByteOrder.nativeOrder()); asShortBuffer().apply { put(drawOrder); position(0) }
        }
    }

    private fun setupShaders() {
        val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vShader); GLES20.glAttachShader(this, fShader); GLES20.glLinkProgram(this)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_texCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "u_texture")
        alphaUniformHandle = GLES20.glGetUniformLocation(program, "u_alpha")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    private fun setupParticleShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, particleVertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, particleFragmentShaderCode)
        particleProgram = GLES20.glCreateProgram().apply {
            GLES20.glAttachShader(this, vertexShader)
            GLES20.glAttachShader(this, fragmentShader)
            GLES20.glLinkProgram(this)
        }
        particlePositionHandle = GLES20.glGetAttribLocation(particleProgram, "vPosition")
        particleDataHandle = GLES20.glGetAttribLocation(particleProgram, "aData")
        particleMvpMatrixHandle = GLES20.glGetUniformLocation(particleProgram, "uMVPMatrix")
    }

    private fun drawParticles() {
        val system = particleSystem ?: return
        system.update()

        particleBuffer.clear()
        for (i in 0 until system.particleCount) {
            particleBuffer.put(system.px[i])
            particleBuffer.put(system.py[i])
            particleBuffer.put(system.palpha[i])
            val state = if (system.isGlowing[i] || system.cosmicEnergy[i] > 0.8f) 2.0f else if (system.isDark[i] || system.cosmicEnergy[i] > 0f) 1.0f else 0.0f
            particleBuffer.put(state)
        }
        particleBuffer.position(0)

        GLES20.glUseProgram(particleProgram)
        GLES20.glUniformMatrix4fv(particleMvpMatrixHandle, 1, false, particleProjectionMatrix, 0)

        GLES20.glEnableVertexAttribArray(particlePositionHandle)
        GLES20.glEnableVertexAttribArray(particleDataHandle)

        particleBuffer.position(0)
        GLES20.glVertexAttribPointer(particlePositionHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, particleBuffer)
        
        particleBuffer.position(2)
        GLES20.glVertexAttribPointer(particleDataHandle, 2, GLES20.GL_FLOAT, false, 4 * 4, particleBuffer)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, system.particleCount)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(particlePositionHandle)
        GLES20.glDisableVertexAttribArray(particleDataHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
