package com.nerosec.storage.persistence.entity

import jakarta.persistence.metamodel.SingularAttribute
import java.time.Instant

object StorageEntityPropertyTypes {

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
