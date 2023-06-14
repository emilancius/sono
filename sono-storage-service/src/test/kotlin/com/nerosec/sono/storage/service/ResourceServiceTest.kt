package com.nerosec.sono.storage.service

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.persistence.entity.EntityId
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.storage.TestingCompanion.Companion.TEST_SYSTEM_STORAGE_PATH
import com.nerosec.sono.storage.persistence.repository.ResourceRepository
import com.nerosec.sono.storage.persistence.repository.StorageRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ResourceServiceTest {

    private lateinit var storageRepository: StorageRepository
    private lateinit var resourceRepository: ResourceRepository
    private lateinit var resourceService: ResourceService

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
    fun `createResource - given empty 'parentId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = "",
                userId = EntityId.generate(EntityType.USER),
                name = "TEST_DIRECTORY"
            )
        }
    }

    @Test
    fun `createResource - given incorrect 'parentId' argument, produces ArgumentException`() {
        val userId = EntityId.generate(EntityType.USER)
        EntityType.values().map { if (it in listOf(EntityType.STORAGE, EntityType.RESOURCE)) "<incorrect_parent_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                resourceService.createResource(
                    parentId = it,
                    userId = userId,
                    name = "TEST_DIRECTORY"
                )
            }
        }
    }

    @Test
    fun `createResource - given empty 'userId' argument, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = "",
                userId = EntityId.generate(EntityType.USER),
                name = "TEST_DIRECTORY"
            )
        }
    }

    @Test
    fun `createResource - given incorrect 'userId' argument, produces ArgumentException`() {
        val parentId = EntityId.generate(EntityType.STORAGE)
        EntityType.values().map { if (it == EntityType.USER) "<incorrect_user_id>" else EntityId.generate(it) }.forEach {
            assertThrows<ArgumentException> {
                resourceService.createResource(
                    parentId = parentId,
                    userId = it,
                    name = "TEST_DIRECTORY"
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
    fun `createResource - given, that resource to be created is not a directory and 'inputStream' is not provided, produces ArgumentException`() {
        assertThrows<ArgumentException> {
            resourceService.createResource(
                parentId = EntityId.generate(EntityType.STORAGE),
                userId = EntityId.generate(EntityType.USER),
                name = "TEST_FILE.txt",
                directory = false
            )
        }
    }

    private fun init() {
        storageRepository = mock<StorageRepository>()
        resourceRepository = mock<ResourceRepository>()
        resourceService = ResourceService(storageRepository, resourceRepository)
        val systemStorageDirectory = Paths.get(TEST_SYSTEM_STORAGE_PATH)
        if (!systemStorageDirectory.exists()) {
            systemStorageDirectory.createDirectories()
        }
    }
}