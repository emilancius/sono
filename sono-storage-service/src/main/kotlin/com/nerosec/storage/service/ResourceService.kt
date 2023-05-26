package com.nerosec.storage.service

import com.nerosec.sono.commons.HashType
import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.IOException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.bytesCount
import com.nerosec.sono.commons.extension.Extensions.compressAsZip
import com.nerosec.sono.commons.extension.Extensions.copyAs
import com.nerosec.sono.commons.extension.Extensions.createFromInputStream
import com.nerosec.sono.commons.extension.Extensions.extractZip
import com.nerosec.sono.commons.extension.Extensions.getContentHash
import com.nerosec.sono.commons.extension.Extensions.isZip
import com.nerosec.sono.commons.extension.Extensions.list
import com.nerosec.sono.commons.extension.Extensions.moveTo
import com.nerosec.sono.commons.extension.Extensions.remove
import com.nerosec.sono.commons.extension.Extensions.renameTo
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireIntArgumentIsInRange
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireStringArgumentContainsAnyText
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireStringArgumentIsEntityId
import com.nerosec.sono.commons.service.BaseService
import com.nerosec.storage.persistence.entity.ResourceEntity
import com.nerosec.storage.persistence.entity.ResourceEntityPropertyTypes
import com.nerosec.storage.persistence.entity.ResourceEntity_
import com.nerosec.storage.persistence.repository.ResourceRepository
import com.nerosec.storage.persistence.repository.StorageRepository
import jakarta.persistence.metamodel.SingularAttribute
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

@Service
class ResourceService(
    val storageRepository: StorageRepository,
    val resourceRepository: ResourceRepository
) : BaseService<ResourceEntity>() {

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
    }

    fun createResource(
        parentId: String, // must point to a directory
        userId: String,
        name: String,
        directory: Boolean = true,
        description: String? = null,
        inputStream: InputStream? = null
    ): ResourceEntity {
        requireStringArgumentContainsAnyText(parentId, "parentId")
        requireStringArgumentIsEntityId(parentId, arrayOf(EntityType.STORAGE, EntityType.RESOURCE), "parentId")
        requireStringArgumentContainsAnyText(userId, "userId")
        requireStringArgumentIsEntityId(userId, EntityType.USER, "userId")
        requireStringArgumentContainsAnyText(name, "name")
        if (!directory && inputStream == null) throw ArgumentException("Argument 'inputStream' is required.")
        val parent =
            when (val entityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageEntityById(parentId)?.let { Paths.get(it.path) }
                EntityType.RESOURCE -> resourceRepository
                    .getResourceEntityById(parentId)
                    ?.let {
                        if (it.trashed) {
                            throw StateException("Resource '$name' could not be created in directory '$parentId': directory '$parentId' is moved to trash.")
                        }
                        if (it.directory) {
                            Paths.get(it.path)
                        } else {
                            throw StateException("Resource '$name' could not be created: '$parentId' is not a directory.")
                        }
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${entityType.name}'.")
            }
        parent ?: throw EntityException(message = "Resource '$name' could not be created: parent directory '$parentId' could not be found.")
        val resourceExists = resourceRepository
            .getResourceEntitiesByParentId(parentId)
            .any { it.directory == directory && it.name == name && !it.trashed }
        if (resourceExists) {
            throw StateException("Resource '$name' could not be created in directory '$parentId': such resource exists.")
        }
        var resource = parent.resolve(name)
        resource =
            if (directory) {
                resource.createDirectory()
            } else {
                val it = resource.createFromInputStream(inputStream!!)
                inputStream.close()
                it
            }
        return resourceRepository
            .save(
                ResourceEntity(
                    parentId = parentId,
                    userId = userId,
                    contentHash = if (directory) null else resource.getContentHash(HashType.SHA_512).uppercase(),
                    name = resource.name,
                    extension = if (resource.extension == "") null else resource.extension,
                    path = resource.toString(),
                    type = null,
                    bytesCount = resource.bytesCount(),
                    directory = directory,
                    description = description
                )
            )
    }

    fun getResourceById(id: String): ResourceEntity? {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        return resourceRepository.getResourceEntityById(id)
    }

    fun listResources(
        page: Int = DEFAULT_PAGE,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        propertyToSortBy: String = DEFAULT_PROPERTY_TO_SORT_BY,
        sortOrder: SortOrder = DEFAULT_SORT_ORDER,
        propertiesToQueryBy: Map<String, Any> = DEFAULT_PROPERTIES_TO_QUERY_BY
    ): Page<ResourceEntity> =
        list(
            resourceRepository,
            page,
            pageSize,
            propertyToSortBy,
            sortOrder,
            propertiesToQueryBy
        )

    fun removeResourceById(id: String) {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        val resourceEntity = resourceRepository.getResourceEntityById(id) ?: throw EntityException(message = "Resource '$id' could not be removed: resource could not be found.")
        Paths.get(resourceEntity.path).remove()
        val resourceIds = ArrayList(resourceEntity.list().map { it.id!! })
        resourceIds.add(id)
        resourceRepository.removeResourceEntitiesByIds(resourceIds)
    }

    fun trashResource(id: String): ResourceEntity {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        var resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be moved to trash: resource could not be found.")
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be moved to trash: resource is in trash.")
        val bin = storageRepository
            .getStorageEntityByUserId(resourceEntity.userId)!!
            .let { Paths.get(it.path).resolve(StorageService.BIN_DIRECTORY) }
        var resource = Paths.get(resourceEntity.path)
        if (bin.resolve(resourceEntity.name).exists()) {
            var name = "${resourceEntity.name.substringBeforeLast('.')} ${System.nanoTime()}"
            if (!resourceEntity.directory && resourceEntity.extension != null) {
                name += ".${resourceEntity.extension}"
            }
            resource = resource.renameTo(name)
        }
        resource.moveTo(bin)
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
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        var resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be restored: resource could not be found.")
        if (!resourceEntity.trashed) throw StateException("Resource '$id' could not be restored: resource is not in trash.")
        val resource = Paths.get(resourceEntity.path)
        val parentId = resourceEntity.parentId
        val parent =
            when (EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageEntityById(parentId)?.let { Paths.get(it.path) }
                else -> {
                    // EntityType.RESOURCE
                    resourceRepository
                        .getResourceEntityById(parentId)
                        ?.let {
                            if (it.trashed) {
                                throw StateException("Resource '$id' could not be restored: it's parent directory '$parentId' is in trash.")
                            }
                            Paths.get(it.path)
                        }
                }
            }
        parent ?: throw EntityException(message = "Resource '$id' could not be restored: it's parent directory '$parentId' could not be found.")
        if (parent.resolve(resourceEntity.name).exists()) {
            throw StateException("Resource '$id' could not be restored: such resource exists in it's parent directory '$parentId'.")
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
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        val resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be copied: resource could not be found.")
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
        val contentHash = if (directory) null else resourceCopy.getContentHash(HashType.SHA_512).uppercase()
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
                val innerResource = Paths.get(resourceCopyEntity.path).resolve(resource.relativize(Paths.get(it.path)))
                val innerResourceEntity = resourceRepository.save(
                    it.copy(
                        id = null,
                        parentId = parentId,
                        contentHash = if (it.directory) null else innerResource.getContentHash(HashType.SHA_512).uppercase(),
                        path = innerResource.toString(),
                        description = null,
                        version = 1,
                        lastUpdated = null
                    )
                )
                if (innerResourceEntity.directory) {
                    parentId = innerResourceEntity.id!!
                }
            }
        }
        return resourceCopyEntity
    }

    fun moveResource(id: String, parentId: String): ResourceEntity {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        requireStringArgumentContainsAnyText(parentId, "parentId")
        requireStringArgumentIsEntityId(parentId, arrayOf(EntityType.STORAGE, EntityType.RESOURCE), "parentId")
        var resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be moved: resource could not be found.")
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be moved: resource is in trash.")
        val parent =
            when (val entityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageEntityById(parentId)?.let { Paths.get(it.path) }
                EntityType.RESOURCE -> resourceRepository
                    .getResourceEntityById(parentId)
                    ?.let {
                        if (it.trashed) {
                            throw StateException("Resource '$id' could not be moved to directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) Paths.get(it.path) else null
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${entityType.name}'.")
            }
        parent ?: throw EntityException(message = "Parent directory '$parentId' could not be found.")
        if (parent.resolve(resourceEntity.name).exists()) {
            throw StateException("Resource '$id' could not be moved to parent directory '$parentId': parent directory '$parentId' contains resource '${resourceEntity.name}'.")
        }
        val resource = Paths.get(resourceEntity.path)
        val moved = resource.moveTo(parent)
        resourceEntity = resourceRepository.save(resourceEntity.copy(parentId = parentId, path = moved.toString()))
        if (resourceEntity.directory) {
            var resourceEntityParentId = resourceEntity.id!!
            resourceEntity.list().forEach {
                val innerResourceEntity = resourceRepository.save(
                    it.copy(
                        parentId = resourceEntityParentId,
                        path = Paths.get(resourceEntity.path).resolve(resource.relativize(Paths.get(it.path))).toString()
                    )
                )
                if (innerResourceEntity.directory) {
                    resourceEntityParentId = innerResourceEntity.id!!
                }
            }
        }
        return resourceEntity
    }

    fun renameResource(id: String, name: String): ResourceEntity {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        requireStringArgumentContainsAnyText(name, "name")
        requireIntArgumentIsInRange(name.length, 1, 255, "name")
        var resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be renamed: resource could not be found.")
        if (resourceEntity.trashed) throw StateException("Resource '$id' could not be renamed: resource is in trash.")
        val resource = Paths.get(resourceEntity.path)
        if (resource.resolveSibling(name).exists()) {
            throw StateException("Resource '$id' could not be renamed to '$name': such resource exists.")
        }
        val renamed = resource.renameTo(name)
        resourceEntity = resourceRepository.save(
            resourceEntity.copy(
                name = renamed.name,
                extension = if (resource.extension == "") null else resource.extension,
                path = renamed.toString()
            )
        )
        if (resourceEntity.directory) {
            resourceEntity.list().forEach {
                resourceRepository.save(
                    it.copy(path = Paths.get(resourceEntity.path).resolve(resource.relativize(Paths.get(it.path))).toString())
                )
            }
        }
        return resourceEntity
    }

    fun compressResources(resourceIds: List<String>, parentId: String, name: String): ResourceEntity {
        if (resourceIds.isEmpty()) throw ArgumentException("Argument 'resourceIds' cannot be empty.")
        resourceIds.forEach {
            requireStringArgumentContainsAnyText(it) { "Argument 'resourceIds' cannot contain an empty value." }
            requireStringArgumentIsEntityId(it, EntityType.RESOURCE) { "Argument 'resourceIds' contains value ($it), that is incorrect." }
        }
        requireStringArgumentContainsAnyText(parentId, "parentId")
        requireStringArgumentIsEntityId(parentId, arrayOf(EntityType.STORAGE, EntityType.RESOURCE), "parentId")
        requireStringArgumentContainsAnyText(name, "name")
        requireIntArgumentIsInRange(name.length, 1, 255, "name")
        val resourceEntities = resourceRepository.getResourceEntitiesByIds(resourceIds)
        if (resourceEntities.size != resourceIds.size) {
            val missingResourcesCount = resourceIds.size - resourceEntities.size
            throw EntityException(message = "$missingResourcesCount resource${if (missingResourcesCount == 1) "" else "s"} could not be found.")
        }
        resourceEntities.forEach {
            if (it.trashed) throw StateException("Resource '${it.id}' cannot be compressed: resource is in trash.")
        }
        lateinit var userId: String
        val parent =
            when (val entityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> {
                    storageRepository.getStorageEntityById(parentId)?.let {
                        userId = it.userId
                        Paths.get(it.path)
                    }
                }

                EntityType.RESOURCE -> resourceRepository
                    .getResourceEntityById(parentId)
                    ?.let {
                        userId = it.userId
                        if (it.trashed) {
                            throw StateException("Resource '$name' could not be created in directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) Paths.get(it.path) else null
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${entityType.name}'.")
            }
        parent ?: throw EntityException(message = "Parent directory '$parentId' could not be found.")
        if (parentId in resourceIds) {
            throw StateException("Parent directory '$parentId' cannot be one of resources, that are being compressed.")
        }
        if (parent.resolve(name).exists()) {
            throw StateException("Resource${if (resourceIds.size == 1) "" else "s"} could not be compressed: resource by name '$name' exists in directory '$parentId'.")
        }
        val compressed = resourceEntities
            .map { Paths.get(it.path) }
            .compressAsZip(parent.resolve(name))
        return resourceRepository.save(
            ResourceEntity(
                parentId = parentId,
                userId = userId,
                contentHash = compressed.getContentHash(HashType.SHA_512).uppercase(),
                name = compressed.name,
                extension = compressed.extension,
                path = compressed.toString(),
                type = null,
                bytesCount = compressed.bytesCount(),
                directory = false
            )
        )
    }

    fun extractResource(id: String, parentId: String): List<ResourceEntity> {
        requireStringArgumentContainsAnyText(id, "id")
        requireStringArgumentIsEntityId(id, EntityType.RESOURCE, "id")
        val resourceEntity = resourceRepository.getResourceEntityById(id)
            ?: throw EntityException(message = "Resource '$id' could not be extracted: resource could not be found.")
        val resource = Paths.get(resourceEntity.path)
        if (!resource.isZip()) throw ArgumentException("Resource '$id' could not be extracted: resource is not an archive.")
        requireStringArgumentContainsAnyText(parentId, "parentId")
        requireStringArgumentIsEntityId(parentId, arrayOf(EntityType.STORAGE, EntityType.RESOURCE), "parentId")
        lateinit var userId: String
        val parent =
            when (val parentEntityType = EntityType.createFromEntityId(parentId)) {
                EntityType.STORAGE -> storageRepository.getStorageEntityById(parentId)?.let {
                    userId = it.userId
                    Paths.get(it.path)
                }

                EntityType.RESOURCE -> resourceRepository
                    .getResourceEntityById(parentId)
                    ?.let {
                        userId = it.userId
                        if (it.trashed) {
                            throw StateException("Resource '$id' could not be extracted to parent directory '$parentId': directory '$parentId' has been moved to trash.")
                        }
                        if (it.directory) Paths.get(it.path) else null
                    }

                else -> throw ArgumentException("Argument 'parentId' is not supported for entity type '${parentEntityType.name}'.")
            }
        parent ?: throw EntityException(message = "Parent directory '$parentId' could not be found.")
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
        paths.forEach { path ->
            val directory = path.isDirectory()
            val contentHash = if (directory) null else path.getContentHash(HashType.SHA_512).uppercase()
            var resourceEntity = ResourceEntity(
                parentId = parentId,
                userId = userId,
                contentHash = contentHash,
                name = path.name,
                extension = if (directory) null else path.extension,
                path = path.toString(),
                type = null,
                bytesCount = path.bytesCount(),
                directory = directory
            )
            resourceEntity = resourceRepository.save(resourceEntity)
            resourceEntities.add(resourceEntity)
            if (directory) {
                persistResources(path.list(), resourceEntity.id!!, userId).forEach { resourceEntities.add(it) }
            }
        }
        return resourceEntities
    }

    private fun ResourceEntity.list(depth: Int = Int.MAX_VALUE): List<ResourceEntity> =
        if (depth < 1 || !this.directory) {
            emptyList()
        } else {
            val resources = ArrayList<ResourceEntity>()
            resourceRepository
                .getResourceEntitiesByParentId(this.id!!)
                .forEach { resource ->
                    resources.add(resource)
                    if (resource.directory) {
                        resource.list(depth.dec()).forEach { resources.add(it) }
                    }
                }
            resources
        }

    override fun getEntityPropertyTypes(): Map<String, SingularAttribute<ResourceEntity, out Any>> = ResourceEntityPropertyTypes.PROPERTY_TYPES

    override fun getPropertiesToSortBy(): List<String> = SUPPORTED_PROPERTIES_TO_SORT_BY

    override fun getPropertiesToQueryBy(): List<String> = SUPPORTED_PROPERTIES_TO_QUERY_BY
}
