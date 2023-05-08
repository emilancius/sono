package com.nerosec.storage.service

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.bytesCount
import com.nerosec.sono.commons.extension.Extensions.calculateContentHash
import com.nerosec.sono.commons.extension.Extensions.compressAsZip
import com.nerosec.sono.commons.extension.Extensions.copyAs
import com.nerosec.sono.commons.extension.Extensions.createFromInputStream
import com.nerosec.sono.commons.extension.Extensions.extractZip
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.extension.Extensions.isZip
import com.nerosec.sono.commons.extension.Extensions.list
import com.nerosec.sono.commons.extension.Extensions.moveTo
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.extension.Extensions.renameTo
import com.nerosec.sono.commons.persistence.EntityType
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.query.Condition
import com.nerosec.sono.commons.persistence.query.Operation
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_PAGE
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_PAGE_SIZE
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_PROPERTIES_TO_QUERY_BY
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_SORT_ORDER
import com.nerosec.sono.commons.service.BaseService.Companion.MAX_PAGE_SIZE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE_SIZE
import com.nerosec.storage.persistence.entity.ResourceEntity
import com.nerosec.storage.persistence.entity.ResourceEntity_
import com.nerosec.storage.persistence.repository.ResourceRepository
import com.nerosec.storage.persistence.repository.StorageRepository
import jakarta.persistence.metamodel.SingularAttribute
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.*

@Service
class ResourceService(
    val resourceRepository: ResourceRepository,
    val storageRepository: StorageRepository
) {

    companion object {
        const val COPY_POSTFIX = "copy"
        const val DEFAULT_PROPERTY_TO_SORT_BY = ResourceEntity_.CREATED
        val SUPPORTED_PROPERTIES_TO_SORT_BY = listOf(
            ResourceEntity_.ID,
            ResourceEntity_.PARENT_ID,
            ResourceEntity_.USER_ID,
            ResourceEntity_.NAME,
            ResourceEntity_.EXTENSION,
            ResourceEntity_.TYPE,
            ResourceEntity_.BYTES_COUNT,
            ResourceEntity_.DIRECTORY,
            ResourceEntity_.TRASHED,
            ResourceEntity_.CREATED,
            ResourceEntity_.LAST_UPDATED
        )
        val SUPPORTED_PROPERTIES_TO_QUERY_BY = listOf(
            ResourceEntity_.ID,
            ResourceEntity_.PARENT_ID,
            ResourceEntity_.USER_ID,
            ResourceEntity_.NAME,
            ResourceEntity_.EXTENSION,
            ResourceEntity_.TYPE,
            ResourceEntity_.DIRECTORY,
            ResourceEntity_.TRASHED
        )
        private val PROPERTY_TYPES = mapOf(
            ResourceEntity_.ID to ResourceEntity_.id as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.PARENT_ID to ResourceEntity_.parentId as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.USER_ID to ResourceEntity_.userId as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.NAME to ResourceEntity_.name as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.EXTENSION to ResourceEntity_.extension as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.PATH to ResourceEntity_.path as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.TYPE to ResourceEntity_.type as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.BYTES_COUNT to ResourceEntity_.bytesCount as SingularAttribute<ResourceEntity, Long>,
            ResourceEntity_.DESCRIPTION to ResourceEntity_.description as SingularAttribute<ResourceEntity, String>,
            ResourceEntity_.DIRECTORY to ResourceEntity_.directory as SingularAttribute<ResourceEntity, Boolean>,
            ResourceEntity_.TRASHED to ResourceEntity_.trashed as SingularAttribute<ResourceEntity, Boolean>,
            ResourceEntity_.VERSION to ResourceEntity_.version as SingularAttribute<ResourceEntity, Int>,
            ResourceEntity_.CREATED to ResourceEntity_.created as SingularAttribute<ResourceEntity, Instant>,
            ResourceEntity_.LAST_UPDATED to ResourceEntity_.lastUpdated as SingularAttribute<ResourceEntity, Instant>
        )
    }

    fun createResource(
        parentId: String,
        userId: String,
        name: String,
        directory: Boolean = true,
        description: String? = null,
        inputStream: InputStream? = null
    ): ResourceEntity {
        if (parentId.trim().isEmpty()) throw ArgumentException("Argument 'parentId' cannot be empty.")
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ArgumentException("Argument 'parentId' is incorrect.")
        }
        if (userId.trim().isEmpty()) throw ArgumentException("Argument 'userId' cannot be empty.")
        if (!userId.isEntityId(EntityType.USER)) throw ArgumentException("Argument 'userId' is incorrect.")
        if (name.trim().isEmpty()) throw ArgumentException("Argument 'name' cannot be empty.")
        if (!directory && inputStream == null) throw ArgumentException("Argument 'inputStream' is required.")
        val parent =
            when (val parentEntityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageById(parentId)?.let { Paths.get(it.path) }
                EntityType.RESOURCE -> resourceRepository
                    .getResourceById(parentId)
                    ?.let {
                        if (it.trashed) {
                            throw StateException("Resource '$name' could not be created in parent directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) {
                            Paths.get(it.path)
                        } else {
                            null
                        }
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${parentEntityType.name}'.")
            }
        parent ?: throw EntityException(
            EntityException.Type.ENTITY_NOT_FOUND,
            "Parent directory '$parentId' could not be found."
        )
        val resourceExists = resourceRepository
            .listResourcesByParentId(parentId)
            .any { it.directory == directory && it.name == name && !it.trashed }
        if (resourceExists) {
            throw StateException("Resource '$name' could not be created in directory '$parentId': such resource exists.")
        }
        val path = parent.resolve(name)
        val resource =
            if (directory) {
                path.createDirectory()
            } else {

                val it = path.createFromInputStream(inputStream!!)
                inputStream.close()
                it
            }
        val contentHash =
            if (resource.isDirectory()) {
                null
            } else {
                resource.calculateContentHash(HashType.SHA_512).uppercase()
            }
        val resourceEntity =
            ResourceEntity(
                parentId = parentId,
                userId = userId,
                contentHash = contentHash,
                name = resource.name,
                extension = if (resource.extension == "") null else resource.extension,
                path = resource.toString(),
                type = null, // TODO
                bytesCount = resource.bytesCount(),
                directory = directory,
                description = description
            )
        return resourceRepository.save(resourceEntity)
    }

    fun getResourceById(id: String): ResourceEntity? {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        return resourceRepository.getResourceById(id)
    }

    fun listResources(
        page: Int = DEFAULT_PAGE,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        propertyToSortBy: String = DEFAULT_PROPERTY_TO_SORT_BY,
        sortOrder: SortOrder = DEFAULT_SORT_ORDER,
        propertiesToQueryBy: Map<String, Any> = DEFAULT_PROPERTIES_TO_QUERY_BY
    ): Page<ResourceEntity> {
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
        val spec = createListResourcesSpec(conditions)
        var sort = Sort.by(propertyToSortBy)
        sort = if (sortOrder == SortOrder.ASC) sort.ascending() else sort.descending()
        val query = PageRequest.of(page - 1, pageSize, sort)
        return resourceRepository.findAll(spec, query)
    }

    fun removeResourceById(id: String) {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        val resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be removed: resource could not be found."
            )
        Paths.get(resourceEntity.path).remove()
        val resourceIds = ArrayList(resourceEntity.list().map { it.id!! })
        resourceIds.add(id)
        resourceRepository.removeResourcesByIds(resourceIds)
    }

    fun trashResource(id: String): ResourceEntity {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        var resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be moved to trash: resource could not be found."
            )
        if (resourceEntity.trashed) {
            throw StateException("Resource '$id' could not be moved to trash: resource is in trash.")
        }
        val storageEntity = storageRepository.getStorageByUserId(resourceEntity.userId)!!
        val bin = Paths.get(storageEntity.path).resolve(StorageService.BIN_DIRECTORY)
        var resource = Paths.get(resourceEntity.path)
        resource =
            if (bin.resolve(resourceEntity.name).exists()) {
                var name = "${resourceEntity.name.substringBeforeLast('.')} ${System.nanoTime()}"
                if (!resourceEntity.directory && resourceEntity.extension != null) {
                    name += ".${resourceEntity.extension}"
                }
                resource.renameTo(name).moveTo(bin)
            } else {
                resource.moveTo(bin)
            }
        val path = Paths.get(resourceEntity.path)
        resourceEntity = resourceRepository.save(
            resourceEntity.copy(
                name = resource.name,
                path = resource.toString(),
                trashed = true
            )
        )
        if (resourceEntity.directory) {
            resourceEntity.list().forEach {
                val p = Paths.get(resourceEntity.path).resolve(path.relativize(Paths.get(it.path)))
                resourceRepository.save(it.copy(path = p.toString(), trashed = true))
            }
        }
        return resourceEntity
    }

    fun restoreResource(id: String): ResourceEntity {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        var resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be restored: resource could not be found."
            )
        if (!resourceEntity.trashed) {
            throw StateException("Resource '$id' could not be restored: resource is not in trash.")
        }
        val resource = Paths.get(resourceEntity.path)
        val parentId = resourceEntity.parentId
        val parent =
            when (EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> {
                    val directory = storageRepository
                        .getStorageById(parentId)
                        ?: throw StateException("Resource '$id' could not be restored: parent directory '$parentId' could not be found.")
                    Paths.get(directory.path)
                }

                else -> {
                    // EntityType.RESOURCE
                    val directory = resourceRepository
                        .getResourceById(parentId)
                        ?: throw StateException("Resource '$id' could not be restored: parent directory '$parentId' could not be found.")
                    if (directory.trashed) throw StateException("Resource '$id' could not be restored: parent directory '$parentId' is in trash.")
                    Paths.get(directory.path)
                }
            }
        if (parent.resolve(resourceEntity.name).exists()) {
            throw StateException("Resource '$id' could not be restored: such resource exists in parent directory '$parentId'.")
        } else {
            val restored = resource.moveTo(parent)
            resourceEntity = resourceRepository.save(resourceEntity.copy(path = restored.toString(), trashed = false))
            if (resourceEntity.directory) {
                resourceEntity.list().forEach {
                    val p = Paths.get(resourceEntity.path).resolve(resource.relativize(Paths.get(it.path)))
                    resourceRepository.save(it.copy(path = p.toString(), trashed = false))
                }
            }
        }
        return resourceEntity
    }

    fun copyResource(id: String): ResourceEntity {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        val resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be copied: resource could not be found."
            )
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be copied: resource is in trash.")
        val resource = Paths.get(resourceEntity.path)
        var copyIndex = 1
        var name: String = resourceEntity.name.substringBeforeLast('.')
        for (index in generateSequence(0) { it }) {
            name = "$name $COPY_POSTFIX${if (copyIndex == 1) "" else " $copyIndex"}"
            if (!resourceEntity.directory && resourceEntity.extension != null) {
                name += ".${resourceEntity.extension}"
            }
            if (resource.resolveSibling(name).exists()) {
                copyIndex++
                name = resourceEntity.name.substringBeforeLast('.')
                continue
            } else {
                break
            }
        }
        val resourceCopy = resource.copyAs(resource.resolveSibling(name))
        val directory = resourceEntity.directory
        val contentHash = if (directory) null else resourceCopy.calculateContentHash(HashType.SHA_512).uppercase()
        val resourceCopyEntity = resourceRepository.save(
            resourceEntity.copy(
                id = null,
                contentHash = contentHash,
                name = resourceCopy.name,
                path = resourceCopy.toString(),
                description = null,
                version = 1,
                lastUpdated = null
            )
        )
        if (directory) {
            var parentId = resourceCopyEntity.id!!
            resourceEntity.list().forEach {
                val resource = Paths.get(resourceCopyEntity.path).resolve(resource.relativize(Paths.get(it.path)))
                val contentHash =
                    if (it.directory) {
                        null
                    } else {
                        resource.calculateContentHash(HashType.SHA_512).uppercase()
                    }
                val entity = resourceRepository.save(
                    it.copy(
                        id = null,
                        parentId = parentId,
                        contentHash = contentHash,
                        path = resource.toString(),
                        description = null,
                        version = 1,
                        lastUpdated = null
                    )
                )
                if (entity.directory) {
                    parentId = entity.id!!
                }
            }
        }
        return resourceCopyEntity
    }

    fun moveResource(id: String, parentId: String): ResourceEntity {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        if (parentId.trim().isEmpty()) throw ArgumentException("Argument 'parentId' cannot be empty.")
        if (!id.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ArgumentException("Argument 'parentId' is incorrect.")
        }
        val resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be moved: resource could not be found."
            )
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be moved: resource is in trash.")
        val parent =
            when (val parentEntityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageById(parentId)?.let { Paths.get(it.path) }
                EntityType.RESOURCE -> resourceRepository
                    .getResourceById(parentId)
                    ?.let {
                        if (it.trashed) {
                            throw StateException("Resource '$id' could not be moved to parent directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) {
                            Paths.get(it.path)
                        } else {
                            null
                        }
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${parentEntityType.name}'.")
            }
        parent ?: throw EntityException(
            EntityException.Type.ENTITY_NOT_FOUND,
            "Parent directory '$parentId' could not be found."
        )
        if (parent.resolve(resourceEntity.name).exists()) {
            throw StateException("Resource '$id' could not be moved to parent directory '$parentId': parent directory '$parentId' contains resource '${resourceEntity.name}'.")
        }
        val resource = Paths.get(resourceEntity.path)
        val moved = resource.moveTo(parent)
        val entity = resourceRepository.save(
            resourceEntity.copy(
                parentId = parentId,
                path = moved.toString()
            )
        )
        if (resourceEntity.directory) {
            var parentId = entity.id!!
            resourceEntity.list().forEach {
                val e = resourceRepository.save(
                    it.copy(
                        parentId = parentId,
                        path = Paths.get(entity.path).resolve(resource.relativize(Paths.get(it.path))).toString()
                    )
                )
                if (e.directory) {
                    parentId = e.id!!
                }
            }
        }
        return entity
    }

    fun renameResource(id: String, name: String): ResourceEntity {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        if (name.trim().isEmpty()) throw ArgumentException("Argument 'name' cannot be empty.")
        if (name.length !in 1..255) throw ArgumentException("Argument 'name' must be 1 to 255 characters length.")
        val resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be renamed: resource could not be found."
            )
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be renamed: resource is in trash.")
        val resource = Paths.get(resourceEntity.path)
        if (resource.resolveSibling(name).exists()) {
            throw StateException("Resource '$id' could not be renamed to '$name': such resource exists.")
        }
        val renamed = resource.renameTo(name)
        val entity = resourceRepository.save(
            resourceEntity.copy(
                name = renamed.name,
                extension = if (resource.extension == "") null else resource.extension,
                path = renamed.toString()
            )
        )
        if (resourceEntity.directory) {
            resourceEntity.list().forEach {
                resourceRepository.save(
                    it.copy(path = Paths.get(entity.path).resolve(resource.relativize(Paths.get(it.path))).toString())
                )
            }
        }
        return entity
    }

    fun compressResources(resourceIds: List<String>, parentId: String, name: String): ResourceEntity {
        if (resourceIds.isEmpty()) throw ArgumentException("Argument 'resourceIds' cannot be empty.")
        resourceIds.forEach {
            if (it.trim().isEmpty()) {
                throw ArgumentException("Argument 'resourceIds' cannot contain empty value.")
            }
            if (!it.isEntityId(EntityType.RESOURCE)) {
                throw ArgumentException("Argument 'resourceIds' contains value ($it), that is incorrect.")
            }
        }
        if (parentId.trim().isEmpty()) throw ArgumentException("Argument 'parentId' cannot be empty.")
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ArgumentException("Argument 'parentId' is incorrect.")
        }
        if (name.trim().isEmpty()) throw ArgumentException("Argument 'name' cannot be empty.")
        if (name.length !in 1..255) throw ArgumentException("Argument 'name' must be 1 to 255 characters length.")
        val resourceEntities = resourceRepository.listResourcesByIds(resourceIds)
        if (resourceEntities.size != resourceIds.size) {
            val missingResourcesCount = resourceIds.size - resourceEntities.size
            throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "$missingResourcesCount resource${if (missingResourcesCount == 1) "" else "s"} could not be found."
            )
        }
        resourceEntities.forEach {
            if (it.trashed) {
                throw StateException("Resource '${it.id}' cannot be compressed: resource is in trash.")
            }
        }
        lateinit var userId: String
        val parent =
            when (val parentEntityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> {
                    storageRepository.getStorageById(parentId)?.let {
                        userId = it.userId
                        Paths.get(it.path)
                    }
                }

                EntityType.RESOURCE -> resourceRepository
                    .getResourceById(parentId)
                    ?.let {
                        userId = it.userId
                        if (it.trashed) {
                            throw StateException("Resource '$name' could not be created in parent directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) {
                            Paths.get(it.path)
                        } else {
                            null
                        }
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${parentEntityType.name}'.")
            }
        parent ?: throw EntityException(
            EntityException.Type.ENTITY_NOT_FOUND,
            "Parent directory '$parentId' could not be found."
        )
        if (parentId in resourceIds) {
            throw StateException("Parent directory '$parentId' cannot be one of resources, that are being compressed.")
        }
        if (parent.resolve(name).exists()) {
            throw StateException("Resource${if (resourceIds.size == 1) "" else "s"} could not be compressed: resource by name '$name' exists in parent directory '$parentId'.")
        }
        val compressed = resourceEntities
            .map { Paths.get(it.path) }
            .compressAsZip(parent.resolve(name))
        return resourceRepository.save(
            ResourceEntity(
                parentId = parentId,
                userId = userId,
                contentHash = compressed.calculateContentHash(HashType.SHA_512).uppercase(),
                name = compressed.name,
                extension = compressed.extension,
                path = compressed.toString(),
                type = null, // TODO
                bytesCount = compressed.bytesCount(),
                directory = false
            )
        )
    }

    fun extractResource(id: String, parentId: String): List<ResourceEntity> {
        if (id.trim().isEmpty()) throw ArgumentException("Argument 'id' cannot be empty.")
        if (!id.isEntityId(EntityType.RESOURCE)) throw ArgumentException("Argument 'id' is incorrect.")
        val resourceEntity = resourceRepository.getResourceById(id)
            ?: throw EntityException(
                EntityException.Type.ENTITY_NOT_FOUND,
                "Resource '$id' could not be extracted: resource could not be found."
            )
        val resource = Paths.get(resourceEntity.path)
        if (!resource.isZip()) {
            throw ArgumentException("Resource '$id' could not be extracted: resource is not an archive.")
        }
        if (parentId.trim().isEmpty()) throw ArgumentException("Argument 'parentId' cannot be empty.")
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ArgumentException("Argument 'parentId' is incorrect.")
        }
        lateinit var userId: String
        val parent =
            when (val parentEntityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageById(parentId)?.let {
                    userId = it.userId
                    Paths.get(it.path)
                }

                EntityType.RESOURCE -> resourceRepository
                    .getResourceById(parentId)
                    ?.let {
                        userId = it.userId
                        if (it.trashed) {
                            throw StateException("Resource '$id' could not be extracted to parent directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) {
                            Paths.get(it.path)
                        } else {
                            null
                        }
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${parentEntityType.name}'.")
            }
        parent ?: throw EntityException(
            EntityException.Type.ENTITY_NOT_FOUND,
            "Parent directory '$parentId' could not be found."
        )
        val zipEntries =
            try {
                resource.extractZip(parent)
            } catch (exception: IOException) {
                if (exception.type == IOException.Type.FILE_ALREADY_EXISTS) {
                    throw StateException("Archive '$id' contains entry, that exists.")
                } else {
                    throw exception
                }
            }
        val entries = ArrayList<Path>()
        zipEntries.forEach {
            if (parent.relativize(it).nameCount == 1) {
                entries.add(it)
            }
        }
        return persistResources(entries, parentId, userId)
    }

    private fun persistResources(paths: List<Path>, parentId: String, userId: String): List<ResourceEntity> {
        val resourceEntities = ArrayList<ResourceEntity>()
        paths.forEach {
            val directory = it.isDirectory()
            val contentHash = if (directory) null else it.calculateContentHash(HashType.SHA_512).uppercase()
            var resourceEntity = ResourceEntity(
                parentId = parentId,
                userId = userId,
                contentHash = contentHash,
                name = it.name,
                extension = if (directory) null else it.extension,
                path = it.toString(),
                type = null, // TODO
                bytesCount = it.bytesCount(),
                directory = directory
            )
            resourceEntity = resourceRepository.save(resourceEntity)
            resourceEntities.add(resourceEntity)
            if (directory) {
                persistResources(it.list(), resourceEntity.id!!, userId).forEach { resourceEntities.add(it) }
            }
        }
        return resourceEntities
    }

    // !get resource's contents
    // update resource's description
    // star resource
    // un-star resource
    // add tag(s) to resource
    // remove tag(s) from resource
    // hide resource
    // un-hide resource

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

    private fun createListResourcesSpec(conditions: List<Condition>): Specification<ResourceEntity> =
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
