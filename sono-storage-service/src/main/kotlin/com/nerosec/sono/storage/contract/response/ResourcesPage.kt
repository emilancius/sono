package com.nerosec.sono.storage.contract.response

import com.fasterxml.jackson.annotation.JsonProperty

data class ResourcesPage(
    val page: Int,
    @JsonProperty("pages_count")
    val pagesCount: Int,
    val contents: List<Resource> = emptyList()
)
