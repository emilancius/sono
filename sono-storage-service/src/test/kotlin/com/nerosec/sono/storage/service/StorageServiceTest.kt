package com.nerosec.sono.storage.service

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.createFromInputStream
import com.nerosec.sono.commons.extension.Extensions.list
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.TestingCompanion.INCORRECT_ENTITY_ID
import com.nerosec.sono.storage.TestingCompanion.STORAGE_PATH
import com.nerosec.sono.storage.TestingCompanion.USER_STORAGE_TEMPORARY_FILES_DIRECTORY
import com.nerosec.sono.storage.TestingCompanion.USER_STORAGE_TRASH_DIRECTORY
import com.nerosec.sono.storage.TestingCompanion.createSystemStorage
import com.nerosec.sono.storage.TestingCompanion.createUserStorage
import com.nerosec.sono.storage.persistence.repository.ResourceRepository
import com.nerosec.sono.storage.persistence.repository.StorageRepository
import com.nerosec.sono.storage.settings.StorageSettings
import com.nerosec.sono.storage.TestingCompanion.removeSystemStorage
import com.nerosec.sono.storage.persistence.entity.StorageEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.kotlin.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class StorageServiceTest {

    lateinit var systemStorage: Path
    lateinit var storageSettings: StorageSettings
    lateinit var storageRepository: StorageRepository
    lateinit var resourceRepository: ResourceRepository
    lateinit var storageService: StorageService

    @BeforeEach
    fun setup() {
        systemStorage = createSystemStorage()
        storageSettings = StorageSettings()
        storageSettings.path = STORAGE_PATH
        storageRepository = mock { StorageRepository::class.java }
        resourceRepository = mock { ResourceRepository::class.java }
        storageService = StorageService(storageSettings, storageRepository, resourceRepository)
    }

    @AfterEach
    fun cleanup() {
        removeSystemStorage()
    }

    @Test
    fun `createStorage - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.createStorage("")
        }
    }

    @Test
    fun `createStorage - given incorrect 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.createStorage(INCORRECT_ENTITY_ID)
        }
    }

    @Test
    fun `createStorage - given 'userId' argument, that storage exists for, produces StateException`() {
        val userId = EntityId.generate(EntityType.USER)
        `when`(storageRepository.existsStorageEntityByUserId(eq(userId))).thenReturn(true)
        assertThrows<StateException> {
            storageService.createStorage(userId)
        }
    }

    @Test
    fun `createStorage - given 'userId' argument, that storage does not exists for, creates storage directory and storage database entry for specified user`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = ""
            )
        storageEntity.id = EntityId.generate(EntityType.STORAGE)
        `when`(storageRepository.existsStorageEntityByUserId(eq(userId))).thenReturn(false)
        `when`(storageRepository.save(any())).thenReturn(storageEntity)
        storageService.createStorage(userId)
        verify(storageRepository, times(1)).save(any())
        val systemStorageContents = systemStorage.list()
        assertEquals(1, systemStorageContents.size)
        val storage = systemStorageContents[0]
        val storageContents = storage.list()
        assertEquals(2, storageContents.size)
        assertTrue(storage.resolve(USER_STORAGE_TRASH_DIRECTORY).exists())
        assertTrue(storage.resolve(USER_STORAGE_TEMPORARY_FILES_DIRECTORY).exists())
    }

    @Test
    fun `getStorageById - given empty 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageById("")
        }
    }

    @Test
    fun `getStorageById - given incorrect 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageById(INCORRECT_ENTITY_ID)
        }
    }

    @Test
    fun `getStorageById - given 'id' argument, returns StorageEntity`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity = createUserStorage(userId)
        val storageId = storageEntity.id!!
        `when`(storageRepository.getStorageEntityById(eq(storageId))).thenReturn(storageEntity)
        assertEquals(storageEntity, storageService.getStorageById(storageId))
    }

    @Test
    fun `getStorageByUserId - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageByUserId("")
        }
    }

    @Test
    fun `getStorageByUserId - given incorrect 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageByUserId(INCORRECT_ENTITY_ID)
        }
    }

    @Test
    fun `getStorageByUserId - given 'userId' argument, returns StorageEntity`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity = createUserStorage(userId)
        `when`(storageRepository.getStorageEntityByUserId(eq(userId))).thenReturn(storageEntity)
        assertEquals(storageEntity, storageService.getStorageByUserId(userId))
    }

    @Test
    fun `removeStorageById - given empty 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.removeStorageById("")
        }
    }

    @Test
    fun `removeStorageById - given incorrect 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.removeStorageById(INCORRECT_ENTITY_ID)
        }
    }

    @Test
    fun `removeStorageById - given 'id' argument for storage, that does not exist, produces EntityException of type ENTITY_NOT_FOUND`() {
        val storageId = EntityId.generate(EntityType.STORAGE)
        `when`(storageRepository.getStorageEntityById(eq(storageId))).thenReturn(null)
        val exception = assertThrows<EntityException> {
            storageService.removeStorageById(storageId)
        }
        assertEquals(EntityException.Type.ENTITY_NOT_FOUND, exception.type)
    }

    @Test
    fun `removeStorageById - given 'id' argument for storage, that exists, removes storage directory, storage database entry and it's contents`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity = createUserStorage(userId)
        val storage = Paths.get(storageEntity.path)
        val storageId = storageEntity.id!!
        `when`(storageRepository.getStorageEntityById(eq(storageId))).thenReturn(storageEntity)
        val resource = storage.resolve("TEXT_FILE.txt")
        resource.createFromInputStream("TEXT_FILE.txt contents".byteInputStream())
        `when`(resourceRepository.removeResourceEntitiesByUserId(eq(userId))).then { }
        storageService.removeStorageById(storageId)
        verify(resourceRepository, times(1)).removeResourceEntitiesByUserId(eq(userId))
        verify(storageRepository, times(1)).removeStorageEntityById(eq(storageId))
        assertTrue(!storage.exists())
        assertTrue(!resource.exists())
    }
}
