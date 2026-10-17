package com.assetflux.nativeviewer.unity

import net.jpountz.lz4.LZ4Factory
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile

/**
 * Pembaca awal blok metadata UnityFS.
 *
 * Catatan:
 * - Mendukung blok metadata dengan kompresi LZ4/LZ4HC.
 * - Tidak mengekstrak SerializedFile atau aset Unity.
 * - Offset blok harus ditentukan sesuai layout UnityFS.
 */
class UnityFSBlockReader {

    data class BlockInfo(
        val uncompressedSize: Int,
        val compressedSize: Int,
        val flags: Int
    )

    fun decompressLz4(
        compressed: ByteArray,
        expectedSize: Int
    ): ByteArray {
        require(expectedSize > 0) {
            "Ukuran hasil dekompresi tidak valid"
        }

        require(compressed.isNotEmpty()) {
            "Data terkompresi kosong"
        }

        val output = ByteArray(expectedSize)
        val decompressor = LZ4Factory.fastestInstance().safeDecompressor()

        val written = decompressor.decompress(
            compressed,
            0,
            compressed.size,
            output,
            0,
            expectedSize
        )

        require(written == expectedSize) {
            "Ukuran hasil dekompresi tidak sesuai"
        }

        return output
    }

    fun readBytes(
        file: File,
        offset: Long,
        size: Int
    ): ByteArray {
        require(file.isFile) {
            "Berkas bundle tidak ditemukan"
        }

        require(offset >= 0L && size >= 0) {
            "Offset atau ukuran tidak valid"
        }

        require(offset + size.toLong() <= file.length()) {
            "Data berada di luar ukuran bundle"
        }

        return RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)

            val result = ByteArray(size)
            raf.readFully(result)
            result
        }
    }
}
