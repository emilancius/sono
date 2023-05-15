package com.nerosec.sono.commons.io.compression

import java.nio.file.Path

interface Extractor {

    fun extract(source: Path, directory: Path? = null): List<Path>
}
