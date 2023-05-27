package com.nerosec.sono.storage.service

import com.nerosec.sono.storage.TestingCompanion.createSystemStorage
import com.nerosec.sono.storage.persistence.repository.ResourceRepository
import com.nerosec.sono.storage.persistence.repository.StorageRepository
import com.nerosec.sono.storage.TestingCompanion.removeSystemStorage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock
import java.nio.file.Path

class ResourceServiceTest {

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

    @AfterEach
    fun cleanup() {
        removeSystemStorage()
    }
}
