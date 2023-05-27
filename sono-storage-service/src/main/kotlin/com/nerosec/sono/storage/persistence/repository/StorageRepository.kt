package com.nerosec.sono.storage.persistence.repository

import com.nerosec.sono.storage.persistence.entity.StorageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface StorageRepository : JpaRepository<StorageEntity, String>, JpaSpecificationExecutor<StorageEntity> {

    fun existsStorageEntityByUserId(userId: String): Boolean

    fun getStorageEntityById(id: String): StorageEntity?

    fun getStorageEntityByUserId(userId: String): StorageEntity?

    @Transactional
    fun removeStorageEntityById(id: String)
}
