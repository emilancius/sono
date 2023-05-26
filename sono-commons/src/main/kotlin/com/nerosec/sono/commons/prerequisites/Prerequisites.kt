package com.nerosec.sono.commons.prerequisites

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.ErrorResponseException
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.core.Response

object Prerequisites {

    // ArgumentException

    fun requireStringArgumentContainsAnyText(argument: String, message: (() -> String?)? = null) =
        requireArgument(!argument.trim().isEmpty(), message)

    fun requireStringArgumentContainsAnyText(argument: String, name: String) =
        requireStringArgumentContainsAnyText(argument) { "Argument '$name' cannot be empty." }

    fun requireStringArgumentIsEntityId(argument: String, entityTypes: Array<EntityType>, message: (() -> String?)? = null) =
        requireArgument(argument.isEntityId(*entityTypes), message)

    fun requireStringArgumentIsEntityId(argument: String, entityType: EntityType, message: (() -> String?)? = null) =
        requireStringArgumentIsEntityId(argument, arrayOf(entityType), message)

    fun requireStringArgumentIsEntityId(argument: String, entityTypes: Array<EntityType>, name: String) =
        requireStringArgumentIsEntityId(argument, entityTypes) { "Argument '$name' is incorrect." }

    fun requireStringArgumentIsEntityId(argument: String, entityType: EntityType, name: String) =
        requireStringArgumentIsEntityId(argument, arrayOf(entityType), name)

    fun requireIntArgumentIsGreater(argument: Int, other: Int, message: (() -> String?)? = null) =
        requireArgument(argument > other, message)

    fun requireIntArgumentIsGreater(argument: Int, other: Int, name: String) =
        requireIntArgumentIsGreater(argument, other) { "Argument's '$name' value ($argument) must be greater than $other." }

    fun requireIntArgumentIsInRange(argument: Int, rangeStart: Int, rangeEnd: Int, message: (() -> String?)? = null) =
        requireArgument(argument in rangeStart..rangeEnd, message)

    fun requireIntArgumentIsInRange(argument: Int, rangeStart: Int, rangeEnd: Int, name: String) =
        requireIntArgumentIsInRange(argument, rangeStart, rangeEnd) { "Argument's '$name' value ($argument) must be in range [$rangeStart; $rangeEnd]." }

    fun requireArgumentIsInCollection(argument: Any?, collection: Collection<Any?>, message: (() -> String?)? = null) =
        requireArgument(argument in collection, message)

    fun requireArgumentIsInCollection(argument: Any?, collection: Collection<Any?>, name: String) =
        requireArgumentIsInCollection(argument, collection) { "Argument's '$name' value ($argument) must be one of $collection." }

    // ErrorResponseException (BAD_REQUEST)

    fun requirePathParameterContainsAnyText(parameter: String, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(!parameter.trim().isEmpty(), request, message)

    fun requirePathParameterContainsAnyText(parameter: String, request: HttpServletRequest, name: String) =
        requirePathParameterContainsAnyText(parameter, request) { "Path parameter '$name' cannot be empty." }

    fun requirePathParameterIsEntityId(parameter: String, entityTypes: Array<EntityType>, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(parameter.isEntityId(*entityTypes), request, message)

    fun requirePathParameterIsEntityId(parameter: String, entityType: EntityType, request: HttpServletRequest, message: (() -> String?)? = null) =
        requirePathParameterIsEntityId(parameter, arrayOf(entityType), request, message)

    fun requirePathParameterIsEntityId(parameter: String, entityTypes: Array<EntityType>, request: HttpServletRequest, name: String) =
        requirePathParameterIsEntityId(parameter, entityTypes, request) { "Path parameter '$name' is incorrect." }

    fun requirePathParameterIsEntityId(parameter: String, entityType: EntityType, request: HttpServletRequest, name: String) =
        requirePathParameterIsEntityId(parameter, arrayOf(entityType), request, name)

    fun requireQueryParameterIsGreater(parameter: Int, other: Int, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(parameter > other, request, message)

    fun requireQueryParameterIsGreater(parameter: Int, other: Int, request: HttpServletRequest, name: String) =
        requireQueryParameterIsGreater(parameter, other, request) { "Query parameter's '$name' value ($parameter) must be greater thant $other." }

    fun requireQueryParameterIsInRange(parameter: Int, rangeStart: Int, rangeEnd: Int, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(parameter in rangeStart..rangeEnd, request, message)

    fun requireQueryParameterIsInRange(parameter: Int, rangeStart: Int, rangeEnd: Int, request: HttpServletRequest, name: String) =
        requireQueryParameterIsInRange(parameter, rangeStart, rangeEnd, request) { "Query parameter's '$name' value ($parameter) must be in range [$rangeStart; $rangeEnd]." }

    fun requireQueryParameterIsInCollection(parameter: Any?, collection: Collection<Any?>, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(parameter in collection, request, message)

    fun requireQueryParameterIsInCollection(parameter: Any?, collection: Collection<Any?>, request: HttpServletRequest, name: String) =
        requireQueryParameterIsInCollection(parameter, collection, request) { "Query parameter's '$name' value ($parameter) must be one of $collection." }

    fun requireRequestBodyPropertyContainsAnyText(property: String, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(!property.trim().isEmpty(), request, message)

    fun requireRequestBodyPropertyContainsAnyText(property: String, request: HttpServletRequest, name: String) =
        requireRequestBodyPropertyContainsAnyText(property, request) { "Request body property '$name' cannot be empty." }

    fun requireRequestBodyPropertyIsEntityId(property: String, entityTypes: Array<EntityType>, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(property.isEntityId(*entityTypes), request, message)

    fun requireRequestBodyPropertyIsEntityId(property: String, entityType: EntityType, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequestBodyPropertyIsEntityId(property, arrayOf(entityType), request, message)

    fun requireRequestBodyPropertyIsEntityId(property: String, entityTypes: Array<EntityType>, request: HttpServletRequest, name: String) =
        requireRequestBodyPropertyIsEntityId(property, entityTypes, request) { "Request body property '$name' is incorrect." }

    fun requireRequestBodyPropertyIsEntityId(property: String, entityType: EntityType, request: HttpServletRequest, name: String) =
        requireRequestBodyPropertyIsEntityId(property, arrayOf(entityType), request, name)

    fun requireRequestBodyPropertyLength(property: String, minLength: Int, maxLength: Int, request: HttpServletRequest, message: (() -> String?)? = null) =
        requireRequest(property.length in minLength..maxLength, request, message)

    fun requireRequestBodyPropertyLength(property: String, minLength: Int, maxLength: Int, request: HttpServletRequest, name: String) =
        requireRequestBodyPropertyLength(property, minLength, maxLength, request) { "Request body property '$name' must be $minLength to $maxLength characters length." }

    private fun requireArgument(expression: Boolean, message: (() -> String?)? = null) {
        if (!expression) throw ArgumentException(message?.invoke())
    }

    private fun requireRequest(expression: Boolean, request: HttpServletRequest, message: (() -> String?)? = null) {
        if (!expression) throw ErrorResponseException(request, Response.Status.BAD_REQUEST, message?.invoke())
    }
}
