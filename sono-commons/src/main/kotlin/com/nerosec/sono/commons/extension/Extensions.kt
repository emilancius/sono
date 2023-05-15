package com.nerosec.sono.commons.extension

import com.nerosec.sono.commons.CopyStrategy
import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.io.compression.CompressionParameters
import com.nerosec.sono.commons.io.compression.CompressionType
import com.nerosec.sono.commons.io.compression.CompressorFactory
import com.nerosec.sono.commons.io.compression.ExtractorFactory
import com.nerosec.sono.commons.persistence.entity.EntityType
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.*

object Extensions {

    private const val UPPERCASE_VERSION_4_UUID_REGEX_STRING =
        "[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}"
    private const val DEFAULT_BUFFER_SIZE = 8 * 1024

    // String

    fun String.mask(substring: String): String = this.replace(substring, "*".repeat(substring.length))

    fun String.isEntityId(vararg entityTypes: EntityType): Boolean {
        val types = if (entityTypes.isEmpty()) EntityType.values() else entityTypes
        return this.matches(Regex("(${types.joinToString("|") { it.name }}).$UPPERCASE_VERSION_4_UUID_REGEX_STRING"))
    }

    fun String.hash(hashType: HashType): String =
        try {
            MessageDigest.getInstance(hashType.value).digest(this.toByteArray()).toHexString()
        } catch (exception: Exception) {
            throw RuntimeException("String could not be hashed to '${hashType.value}'.", exception)
        }

    // ByteArray

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    // Path

    fun Path.list(depth: Int = 1): List<Path> {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "Entries for '$this' could not be listed: '$this' could not be found."
            )
        }
        if (!this.isDirectory()) {
            throw IOException(
                IOException.Type.FILE_IS_NOT_A_DIRECTORY,
                "Entries for '$this' could not be listed: '$this' is not a directory."
            )
        }
        if (depth < 1) return emptyList()
        val entries = ArrayList<Path>()
        this.listDirectoryEntries().forEach { entry ->
            entries.add(entry)
            if (entry.isDirectory()) {
                entry.list(depth.dec()).forEach { entries.add(it) }
            }
        }
        return entries
    }

    fun Path.remove() {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be removed: '$this' could not be found."
            )
        }
        if (this.isDirectory()) this.removeDirectoryContents()
        this.deleteExisting()
    }

    fun Path.createFromInputStream(inputStream: InputStream): Path {
        if (this.exists()) {
            throw IOException(
                IOException.Type.FILE_ALREADY_EXISTS,
                "'$this' could not be created from an input stream: '$this' exists."
            )
        }
        if (!this.parent.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be created from an input stream: parent directory for '$this' could not be found."
            )
        }
        return inputStream.copyTo(this)
    }

    fun Path.bytesCount(): Long {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "Bytes count for '$this' could not be calculated: '$this' could not be found."
            )
        }
        return if (this.isDirectory()) {
            this.list(Int.MAX_VALUE).sumOf { if (it.isDirectory()) 0L else it.bytesCount() }
        } else {
            Files.size(this)
        }
    }

    fun Path.getContentHash(hashType: HashType): String {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "Content hash of type '${hashType.value}' for '$this' could not be calculated: '$this' could not be found."
            )
        }
        val digest = MessageDigest.getInstance(hashType.value)
        this.inputStream().use { inputStream ->
            val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = inputStream.read(byteArray, 0, DEFAULT_BUFFER_SIZE)
            for (i in generateSequence(0) { it }) {
                if (read > -1) {
                    digest.update(byteArray, 0, read)
                    read = inputStream.read(byteArray, 0, DEFAULT_BUFFER_SIZE)
                } else {
                    break
                }
            }
        }
        return digest.digest().toHexString()
    }

    fun Path.copyAs(target: Path, copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT): Path {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be copied as '$target': '$this' could not be found."
            )
        }
        if (!this.parent.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be copied as '$target': parent directory '${this.parent}' could not be found."
            )
        }
        return this.copy(target, copyStrategy)
    }

    fun Path.moveTo(directory: Path, copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT): Path {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be moved to directory '$directory': '$this' could not be found."
            )
        }
        if (!directory.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be moved to directory '$directory': directory '$directory' could not be found."
            )
        }
        if (!directory.isDirectory()) {
            throw IOException(
                IOException.Type.FILE_IS_NOT_A_DIRECTORY,
                "'$this' could not be moved to '$directory': '$directory' is not a directory."
            )
        }
        val copy = copyTo(directory, copyStrategy)
        remove()
        return copy
    }

    fun Path.renameTo(name: String): Path {
        if (name.trim().isEmpty()) throw ArgumentException("Argument 'name' cannot be empty.")
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "'$this' could not be renamed to '$name': '$this' could not be found."
            )
        }
        val path = this.resolveSibling(name)
        if (path.exists()) {
            throw IOException(
                IOException.Type.FILE_ALREADY_EXISTS,
                "'$this' could not be renamed to '$name': '$path' exists."
            )
        }
        Files.move(this, path)
        return path
    }

    fun List<Path>.compressAsZip(destination: Path, compressionLevel: Int = 5): Path {
        val parameters = CompressionParameters.Creator()
            .level(compressionLevel)
            .create()
        return CompressorFactory
            .create(CompressionType.ZIP, parameters)
            .compress(this, destination)
    }

    fun Path.compressAsZip(destination: Path, compressionLevel: Int = 5): Path =
        listOf(this).compressAsZip(destination, compressionLevel)

    fun Path.isZip(): Boolean {
        if (!this.exists()) {
            throw IOException(
                IOException.Type.FILE_NOT_FOUND,
                "Cannot determine if '$this' is an archive of type '${CompressionType.ZIP.name}': '$this' could not be found."
            )
        }
        if (this.isDirectory()) {
            return false
        }
        return try {
            ZipFile(this.toString())
            true
        } catch (exception: ZipException) {
            false
        }
    }

    fun Path.extractZip(directory: Path): List<Path> = ExtractorFactory
        .create(CompressionType.ZIP)
        .extract(this, directory)

    private fun Path.removeDirectoryContents() {
        this.list(Int.MAX_VALUE).reversed().forEach { it.deleteExisting() }
    }

    private fun Path.copy(target: Path, copyStrategy: CopyStrategy): Path {
        if (target.exists()) {
            when (copyStrategy) {
                CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT ->
                    throw IOException(
                        IOException.Type.FILE_ALREADY_EXISTS,
                        "'$this' could not be copied to '$target': '$target' exists."
                    )

                CopyStrategy.OVERWRITE_ON_CONFLICT -> target.remove()
            }
        }
        Files.copy(this, target)
        if (this.isDirectory()) {
            this.list(Int.MAX_VALUE).forEach {
                Files.copy(it, target.resolve(this.relativize(it)))
            }
        }
        return target
    }

    private fun Path.copyTo(
        directory: Path,
        copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT
    ): Path = this.copy(directory.resolve(this.name), copyStrategy)

    // InputStream

    private fun InputStream.copyTo(path: Path): Path {
        Files.copy(this, path)
        return path
    }
}
