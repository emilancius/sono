package com.nerosec.storage.persistence.entity

import com.nerosec.sono.commons.persistence.entity.BaseEntity
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.storage.contract.response.Storage
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = StorageEntity.TABLE)
data class StorageEntity(
    @Column(name = COL_USER_ID)
    val userId: String,
    @Column(name = COL_PATH)
    val path: String,
) : BaseEntity() {

    companion object {
        const val TABLE = "STORAGE"
        const val COL_USER_ID = "USER_ID"
        const val COL_PATH = "PATH"
    }

    override fun beforeSave() {
        super.beforeSave()
        if (id == null) {
            id = EntityId.generate(EntityType.STORAGE)
        }
    }

    fun copy(
        id: String? = this.id,
        userId: String = this.userId,
        path: String = this.path,
        version: Int = this.version,
        created: Instant? = this.created,
        lastUpdated: Instant? = this.lastUpdated
    ): StorageEntity {
        val storageEntity =
            StorageEntity(
                userId,
                path
            )
        storageEntity.id = id
        storageEntity.version = version
        storageEntity.created = created
        storageEntity.lastUpdated = lastUpdated
        return storageEntity
    }

    fun toStorage(): Storage =
        Storage(
            id = id!!,
            userId = userId,
            created = created!!
        )
}
