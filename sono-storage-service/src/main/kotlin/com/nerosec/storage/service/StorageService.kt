package com.nerosec.storage.service

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.hash
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.persistence.EntityType
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.query.Condition
import com.nerosec.sono.commons.persistence.query.Operation
import com.nerosec.sono.commons.service.BaseService
import com.nerosec.storage.persistence.entity.ResourceEntity
import com.nerosec.storage.persistence.entity.StorageEntity
import com.nerosec.storage.persistence.entity.StorageEntity_
import com.nerosec.storage.persistence.repository.ResourceRepository
import com.nerosec.storage.persistence.repository.StorageRepository
import com.nerosec.storage.settings.StorageSettings
import jakarta.persistence.metamodel.SingularAttribute
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectory

@Service
class StorageService(
    private val storageSettings: StorageSettings,
    private val storageRepository: StorageRepository,
    private val resourceRepository: ResourceRepository
) : BaseService() {

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
        private val PROPERTY_TYPES = mapOf(
            StorageEntity_.ID to StorageEntity_.id as SingularAttribute<StorageEntity, String>,
            StorageEntity_.USER_ID to StorageEntity_.userId as SingularAttribute<StorageEntity, String>,
            StorageEntity_.PATH to StorageEntity_.path as SingularAttribute<StorageEntity, String>,
            StorageEntity_.VERSION to StorageEntity_.version as SingularAttribute<StorageEntity, Int>,
            StorageEntity_.CREATED to StorageEntity_.created as SingularAttribute<StorageEntity, Instant>,
            StorageEntity_.LAST_UPDATED to StorageEntity_.lastUpdated as SingularAttribute<StorageEntity, Instant>
        )
    }

    fun createStorage(userId: String): StorageEntity {
        if (userId.trim().isEmpty()) throw ArgumentException("Argument 'userId' cannot be empty.")
        if (!userId.isEntityId(EntityType.USER)) throw ArgumentException("Argument 'userId' is incorrect.")
        if (storageRepository.existsStorageByUserId(userId)) {
            throw StateException("Storage could not be created for user '$userId': storage exists.")
        }
        val systemStorage = Paths.get(storageSettings.path)
        val storage = systemStorage.resolve(userId.hash(HashType.SHA_512).uppercase()).createDirectory()
        storage.resolve(BIN_DIRECTORY).createDirectory()
        storage.resolve(TEMP_DIRECTORY).createDirectory()
        val storageEntity = StorageEntity(userId = userId, path = storage.toString())
        return storageRepository.save(storageEntity)
    }

    fun getStorageById(id: String): StorageEntity? {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.STORAGE)) throw ArgumentException("Argument 'id' is incorrect.")
        return storageRepository.getStorageById(id)
    }

    fun listStorages(
        page: Int = DEFAULT_PAGE,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        propertyToSortBy: String = DEFAULT_PROPERTY_TO_SORT_BY,
        sortOrder: SortOrder = DEFAULT_SORT_ORDER,
        propertiesToQueryBy: Map<String, Any> = DEFAULT_PROPERTIES_TO_QUERY_BY
    ): Page<StorageEntity> {
        if (page < MIN_PAGE) throw ArgumentException("Argument's 'page' value ($page) cannot be less than $MIN_PAGE.")
        if (pageSize !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            throw ArgumentException("Argument's 'pageSize' value ($pageSize) must be in range [$MIN_PAGE_SIZE; $MAX_PAGE_SIZE].")
        }
        if (propertyToSortBy !in SUPPORTED_PROPERTIES_TO_SORT_BY) {
            throw ArgumentException("Argument's 'propertyToSortBy' value ($propertyToSortBy) is not supported. Supported properties to sort by are: $SUPPORTED_PROPERTIES_TO_SORT_BY.")
        }
        propertiesToQueryBy.forEach { (property, _) ->
            if (property !in SUPPORTED_PROPERTIES_TO_QUERY_BY) {
                throw ArgumentException("Argument 'propertiesToQueryBy' contains property, that is not supported ($property). Supported properties to query by are: $SUPPORTED_PROPERTIES_TO_QUERY_BY.")
            }
        }
        val conditions = propertiesToQueryBy.map { (property, value) -> Condition(property, value, Operation.EQUALS) }
        val spec = createListStoragesSpec(conditions)
        var sort = Sort.by(propertyToSortBy)
        sort = if (sortOrder == SortOrder.ASC) sort.ascending() else sort.descending()
        val query = PageRequest.of(page - 1, pageSize, sort)
        return storageRepository.findAll(spec, query)
    }

    fun removeStorageById(id: String) {
        val storageEntry = getStorageById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Storage '$id' could not be removed: no such storage could be found."
            )
        Paths.get(storageEntry.path).remove()
        val resourceIds = ArrayList<String>()
        resourceRepository
            .listResourcesByParentId(id)
            .forEach { resource ->
                resource.list().forEach { resourceIds.add(it.id!!) }
                resourceIds.add(resource.id!!)
            }
        resourceRepository.removeResourcesByIds(resourceIds)
        storageRepository.removeStorageById(id)
    }

    fun getStorageByUserId(userId: String): StorageEntity? {
        if (userId.trim().isEmpty()) throw ArgumentException("Argument 'userId' cannot be empty.")
        if (!userId.isEntityId(EntityType.USER)) throw ArgumentException("Argument 'userId' is incorrect.")
        return storageRepository.getStorageByUserId(userId)
    }

    private fun ResourceEntity.list(depth: Int = Int.MAX_VALUE): List<ResourceEntity> =
        if (depth < 1 || !this.directory) {
            emptyList()
        } else {
            val resources = ArrayList<ResourceEntity>()
            resourceRepository
                .listResourcesByParentId(this.id!!)
                .forEach { resource ->
                    resources.add(resource)
                    if (resource.directory) {
                        resource.list(depth.dec()).forEach { resources.add(it) }
                    }
                }
            resources
        }

    private fun createListStoragesSpec(conditions: List<Condition>): Specification<StorageEntity> =
        Specification { root, _, c ->
            val predicates = conditions.map { (property, value, operation) ->
                val prop = PROPERTY_TYPES[property]
                    ?: throw ArgumentException("Argument 'conditions' contains condition for a property ($property), that is not supported.")
                if (operation == Operation.EQUALS) {
                    c.equal(root[prop], value)
                } else {
                    throw ArgumentException("Argument 'conditions' contains condition for an operation ($operation), that is not supported.")
                }
            }
            c.and(*predicates.toTypedArray())
        }
}
