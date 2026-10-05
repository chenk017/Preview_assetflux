package com.assetflux.preview

import net.jpountz.lz4.LZ4Factory
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class UnityBundleBlock(
    val uncompressedSize: Long,
    val compressedSize: Long,
    val flags: Int
)

data class UnityBundleEntry(
    val offset: Long,
    val size: Long,
    val flags: Long,
    val path: String
)

data class UnityBundleMetadata(
    val blocks: List<UnityBundleBlock>,
    val entries: List<UnityBundleEntry>
)

object UnityBundleMetadataReader {

    private const val MAX_METADATA_SIZE = 32 * 1024 * 1024
    private const val MAX_BLOCK_COUNT = 100_000
    private const val MAX_ENTRY_COUNT = 100_000

    fun read(
        inputAfterHeader: InputStream,
        header: UnityBundleHeader
    ): UnityBundleMetadata {
        require(header.formatVersion >= 6) {
            "Parser metadata ini ditujukan untuk UnityFS versi 6 ke atas."
        }

        require((header.flags and 0x80L) == 0L) {
            "Metadata berada di akhir bundle; mode ini belum didukung."
        }

        val compressedSize = header.compressedBlocksInfoSize
        val uncompressedSize = header.uncompressedBlocksInfoSize

        require(compressedSize in 1L..MAX_METADATA_SIZE.toLong()) {
            "Ukuran metadata terkompresi tidak valid."
        }
        require(uncompressedSize in 1L..MAX_METADATA_SIZE.toLong()) {
            "Ukuran metadata hasil dekompresi tidak valid."
        }

        val compressed = ByteArray(compressedSize.toInt())
        DataInputStream(inputAfterHeader).readFully(compressed)

        val metadataBytes = when (header.flags and 0x3fL) {
            0L -> {
                require(compressedSize == uncompressedSize) {
                    "Ukuran metadata tidak cocok untuk data tanpa kompresi."
                }
                compressed
            }

            2L, 3L -> {
                val output = ByteArray(uncompressedSize.toInt())
                val decompressor = LZ4Factory.fastestInstance()
                    .safeDecompressor()

                val decodedSize = decompressor.decompress(
                    compressed,
                    0,
                    compressed.size,
                    output,
                    0,
                    output.size
                )

                require(decodedSize == output.size) {
                    "Ukuran metadata hasil dekompresi tidak cocok."
                }
                output
            }

            else -> throw IllegalArgumentException(
                "Metode kompresi metadata belum didukung: " +
                    (header.flags and 0x3fL)
            )
        }

        val data = DataInputStream(ByteArrayInputStream(metadataBytes))

        // Hash 16 byte di awal metadata.
        val hash = ByteArray(16)
        data.readFully(hash)

        val blockCount = data.readInt()
        require(blockCount in 0..MAX_BLOCK_COUNT) {
            "Jumlah blok tidak valid: $blockCount"
        }

        val blocks = ArrayList<UnityBundleBlock>(blockCount)

        repeat(blockCount) {
            val uncompressedBlockSize = readUnsignedInt(data)
            val compressedBlockSize = readUnsignedInt(data)
            val blockFlags = data.readUnsignedShort()

            require(uncompressedBlockSize > 0L) {
                "Ukuran blok hasil dekompresi tidak valid."
            }
            require(compressedBlockSize > 0L) {
                "Ukuran blok terkompresi tidak valid."
            }

            blocks.add(
                UnityBundleBlock(
                    uncompressedSize = uncompressedBlockSize,
                    compressedSize = compressedBlockSize,
                    flags = blockFlags
                )
            )
        }

        val entryCount = data.readInt()
        require(entryCount in 0..MAX_ENTRY_COUNT) {
            "Jumlah file internal tidak valid: $entryCount"
        }

        val entries = ArrayList<UnityBundleEntry>(entryCount)

        repeat(entryCount) {
            val offset = data.readLong()
            val size = data.readLong()
            val flags = readUnsignedInt(data)
            val path = readNullTerminatedString(data)

            require(offset >= 0L && size >= 0L) {
                "Offset atau ukuran file internal tidak valid."
            }

            entries.add(
                UnityBundleEntry(
                    offset = offset,
                    size = size,
                    flags = flags,
                    path = path
                )
            )
        }

        require(data.available() == 0) {
            "Masih ada ${data.available()} byte metadata yang belum dibaca."
        }

        return UnityBundleMetadata(
            blocks = blocks,
            entries = entries
        )
    }

    private fun readUnsignedInt(input: DataInputStream): Long =
        input.readInt().toLong() and 0xffffffffL

    private fun readNullTerminatedString(
        input: DataInputStream
    ): String {
        val bytes = ArrayList<Byte>()

        while (bytes.size < 16_384) {
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
            "Nama file internal terlalu panjang."
        )
    }
}
