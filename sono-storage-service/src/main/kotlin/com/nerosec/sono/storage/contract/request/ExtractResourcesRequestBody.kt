package com.nerosec.sono.storage.contract.request

import com.fasterxml.jackson.annotation.JsonProperty

data class ExtractResourcesRequestBody(
    @JsonProperty("parent_id")
    val parentId: String
)
