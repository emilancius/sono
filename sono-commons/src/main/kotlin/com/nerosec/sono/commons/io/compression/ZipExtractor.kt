package com.nerosec.sono.commons.io.compression

import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.extension.Extensions.createFromInputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream


class ZipExtractor : Extractor {

    override fun extract(source: Path, directory: Path?): List<Path> {
        if (!source.exists()) {
            throw IOException(IOException.Type.FILE_NOT_FOUND, "'$this' could not be found.")
        }
        val dir = directory
            ?.let {
                if (!directory.parent.exists()) {
                    throw IOException(
                        IOException.Type.FILE_NOT_FOUND,
                        "Parent directory (${it.parent}) for '$directory' could not be found."
                    )
                }
                it
            }
            ?: source.parent!!
        if (!dir.exists()) {
            dir.createDirectory()
        }
        val paths = ArrayList<Path>()
        ZipInputStream(source.inputStream()).use { inputStream ->
            var entry = inputStream.nextEntry
            for (i in generateSequence(0) { it }) {
                if (entry == null) {
                    break
                }
                var path = dir.resolve(entry.name)
                if (path.exists()) {
                    throw IOException(
                        IOException.Type.FILE_ALREADY_EXISTS,
                        "'$source' could not be extracted: archive contains entry, that exists."
                    )
                }
                path =
                    if (entry.isDirectory) {
                        path.createDirectory()
                    } else {
                        path.createFromInputStream(inputStream)
                    }
                paths.add(path)
                inputStream.closeEntry()
                entry = inputStream.nextEntry
            }
        }
        return paths
    }
}
