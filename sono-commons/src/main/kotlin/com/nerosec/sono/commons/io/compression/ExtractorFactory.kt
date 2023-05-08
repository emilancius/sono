package com.nerosec.sono.commons.io.compression

class ExtractorFactory {

    companion object {
        fun create(type: CompressionType): Extractor =
            when (type) {
                CompressionType.ZIP -> ZipExtractor()
            }
    }
}
