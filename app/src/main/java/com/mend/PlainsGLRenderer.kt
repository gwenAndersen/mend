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
        varying vec2 v_texCoord;
        void main() {
          gl_FragColor = texture2D(u_texture, v_texCoord);
        }
    """.trimIndent()

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureUniformHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    private var offsetX = 0f
    private var offsetY = 0f

    private var layerTexture = 0
    private var screenWidth = 0
    private var screenHeight = 0

    fun setOffset(x: Float, y: Float) {
        offsetX = x
        offsetY = y
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f) // Transparent/Black base
        setupBuffers()
        setupShaders()
        
        // Load the provided vector drawable
        layerTexture = loadVectorTexture(R.drawable.plains_layer_1)
        android.util.Log.d("PlainsRenderer", "Layer texture loaded: $layerTexture")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        
        // Setup ortho projection based on screen aspect ratio
        val ratio = width.toFloat() / height.toFloat()
        Matrix.orthoM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, -1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (layerTexture == 0) return

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Calculate scaling to fill the screen (Center Crop)
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val textureRatio = 800f / 1601f
        
        var baseScaleX = 1f
        var baseScaleY = 1f
        
        if (screenRatio > textureRatio) {
            // Screen is wider than texture ratio - scale Y to fill width
            baseScaleX = screenRatio
            baseScaleY = screenRatio / textureRatio
        } else {
            // Screen is taller than texture ratio - scale X to fill height
            baseScaleX = textureRatio
            baseScaleY = 1f
        }

        // Remove extra scale so the frame fits perfectly top-to-bottom on most phones (like Poco C65)
        val extraScale = 1.0f
        
        Matrix.setIdentityM(viewMatrix, 0)
        // Apply parallax offset using only natural bleed (if any)
        var maxOffset = (baseScaleX * extraScale) - (screenWidth.toFloat() / screenHeight.toFloat())
        if (maxOffset < 0) maxOffset = 0f // prevent negative parallax if screen is wider
        Matrix.translateM(viewMatrix, 0, -offsetX * maxOffset, 0f, 0f)
        Matrix.scaleM(viewMatrix, 0, baseScaleX * extraScale, baseScaleY * extraScale, 1f)
        
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, layerTexture)
        GLES20.glUniform1i(textureUniformHandle, 0)
        
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun loadVectorTexture(resId: Int): Int {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return 0
        
        // NOTE: The vector layers are exported from a Figma frame named "Viewport Boundary".
        // This frame MUST have "Clip content" enabled in Figma so that the exported SVG 
        // perfectly maintains the 800x1601 aspect ratio, even if objects bleed out of bounds.
        // Because the Y-axis is perfectly matched to the device screen, ensuring all layers
        // share this exact 800x1601 viewport keeps everything perfectly aligned.
        val scale = 2.0f
        val width = (800 * scale).toInt()
        val height = (1601 * scale).toInt()
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        
        val textureId = loadTexture(bitmap)
        bitmap.recycle()
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
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
