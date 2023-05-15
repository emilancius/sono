package com.nerosec.sono.commons.io.compression

import java.nio.file.Path

interface Compressor {

    fun compress(paths: List<Path>, target: Path): Path

    fun compress(path: Path, target: Path): Path = compress(listOf(path), target)
}
