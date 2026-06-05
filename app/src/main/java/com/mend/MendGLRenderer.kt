package com.mend

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

class MendGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    data class Icon(
        val iconInfo: IconInfo,
        val appIconBitmap: Bitmap,
        var appIconTextureId: Int = 0,
        val textBitmap: Bitmap? = null,
        var textTextureId: Int = 0,
        var animationPhase: Float = 0f
    )

    private val icons = mutableListOf<Icon>()

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

    private val quadVertices = floatArrayOf(
        -0.5f,  0.5f,  // top left
        -0.5f, -0.5f,  // bottom left
         0.5f, -0.5f,  // bottom right
         0.5f,  0.5f   // top right
    )

    private val textureCoordinates = floatArrayOf(
        0.0f, 0.0f,     // top left
        0.0f, 1.0f,     // bottom left
        1.0f, 1.0f,     // bottom right
        1.0f, 0.0f      // top right
    )

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3)

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer
    private lateinit var drawListBuffer: java.nio.ShortBuffer

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureUniformHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val sensorRotationMatrix = FloatArray(16)

    private var screenWidth = 0
    private var screenHeight = 0
    private var lastFrameTime = System.nanoTime()

    init {
        Matrix.setIdentityM(sensorRotationMatrix, 0)
    }

    fun updateIconLayout(layout: Map<Int, List<IconInfo>>) {
        icons.clear()
        val packageManager = context.packageManager
        for (page in layout.values) {
            for (iconInfo in page) {
                try {
                    val appIconDrawable = packageManager.getApplicationIcon(iconInfo.packageName)
                    val appIconBitmap = drawableToBitmap(appIconDrawable)

                    var textBitmap: Bitmap? = null
                    if (!iconInfo.text.isNullOrEmpty()) {
                        textBitmap = drawTextToBitmap(iconInfo.text)
                    }

                    icons.add(Icon(iconInfo, appIconBitmap, textBitmap = textBitmap))
                } catch (e: PackageManager.NameNotFoundException) {
                    // App not found, ignore
                }
            }
        }
    }

    fun updateSensorData(rotationVector: FloatArray) {
        System.arraycopy(rotationVector, 0, sensorRotationMatrix, 0, 16)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        setupBuffers()
        setupShaders()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width
        screenHeight = height
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f // Convert to seconds
        lastFrameTime = currentTime

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(textureUniformHandle, 0)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // Apply sensor rotation to the view matrix for parallax
        val tempViewMatrix = FloatArray(16)
        Matrix.multiplyMM(tempViewMatrix, 0, sensorRotationMatrix, 0, viewMatrix, 0)

        for (icon in icons) {
            // Update animation phase
            icon.animationPhase += deltaTime * 2.0f // Adjust speed as needed

            // Draw App Icon
            if (icon.appIconTextureId == 0) {
                icon.appIconTextureId = loadTexture(icon.appIconBitmap)
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, icon.appIconTextureId)

            val iconModelMatrix = FloatArray(16)
            Matrix.setIdentityM(iconModelMatrix, 0)

            val x = (icon.iconInfo.boundsInScreen.centerX() / screenWidth.toFloat()) * 2 - 1
            val y = (icon.iconInfo.boundsInScreen.centerY() / screenHeight.toFloat()) * -2 + 1
            Matrix.translateM(iconModelMatrix, 0, x, y, 0f)

            // Apply jiggle animation
            val jiggleX = sin(icon.animationPhase * 5.0f) * 0.02f // Adjust amplitude and frequency
            val jiggleY = sin(icon.animationPhase * 7.0f + 1.0f) * 0.02f
            Matrix.translateM(iconModelMatrix, 0, jiggleX, jiggleY, 0f)

            val iconWidth = icon.iconInfo.boundsInScreen.width() / screenWidth.toFloat()
            val iconHeight = icon.iconInfo.boundsInScreen.height() / screenHeight.toFloat()
            Matrix.scaleM(iconModelMatrix, 0, iconWidth, iconHeight, 1f)

            Matrix.multiplyMM(mvpMatrix, 0, tempViewMatrix, 0, iconModelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

            // Draw Text (App Name)
            icon.textBitmap?.let { textBitmap ->
                if (icon.textTextureId == 0) {
                    icon.textTextureId = loadTexture(textBitmap)
                }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, icon.textTextureId)

                val textModelMatrix = FloatArray(16)
                Matrix.setIdentityM(textModelMatrix, 0)

                // Position text below the icon
                val textYOffset = -iconHeight / 2 - (textBitmap.height / screenHeight.toFloat()) / 2 // Adjust Y position
                Matrix.translateM(textModelMatrix, 0, x, y + textYOffset, 0f)

                // Apply same jiggle animation to text for consistency
                Matrix.translateM(textModelMatrix, 0, jiggleX, jiggleY, 0f)

                val textWidth = textBitmap.width / screenWidth.toFloat()
                val textHeight = textBitmap.height / screenHeight.toFloat()
                Matrix.scaleM(textModelMatrix, 0, textWidth, textHeight, 1f)

                Matrix.multiplyMM(mvpMatrix, 0, tempViewMatrix, 0, textModelMatrix, 0)
                Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun setupBuffers() {
        val bb = ByteBuffer.allocateDirect(quadVertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(quadVertices)
        vertexBuffer.position(0)

        val tb = ByteBuffer.allocateDirect(textureCoordinates.size * 4)
        tb.order(ByteOrder.nativeOrder())
        textureBuffer = tb.asFloatBuffer()
        textureBuffer.put(textureCoordinates)
        textureBuffer.position(0)

        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)
    }

    private fun setupShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_texCoord")
        textureUniformHandle = GLES20.glGetUniformLocation(program, "u_texture")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    private fun loadTexture(bitmap: Bitmap): Int {
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return textureId
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }

    private fun drawTextToBitmap(text: String, fontSize: Float = 24f, textColor: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = fontSize
        paint.color = textColor
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val width = bounds.width() + 4 // Add some padding
        val height = bounds.height() + 4 // Add some padding

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawARGB(0, 0, 0, 0) // Transparent background
        canvas.drawText(text, (width / 2).toFloat(), (height / 2).toFloat() - bounds.exactCenterY(), paint)

        return bitmap
    }
}
