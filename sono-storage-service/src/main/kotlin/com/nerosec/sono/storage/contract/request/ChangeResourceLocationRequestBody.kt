package com.nerosec.sono.storage.contract.request

import com.fasterxml.jackson.annotation.JsonProperty

data class ChangeResourceLocationRequestBody(
    @JsonProperty("parent_id")
    val parentId: String
)
