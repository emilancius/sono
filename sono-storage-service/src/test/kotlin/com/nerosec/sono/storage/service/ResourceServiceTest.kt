package com.nerosec.sono.storage.service

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.TestingCompanion.createSystemStorage
import com.nerosec.sono.storage.TestingCompanion.removeSystemStorage
import com.nerosec.sono.storage.persistence.entity.ResourceEntity
import com.nerosec.sono.storage.persistence.repository.ResourceRepository
import com.nerosec.sono.storage.persistence.repository.StorageRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.file.Path
import java.time.Instant

class ResourceServiceTest {

    companion object {
        private const val TEST_DIRECTORY = "TEST_DIRECTORY"
        private const val TEST_FILE = "TEST_FILE.txt"
    }

    lateinit var systemStorage: Path
    lateinit var storageRepository: StorageRepository
    lateinit var resourceRepository: ResourceRepository
    lateinit var resourceService: ResourceService

    @BeforeEach
    fun setup() {
        systemStorage = createSystemStorage()
        storageRepository = mock { StorageRepository::class.java }
        resourceRepository = mock { ResourceRepository::class.java }
        resourceService = ResourceService(storageRepository, resourceRepository)
    }

    @Test
    fun `createResource - given empty 'parentId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = "",
                userId = EntityId.generate(EntityType.USER),
                name = TEST_DIRECTORY
            )
        }
    }

    @Test
    fun `createResource - given incorrect 'parentId' argument, produces ArgumentException`() {
        val userId = EntityId.generate(EntityType.USER)
        EntityType.values()
            .map { if (it == EntityType.STORAGE || it == EntityType.RESOURCE) "<incorrect_storage_or_resource_id>" else EntityId.generate(it) }
            .forEach { parentId ->
                assertThrows<ArgumentException> {
                    resourceService.createResource(
                        parentId = parentId,
                        userId = userId,
                        name = TEST_DIRECTORY
                    )
                }
            }
    }

    @Test
    fun `createResource - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = "",
                name = TEST_DIRECTORY
            )
        }
    }

    @Test
    fun `createResource - given incorrect 'userId' argument, produces ArgumentException`() {
        val parentId = EntityId.generate(EntityType.STORAGE)
        EntityType.values()
            .map { if (it == EntityType.USER) "<incorrect_user_id>" else EntityId.generate(it) }
            .forEach { userId ->
                assertThrows<ArgumentException> {
                    resourceService.createResource(
                        parentId = parentId,
                        userId = userId,
                        name = TEST_DIRECTORY
                    )
                }
            }
    }

    @Test
    fun `createResource - given empty 'name' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = EntityId.generate(EntityType.USER),
                name = ""
            )
        }
    }

    @Test
    fun `createResource - given 'inputStream' argument is not provided for non-directory creation, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = EntityId.generate(EntityType.USER),
                name = TEST_FILE,
                directory = false,
                inputStream = null
            )
        }
    }

    @Test
    fun `createResource - given correct 'parentId' argument of type 'STORAGE', but no such storage could be found, produces EntityException`() {
        val parentId = EntityId.generate(EntityType.STORAGE)
        `when`(storageRepository.getStorageEntityById(eq(parentId))).thenReturn(null)
        assertThrows<EntityException> {
            resourceService.createResource(
                parentId = parentId,
                userId = EntityId.generate(EntityType.USER),
                name = TEST_DIRECTORY
            )
        }
    }

    @Test
    fun `createResource - given correct 'parentId' argument of type 'RESOURCE', but no such resource could be found, produces EntityException `() {
        val parentId = EntityId.generate(EntityType.RESOURCE)
        `when`(resourceRepository.getResourceEntityById(eq(parentId))).thenReturn(null)
        assertThrows<EntityException> {
            resourceService.createResource(
                parentId = parentId,
                userId = EntityId.generate(EntityType.USER),
                name = TEST_DIRECTORY
            )
        }
    }

    @Test
    fun `createResource - given correct 'parentId' argument of type 'RESOURCE' for resource, that is not a directory, produces StateException`() {
        val parentId = EntityId.generate(EntityType.RESOURCE)
        val userId = EntityId.generate(EntityType.USER)
        val parentResourceEntity =
            ResourceEntity(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = userId,
                contentHash = null,
                name = TEST_FILE,
                extension = "txt",
                path = "",
                type = null,
                bytesCount = 0L,
                directory = false
            )
        parentResourceEntity.id = parentId
        parentResourceEntity.created = Instant.now()
        `when`(resourceRepository.getResourceEntityById(eq(parentId))).thenReturn(parentResourceEntity)
        assertThrows<StateException> {
            resourceService.createResource(
                parentId = parentId,
                userId = userId,
                name = TEST_DIRECTORY
            )
        }
    }

    @Test
    fun `createResource - given correct 'parentId' argument of type 'RESOURCE' for resource, that is trashed, produces StateException`() {
        val parentId = EntityId.generate(EntityType.RESOURCE)
        val userId = EntityId.generate(EntityType.USER)
        val parentResourceEntity =
            ResourceEntity(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = userId,
                contentHash = null,
                name = TEST_DIRECTORY,
                extension = null,
                path = "",
                type = null,
                bytesCount = 0L,
                directory = true,
                trashed = true
            )
        parentResourceEntity.id = parentId
        parentResourceEntity.created = Instant.now()
        `when`(resourceRepository.getResourceEntityById(eq(parentId))).thenReturn(parentResourceEntity)
        assertThrows<StateException> {
            resourceService.createResource(
                parentId = parentId,
                userId = userId,
                name = TEST_DIRECTORY
            )
        }
    }

    @AfterEach
    fun cleanup() {
        removeSystemStorage()
    }
}
