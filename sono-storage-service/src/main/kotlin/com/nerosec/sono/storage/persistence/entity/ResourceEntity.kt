package com.nerosec.sono.storage.persistence.entity

import com.nerosec.sono.commons.persistence.entity.BaseEntity
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.contract.response.Resource
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = ResourceEntity.TABLE)
class ResourceEntity(
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
    val description: String? = null
) : BaseEntity() {

    companion object {
        const val TABLE = "RESOURCE"
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
    }

    override fun beforeSave() {
        super.beforeSave()
        if (id == null) {
            id = EntityId.generate(EntityType.RESOURCE)
        }
    }

    fun copy(
        id: String? = this.id,
        parentId: String = this.parentId,
        userId: String = this.userId,
        contentHash: String? = this.contentHash,
        name: String = this.name,
        extension: String? = this.extension,
        path: String = this.path,
        type: String? = this.type,
        bytesCount: Long = this.bytesCount,
        directory: Boolean = this.directory,
        trashed: Boolean = this.trashed,
        description: String? = this.description,
        version: Int = this.version,
        created: Instant? = this.created,
        lastUpdated: Instant? = this.lastUpdated
    ): ResourceEntity {
        val resourceEntity =
            ResourceEntity(
                parentId,
                userId,
                contentHash,
                name,
                extension,
                path,
                type,
                bytesCount,
                directory,
                trashed,
                description
            )
        resourceEntity.id = id
        resourceEntity.version = version
        resourceEntity.created = created
        resourceEntity.lastUpdated = lastUpdated
        return resourceEntity
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
