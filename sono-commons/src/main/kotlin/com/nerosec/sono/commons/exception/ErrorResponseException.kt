package com.nerosec.sono.commons.exception

import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.core.Response

class ErrorResponseException(
    val request: HttpServletRequest,
    val status: Response.Status,
    message: String? = null
) : RuntimeException(message)
