package com.nerosec.sono.commons.persistence.query

data class Condition(
    val property: String,
    val value: Any? = null,
    val operation: Operation
)
