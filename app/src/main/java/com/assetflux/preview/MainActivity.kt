
package com.assetflux.preview

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.opengl.GLSurfaceView

class MainActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: CubeRenderer

    companion object {
        private const val REQUEST_TEXTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        renderer = CubeRenderer()
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(22, 24, 34))
        }

        val title = TextView(this).apply {
            text = "Preview AssetFlux"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12, 14, 12, 14)
        }

        val chooseButton = Button(this).apply {
            text = "Pilih Texture2D (PNG)"
            isAllCaps = false
            setOnClickListener {
                openTexturePicker()
            }
        }

        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            glView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addControlRow(
            root,
            listOf(
                "←" to { renderer.adjustTransform(positionXDelta = -0.15f) },
                "→" to { renderer.adjustTransform(positionXDelta = 0.15f) },
                "↑" to { renderer.adjustTransform(positionYDelta = 0.15f) },
                "↓" to { renderer.adjustTransform(positionYDelta = -0.15f) }
            )
        )

        addControlRow(
            root,
            listOf(
                "X−" to { renderer.adjustTransform(rotationXDelta = -10f) },
                "X+" to { renderer.adjustTransform(rotationXDelta = 10f) },
                "Z−" to { renderer.adjustTransform(rotationZDelta = -10f) },
                "Z+" to { renderer.adjustTransform(rotationZDelta = 10f) }
            )
        )

        addControlRow(
            root,
            listOf(
                "Perkecil" to { renderer.adjustTransform(scaleFactor = 0.9f) },
                "Perbesar" to { renderer.adjustTransform(scaleFactor = 1.1f) },
                "Reset" to { renderer.resetTransform() }
            )
        )

        root.addView(
            chooseButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun addControlRow(
        root: LinearLayout,
        controls: List<Pair<String, () -> Unit>>
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        controls.forEach { (label, action) ->
            row.addView(
                controlButton(label, action),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        root.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun controlButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            setPadding(2, 0, 2, 0)
            setOnClickListener { action() }
        }
    }

    private fun openTexturePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
        }

        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_TEXTURE)
    }

    @Deprecated("Using onActivityResult for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_TEXTURE ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri: Uri = data?.data ?: return

        try {
            val bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }

            if (bitmap == null) {
                Toast.makeText(
                    this,
                    "Gambar tidak dapat dibaca",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            renderer.setBitmap(bitmap)

            Toast.makeText(
                this,
                "Tekstur dipilih: ${bitmap.width} x ${bitmap.height}",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Gagal membuka gambar: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::glView.isInitialized) {
            glView.onResume()
        }
    }

    override fun onPause() {
        if (::glView.isInitialized) {
            glView.onPause()
        }
        super.onPause()
    }
}

