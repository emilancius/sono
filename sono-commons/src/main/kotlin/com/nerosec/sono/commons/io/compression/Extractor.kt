package com.nerosec.sono.commons.io.compression

import java.nio.file.Path

fun interface Extractor {

    fun extract(source: Path, directory: Path?): List<Path>
}
