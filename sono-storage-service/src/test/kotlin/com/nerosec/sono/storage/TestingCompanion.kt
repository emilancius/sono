package com.nerosec.sono.storage

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.extension.Extensions.hash
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.persistence.entity.StorageEntity
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory

object TestingCompanion {

    const val STORAGE_PATH = "TEST_STORAGE"
    const val USER_STORAGE_TRASH_DIRECTORY = "bin"
    const val USER_STORAGE_TEMPORARY_FILES_DIRECTORY = "temp"
    const val INCORRECT_ENTITY_ID = "<incorrect_entity_id>"

    val storage: Path = Paths.get(STORAGE_PATH)

    fun createSystemStorage(): Path = storage.createDirectories()

    fun removeSystemStorage() = storage.remove()

    fun createUserStorage(userId: String): StorageEntity {
        val storage = storage.resolve(userId.hash(HashType.SHA_512).uppercase())
        storage.createDirectory()
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = storage.toString(),
            )
        storageEntity.id = EntityId.generate(EntityType.STORAGE)
        storageEntity.created = Instant.now()
        return storageEntity
    }
}
