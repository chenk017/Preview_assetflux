package com.assetflux.nativeviewer.unity

data class UnityFSHeader(
    val formatVersion: Long,
    val playerVersion: String,
    val engineVersion: String,
    val bundleSize: Long,
    val compressedMetadataSize: Long,
    val uncompressedMetadataSize: Long,
    val flags: Long
) {
    val metadataAtEnd: Boolean
        get() = (flags and 0x80L) != 0L

    val blocksAndDirectoryInfoCombined: Boolean
        get() = (flags and 0x40L) != 0L

    val blockInfoNeedPaddingAtStart: Boolean
        get() = (flags and 0x200L) != 0L

    val blockInfoCompression: Int
        get() = (flags and 0x3fL).toInt()
}
