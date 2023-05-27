package com.nerosec.sono.storage.contract.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class Storage(
    val id: String,
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("created_on")
    val created: Instant
)
