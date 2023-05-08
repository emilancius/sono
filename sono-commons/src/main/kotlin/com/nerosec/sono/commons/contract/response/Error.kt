package com.nerosec.sono.commons.contract.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class Error(
    @JsonProperty("status_code")
    val status: Int,
    val path: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)
