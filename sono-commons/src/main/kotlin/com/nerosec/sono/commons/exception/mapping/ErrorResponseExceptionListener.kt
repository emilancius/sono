package com.nerosec.sono.commons.exception.mapping

import com.nerosec.sono.commons.contract.response.Error
import com.nerosec.sono.commons.exception.ErrorResponseException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper

class ErrorResponseExceptionListener : ExceptionMapper<ErrorResponseException> {

    override fun toResponse(exception: ErrorResponseException): Response {
        val status = exception.status
        val responseBody =
            Error(
                status = status.statusCode,
                path = exception.request.requestURI,
                message = exception.message ?: "Unexpected error occurred."
            )
        return Response
            .status(status)
            .entity(responseBody)
            .build()
    }
}
