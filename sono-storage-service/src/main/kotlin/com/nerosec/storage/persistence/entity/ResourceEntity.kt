package com.nerosec.storage.persistence.entity

import com.nerosec.sono.commons.persistence.EntityId
import com.nerosec.sono.commons.persistence.EntityType
import com.nerosec.storage.contract.response.Resource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = ResourceEntity.TABLE)
data class ResourceEntity(
    @Id
    @Column(name = COL_ID)
    var id: String? = null,
    @Column(name = COL_PARENT_ID)
    val parentId: String,
    @Column(name = COL_USER_ID)
    val userId: String,
    @Column(name = COL_CONTENT_HASH)
    val contentHash: String?,
    @Column(name = COL_NAME)
    val name: String,
    @Column(name = COL_EXTENSION)
    val extension: String?,
    @Column(name = COL_PATH)
    val path: String,
    @Column(name = COL_TYPE)
    val type: String?,
    @Column(name = COL_BYTES_COUNT)
    val bytesCount: Long,
    @Column(name = COL_IS_DIRECTORY)
    val directory: Boolean,
    @Column(name = COL_IS_TRASHED)
    val trashed: Boolean = false,
    @Column(name = COL_DESCRIPTION)
    val description: String? = null,
    @Column(name = COL_VERSION)
    var version: Int = 1,
    @Column(name = COL_CREATED_AT)
    var created: Instant? = null,
    @Column(name = COL_LAST_UPDATED_AT)
    var lastUpdated: Instant? = null
) {
    companion object {
        const val TABLE = "RESOURCE"
        const val COL_ID = "ID"
        const val COL_PARENT_ID = "PARENT_ID"
        const val COL_USER_ID = "USER_ID"
        const val COL_CONTENT_HASH = "CONTENT_HASH"
        const val COL_NAME = "NAME"
        const val COL_EXTENSION = "EXTENSION"
        const val COL_PATH = "PATH"
        const val COL_TYPE = "TYPE"
        const val COL_BYTES_COUNT = "BYTES_COUNT"
        const val COL_IS_DIRECTORY = "IS_DIRECTORY"
        const val COL_IS_TRASHED = "IS_TRASHED"
        const val COL_DESCRIPTION = "DESCRIPTION"
        const val COL_VERSION = "VERSION"
        const val COL_CREATED_AT = "CREATED_AT"
        const val COL_LAST_UPDATED_AT = "LAST_UPDATED_AT"
    }

    @PrePersist
    fun beforeSave() {
        if (id == null) {
            id = EntityId.generate(EntityType.RESOURCE)
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

    fun toResource(): Resource =
        Resource(
            id = id!!,
            parentId = parentId,
            userId = userId,
            contentHash = contentHash,
            name = name,
            extension = extension,
            type = type,
            bytesCount = bytesCount,
            directory = directory,
            trashed = trashed,
            description = description,
            version = version,
            created = created!!,
            lastUpdated = lastUpdated
        )
}
