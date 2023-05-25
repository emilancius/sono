package com.nerosec.storage.service

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.hash
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireStringArgumentContainsAnyText
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireStringArgumentIsEntityId
import com.nerosec.sono.commons.service.BaseService
import com.nerosec.storage.persistence.entity.StorageEntity
import com.nerosec.storage.persistence.entity.StorageEntity_
import com.nerosec.storage.persistence.repository.ResourceRepository
import com.nerosec.storage.persistence.repository.StorageRepository
import com.nerosec.storage.settings.StorageSettings
import jakarta.persistence.metamodel.SingularAttribute
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectory

@Service
class StorageService(
    private val storageSettings: StorageSettings,
    private val storageRepository: StorageRepository,
    private val resourceRepository: ResourceRepository
) : BaseService<StorageEntity>() {

    companion object {
        const val BIN_DIRECTORY = "bin"
        const val TEMP_DIRECTORY = "temp"
        const val DEFAULT_PROPERTY_TO_SORT_BY = StorageEntity_.CREATED
        val SUPPORTED_PROPERTIES_TO_SORT_BY = listOf(
            StorageEntity_.ID,
            StorageEntity_.USER_ID,
            StorageEntity_.CREATED
        )
        val SUPPORTED_PROPERTIES_TO_QUERY_BY = listOf(
            StorageEntity_.ID,
            StorageEntity_.USER_ID
        )

        @Suppress("UNCHECKED_CAST")
        val PROPERTY_TYPES =
            mapOf(
                StorageEntity_.ID to StorageEntity_.id as SingularAttribute<StorageEntity, String>,
                StorageEntity_.USER_ID to StorageEntity_.userId as SingularAttribute<StorageEntity, String>,
                StorageEntity_.PATH to StorageEntity_.path as SingularAttribute<StorageEntity, String>,
                StorageEntity_.VERSION to StorageEntity_.version as SingularAttribute<StorageEntity, Int>,
                StorageEntity_.CREATED to StorageEntity_.created as SingularAttribute<StorageEntity, Instant>,
                StorageEntity_.LAST_UPDATED to StorageEntity_.lastUpdated as SingularAttribute<StorageEntity, Instant>
            )
    }

    fun createStorage(userId: String): StorageEntity {
        requireStringArgumentContainsAnyText(userId, "userId")
        requireStringArgumentIsEntityId(userId, "userId", EntityType.USER)
        if (storageRepository.existsStorageEntityByUserId(userId)) {
            throw StateException("Storage could not be created for user '$userId': storage exists.")
        }
        val systemStorage = Paths.get(storageSettings.path)
        val storage = systemStorage.resolve(userId.hash(HashType.SHA_512).uppercase()).createDirectory()
        storage.resolve(BIN_DIRECTORY).createDirectory()
        storage.resolve(TEMP_DIRECTORY).createDirectory()
        val storageEntity =
            StorageEntity(
                userId = userId,
                path = storage.toString()
            )
        return storageRepository.save(storageEntity)
    }

    fun getStorageById(id: String): StorageEntity? {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, "id", EntityType.STORAGE)
        return storageRepository.getStorageEntityById(id)
    }

    fun getStorageByUserId(userId: String): StorageEntity? {
        requireStringArgumentContainsAnyText(userId, "userId")
        requireStringArgumentIsEntityId(userId, "userId", EntityType.USER)
        return storageRepository.getStorageEntityByUserId(userId)
    }

    fun listStorages(
        page: Int = DEFAULT_PAGE,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        propertyToSortBy: String = DEFAULT_PROPERTY_TO_SORT_BY,
        sortOrder: SortOrder = DEFAULT_SORT_ORDER,
        propertiesToQueryBy: Map<String, Any> = DEFAULT_PROPERTIES_TO_QUERY_BY
    ): Page<StorageEntity> =
        list(
            storageRepository,
            page,
            pageSize,
            propertyToSortBy,
            sortOrder,
            propertiesToQueryBy
        )

    fun removeStorageById(id: String) {
        val storageEntry = getStorageById(id)
            ?: throw EntityException(message = "Storage '$id' could not be removed: no such storage could be found.")
        Paths.get(storageEntry.path).remove()
        resourceRepository.removeResourceEntitiesByUserId(storageEntry.userId)
        storageRepository.removeStorageEntityById(id)
    }

    override fun getEntityPropertyTypes(): Map<String, SingularAttribute<StorageEntity, out Any>> = PROPERTY_TYPES

    override fun getPropertiesToSortBy(): List<String> = SUPPORTED_PROPERTIES_TO_SORT_BY

    override fun getPropertiesToQueryBy(): List<String> = SUPPORTED_PROPERTIES_TO_QUERY_BY
}
