package com.assetflux.preview

/**
 * Data geometri mesh untuk renderer AssetFlux.
 *
 * Format setiap vertex:
 * X, Y, Z, U, V
 *
 * Tiga nilai pertama adalah posisi.
 * Dua nilai berikutnya adalah koordinat tekstur.
 */
data class MeshData(
    val vertices: FloatArray,
    val indices: ShortArray
) {
    companion object {
        const val FLOATS_PER_VERTEX = 5
        const val POSITION_COMPONENTS = 3
        const val UV_COMPONENTS = 2
        const val BYTES_PER_FLOAT = 4
        const val BYTES_PER_SHORT = 2
    }

    init {
        require(vertices.isNotEmpty()) {
            "Mesh harus memiliki vertex."
        }

        require(vertices.size % FLOATS_PER_VERTEX == 0) {
            "Format vertex harus terdiri dari X, Y, Z, U, V."
        }

        require(indices.isNotEmpty()) {
            "Mesh harus memiliki indeks."
        }

        require(indices.size % 3 == 0) {
            "Indeks harus membentuk segitiga."
        }

        val vertexCount = vertices.size / FLOATS_PER_VERTEX

        require(vertexCount <= Short.MAX_VALUE.toInt()) {
            "Jumlah vertex melampaui batas indeks Short positif."
        }

        indices.forEach { index ->
            val vertexIndex = index.toInt()

            require(vertexIndex >= 0 && vertexIndex < vertexCount) {
                "Indeks vertex di luar rentang: $vertexIndex"
            }
        }
    }

    val vertexCount: Int
        get() = vertices.size / FLOATS_PER_VERTEX

    val triangleCount: Int
        get() = indices.size / 3
}
