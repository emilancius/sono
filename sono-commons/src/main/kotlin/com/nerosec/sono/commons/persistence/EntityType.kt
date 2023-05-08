package com.nerosec.sono.commons.persistence

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.extension.Extensions.isEntityId

enum class EntityType {
    USER,
    STORAGE,
    RESOURCE;

    companion object {
        fun createFromEntityId(entityId: String): EntityType {
            if (entityId.trim().isEmpty()) throw ArgumentException("Argument 'entityId' cannot be empty.")
            if (!entityId.isEntityId()) throw ArgumentException("Argument 'entityId' is incorrect.")
            return EntityType.valueOf(entityId.substringBefore('.'))
        }
    }
}
