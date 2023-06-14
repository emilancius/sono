package com.nerosec.sono.storage

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.extension.Extensions.hash
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.persistence.entity.StorageEntity
import org.mockito.Mockito
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectory

class TestingCompanion {

    companion object {
        const val TEST_SYSTEM_STORAGE_PATH = "TEST_STORAGE"
        const val TEST_STORAGE_TRASH_DIRECTORY = "bin"
        const val TEST_STORAGE_TEMPORARY_FILES_DIRECTORY = "temp"

        fun <T> any(type: Class<T>): T = Mockito.any(type)

        fun createStorage(userId: String): StorageEntity {
            val storage = Paths.get(TEST_SYSTEM_STORAGE_PATH).resolve(userId.hash(HashType.SHA_512).uppercase())
            val storageEntity =
                StorageEntity(
                    userId = userId,
                    path = storage.toString()
                )
            storageEntity.id = EntityId.generate(EntityType.STORAGE)
            storageEntity.created = Instant.now()
            storage.createDirectory()
            storage.resolve(TEST_STORAGE_TRASH_DIRECTORY).createDirectory()
            storage.resolve(TEST_STORAGE_TEMPORARY_FILES_DIRECTORY).createDirectory()
            return storageEntity
        }
    }
}