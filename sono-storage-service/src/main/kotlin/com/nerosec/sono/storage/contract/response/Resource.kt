package com.nerosec.sono.storage.contract.response

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class Resource(
    val id: String,
    @JsonProperty("parent_id")
    val parentId: String,
    @JsonProperty("user_id")
    val userId: String,
    @JsonProperty("content_hash")
    val contentHash: String?,
    val name: String,
    val extension: String?,
    val type: String?,
    @JsonProperty("bytes_count")
    val bytesCount: Long,
    @JsonProperty("is_directory")
    val directory: Boolean,
    @JsonProperty("is_trashed")
    val trashed: Boolean,
    val description: String?,
    val version: Int,
    @JsonProperty("created_on")
    val created: Instant,
    @JsonProperty("last_updated_on")
    val lastUpdated: Instant?
)
