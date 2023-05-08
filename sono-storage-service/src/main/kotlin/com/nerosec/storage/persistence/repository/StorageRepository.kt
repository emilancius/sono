package com.nerosec.storage.persistence.repository

import com.nerosec.storage.persistence.entity.StorageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface StorageRepository : JpaRepository<StorageEntity, String>, JpaSpecificationExecutor<StorageEntity> {

    fun existsStorageByUserId(userId: String): Boolean

    fun getStorageById(id: String): StorageEntity?

    fun getStorageByUserId(userId: String): StorageEntity?

    @Transactional
    fun removeStorageById(id: String)
}
