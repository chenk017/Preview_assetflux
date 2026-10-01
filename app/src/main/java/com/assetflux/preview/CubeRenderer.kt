
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

    @Volatile
    private var pendingBitmap: Bitmap? = null

    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var uvHandle = 0
    private var mvpHandle = 0
    private var textureHandle = 0
    private var uvOffsetHandle = 0
    private var textureOffset = 0f

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val vpModel = FloatArray(16)

    private var angle = 0f

    private val vertexShader = """
        uniform mat4 uMVP;
        attribute vec3 aPosition;
        attribute vec2 aUV;
        varying vec2 vUV;

        void main() {
            gl_Position = uMVP * vec4(aPosition, 1.0);
            vUV = aUV;
        }
    """.trimIndent()

    private val fragmentShader = """
    precision mediump float;
    uniform sampler2D uTexture;
    uniform vec2 uUVOffset;
    varying vec2 vUV;

    void main() {
        vec4 color = texture2D(
            uTexture, fract(vUV + uUVOffset)
        );

        // Buang piksel yang sepenuhnya transparan
        if (color.a <= 0.01) {
            discard;
        }

        gl_FragColor = color;
    }
""".trimIndent()

    private val vertices = floatArrayOf(
        // Front
        -1f,-1f, 1f, 0f,1f,
         1f,-1f, 1f, 1f,1f,
         1f, 1f, 1f, 1f,0f,
        -1f, 1f, 1f, 0f,0f,

        // Back
         1f,-1f,-1f, 0f,1f,
        -1f,-1f,-1f, 1f,1f,
        -1f, 1f,-1f, 1f,0f,
         1f, 1f,-1f, 0f,0f,

        // Left
        -1f,-1f,-1f, 0f,1f,
        -1f,-1f, 1f, 1f,1f,
        -1f, 1f, 1f, 1f,0f,
        -1f, 1f,-1f, 0f,0f,

        // Right
         1f,-1f, 1f, 0f,1f,
         1f,-1f,-1f, 1f,1f,
         1f, 1f,-1f, 1f,0f,
         1f, 1f, 1f, 0f,0f,

        // Top
        -1f, 1f, 1f, 0f,1f,
         1f, 1f, 1f, 1f,1f,
         1f, 1f,-1f, 1f,0f,
        -1f, 1f,-1f, 0f,0f,

        // Bottom
        -1f,-1f,-1f, 0f,1f,
         1f,-1f,-1f, 1f,1f,
         1f,-1f, 1f, 1f,0f,
        -1f,-1f, 1f, 0f,0f
    )

    private val indices = shortArrayOf(
         0,1,2, 0,2,3,
         4,5,6, 4,6,7,
         8,9,10, 8,10,11,
        12,13,14, 12,14,15,
        16,17,18, 16,18,19,
        20,21,22, 20,22,23
    )

    private val meshData by lazy {
        MeshData(vertices, indices)
    }

    fun setBitmap(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?
    ) {
        GLES20.glClearColor(0.08f, 0.09f, 0.13f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST) 
        // Alpha transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(
               GLES20.GL_SRC_ALPHA,
               GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val vertexData = ByteBuffer
            .allocateDirect(
                meshData.vertices.size * MeshData.BYTES_PER_FLOAT
            )
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        vertexData.put(meshData.vertices)
        vertexData.position(0)
        vertexBuffer = vertexData

        val indexData = ByteBuffer
            .allocateDirect(
                meshData.indices.size * MeshData.BYTES_PER_SHORT
            )
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()

        indexData.put(meshData.indices)
        indexData.position(0)
        indexBuffer = indexData

        program = createProgram(vertexShader, fragmentShader)

        positionHandle = GLES20.glGetAttribLocation(
            program, "aPosition"
        )
        uvHandle = GLES20.glGetAttribLocation(
            program, "aUV"
        )
        mvpHandle = GLES20.glGetUniformLocation(
            program, "uMVP"
        )
        textureHandle = GLES20.glGetUniformLocation(
            program, "uTexture"
        )
        uvOffsetHandle = GLES20.glGetUniformLocation(
            program, "uUVOffset"
        )

        Matrix.setLookAtM(
            view, 0,
            0f, 0f, 6f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        uploadTexture(createTestTexture())
    }

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int
    ) {
        GLES20.glViewport(0, 0, width, height)

        val safeHeight = if (height == 0) 1 else height
        val ratio = width.toFloat() / safeHeight.toFloat()

        Matrix.frustumM(
            projection, 0,
            -ratio, ratio,
            -1f, 1f,
            3f, 10f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(
            GLES20.GL_COLOR_BUFFER_BIT or
                GLES20.GL_DEPTH_BUFFER_BIT
        )

        val newBitmap = pendingBitmap
        if (newBitmap != null) {
            pendingBitmap = null
            uploadTexture(newBitmap)
        }

        GLES20.glUseProgram(program)

        angle += 0.7f
        textureOffset = (textureOffset + 0.0015f) % 1f

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, 18f, 1f, 0f, 0f)

        Matrix.multiplyMM(
            vpModel, 0, view, 0, model, 0
        )
        Matrix.multiplyMM(
            mvp, 0, projection, 0, vpModel, 0
        )

        GLES20.glUniformMatrix4fv(
            mvpHandle, 1, false, mvp, 0
        )

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D, textureId
        )
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniform2f(
            uvOffsetHandle, textureOffset, 0f
        )

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            MeshData.POSITION_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            MeshData.FLOATS_PER_VERTEX * MeshData.BYTES_PER_FLOAT,
            vertexBuffer
        )

        vertexBuffer.position(MeshData.POSITION_COMPONENTS)
        GLES20.glEnableVertexAttribArray(uvHandle)
        GLES20.glVertexAttribPointer(
            uvHandle,
            MeshData.UV_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            MeshData.FLOATS_PER_VERTEX * MeshData.BYTES_PER_FLOAT,
            vertexBuffer
        )

        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            meshData.indices.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
    }

    private fun uploadTexture(bitmap: Bitmap) {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

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
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLUtils.texImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            bitmap,
            0
        )

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun createTestTexture(): Bitmap {
        val size = 128
        val cell = 16
        val bitmap = Bitmap.createBitmap(
            size, size, Bitmap.Config.ARGB_8888
        )

        for (y in 0 until size) {
            for (x in 0 until size) {
                val alternate = ((x / cell) + (y / cell)) % 2 == 0
                val color = if (alternate) {
                    Color.rgb(170, 70, 255)
                } else {
                    Color.rgb(30, 225, 235)
                }
                bitmap.setPixel(x, y, color)
            }
        }

        return bitmap
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String
    ): Int {
        val vertex = compileShader(
            GLES20.GL_VERTEX_SHADER, vertexSource
        )
        val fragment = compileShader(
            GLES20.GL_FRAGMENT_SHADER, fragmentSource
        )

        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)

        val status = IntArray(1)
        GLES20.glGetProgramiv(
            result, GLES20.GL_LINK_STATUS, status, 0
        )

        if (status[0] == 0) {
            val message = GLES20.glGetProgramInfoLog(result)
            GLES20.glDeleteProgram(result)
            throw RuntimeException("Shader link gagal: $message")
        }

        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)

        return result
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
            shader, GLES20.GL_COMPILE_STATUS, status, 0
        )

        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile gagal: $message")
        }

        return shader
    }
}

