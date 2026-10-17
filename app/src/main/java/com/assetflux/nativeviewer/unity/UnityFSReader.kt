package com.assetflux.nativeviewer.unity

import java.io.EOFException
import java.io.File
import java.io.InputStream

class UnityFSReader {

    companion object {
        private const val MAX_HEADER_STRING_BYTES = 128
        private const val MAX_METADATA_BYTES = 64L * 1024L * 1024L
        private const val MAX_BUNDLE_SIZE = Int.MAX_VALUE.toLong()
    }

    fun readHeader(file: File): UnityFSHeader {
        require(file.isFile) {
            "Berkas bundle tidak ditemukan"
        }

        val actualSize = file.length()

        require(actualSize >= 16L) {
            "Berkas terlalu kecil"
        }

        require(actualSize <= MAX_BUNDLE_SIZE) {
            "Bundle terlalu besar untuk pembaca saat ini"
        }

        return file.inputStream().buffered().use { input ->
            val header = readHeader(input)

            require(header.bundleSize == actualSize) {
                "Ukuran bundle tidak cocok"
            }

            header
        }
    }

    fun readHeader(input: InputStream): UnityFSHeader {
        val reader = HeaderInput(input)

        val signature = reader.readNullTerminatedString(32)

        require(signature == "UnityFS") {
            "Signature bukan UnityFS"
        }

        val formatVersion = reader.readUInt32()

        require(formatVersion in 6L..10L) {
            "Versi UnityFS belum didukung: $formatVersion"
        }

        val playerVersion =
            reader.readNullTerminatedString(MAX_HEADER_STRING_BYTES)

        val engineVersion =
            reader.readNullTerminatedString(MAX_HEADER_STRING_BYTES)

        val bundleSize = reader.readInt64()
        val compressedMetadataSize = reader.readUInt32()
        val uncompressedMetadataSize = reader.readUInt32()
        val flags = reader.readUInt32()

        require(bundleSize >= reader.bytesRead) {
            "Ukuran bundle tidak valid"
        }

        require(bundleSize <= MAX_BUNDLE_SIZE) {
            "Bundle terlalu besar"
        }

        require(compressedMetadataSize <= MAX_METADATA_BYTES) {
            "Metadata terkompresi terlalu besar"
        }

        require(uncompressedMetadataSize in 1L..MAX_METADATA_BYTES) {
            "Ukuran metadata hasil dekompresi tidak valid"
        }

        require(compressedMetadataSize <= bundleSize) {
            "Ukuran metadata melebihi ukuran bundle"
        }

        return UnityFSHeader(
            formatVersion = formatVersion,
            playerVersion = playerVersion,
            engineVersion = engineVersion,
            bundleSize = bundleSize,
            compressedMetadataSize = compressedMetadataSize,
            uncompressedMetadataSize = uncompressedMetadataSize,
            flags = flags
        )
    }

    private class HeaderInput(
        private val input: InputStream
    ) {
        var bytesRead = 0L
            private set

        private fun readByte(): Int {
            val value = input.read()

            if (value < 0) {
                throw EOFException("Header UnityFS tidak lengkap")
            }

            bytesRead++
            return value
        }

        fun readUInt32(): Long {
            val b0 = readByte().toLong()
            val b1 = readByte().toLong()
            val b2 = readByte().toLong()
            val b3 = readByte().toLong()

            return (b0 shl 24) or
                (b1 shl 16) or
                (b2 shl 8) or
                b3
        }

        fun readInt64(): Long {
            val high = readUInt32()
            val low = readUInt32()

            require(high <= Int.MAX_VALUE.toLong()) {
                "Ukuran bundle berada di luar batas yang didukung"
            }

            return (high shl 32) or low
        }

        fun readNullTerminatedString(maxBytes: Int): String {
            val bytes = ArrayList<Byte>(minOf(maxBytes, 32))

            while (bytes.size < maxBytes) {
                val value = readByte()

                if (value == 0) {
                    return bytes.toByteArray()
                        .toString(Charsets.UTF_8)
                }

                bytes.add(value.toByte())
            }

            throw IllegalArgumentException(
                "String header tidak memiliki terminator"
            )
        }
    }
}
