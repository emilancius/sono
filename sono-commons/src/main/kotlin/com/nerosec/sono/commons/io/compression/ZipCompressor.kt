package com.nerosec.sono.commons.io.compression

import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.extension.Extensions.list
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class ZipCompressor(private val parameters: CompressionParameters) : Compressor {

    companion object {
        private val PATH_SEPARATOR: Char = File.separatorChar
    }

    override fun compress(paths: List<Path>, target: Path): Path {
        paths.forEach {
            if (!it.exists()) {
                throw IOException(
                    IOException.Type.FILE_NOT_FOUND,
                    "'$it' could not be compressed: '$it' could not be found."
                )
            }
        }
        if (target.exists()) {
            throw IOException(IOException.Type.FILE_ALREADY_EXISTS, "'$target' could not be created: '$target' exists.")
        }
        if (!target.parent.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$target' could not be created: parent directory '${target.parent}' could not be found."
            )
        }
        val outputStream = ZipOutputStream(target.outputStream())
        outputStream.setLevel(parameters.level)
        outputStream.use {
            paths.forEach { path -> compress(path, path.name, it) }
        }
        return target
    }

    private fun compress(path: Path, name: String, outputStream: ZipOutputStream) {
        if (path.isDirectory()) {
            // Entry, that is directory must end in path separator character.
            val entry = if (name.last() == PATH_SEPARATOR) name else "$name$PATH_SEPARATOR"
            outputStream.putNextEntry(ZipEntry(entry))
            outputStream.closeEntry()
            path.list().forEach { compress(it, "$name$PATH_SEPARATOR${it.name}", outputStream) }
        } else {
            outputStream.putNextEntry(ZipEntry(name))
            path.inputStream().use { it.copyTo(outputStream) }
            outputStream.closeEntry()
        }
    }
}
