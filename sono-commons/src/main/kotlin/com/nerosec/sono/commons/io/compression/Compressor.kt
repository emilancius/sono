package com.nerosec.sono.commons.io.compression

import java.nio.file.Path

interface Compressor {

    fun compress(paths: List<Path>, destination: Path): Path

    fun compress(path: Path, destination: Path): Path = compress(listOf(path), destination)
}
