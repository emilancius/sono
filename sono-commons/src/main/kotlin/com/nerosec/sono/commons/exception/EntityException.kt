package com.nerosec.sono.commons.exception

class EntityException(
    val type: Type = Type.ENTITY_NOT_FOUND,
    message: String? = null
) : RuntimeException(message) {

    enum class Type {
        ENTITY_NOT_FOUND
    }
}
