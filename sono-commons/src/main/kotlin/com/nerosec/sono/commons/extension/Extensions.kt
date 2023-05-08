package com.nerosec.sono.commons.extension

import com.nerosec.sono.commons.CopyStrategy
import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.extension.Extensions.compressAsZip
import com.nerosec.sono.commons.extension.Extensions.copyAs
import com.nerosec.sono.commons.extension.Extensions.copyTo
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.io.compression.CompressionParameters
import com.nerosec.sono.commons.io.compression.CompressionType
import com.nerosec.sono.commons.io.compression.CompressorFactory
import com.nerosec.sono.commons.io.compression.ExtractorFactory
import com.nerosec.sono.commons.persistence.EntityType
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.*

object Extensions {

    private const val UPPERCASE_VERSION_4_UUID_REGEX_STRING =
        "[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}"
    private const val DEFAULT_BUFFER_SIZE = 8 * 1024;

    fun String.mask(substring: String): String = this.replace(substring, "*".repeat(substring.length))

    fun String.isEntityId(vararg entityTypes: EntityType): Boolean {
        val types = if (entityTypes.isEmpty()) EntityType.values() else entityTypes
        return this.matches(Regex("(${types.joinToString("|") { it.name }}).$UPPERCASE_VERSION_4_UUID_REGEX_STRING"))
    }

    fun String.hash(hashType: HashType): String =
        try {
            MessageDigest.getInstance(hashType.value).digest(this.toByteArray()).toHexString()
        } catch (exception: Exception) {
            throw RuntimeException("String could not be hashed.", exception)
        }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    fun Path.list(depth: Int = 1): List<Path> {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        if (!this.isDirectory()) throw IOException("'$this' is not a directory.")
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

    fun Path.removeDirectoryContents() {
        this.list(Int.MAX_VALUE).reversed().forEach { it.deleteExisting() }
    }

    fun Path.remove() {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        if (this.isDirectory()) this.removeDirectoryContents()
        this.deleteExisting()
    }

    fun InputStream.copyTo(path: Path): Path {
        if (path.exists()) throw IOException("'$path' exists.")
        if (!path.parent.exists()) throw IOException("Parent directory for '$path' could not be found.")
        Files.copy(this, path)
        return path
    }

    fun Path.createFromInputStream(inputStream: InputStream): Path =
        inputStream.copyTo(this)

    fun Path.bytesCount(): Long {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        return if (this.isDirectory()) {
            this.list(Int.MAX_VALUE).sumOf { if (it.isDirectory()) 0L else it.bytesCount() }
        } else {
            Files.size(this)
        }
    }

    fun Path.calculateContentHash(hashType: HashType): String {
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

    fun Path.copyAs(path: Path, copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT): Path {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        if (!this.parent.exists()) throw IOException("Parent directory for '$this' could not be found.")
        return this.copy(path, copyStrategy)
    }

    fun Path.copyTo(directory: Path, copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT): Path {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        if (!directory.exists()) throw IOException("Directory '$directory' could not be found.")
        if (!directory.isDirectory()) throw IOException("'$directory' is not a directory.")
        return this.copy(directory.resolve(this.name), copyStrategy)
    }

    fun Path.moveTo(directory: Path, copyStrategy: CopyStrategy = CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT): Path {
        val copy = copyTo(directory, copyStrategy)
        remove()
        return copy
    }

    fun Path.renameTo(name: String): Path {
        if (name.trim().isEmpty()) throw ArgumentException("Argument 'name' cannot be empty.")
        if (!this.exists()) throw IOException("'$this' could not be found.")
        val path = this.resolveSibling(name)
        if (path.exists()) throw IOException("'$path' exists.")
        Files.move(this, path)
        return path
    }

    fun List<Path>.compressAsZip(destination: Path, compressionLevel: Int = 5): Path {
        val parameters = CompressionParameters.Creator().level(compressionLevel).create()
        return CompressorFactory.create(CompressionType.ZIP, parameters).compress(this, destination)
    }

    fun Path.compressAsZip(destination: Path, compressionLevel: Int = 5): Path =
        listOf(this).compressAsZip(destination, compressionLevel)

    fun Path.isZip(): Boolean {
        if (!this.exists()) throw IOException("'$this' could not be found.")
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

    fun Path.extractZip(directory: Path): List<Path> {
        if (!this.exists()) throw IOException("'$this' could not be found.")
        if (!this.isZip()) throw IOException("'$this' is not an archive.")
        return ExtractorFactory.create(CompressionType.ZIP).extract(this, directory)
    }

    private fun Path.copy(copy: Path, copyStrategy: CopyStrategy): Path {
        if (copy.exists()) {
            when (copyStrategy) {
                CopyStrategy.RAISE_EXCEPTION_ON_CONFLICT -> throw IOException("'$copy' exists.")
                CopyStrategy.OVERWRITE_ON_CONFLICT -> copy.remove()
            }
        }
        Files.copy(this, copy)
        if (this.isDirectory()) {
            this.list(Int.MAX_VALUE).forEach {
                Files.copy(it, copy.resolve(this.relativize(it)))
            }
        }
        return copy
    }
}
