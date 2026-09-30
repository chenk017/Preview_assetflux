package com.assetflux.preview

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle

class MainActivity : Activity() {
    private lateinit var surface: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        surface = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(CubeRenderer())
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContentView(surface)
    }

    override fun onPause() {
        super.onPause()
        surface.onPause()
    }

    override fun onResume() {
        super.onResume()
        surface.onResume()
    }
}
