package com.nerosec.storage.contract.request

import com.fasterxml.jackson.annotation.JsonProperty

data class CreateStorageRequestBody(
    @JsonProperty("user_id")
    val userId: String
)
