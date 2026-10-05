package com.assetflux.preview

import net.jpountz.lz4.LZ4Factory
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream

data class UnityBundleDataBlock(
    val index: Int,
    val compression: Int,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val data: ByteArray
)

data class UnityBundleData(
    val blocks: List<UnityBundleDataBlock>,
    val totalUncompressedSize: Long
)

object UnityBundleDataReader {

    private const val COMPRESSION_MASK = 0x3f
    private const val BLOCK_INFO_AT_END = 0x80L
    private const val BLOCK_INFO_PADDING = 0x200L

    fun read(
        input: InputStream,
        header: UnityBundleHeader,
        metadata: UnityBundleMetadata
    ): UnityBundleData {
        require((header.flags and BLOCK_INFO_AT_END) == 0L) {
            "Data reader belum mendukung BlocksInfoAtEnd."
        }

        require((header.flags and BLOCK_INFO_PADDING) == 0L) {
            "Data reader belum mendukung 16-byte alignment."
        }

        val dataBlocks = ArrayList<UnityBundleDataBlock>(
            metadata.blocks.size
        )

        var totalUncompressedSize = 0L
        val data = DataInputStream(input)

        metadata.blocks.forEachIndexed { index, block ->
            require(block.compressedSize <= Int.MAX_VALUE) {
                "Ukuran block terlalu besar: ${block.compressedSize}"
            }

            require(block.uncompressedSize <= Int.MAX_VALUE) {
                "Ukuran hasil block terlalu besar: ${block.uncompressedSize}"
            }

            val compressed = ByteArray(
                block.compressedSize.toInt()
            )

            data.readFully(compressed)

            val compression =
                block.flags and COMPRESSION_MASK

            val decoded = when (compression) {
                0 -> {
                    require(
                        block.compressedSize ==
                            block.uncompressedSize
                    ) {
                        "Ukuran block tanpa kompresi tidak cocok."
                    }

                    compressed
                }

                1 -> {
                    decodeLzma(
                        compressed,
                        block.uncompressedSize
                    )
                }

                2, 3 -> {
                    val output = ByteArray(
                        block.uncompressedSize.toInt()
                    )

                    val decompressor = LZ4Factory
                        .fastestInstance()
                        .safeDecompressor()

                    val decodedSize =
                        decompressor.decompress(
                            compressed,
                            0,
                            compressed.size,
                            output,
                            0,
                            output.size
                        )

                    require(
                        decodedSize == output.size
                    ) {
                        "Ukuran hasil block #$index tidak cocok: " +
                            "$decodedSize != ${output.size}"
                    }

                    output
                }

                else -> {
                    throw IllegalArgumentException(
                        "Kompresi data block belum didukung: " +
                            compression
                    )
                }
            }

            dataBlocks.add(
                UnityBundleDataBlock(
                    index = index,
                    compression = compression,
                    compressedSize = block.compressedSize,
                    uncompressedSize = block.uncompressedSize,
                    data = decoded
                )
            )

            totalUncompressedSize +=
                decoded.size.toLong()
        }

        return UnityBundleData(
            blocks = dataBlocks,
            totalUncompressedSize =
                totalUncompressedSize
        )
    }

    private fun decodeLzma(
        compressed: ByteArray,
        uncompressedSize: Long
    ): ByteArray {
        require(compressed.size >= 5) {
            "Data LZMA terlalu pendek untuk header properties."
        }

        require(uncompressedSize <= Int.MAX_VALUE) {
            "Ukuran hasil LZMA terlalu besar: $uncompressedSize"
        }

        val properties = compressed[0]

        val dictionarySize =
            (compressed[1].toLong() and 0xffL) or
            ((compressed[2].toLong() and 0xffL) shl 8) or
            ((compressed[3].toLong() and 0xffL) shl 16) or
            ((compressed[4].toLong() and 0xffL) shl 24)

        require(dictionarySize <= Int.MAX_VALUE) {
            "Dictionary LZMA terlalu besar: $dictionarySize"
        }

        val rawLzma = ByteArrayInputStream(
            compressed,
            5,
            compressed.size - 5
        )

        val output = ByteArray(
            uncompressedSize.toInt()
        )

        LZMAInputStream(
            rawLzma,
            uncompressedSize,
            properties,
            dictionarySize.toInt()
        ).use { lzma ->
            var offset = 0

            while (offset < output.size) {
                val count = lzma.read(
                    output,
                    offset,
                    output.size - offset
                )

                if (count < 0) {
                    break
                }

                if (count == 0) {
                    continue
                }

                offset += count
            }

            require(offset == output.size) {
                "Ukuran hasil LZMA tidak cocok: " +
                    "$offset != ${output.size}"
            }
        }

        return output
    }
}
