package com.nerosec.sono.storage.contract.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class CreateResourceRequestBody(
    @JsonProperty("parent_id")
    val parentId: String,
    @JsonProperty("user_id")
    val userId: String,
    val name: String,
    @JsonProperty("is_directory")
    val directory: Boolean,
    val description: String? = null
) {
    companion object {
        private val mapper = jacksonObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        fun createFromJsonString(json: String): CreateResourceRequestBody = mapper
            .readValue(json, CreateResourceRequestBody::class.java)
    }
}
