package com.nerosec.sono.commons.exception

class EntityException(type: Type, message: String? = null) : RuntimeException(message) {

    enum class Type {
        ENTITY_NOT_FOUND
    }
}
