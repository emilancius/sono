package com.nerosec.storage.persistence.entity

import jakarta.persistence.metamodel.SingularAttribute
import java.time.Instant

object ResourceEntityPropertyTypes {

    @Suppress("UNCHECKED_CAST")
    val PROPERTY_TYPES =
        mapOf(
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
