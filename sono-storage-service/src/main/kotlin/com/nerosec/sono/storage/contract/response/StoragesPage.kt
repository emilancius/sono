package com.nerosec.sono.storage.contract.response

import com.fasterxml.jackson.annotation.JsonProperty

data class StoragesPage(
    val page: Int,
    @JsonProperty("pages_count")
    val pagesCount: Int,
    val contents: List<Storage> = emptyList()
)
