package com.nerosec.sono.commons.io.compression

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.extension.Extensions.createFromInputStream
import com.nerosec.sono.commons.extension.Extensions.isZip
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream


class ZipExtractor : Extractor {

    override fun extract(source: Path, directory: Path?): List<Path> {
        if (!source.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$source' could not be extracted: '$source' could not be found."
            )
        }
        if (!source.isZip()) {
            throw ArgumentException(
                "'$source' could not be extracted: '$source' is not an archive of type '${CompressionType.ZIP.name}'"
            )
        }
        val d = directory ?: source.parent
        if (!d.parent.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$source' could not be extracted: parent directory '${d.parent}' could not be found."
            )
        }
        if (!d.exists()) {
            d.createDirectory()
        }
        val paths = ArrayList<Path>()
        ZipInputStream(source.inputStream()).use { inputStream ->
            var entry = inputStream.nextEntry
            for (i in generateSequence(0) { it }) {
                entry ?: break
                var path = d.resolve(entry.name)
                if (path.exists()) {
                    throw IOException(
                        IOException.Type.FILE_ALREADY_EXISTS,
                        "'$source' could ot be extracted: archive contains entry '${entry.name}', that exists in directory '$d'."
                    )
                }
                path = if (entry.isDirectory) path.createDirectory() else path.createFromInputStream(inputStream)
                paths.add(path)
                inputStream.closeEntry()
                entry = inputStream.nextEntry
            }
        }
        return paths
    }
}
