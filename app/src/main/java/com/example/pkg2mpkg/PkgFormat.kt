package com.example.pkg2mpkg

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wallpaper Engine PKG / MPKG container format.
 *
 * Reverse-engineered from RePKG:
 *   https://github.com/notscuffed/repkg
 *
 * Structure (little-endian):
 *   - magic        : int32 length + UTF-8 string
 *   - entryCount   : int32
 *   - entries[]    : int32 pathLength + UTF-8 path, int32 offset, int32 length
 *   - data[]       : raw bytes referenced by entries
 */
object PkgFormat {

    data class Entry(
        val fullPath: String,
        var bytes: ByteArray,
        var offset: Int = 0,
        var length: Int = 0
    ) {
        val extension: String
            get() {
                val idx = fullPath.lastIndexOf('.')
                return if (idx >= 0) fullPath.substring(idx).lowercase() else ""
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            return fullPath == other.fullPath && offset == other.offset &&
                    length == other.length && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = fullPath.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + offset
            result = 31 * result + length
            return result
        }
    }

    data class Package(
        var magic: String = "PKGV0005",
        val entries: MutableList<Entry> = mutableListOf(),
        var headerSize: Int = 0
    )

    @Throws(PkgException::class)
    fun read(input: InputStream): Package {
        val all = input.readBytes()
        val buf = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN)

        if (buf.remaining() < 4) throw PkgException("文件太小，无法解析")

        val magic = buf.readStringI32Size(maxLen = 32)
        if (buf.remaining() < 4) throw PkgException("条目数量缺失")

        val entryCount = buf.int
        if (entryCount < 0 || entryCount > 50_000) {
            throw PkgException("条目数量异常: $entryCount")
        }

        val entries = mutableListOf<Entry>()
        repeat(entryCount) {
            if (buf.remaining() < 4) throw PkgException("条目路径长度缺失")
            val path = buf.readStringI32Size(maxLen = 255)
            if (buf.remaining() < 8) throw PkgException("条目偏移/长度缺失: $path")
            val offset = buf.int
            val length = buf.int
            entries.add(Entry(fullPath = path, bytes = ByteArray(0), offset = offset, length = length))
        }

        val dataStart = buf.position()
        val packageSize = all.size

        entries.forEachIndexed { index, entry ->
            val start = dataStart + entry.offset
            val end = start + entry.length
            if (start < 0 || end > packageSize) {
                throw PkgException("条目 #$index (${entry.fullPath}) 数据越界")
            }
            entry.bytes = all.copyOfRange(start, end)
        }

        return Package(
            magic = magic,
            entries = entries,
            headerSize = dataStart
        )
    }

    @Throws(PkgException::class)
    fun write(pkg: Package, output: OutputStream) {
        if (pkg.entries.isEmpty()) throw PkgException("包内没有条目")

        val header = ByteArrayOutputStream()
        header.writeStringI32Size(pkg.magic)
        header.writeLeInt(pkg.entries.size)

        var currentOffset = 0
        pkg.entries.forEach { entry ->
            entry.length = entry.bytes.size
            entry.offset = currentOffset
            header.writeStringI32Size(entry.fullPath)
            header.writeLeInt(entry.offset)
            header.writeLeInt(entry.length)
            currentOffset += entry.length
        }

        output.write(header.toByteArray())
        pkg.entries.forEach { output.write(it.bytes) }
    }

    private fun ByteBuffer.readStringI32Size(maxLen: Int): String {
        if (remaining() < 4) throw PkgException("字符串长度字段不足")
        val size = int
        if (size < 0) throw PkgException("字符串长度不能为负: $size")
        val len = if (maxLen > 0) size.coerceAtMost(maxLen) else size
        if (remaining() < len) throw PkgException("字符串数据不足")
        val bytes = ByteArray(len)
        get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun ByteArrayOutputStream.writeStringI32Size(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeLeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeLeInt(value: Int) {
        write(value)
        write(value shr 8)
        write(value shr 16)
        write(value shr 24)
    }

    class PkgException(message: String) : Exception(message)
}
