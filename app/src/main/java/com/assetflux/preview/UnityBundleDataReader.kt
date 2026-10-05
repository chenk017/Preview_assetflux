package com.assetflux.preview

import net.jpountz.lz4.LZ4Factory
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
}
