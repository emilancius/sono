package com.nerosec.storage.contract.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class StoragesPage(
    val page: Int,
    @JsonProperty("pages_count")
    val pagesCount: Int,
    val contents: List<Storage> = emptyList()
)
