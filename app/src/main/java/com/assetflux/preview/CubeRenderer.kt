package com.assetflux.preview

import android.opengl.GLES20
import android.opengl.GLSurfaceView
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
    private var colorHandle = 0
    private var matrixHandle = 0
    private var angle = 0f

    private val vertices = floatArrayOf(
        // Position XYZ              // RGB color
        -1f,-1f,-1f, 1f,0f,0f,
         1f,-1f,-1f, 0f,1f,0f,
         1f, 1f,-1f, 0f,0f,1f,
        -1f, 1f,-1f, 1f,1f,0f,
        -1f,-1f, 1f, 1f,0f,1f,
         1f,-1f, 1f, 0f,1f,1f,
         1f, 1f, 1f, 1f,1f,1f,
        -1f, 1f, 1f, 1f,0.5f,0f
    )

    private val indices = shortArrayOf(
        0,1,2, 0,2,3,
        4,6,5, 4,7,6,
        0,4,5, 0,5,1,
        3,2,6, 3,6,7,
        0,3,7, 0,7,4,
        1,5,6, 1,6,2
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private val vertexShader = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        attribute vec3 aColor;
        varying vec3 vColor;
        void main() {
            gl_Position = uMVP * aPosition;
            vColor = aColor;
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        varying vec3 vColor;
        void main() {
            gl_FragColor = vec4(vColor, 1.0);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.045f, 0.09f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        indexBuffer.put(indices).position(0)

        program = GLES20.glCreateProgram().also { p ->
            val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(p, vs)
            GLES20.glAttachShader(p, fs)
            GLES20.glLinkProgram(p)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMVP")

        Matrix.setLookAtM(
            view, 0,
            0f, 0f, 6f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1)
        Matrix.frustumM(projection, 0, -ratio, ratio, -1f, 1f, 2f, 20f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, angle, 0.7f, 1f, 0.2f)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false,
            6 * 4, vertexBuffer
        )

        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(
            colorHandle, 3, GLES20.GL_FLOAT, false,
            6 * 4, vertexBuffer
        )

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mvp, 0)
        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES, indices.size,
            GLES20.GL_UNSIGNED_SHORT, indexBuffer
        )

        angle = (angle + 0.7f) % 360f
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compile failed: $error")
            }
        }
    }
}
