package com.nerosec.sono.commons.exception

class EntityException(
    type: Type = Type.ENTITY_NOT_FOUND,
    message: String? = null
) : RuntimeException(message) {

    enum class Type {
        ENTITY_NOT_FOUND
    }
}
