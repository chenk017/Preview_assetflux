package com.assetflux.preview

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class UnityBundleHeader(
    val signature: String,
    val formatVersion: Int,
    val playerVersion: String,
    val engineVersion: String,
    val bundleSize: Long
)

object UnityBundleHeaderReader {

    fun read(input: InputStream): UnityBundleHeader {
        val data = DataInputStream(input)

        val signature = readNullTerminatedString(data)

        require(signature == "UnityFS") {
            "Signature bukan UnityFS: $signature"
        }

        val formatVersion = data.readInt()
        require(formatVersion in 1..100) {
            "Versi format tidak wajar: $formatVersion"
        }

        val playerVersion = readNullTerminatedString(data)
        val engineVersion = readNullTerminatedString(data)
        val bundleSize = data.readLong()

        require(bundleSize > 0L) {
            "Ukuran bundle tidak valid: $bundleSize"
        }

        return UnityBundleHeader(
            signature = signature,
            formatVersion = formatVersion,
            playerVersion = playerVersion,
            engineVersion = engineVersion,
            bundleSize = bundleSize
        )
    }

    private fun readNullTerminatedString(
        input: DataInputStream
    ): String {
        val bytes = ArrayList<Byte>()

        while (bytes.size < 1024) {
            val value = input.readUnsignedByte()
            if (value == 0) {
                return String(
                    bytes.toByteArray(),
                    StandardCharsets.UTF_8
                )
            }
            bytes.add(value.toByte())
        }

        throw IllegalArgumentException(
            "String header terlalu panjang"
        )
    }
}
