package com.assetflux.preview


package com.assetflux.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CubeRenderer : GLSurfaceView.Renderer {

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)

    private var program = 0
    private var positionHandle = 0
    private var uvHandle = 0
    private var matrixHandle = 0
    private var textureHandle = 0
    private var textureId = 0
    private var angle = 0f

    // Vertex format: X, Y, Z, U, V.
    // Each face has separate UV coordinates.
    private val vertices = floatArrayOf(
        // Front (+Z)
        -1f, -1f,  1f, 0f, 1f,
         1f, -1f,  1f, 1f, 1f,
         1f,  1f,  1f, 1f, 0f,
        -1f,  1f,  1f, 0f, 0f,

        // Back (-Z)
         1f, -1f, -1f, 0f, 1f,
        -1f, -1f, -1f, 1f, 1f,
        -1f,  1f, -1f, 1f, 0f,
         1f,  1f, -1f, 0f, 0f,

        // Left (-X)
        -1f, -1f, -1f, 0f, 1f,
        -1f, -1f,  1f, 1f, 1f,
        -1f,  1f,  1f, 1f, 0f,
        -1f,  1f, -1f, 0f, 0f,

        // Right (+X)
         1f, -1f,  1f, 0f, 1f,
         1f, -1f, -1f, 1f, 1f,
         1f,  1f, -1f, 1f, 0f,
         1f,  1f,  1f, 0f, 0f,

        // Top (+Y)
        -1f,  1f,  1f, 0f, 1f,
         1f,  1f,  1f, 1f, 1f,
         1f,  1f, -1f, 1f, 0f,
        -1f,  1f, -1f, 0f, 0f,

        // Bottom (-Y)
        -1f, -1f, -1f, 0f, 1f,
         1f, -1f, -1f, 1f, 1f,
         1f, -1f,  1f, 1f, 0f,
        -1f, -1f,  1f, 0f, 0f
    )

    private val indices = shortArrayOf(
         0,  1,  2,   0,  2,  3,
         4,  5,  6,   4,  6,  7,
         8,  9, 10,   8, 10, 11,
        12, 13, 14,  12, 14, 15,
        16, 17, 18,  16, 18, 19,
        20, 21, 22,  20, 22, 23
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private val vertexShader = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        attribute vec2 aUV;
        varying vec2 vUV;

        void main() {
            gl_Position = uMVP * aPosition;
            vUV = aUV;
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;

        uniform sampler2D uTexture;
        varying vec2 vUV;

        void main() {
            gl_FragColor = texture2D(uTexture, vUV);
        }
    """.trimIndent()

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?
    ) {
        GLES20.glClearColor(
            0.035f, 0.045f, 0.09f, 1f
        )
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Prepare vertex buffer.
        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        // Prepare index buffer.
        indexBuffer = ByteBuffer
            .allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

        indexBuffer.put(indices)
        indexBuffer.position(0)

        // Compile shaders.
        val vs = compileShader(
            GLES20.GL_VERTEX_SHADER,
            vertexShader
        )

        val fs = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            fragmentShader
        )

        // Link shader program.
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)

        GLES20.glGetProgramiv(
            program,
            GLES20.GL_LINK_STATUS,
            linkStatus,
            0
        )

        if (linkStatus[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            program = 0

            throw RuntimeException(
                "Program link failed: $error"
            )
        }

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        // Get shader attribute and uniform locations.
        positionHandle = GLES20.glGetAttribLocation(
            program, "aPosition"
        )

        uvHandle = GLES20.glGetAttribLocation(
            program, "aUV"
        )

        matrixHandle = GLES20.glGetUniformLocation(
            program, "uMVP"
        )

        textureHandle = GLES20.glGetUniformLocation(
            program, "uTexture"
        )

        // Generate test texture.
        createTestTexture()

        // Camera position.
        Matrix.setLookAtM(
            view, 0,
            0f, 0f, 6f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    private fun createTestTexture() {
        val size = 128
        val cellSize = 16

        val bitmap = Bitmap.createBitmap(
            size,
            size,
            Bitmap.Config.ARGB_8888
        )

        // Generate a purple-cyan checkerboard texture.
        for (y in 0 until size) {
            for (x in 0 until size) {
                val cellX = x / cellSize
                val cellY = y / cellSize

                val color = if (
                    (cellX + cellY) % 2 == 0
                ) {
                    Color.rgb(180, 55, 245)
                } else {
                    Color.rgb(25, 210, 235)
                }

                bitmap.setPixel(x, y, color)
            }
        }

        val ids = IntArray(1)

        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textureId
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_REPEAT
        )

        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_REPEAT
        )

        GLUtils.texImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            bitmap,
            0
        )

        bitmap.recycle()

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            0
        )
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(
            0, 0, width, height
        )

        val safeHeight = height.coerceAtLeast(1)
        val ratio = width.toFloat() / safeHeight

        Matrix.frustumM(
            projection, 0,
            -ratio, ratio,
            -1f, 1f,
            2f, 20f
        )

        Matrix.multiplyMM(
            vp, 0,
            projection, 0,
            view, 0
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT or
                GLES20.GL_DEPTH_BUFFER_BIT
        )

        GLES20.glUseProgram(program)

        // Rotate the cube.
        Matrix.setIdentityM(model, 0)

        Matrix.rotateM(
            model, 0,
            angle,
            0.7f, 1f, 0.2f
        )

        Matrix.multiplyMM(
            mvp, 0,
            vp, 0,
            model, 0
        )

        // Position attribute: XYZ.
        vertexBuffer.position(0)

        GLES20.glEnableVertexAttribArray(
            positionHandle
        )

        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            5 * 4,
            vertexBuffer
        )

        // UV attribute.
        vertexBuffer.position(3)

        GLES20.glEnableVertexAttribArray(
            uvHandle
        )

        GLES20.glVertexAttribPointer(
            uvHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            5 * 4,
            vertexBuffer
        )

        // Send transformation matrix.
        GLES20.glUniformMatrix4fv(
            matrixHandle,
            1,
            false,
            mvp,
            0
        )

        // Bind texture to texture unit 0.
        GLES20.glActiveTexture(
            GLES20.GL_TEXTURE0
        )

        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textureId
        )

        GLES20.glUniform1i(
            textureHandle,
            0
        )

        // Draw indexed triangles.
        indexBuffer.position(0)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indices.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        // Advance rotation.
        angle = (angle + 0.7f) % 360f
    }

    private fun compileShader(
        type: Int,
        source: String
    ): Int {
        val shader = GLES20.glCreateShader(type)

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)

        GLES20.glGetShaderiv(
            shader,
            GLES20.GL_COMPILE_STATUS,
            status,
            0
        )

        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)

            GLES20.glDeleteShader(shader)

            throw RuntimeException(
                "Shader compile failed: $error"
            )
        }

        return shader
    }
}

