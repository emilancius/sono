package com.nerosec.sono.commons.io.compression

import java.nio.file.Path

interface Extractor {

    fun extract(path: Path, directory: Path? = null): List<Path>
}
