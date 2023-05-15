package com.nerosec.storage.contract.controller

import com.nerosec.sono.commons.exception.EntityException
import com.nerosec.sono.commons.exception.ErrorResponseException
import com.nerosec.sono.commons.exception.ParameterException
import com.nerosec.sono.commons.exception.StateException
import com.nerosec.sono.commons.extension.Extensions.isEntityId
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_PAGE
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_PAGE_SIZE
import com.nerosec.sono.commons.service.BaseService.Companion.DEFAULT_SORT_ORDER
import com.nerosec.sono.commons.service.BaseService.Companion.MAX_PAGE_SIZE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE
import com.nerosec.sono.commons.service.BaseService.Companion.MIN_PAGE_SIZE
import com.nerosec.storage.contract.request.CreateStorageRequestBody
import com.nerosec.storage.contract.response.StoragesPage
import com.nerosec.storage.persistence.entity.StorageEntity_
import com.nerosec.storage.service.StorageService
import com.nerosec.storage.service.StorageService.Companion.DEFAULT_PROPERTY_TO_SORT_BY
import com.nerosec.storage.service.StorageService.Companion.SUPPORTED_PROPERTIES_TO_SORT_BY
import jakarta.servlet.http.HttpServletRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import java.net.URI

@Controller
@Path(StorageController.BASE_PATH)
@Produces(APPLICATION_JSON)
class StorageController(private val storageService: StorageService) {

    companion object {
        private val logger = LoggerFactory.getLogger(StorageController::class.java)
        const val BASE_PATH = "/storages"
        private const val PATH_PARAMETER_ID = "id"
        private const val QUERY_PARAMETER_PAGE = "page"
        private const val QUERY_PARAMETER_PAGE_SIZE = "page_size"
        private const val QUERY_PARAMETER_SORT_BY = "sort_by"
        private const val QUERY_PARAMETER_SORT_ORDER = "sort_order"
        private const val QUERY_PARAMETER_ID = "id"
        private const val QUERY_PARAMETER_USER_ID = "user_id"
        private val CONTRACT_TO_ENTITY_PROPERTY_MAPPING = mapOf(
            "id" to StorageEntity_.ID,
            "user_id" to StorageEntity_.USER_ID,
            "created_on" to StorageEntity_.CREATED
        )
        private val ENTITY_TO_CONTRACT_PROPERTY_MAPPING = mapOf(
            StorageEntity_.ID to "id",
            StorageEntity_.USER_ID to "user_id",
            StorageEntity_.CREATED to "created_on"
        )
        private val SUPPORTED_CONTRACT_PROPERTIES_TO_SORT_BY = SUPPORTED_PROPERTIES_TO_SORT_BY
            .map { ENTITY_TO_CONTRACT_PROPERTY_MAPPING[it] }
    }

    @POST
    @Consumes(APPLICATION_JSON)
    fun createStorage(requestBody: CreateStorageRequestBody, @Context request: HttpServletRequest): Response {
        val userId = requestBody.userId
        if (userId.trim().isEmpty()) throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'user_id' cannot be empty.")
        if (!userId.isEntityId()) throw ErrorResponseException(request, BAD_REQUEST, "Request body parameter 'user_id' is incorrect.")
        return try {
            val storageEntity = storageService.createStorage(userId)
            Response
                .created(URI.create("$BASE_PATH/${storageEntity.id}"))
                .entity(storageEntity.toStorage())
                .build()
        } catch (exception: StateException) {
            throw ErrorResponseException(request, CONFLICT, exception.message)
        } catch (exception: Exception) {
            logger.error("Storage for user '{}' could not be created: unexpected error occurred.", userId, exception)
            throw ErrorResponseException(
                request,
                INTERNAL_SERVER_ERROR,
                "Storage for user '$userId' could not be created: unexpected error occurred."
            )
        }
    }

    @GET
    @Path("/{$PATH_PARAMETER_ID}")
    fun getStorage(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        if (!id.isEntityId()) throw ErrorResponseException(request, BAD_REQUEST, "Path parameter 'id' is incorrect.")
        val storageEntity =
            try {
                storageService.getStorageById(id)
            } catch (exception: Exception) {
                logger.error("Storage '{}' could not be retrieved: unexpected error occurred.", id, exception)
                throw ErrorResponseException(
                    request,
                    INTERNAL_SERVER_ERROR,
                    "Storage '$id' could not be retrieved: unexpected error occurred."
                )
            }
        return storageEntity
            ?.let {
                Response
                    .status(OK)
                    .entity(it.toStorage())
                    .build()
            }
            ?: throw ErrorResponseException(request, NOT_FOUND, "Storage '$id' could not be found.")
    }

    @GET
    fun listStorages(
        @QueryParam(QUERY_PARAMETER_PAGE) page: Int? = null,
        @QueryParam(QUERY_PARAMETER_PAGE_SIZE) pageSize: Int? = null,
        @QueryParam(QUERY_PARAMETER_SORT_BY) sortBy: String? = null,
        @QueryParam(QUERY_PARAMETER_SORT_ORDER) sortOrder: SortOrder? = null,
        @QueryParam(QUERY_PARAMETER_ID) id: String? = null,
        @QueryParam(QUERY_PARAMETER_USER_ID) userId: String? = null,
        @Context request: HttpServletRequest
    ): Response {
        page?.let {
            if (it < MIN_PAGE) {
                throw ErrorResponseException(
                    request,
                    BAD_REQUEST,
                    "Query parameter's '$QUERY_PARAMETER_PAGE' value ($it) cannot be less than $MIN_PAGE."
                )
            }
        }
        pageSize?.let {
            if (it !in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
                throw ErrorResponseException(
                    request,
                    BAD_REQUEST,
                    "Query parameter's '$QUERY_PARAMETER_PAGE_SIZE' value ($it) must be in range [$MIN_PAGE_SIZE; $MAX_PAGE_SIZE]."
                )
            }
        }
        sortBy?.let {
            if (it !in SUPPORTED_CONTRACT_PROPERTIES_TO_SORT_BY) {
                throw ErrorResponseException(
                    request,
                    BAD_REQUEST,
                    "Query parameter's '$QUERY_PARAMETER_SORT_BY' value ($it) is not supported. Supported properties to be sorted by are: $SUPPORTED_CONTRACT_PROPERTIES_TO_SORT_BY."
                )
            }
        }
        val propertiesToQueryBy = HashMap<String, Any>()
        id?.let { propertiesToQueryBy[StorageEntity_.ID] = it }
        userId?.let { propertiesToQueryBy[StorageEntity_.USER_ID] = it }
        val p = page ?: DEFAULT_PAGE
        return try {
            val res = storageService
                .listStorages(
                    page = p,
                    pageSize = pageSize ?: DEFAULT_PAGE_SIZE,
                    propertyToSortBy = sortBy?.let { CONTRACT_TO_ENTITY_PROPERTY_MAPPING[it] }
                        ?: DEFAULT_PROPERTY_TO_SORT_BY,
                    sortOrder = sortOrder ?: DEFAULT_SORT_ORDER,
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
                StoragesPage(
                    page = p,
                    pagesCount = pagesCount,
                    contents = res.content.map { it.toStorage() }
                )
            Response
                .status(OK)
                .entity(responseBody)
                .build()
        } catch (exception: ParameterException) {
            throw ErrorResponseException(request, BAD_REQUEST, exception.message)
        } catch (exception: Exception) {
            logger.error("Storages could not be listed: unexpected error occurred.", exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Storages could not be listed: unexpected error occurred.")
        }
    }

    @DELETE
    @Path("/{$PATH_PARAMETER_ID}")
    fun removeStorage(@PathParam(PATH_PARAMETER_ID) id: String, @Context request: HttpServletRequest): Response {
        if (!id.isEntityId()) throw ErrorResponseException(request, BAD_REQUEST, "Path parameter 'id' is incorrect.")
        return try {
            storageService.removeStorageById(id)
            Response
                .status(OK)
                .build()
        } catch (exception: EntityException) {
            throw ErrorResponseException(request, NOT_FOUND, exception.message)
        } catch (exception: Exception) {
            logger.error("Storage '{}' could not be removed: unexpected error occurred.", id, exception)
            throw ErrorResponseException(request, INTERNAL_SERVER_ERROR, "Storage '$id' could not be removed: unexpected error occurred.")
        }
    }
}
