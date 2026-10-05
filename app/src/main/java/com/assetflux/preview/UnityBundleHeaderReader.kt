package com.assetflux.preview

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class UnityBundleHeader(
    val signature: String,
    val formatVersion: Int,
    val playerVersion: String,
    val engineVersion: String,
    val bundleSize: Long,
    val compressedBlocksInfoSize: Long,
    val uncompressedBlocksInfoSize: Long,
    val flags: Long
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
        val compressedSize = readUnsignedInt(data)
        val uncompressedSize = readUnsignedInt(data)
        val flags = readUnsignedInt(data)

        require(bundleSize > 0L) {
            "Ukuran bundle tidak valid: $bundleSize"
        }
        require(compressedSize in 1L..(32L * 1024 * 1024)) {
            "Ukuran metadata terkompresi tidak valid: $compressedSize"
        }
        require(uncompressedSize in 1L..(32L * 1024 * 1024)) {
            "Ukuran metadata hasil dekompresi tidak valid: $uncompressedSize"
        }

        return UnityBundleHeader(
            signature = signature,
            formatVersion = formatVersion,
            playerVersion = playerVersion,
            engineVersion = engineVersion,
            bundleSize = bundleSize,
            compressedBlocksInfoSize = compressedSize,
            uncompressedBlocksInfoSize = uncompressedSize,
            flags = flags
        )
    }

    private fun readUnsignedInt(input: DataInputStream): Long =
        input.readInt().toLong() and 0xffffffffL

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

        throw IllegalArgumentException("String header terlalu panjang")
    }
}
