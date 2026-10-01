package com.assetflux.preview

/**
 * Transform lokal objek 3D.
 *
 * Posisi menggunakan satuan ruang objek.
 * Rotasi menggunakan derajat.
 * Skala 1.0 berarti ukuran asli.
 */
data class TransformData(
    var positionX: Float = 0f,
    var positionY: Float = 0f,
    var positionZ: Float = 0f,

    var rotationX: Float = 18f,
    var rotationY: Float = 0f,
    var rotationZ: Float = 0f,

    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var scaleZ: Float = 1f
) {
    init {
        require(
            positionX.isFinite() &&
            positionY.isFinite() &&
            positionZ.isFinite()
        ) {
            "Posisi transform harus berupa angka valid."
        }

        require(
            rotationX.isFinite() &&
            rotationY.isFinite() &&
            rotationZ.isFinite()
        ) {
            "Rotasi transform harus berupa angka valid."
        }

        require(
            scaleX.isFinite() &&
            scaleY.isFinite() &&
            scaleZ.isFinite() &&
            scaleX != 0f &&
            scaleY != 0f &&
            scaleZ != 0f
        ) {
            "Skala harus valid dan tidak boleh nol."
        }
    }
}
