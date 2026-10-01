package com.assetflux.preview

/**
 * Membuat geometri mesh tambahan untuk Preview AssetFlux.
 * Format vertex: X, Y, Z, U, V.
 */
object MeshFactory {

    fun pyramid(): MeshData {
        val vertices = floatArrayOf(
            // Sisi depan
             0f,  1f,  0f, 0.5f, 0f,
            -1f, -1f,  1f, 0f,   1f,
             1f, -1f,  1f, 1f,   1f,

            // Sisi kanan
             0f,  1f,  0f, 0.5f, 0f,
             1f, -1f,  1f, 0f,   1f,
             1f, -1f, -1f, 1f,   1f,

            // Sisi belakang
             0f,  1f,  0f, 0.5f, 0f,
             1f, -1f, -1f, 0f,   1f,
            -1f, -1f, -1f, 1f,   1f,

            // Sisi kiri
             0f,  1f,  0f, 0.5f, 0f,
            -1f, -1f, -1f, 0f,   1f,
            -1f, -1f,  1f, 1f,   1f,

            // Alas persegi
            -1f, -1f,  1f, 0f, 0f,
             1f, -1f,  1f, 1f, 0f,
             1f, -1f, -1f, 1f, 1f,
            -1f, -1f, -1f, 0f, 1f
        )

        val indices = shortArrayOf(
             0,  1,  2,
             3,  4,  5,
             6,  7,  8,
             9, 10, 11,
            12, 13, 14,
            12, 14, 15
        )

        return MeshData(vertices, indices)
    }
}
