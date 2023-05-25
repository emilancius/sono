package com.nerosec.sono.commons.prerequisites

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.ErrorResponseException
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.core.Response

object Prerequisites {

    fun requireStringArgumentContainsAnyText(argument: String, message: (() -> String)? = null) =
        requireArgument(argument.trim().isEmpty(), message)

    fun requireStringArgumentIsEntityId(
        argument: String,
        vararg entityTypes: EntityType,
        message: (() -> String)? = null
    ) = requireArgument(argument.isEntityId(*entityTypes), message)

    fun requireStringArgumentContainsAnyText(argument: String, name: String) =
        requireArgument(argument.trim().isEmpty()) { "Argument '$name' cannot be empty." }

    fun requireStringArgumentIsEntityId(
        argument: String,
        name: String,
        vararg entityTypes: EntityType
    ) = requireArgument(argument.isEntityId(*entityTypes)) { "Argument '$name' is incorrect." }

    fun requireIntArgumentIsGreaterThan(argument: Int, name: String, other: Int) =
        requireArgument(argument > other) { "Argument's '$name' value ($argument) must be greater than '$other'." }

    fun requireIntArgumentInInIncRange(
        argument: Int,
        rangeStart: Int,
        rangeEnd: Int,
        message: (() -> String)? = null
    ) = requireArgument(argument in rangeStart..rangeEnd, message)

    fun requireIntArgumentInInIncRange(
        argument: Int,
        name: String,
        rangeStart: Int,
        rangeEnd: Int
    ) = requireArgument(argument in rangeStart..rangeEnd) { "Argument's '$name' value ($argument) must be in range [$rangeStart; $rangeEnd]." }

    fun requireArgumentIsInCollection(argument: Any, collection: Collection<Any>, message: (() -> String)? = null) =
        requireArgument(argument in collection, message)

    private fun requireArgument(expression: Boolean, message: (() -> String)? = null) {
        if (!expression) {
            throw ArgumentException(message?.invoke())
        }
    }

    fun requireRequestBodyPropertyContainsAnyText(property: String, name: String, request: HttpServletRequest) =
        requireRequest(!property.trim().isEmpty(), request) { "Request body property '$name' cannot be empty." }

    fun requireRequestBodyPropertyIsEntityId(property: String, name: String, entityType: EntityType, request: HttpServletRequest) =
        requireRequest(property.isEntityId(entityType), request) { "Request body property '$name' is incorrect." }

    fun requireRequestPathParameterIsEntityId(parameter: String, name: String, entityType: EntityType, request: HttpServletRequest) =
        requireRequest(parameter.isEntityId(entityType), request) { "Path parameter '$name' is incorrect." }

    fun requireRequestQueryParameterIsGreater(parameter: Int, name: String, other: Int, request: HttpServletRequest) =
        requireRequest(parameter > other, request) { "Query parameter's '$name' value ($parameter) must be greater than $other." }

    fun requireRequestQueryParameterIsInIncRange(parameter: Int, name: String, rangeStart: Int, rangeEnd: Int, request: HttpServletRequest) =
        requireRequest(parameter in rangeStart..rangeEnd, request) { "Query parameter's '$name' value ($parameter) must be in range [$rangeStart; $rangeEnd]." }

    fun requireRequestQueryParameterIsInCollection(parameter: Any?, name: String, collection: Collection<Any?>, request: HttpServletRequest) =
        requireRequest(parameter in collection, request) { "Query parameter's '$name' value ($parameter) must be one of $collection." }

    private fun requireRequest(expression: Boolean, request: HttpServletRequest, message: () -> String) {
        if (!expression) throw ErrorResponseException(request, Response.Status.BAD_REQUEST, message.invoke())
    }
}
