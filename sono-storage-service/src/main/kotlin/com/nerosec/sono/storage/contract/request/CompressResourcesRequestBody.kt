package com.nerosec.sono.storage.contract.request

import com.fasterxml.jackson.annotation.JsonProperty

data class CompressResourcesRequestBody(
    @JsonProperty("resource_ids")
    val resourceIds: List<String>,
    @JsonProperty("parent_id")
    val parentId: String,
    val name: String
)
