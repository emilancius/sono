package com.nerosec.sono.commons.io.compression

import com.nerosec.sono.commons.exception.ArgumentException

class CompressionParameters private constructor(val level: Int) {

    data class Creator(var level: Int = 5) {

        fun level(level: Int): Creator {
            if (level !in 0..9) throw ArgumentException("Compression parameter 'level' must be in [0, 9] range.")
            this.level = level
            return this
        }

        fun create(): CompressionParameters = CompressionParameters(level)
    }
}
