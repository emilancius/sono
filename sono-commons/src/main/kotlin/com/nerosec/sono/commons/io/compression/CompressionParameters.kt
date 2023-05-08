package com.nerosec.sono.commons.io.compression

class CompressionParameters private constructor(
    val level: Int
) {
    data class Creator(
        var level: Int = 5
    ) {
        fun level(level: Int): Creator {
            require(level in 0..9) {
                "Compression parameter 'level' must be in [0; 9] range."
            }
            this.level = level
            return this
        }

        fun create(): CompressionParameters = CompressionParameters(level)
    }
}
