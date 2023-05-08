package com.nerosec.storage.persistence.entity

import com.nerosec.sono.commons.persistence.EntityId
import com.nerosec.sono.commons.persistence.EntityType
import com.nerosec.storage.contract.response.Storage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = StorageEntity.TABLE)
data class StorageEntity(
    @Id
    @Column(name = COL_ID)
    var id: String? = null,
    @Column(name = COL_USER_ID)
    val userId: String,
    @Column(name = COL_PATH)
    val path: String,
    @Column(name = COL_VERSION)
    var version: Int = 1,
    @Column(name = COL_CREATED_AT)
    var created: Instant? = null,
    @Column(name = COL_LAST_UPDATED_AT)
    var lastUpdated: Instant? = null
) {
    companion object {
        const val TABLE = "STORAGE"
        const val COL_ID = "ID"
        const val COL_USER_ID = "USER_ID"
        const val COL_PATH = "PATH"
        const val COL_VERSION = "VERSION"
        const val COL_CREATED_AT = "CREATED_AT"
        const val COL_LAST_UPDATED_AT = "LAST_UPDATED_AT"
    }

    @PrePersist
    fun beforeSave() {
        if (id == null) {
            id = EntityId.generate(EntityType.STORAGE)
        }
        if (created == null) {
            created = Instant.now()
        }
    }

    @PreUpdate
    fun beforeUpdate() {
        version = version.inc()
        lastUpdated = Instant.now()
    }

    fun toStorage(): Storage =
        Storage(
            id = id!!,
            userId = userId,
            created = created!!
        )
}
