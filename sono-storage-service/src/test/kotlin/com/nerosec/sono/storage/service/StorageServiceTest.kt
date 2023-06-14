package com.nerosec.sono.storage.service

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.hash
import com.nerosec.sono.commons.extension.Extensions.list
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.TestingCompanion.Companion.TEST_STORAGE_TEMPORARY_FILES_DIRECTORY
import com.nerosec.sono.storage.TestingCompanion.Companion.TEST_STORAGE_TRASH_DIRECTORY
import com.nerosec.sono.storage.TestingCompanion.Companion.TEST_SYSTEM_STORAGE_PATH
import com.nerosec.sono.storage.TestingCompanion.Companion.any
import com.nerosec.sono.storage.TestingCompanion.Companion.createStorage
import com.nerosec.sono.storage.persistence.entity.StorageEntity
import com.nerosec.sono.storage.persistence.repository.ResourceRepository
import com.nerosec.sono.storage.persistence.repository.StorageRepository
import com.nerosec.sono.storage.settings.StorageSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class StorageServiceTest {

    private lateinit var storageSettings: StorageSettings
    private lateinit var storageRepository: StorageRepository
    private lateinit var resourceRepository: ResourceRepository
    private lateinit var storageService: StorageService

    @BeforeEach
    fun setup() {
        init()
    }

    @AfterEach
    fun cleanup() {
        val systemStorageDirectory = Paths.get(TEST_SYSTEM_STORAGE_PATH)
        if (systemStorageDirectory.exists()) {
            systemStorageDirectory.remove()
        }
    }

    @Test
    fun `createStorage - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.createStorage("")
        }
    }

    @Test
    fun `createStorage - given incorrect 'userId' argument, produces ArgumentException`() {
        EntityType.values().map { if (it == EntityType.USER) "<incorrect_user_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                storageService.createStorage(it)
            }
        }
    }

    @Test
    fun `createStorage - given, that storage database entry exists, produces StateException`() {
        val userId = EntityId.generate(EntityType.USER)
        whenever(storageRepository.existsStorageEntityByUserId(eq(userId))).thenReturn(true)
        assertThrows<StateException> {
            storageService.createStorage(userId)
        }
    }

    @Test
    fun `createStorage - given correct 'userId' argument, creates storage directories and database entry`() {
        val userId = EntityId.generate(EntityType.USER)
        whenever(storageRepository.existsStorageEntityByUserId(userId)).thenReturn(false)
        val systemStorage = Paths.get(TEST_SYSTEM_STORAGE_PATH)
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = systemStorage.resolve(userId.hash(HashType.SHA_512).uppercase()).toString()
            )
        storageEntity.id = EntityId.generate(EntityType.STORAGE)
        storageEntity.created = Instant.now()
        whenever(storageRepository.save(any(StorageEntity::class.java))).thenReturn(storageEntity)
        storageService.createStorage(userId)
        val systemStorageContents = systemStorage.list()
        assertEquals(1, systemStorageContents.size)
        val storage = systemStorageContents[0]
        assertTrue(storage.resolve(TEST_STORAGE_TRASH_DIRECTORY).exists())
        assertTrue(storage.resolve(TEST_STORAGE_TEMPORARY_FILES_DIRECTORY).exists())
        verify(storageRepository, times(1)).save(any(StorageEntity::class.java))
    }

    @Test
    fun `getStorageById - given empty 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageById("")
        }
    }

    @Test
    fun `getStorageById - given incorrect 'id' argument, produces ArgumentException`() {
        EntityType.values().map { if (it == EntityType.STORAGE) "<incorrect_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                storageService.getStorageById(it)
            }
        }
    }

    @Test
    fun `getStorageById - given correct 'id' argument, returns StorageEntity if one exists`() {
        val id = EntityId.generate(EntityType.STORAGE)
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = Paths.get(TEST_SYSTEM_STORAGE_PATH).resolve(userId.hash(HashType.SHA_512).uppercase()).toString()
            )
        storageEntity.id = id
        storageEntity.created = Instant.now()
        whenever(storageRepository.getStorageEntityById(eq(id))).thenReturn(storageEntity)
        assertEquals(storageEntity, storageService.getStorageById(id))
    }

    @Test
    fun `getStorageById - given correct 'id' argument, returns null if no such storage exists`() {
        val id = EntityId.generate(EntityType.STORAGE)
        whenever(storageRepository.getStorageEntityById(eq(id))).thenReturn(null)
        assertEquals(null, storageService.getStorageById(id))
    }

    @Test
    fun `getStorageByUserId - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.getStorageByUserId("")
        }
    }

    @Test
    fun `getStorageByUserId - given incorrect 'userId' argument, produces ArgumentException`() {
        EntityType.values().map { if (it == EntityType.USER) "<incorrect_user_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                storageService.getStorageByUserId(it)
            }
        }
    }

    @Test
    fun `getStorageByUserId - given correct 'userId' argument, returns StorageEntity if one exists`() {
        val id = EntityId.generate(EntityType.STORAGE)
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = Paths.get(TEST_SYSTEM_STORAGE_PATH).resolve(userId.hash(HashType.SHA_512).uppercase()).toString()
            )
        storageEntity.id = id
        storageEntity.created = Instant.now()
        whenever(storageRepository.getStorageEntityByUserId(eq(userId))).thenReturn(storageEntity)
        assertEquals(storageEntity, storageService.getStorageByUserId(userId))
    }

    @Test
    fun `getStorageByUserId - given correct 'userId' argument, returns StorageEntity if no such storage exists`() {
        val userId = EntityId.generate(EntityType.USER)
        whenever(storageRepository.getStorageEntityByUserId(eq(userId))).thenReturn(null)
        assertEquals(null, storageService.getStorageByUserId(userId))
    }

    @Test
    fun `listStorages - given 'page' argument, that is less than 1, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.listStorages(page = 0)
        }
    }

    @Test
    fun `listStorages - given 'pageSize' argument, that is not in 1 to 1000 range, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.listStorages(pageSize = 0)
        }
        assertThrows<ArgumentException> {
            storageService.listStorages(pageSize = 1001)
        }
    }

    @Test
    fun `listStorages - given 'propertyToSortBy' argument, that is not supported, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.listStorages(propertyToSortBy = "<unsupported_property>")
        }
    }

    @Test
    fun `listStorages - given 'propertiesToQueryBy' argument, that contains property, that is not supported, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.listStorages(propertiesToQueryBy = mapOf("<unsupported_property>" to "any"))
        }
    }

    @Test
    fun `listStorages - lists storages`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = Paths.get(TEST_SYSTEM_STORAGE_PATH).resolve(userId.hash(HashType.SHA_512).uppercase()).toString()
            )
        storageEntity.id = EntityId.generate(EntityType.STORAGE)
        storageEntity.created = Instant.now()
        whenever(storageRepository.findAll(any(), any(Pageable::class.java))).thenReturn(PageImpl(listOf(storageEntity)))
        assertEquals(1, storageService.listStorages().size)
    }

    @Test
    fun `removeStorageById - given empty 'id' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            storageService.removeStorageById("")
        }
    }

    @Test
    fun `removeStorageById - given incorrect 'id' argument, produces ArgumentException`() {
        EntityType.values().map { if (it == EntityType.STORAGE) "<incorrect_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                storageService.removeStorageById(it)
            }
        }
    }

    @Test
    fun `removeStorageById - given correct 'id' argument, produces EntityException if no such storage exists`() {
        val id = EntityId.generate(EntityType.STORAGE)
        whenever(storageRepository.getStorageEntityById(eq(id))).thenReturn(null)
        val exception = assertThrows<EntityException> {
            storageService.removeStorageById(id)
        }
        assertEquals(EntityException.Type.ENTITY_NOT_FOUND, exception.type)
    }

    @Test
    fun `removeStorageById - given correct 'id' argument, removes storage directory, its contents and database entry`() {
        val userId = EntityId.generate(EntityType.USER)
        val storageEntity = createStorage(userId)
        val id = storageEntity.id!!
        val storage = Paths.get(storageEntity.path)
        whenever(storageRepository.getStorageEntityById(eq(id))).thenReturn(storageEntity)
        storageService.removeStorageById(id)
        assertTrue(!storage.resolve(TEST_STORAGE_TRASH_DIRECTORY).exists())
        assertTrue(!storage.resolve(TEST_STORAGE_TEMPORARY_FILES_DIRECTORY).exists())
        assertTrue(!storage.exists())
        verify(resourceRepository, times(1)).removeResourceEntitiesByUserId(eq(userId))
        verify(storageRepository, times(1)).removeStorageEntityById(eq(id))
    }

    private fun init() {
        storageSettings = StorageSettings()
        storageSettings.path = TEST_SYSTEM_STORAGE_PATH
        storageRepository = mock<StorageRepository>()
        resourceRepository = mock<ResourceRepository>()
        storageService = StorageService(storageSettings, storageRepository, resourceRepository)
        val systemStorageDirectory = Paths.get(TEST_SYSTEM_STORAGE_PATH)
        if (!systemStorageDirectory.exists()) {
            systemStorageDirectory.createDirectories()
        }
    }
}