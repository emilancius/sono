package com.nerosec.sono.commons.io.compression

class CompressorFactory {
    companion object {
        fun create(compressionType: CompressionType, parameters: CompressionParameters): Compressor =
            when (compressionType) {
                CompressionType.ZIP -> ZipCompressor(parameters)
            }
    }
}