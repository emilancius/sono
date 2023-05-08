package com.nerosec.sono.commons.exception

class IOException(val type: Type, message: String? = null) : RuntimeException(message) {

    enum class Type {
        FILE_ALREADY_EXISTS,
        FILE_NOT_FOUND,
        FILE_IS_NOT_A_DIRECTORY
    }
}
