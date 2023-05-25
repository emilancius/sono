package com.nerosec.storage.contract.controller

import com.nerosec.sono.commons.contract.BaseController
import com.nerosec.sono.commons.exception.*
import com.nerosec.sono.commons.extension.Extensions.compressAsZip
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.io.compression.CompressionType
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.entity.EntityType
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireRequestBodyPropertyContainsAnyText
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireRequestPathParameterIsEntityId
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireRequestQueryParameterIsGreater
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireRequestQueryParameterIsInCollection
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireRequestQueryParameterIsInIncRange
import com.nerosec.sono.commons.service.BaseService
import com.nerosec.sono.commons.service.BaseService.Companion.MAX_PAGE_SIZE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE_SIZE
import com.nerosec.storage.contract.request.*
import com.nerosec.storage.contract.response.ResourcesPage
import com.nerosec.storage.persistence.entity.ResourceEntity_
import com.nerosec.storage.service.ResourceService
import com.nerosec.storage.service.ResourceService.Companion.SUPPORTED_PROPERTIES_TO_SORT_BY
import com.nerosec.storage.service.StorageService
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType.*
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import jakarta.ws.rs.core.StreamingOutput
import org.glassfish.jersey.media.multipart.FormDataParam
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import java.io.InputStream
import java.net.URI
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.io.path.name

@Controller
@Path(ResourceController.BASE_PATH)
@Produces(APPLICATION_JSON)
class ResourceController(
    private val storageService: StorageService,
    private val resourceService: ResourceService
) : BaseController() {

    companion object {
        private val logger = LoggerFactory.getLogger(ResourceController::class.java)
        const val BASE_PATH = "/resources"
        private const val FORM_DATA_PARAMETER_RESOURCE = "resource"
        private const val FORM_DATA_PARAMETER_RESOURCE_PROPERTIES = "resource_properties"
        private const val QUERY_PARAMETER_PARENT_ID = "parent_id"
        private const val QUERY_PARAMETER_CONTENT_HASH = "content_hash"
        private const val QUERY_PARAMETER_NAME = "name"
        private const val QUERY_PARAMETER_EXTENSION = "extension"
        private const val QUERY_PARAMETER_TYPE = "type"
        private const val QUERY_PARAMETER_DIRECTORY = "is_directory"
        private const val QUERY_PARAMETER_TRASHED = "is_trashed"
        private const val QUERY_PARAMETER_VERSION = "version"
        private val CONTRACT_TO_ENTITY_PROPERTY_MAPPING = mapOf(
            "id" to ResourceEntity_.ID,
            "parent_id" to ResourceEntity_.PARENT_ID,
            "user_id" to ResourceEntity_.USER_ID,
            "content_hash" to ResourceEntity_.CONTENT_HASH,
            "name" to ResourceEntity_.NAME,
            "extension" to ResourceEntity_.EXTENSION,
            "type" to ResourceEntity_.TYPE,
            "bytes_count" to ResourceEntity_.BYTES_COUNT,
            "is_directory" to ResourceEntity_.DIRECTORY,
            "is_trashed" to ResourceEntity_.TRASHED,
            "description" to ResourceEntity_.DESCRIPTION,
            "version" to ResourceEntity_.VERSION,
            "created_on" to ResourceEntity_.CREATED,
            "last_updated_on" to ResourceEntity_.LAST_UPDATED
        )
        private val ENTITY_TO_CONTRACT_PROPERTY_MAPPING = mapOf(
            ResourceEntity_.ID to "id",
            ResourceEntity_.PARENT_ID to "parent_id",
            ResourceEntity_.USER_ID to "user_id",
            ResourceEntity_.CONTENT_HASH to "content_hash",
            ResourceEntity_.NAME to "name",
            ResourceEntity_.EXTENSION to "extension",
            ResourceEntity_.TYPE to "type",
            ResourceEntity_.BYTES_COUNT to "bytes_count",
            ResourceEntity_.DIRECTORY to "is_directory",
            ResourceEntity_.TRASHED to "is_trashed",
            ResourceEntity_.DESCRIPTION to "description",
            ResourceEntity_.VERSION to "version",
            ResourceEntity_.CREATED to "created_on",
            ResourceEntity_.LAST_UPDATED to "last_updated_on"
        )
        private val SUPPORTED_CONTRACT_PROPERTIES_TO_SORT_BY = SUPPORTED_PROPERTIES_TO_SORT_BY
            .map { ENTITY_TO_CONTRACT_PROPERTY_MAPPING[it] }
    }

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    fun createResource(
        @FormDataParam(FORM_DATA_PARAMETER_RESOURCE_PROPERTIES) properties: String,
        @FormDataParam(FORM_DATA_PARAMETER_RESOURCE) resourceInputStream: InputStream? = null,
        @Context request: HttpServletRequest
    ): Response {
        val (parentId, userId, name, directory, description) = CreateResourceRequestBody.createFromJsonString(properties)
        if (parentId.trim().isEmpty()) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'parent_id' cannot be empty.")
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'parent_id' is incorrect.")
        if (userId.trim().isEmpty()) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'user_id' cannot be empty.")
        if (!userId.isEntityId(EntityType.USER)) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'user_id' is incorrect.")
        if (name.trim().isEmpty()) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'name' cannot be empty.")
        if (!directory && resourceInputStream == null) throw ErrorResponseException(request, BAD_REQUEST, "Request parameter 'resource' is required.")
        return try {
            val resourceEntity = resourceService
                .createResource(parentId, userId, name, directory, description, resourceInputStream)
            Response
                .created(URI.create("$BASE_PATH/${resourceEntity.id}"))
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: ArgumentException) {
            throw ErrorResponseException(
                request,
                BAD_REQUEST,
                "Request parameter's 'parent_id' value ($parentId) of type '${parentId.substringBefore('.')}' is not supported."
            )
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be created: unexpected error occurred.", name, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$name' could not be created: unexpected error occurred.")
        }
    }

    @GET
    @Path("/{$PATH_PARAMETER_ID}")
    fun getResource(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        val resourceEntity =
            try {
                resourceService.getResourceById(id)
            } catch (exception: Exception) {
                logger.error("Resource '{}' could not be retrieved: unexpected error occurred.", id, exception)
                throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be retrieved: unexpected error occurred.")
            }
        return resourceEntity
            ?.let {
                Response
                    .status(OK)
                    .entity(it.toResource())
                    .build()
            }
            ?: throw ErrorResponseException(request, NOT_FOUND, "Resource '$id' could not be found.")
    }

    @GET
    fun listStorages(
        @QueryParam(QUERY_PARAMETER_PAGE) page: Int? = null,
        @QueryParam(QUERY_PARAMETER_PAGE_SIZE) pageSize: Int? = null,
        @QueryParam(QUERY_PARAMETER_SORT_BY) sortBy: String? = null,
        @QueryParam(QUERY_PARAMETER_SORT_ORDER) sortOrder: SortOrder? = null,
        @QueryParam(QUERY_PARAMETER_ID) id: String? = null,
        @QueryParam(QUERY_PARAMETER_PARENT_ID) parentId: String? = null,
        @QueryParam(QUERY_PARAMETER_USER_ID) userId: String? = null,
        @QueryParam(QUERY_PARAMETER_CONTENT_HASH) contentHash: String? = null,
        @QueryParam(QUERY_PARAMETER_NAME) name: String? = null,
        @QueryParam(QUERY_PARAMETER_EXTENSION) extension: String? = null,
        @QueryParam(QUERY_PARAMETER_TYPE) type: String? = null,
        @QueryParam(QUERY_PARAMETER_DIRECTORY) directory: Boolean? = null,
        @QueryParam(QUERY_PARAMETER_TRASHED) trashed: Boolean? = null,
        @QueryParam(QUERY_PARAMETER_VERSION) version: Int? = null,
        @Context request: HttpServletRequest
    ): Response {
        page?.let { requireRequestQueryParameterIsGreater(it, QUERY_PARAMETER_PAGE, MIN_PAGE - 1, request) }
        pageSize?.let { requireRequestQueryParameterIsInIncRange(it, QUERY_PARAMETER_PAGE_SIZE, MIN_PAGE_SIZE, MAX_PAGE_SIZE, request) }
        sortBy?.let { requireRequestQueryParameterIsInCollection(it, QUERY_PARAMETER_SORT_BY, SUPPORTED_CONTRACT_PROPERTIES_TO_SORT_BY, request) }
        val propertiesToQueryBy = HashMap<String, Any>()
        id?.let { propertiesToQueryBy[ResourceEntity_.ID] = it }
        parentId?.let { propertiesToQueryBy[ResourceEntity_.PARENT_ID] = it }
        userId?.let { propertiesToQueryBy[ResourceEntity_.USER_ID] = it }
        contentHash?.let { propertiesToQueryBy[ResourceEntity_.CONTENT_HASH] = it }
        name?.let { propertiesToQueryBy[ResourceEntity_.NAME] = it }
        extension?.let { propertiesToQueryBy[ResourceEntity_.EXTENSION] = it }
        type?.let { propertiesToQueryBy[ResourceEntity_.TYPE] = it }
        directory?.let { propertiesToQueryBy[ResourceEntity_.DIRECTORY] = it }
        trashed?.let { propertiesToQueryBy[ResourceEntity_.TRASHED] = it }
        version?.let { propertiesToQueryBy[ResourceEntity_.VERSION] = it }
        val p = page ?: BaseService.DEFAULT_PAGE
        return try {
            val res = resourceService
                .listResources(
                    page = p,
                    pageSize = pageSize ?: BaseService.DEFAULT_PAGE_SIZE,
                    propertyToSortBy = sortBy?.let { CONTRACT_TO_ENTITY_PROPERTY_MAPPING[it] } ?: StorageService.DEFAULT_PROPERTY_TO_SORT_BY,
                    sortOrder = sortOrder ?: BaseService.DEFAULT_SORT_ORDER,
                    propertiesToQueryBy = propertiesToQueryBy
                )
            var pagesCount = res.totalPages
            pagesCount = if (pagesCount == 0) 1 else pagesCount
            page?.let {
                if (it > pagesCount) {
                    throw ParameterException("Query parameter's '$QUERY_PARAMETER_PAGE' value ($it) cannot be greater than the last page ($pagesCount).")
                }
            }
            val responseBody =
                ResourcesPage(
                    page = p,
                    pagesCount = pagesCount,
                    contents = res.content.map { it.toResource() }
                )
            Response
                .status(OK)
                .entity(responseBody)
                .build()
        } catch (exception: ParameterException) {
            throw ErrorResponseException(request, BAD_REQUEST, exception.message)
        } catch (exception: Exception) {
            logger.error("Resources could not be listed: unexpected error occurred.", exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resources could not be listed: unexpected error occurred.")
        }
    }

    @DELETE
    @Path("/{$PATH_PARAMETER_ID}")
    fun removeResource(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        return try {
            resourceService.removeResourceById(id)
            Response
                .status(OK)
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be removed: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be removed: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/trash")
    fun trashResource(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        return try {
            val resourceEntity = resourceService.trashResource(id)
            Response
                .status(OK)
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be moved to trash: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be moved to trash: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/restore")
    fun restoreResource(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        return try {
            val resourceEntity = resourceService.restoreResource(id)
            Response
                .status(OK)
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be moved restored: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be moved restored: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/copy")
    fun copyResource(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        return try {
            val resourceEntity = resourceService.copyResource(id)
            Response
                .status(OK)
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be copied: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be copied: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/move")
    fun moveResource(
        @PathParam(PATH_PARAMETER_ID) id: String,
        requestBody: ChangeResourceLocationRequestBody,
        @Context request: HttpServletRequest
    ): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        val parentId = requestBody.parentId
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'parent_id' is incorrect.")
        }
        return try {
            val resourceEntity = resourceService.moveResource(id, parentId)
            Response
                .status(OK)
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be moved: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be copied: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/rename")
    fun renameResource(
        @PathParam(PATH_PARAMETER_ID) id: String,
        requestBody: RenameResourceRequestBody,
        @Context request: HttpServletRequest
    ): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        val name = requestBody.name
        requireRequestBodyPropertyContainsAnyText(name, "name", request)
        if (name.length !in 1..255) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'name' must be 1 to 255 characters length.")
        }
        return try {
            val resourceEntity = resourceService.renameResource(id, name)
            Response
                .status(OK)
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be moved: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be copied: unexpected error occurred.")
        }
    }

    @POST
    @Path("/actions/compress")
    fun compressResources(
        requestBody: CompressResourcesRequestBody,
        @Context request: HttpServletRequest
    ): Response {
        val (resourceIds, parentId, name) = requestBody
        if (resourceIds.isEmpty()) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'request_ids' cannot be empty.")
        }
        resourceIds.forEach {
            if (it.trim().isEmpty()) {
                throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'request_ids' cannot contain empty value.")
            }
            if (!it.isEntityId(EntityType.RESOURCE)) {
                throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'request_ids' contains value ($it), that is incorrect.")
            }
        }
        if (parentId.trim().isEmpty()) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'parent_id' cannot be empty.")
        }
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'parent_id' is incorrect.")
        }
        if (name.trim().isEmpty()) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'name' cannot be empty.")
        }
        if (name.length !in 1..255) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'name' must be 1 to 255 characters length.")
        }
        return try {
            val resourceEntity = resourceService.compressResources(resourceIds, parentId, name)
            Response
                .created(URI.create("$BASE_PATH/${resourceEntity.id}"))
                .entity(resourceEntity.toResource())
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resources could not be compressed: unexpected error occurred.", exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resources could not be compressed: unexpected error occurred.")
        }
    }

    @POST
    @Path("/{$PATH_PARAMETER_ID}/actions/extract")
    fun extractResource(
        @PathParam(PATH_PARAMETER_ID) id: String,
        requestBody: ExtractResourcesRequestBody,
        @Context request: HttpServletRequest
    ): Response {
        if (!id.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ErrorResponseException(request, BAD_REQUEST, "Path parameter 'id' is incorrect.")
        }
        val parentId = requestBody.parentId
        if (parentId.trim().isEmpty()) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'parent_id' cannot be empty.")
        }
        if (!parentId.isEntityId(EntityType.STORAGE, EntityType.RESOURCE)) {
            throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'parent_id' is incorrect.")
        }
        return try {
            val resourceEntities = resourceService.extractResource(id, parentId)
            Response
                .status(CREATED)
                .entity(resourceEntities.map { it.toResource() })
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource could not be extracted: unexpected error occurred.", exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource could not be extracted: unexpected error occurred.")
        }
    }

    @GET
    @Path("/{$PATH_PARAMETER_ID}/contents")
    @Produces(APPLICATION_OCTET_STREAM)
    fun getResourceContents(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        requireRequestPathParameterIsEntityId(id, PATH_PARAMETER_ID, EntityType.RESOURCE, request)
        return try {
            val resourceEntity = resourceService
                .getResourceById(id)
                ?: throw EntityException(EntityException.Type.ENTITY_NOT_FOUND, "Resource '$id' could not be found.")
            val resource =
                if (resourceEntity.directory) {
                    val storage = Paths.get(storageService.getStorageByUserId(resourceEntity.userId)!!.path)
                    val archive = storage
                        .resolve(StorageService.TEMP_DIRECTORY)
                        .resolve("${resourceEntity.name} ${System.nanoTime()}.${CompressionType.ZIP.extension}")
                    Paths.get(resourceEntity.path).compressAsZip(archive)
                } else {
                    Paths.get(resourceEntity.path)
                }
            val streamingOutput = StreamingOutput { outputStream ->
                val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                var length: Int
                resource.inputStream().use { inputStream ->
                    while (inputStream.read(bytes).also { length = it } > 0) {
                        outputStream.write(bytes, 0, length)
                    }
                    outputStream.flush()
                }
            }
            Response
                .status(OK)
                .entity(streamingOutput)
                .type(APPLICATION_OCTET_STREAM)
                .header("content-disposition", "attachment; filename=${resource.name}")
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: Exception) {
            logger.error("Resource '{}' could not be retrieved: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Resource '$id' could not be retrieved: unexpected error occurred.")
        }
    }
}
