
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
    private lateinit var bundleInfoText: TextView

    companion object {
        private const val REQUEST_TEXTURE = 1001
        private const val REQUEST_MODEL_OBJ = 1002
        private const val REQUEST_UNITY_BUNDLE = 1003
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

        val chooseUnityBundleButton = Button(this).apply {
            text = "Pilih Unity Bundle (.unity3d)"
            isAllCaps = false
            setOnClickListener {
                openUnityBundlePicker()
            }
        }

        bundleInfoText = TextView(this).apply {
            text = "Unity Bundle belum dipilih"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 12, 16, 12)
            setGravity(Gravity.START)
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

        root.addView(
            chooseUnityBundleButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            bundleInfoText,
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

    private fun openUnityBundlePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_UNITY_BUNDLE)
    }

    private fun openTexturePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
        }

        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_TEXTURE)
    }

    private fun openObjPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_MODEL_OBJ)
    }

    @Deprecated("Using onActivityResult for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) return

        val uri: Uri = data?.data ?: return

        if (requestCode == REQUEST_UNITY_BUNDLE) {
            try {
                val result = contentResolver.openInputStream(uri)?.use { stream ->
                    val header = UnityBundleHeaderReader.read(stream)
                    val metadata = UnityBundleMetadataReader.read(stream, header)
                    val bundleData = UnityBundleDataReader.read(
                        stream,
                        header,
                        metadata
                    )

                    Triple(header, metadata, bundleData)
                } ?: throw IllegalArgumentException(
                    "File Unity Bundle tidak dapat dibuka."
                )

                val header = result.first
                val metadata = result.second
                val bundleData = result.third

                val samplePaths = metadata.entries
                    .take(3)
                    .joinToString("\n") { it.path }

                val message = buildString {
                    append("UnityFS valid\n")
                    append("Unity: ${header.engineVersion}\n")
                    append("Ukuran: ${header.bundleSize} byte\n")
                    append("Blok data: ${metadata.blocks.size}\n")
                    append("File internal: ${metadata.entries.size}")

                    append("\n\nData block:")

                    bundleData.blocks.forEach { block ->
                        append("\nBlock #${block.index}")
                        append("\nKompresi: ${block.compression}")
                        append("\nCompressed: ${block.compressedSize} byte")
                        append("\nUncompressed: ${block.uncompressedSize} byte")
                    }

                    append("\nTotal hasil: ${bundleData.totalUncompressedSize} byte")

                    if (samplePaths.isNotBlank()) {
                        append("\n\nContoh file:\n")
                        append(samplePaths)
                    }
                }

                bundleInfoText.text = message
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Gagal membaca metadata Unity Bundle: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (requestCode == REQUEST_MODEL_OBJ) {
            try {
                val mesh = contentResolver.openInputStream(uri)?.use {
                    ObjMeshParser.parse(it)
                } ?: throw IllegalArgumentException("File OBJ tidak dapat dibuka.")

                renderer.setMesh(mesh)

                Toast.makeText(
                    this,
                    "Model OBJ dimuat: ${mesh.vertexCount} vertex, ${mesh.triangleCount} segitiga",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Gagal membuka OBJ: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (requestCode != REQUEST_TEXTURE) return

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

