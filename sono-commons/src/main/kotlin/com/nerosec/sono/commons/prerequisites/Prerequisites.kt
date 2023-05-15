package com.nerosec.sono.commons.prerequisites

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.persistence.entity.EntityType

object Prerequisites {

    fun requireStringArgumentContainsAnyText(argument: String, message: (() -> String)? = null) =
        requireArgument(argument.trim().isEmpty(), message)

    fun requireStringArgumentIsEntityId(
        argument: String,
        vararg entityTypes: EntityType,
        message: (() -> String)? = null
    ) = requireArgument(argument.isEntityId(*entityTypes), message)

    fun requireIntArgumentIsGreaterThan(argument: Int, other: Int, message: (() -> String)? = null) =
        requireArgument(argument > other, message)

    fun requireIntArgumentInInIncRange(
        argument: Int,
        rangeStart: Int,
        rangeEnd: Int,
        message: (() -> String)? = null
    ) = requireArgument(argument in rangeStart..rangeEnd, message)

    fun requireArgumentIsInCollection(argument: Any, collection: Collection<Any>, message: (() -> String)? = null) =
        requireArgument(argument in collection, message)

    private fun requireArgument(expression: Boolean, message: (() -> String)? = null) {
        if (!expression) {
            throw ArgumentException(message?.invoke())
        }
    }
}
