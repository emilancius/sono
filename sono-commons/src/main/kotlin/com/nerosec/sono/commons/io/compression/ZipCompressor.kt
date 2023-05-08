package com.nerosec.sono.commons.io.compression

import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.extension.Extensions.list
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class ZipCompressor(
    private val parameters: CompressionParameters
) : Compressor {

    companion object {
        private val PATH_SEPARATOR: Char = File.separatorChar
    }

    override fun compress(paths: List<Path>, destination: Path): Path {
        paths.forEach {
            if (!it.exists()) {
                throw IOException(IOException.Type.FILE_NOT_FOUND, "'$it' could not be found.")
            }
        }
        if (destination.exists()) {
            throw IOException(IOException.Type.FILE_ALREADY_EXISTS, "'$destination' exists.")
        }
        if (!destination.parent.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "Parent directory (${destination.parent}) for '$destination' could not be found."
            )
        }
        val outputStream = ZipOutputStream(destination.outputStream())
        outputStream.setLevel(parameters.level)
        outputStream.use {
            paths.forEach { path -> compress(path, path.name, it) }
        }
        return destination
    }

    private fun compress(path: Path, name: String, outputStream: ZipOutputStream) {
        if (path.isDirectory()) {
            val entry = if (name.last() == PATH_SEPARATOR) name else "$name$PATH_SEPARATOR"
            outputStream.putNextEntry(ZipEntry(entry))
            outputStream.closeEntry()
            path.list().forEach {
                compress(it, "$name$PATH_SEPARATOR${it.name}", outputStream)
            }
            return
        }
        outputStream.putNextEntry(ZipEntry(name))
        path.inputStream().use { it.copyTo(outputStream) }
        outputStream.closeEntry()
    }
}
