package com.nerosec.sono.commons.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant

@MappedSuperclass
abstract class BaseEntity {

    companion object {
        const val COL_ID = "ID"
        const val COL_VERSION = "VERSION"
        const val COL_CREATED_AT = "CREATED_AT"
        const val COL_LAST_UPDATED_AT = "LAST_UPDATED_AT"
    }

    @Id
    @Column(name = COL_ID)
    var id: String? = null

    @Column(name = COL_VERSION)
    var version: Int = 1

    @Column(name = COL_CREATED_AT)
    var created: Instant? = null

    @Column(name = COL_LAST_UPDATED_AT)
    var lastUpdated: Instant? = null

    @PrePersist
    open fun beforeSave() {
        if (created == null) {
            created = Instant.now()
        }
    }

    @PreUpdate
    fun beforeUpdate() {
        version = version.inc()
        lastUpdated = Instant.now()
    }
}
