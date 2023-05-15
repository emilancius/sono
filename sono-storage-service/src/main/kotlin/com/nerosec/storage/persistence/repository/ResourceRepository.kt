package com.nerosec.storage.persistence.repository

import com.nerosec.storage.persistence.entity.ResourceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ResourceRepository : JpaRepository<ResourceEntity, String>, JpaSpecificationExecutor<ResourceEntity> {

    fun getResourceEntityById(id: String): ResourceEntity?

    fun getResourceEntitiesByParentId(parentId: String): List<ResourceEntity>

    @Query("SELECT resource FROM ResourceEntity resource WHERE resource.id IN (:ids)")
    fun getResourceEntitiesByIds(@Param("ids") ids: List<String>): List<ResourceEntity>

    @Transactional
    fun removeResourceEntitiesByUserId(userId: String)

    @Modifying
    @Transactional
    @Query("DELETE FROM ResourceEntity resource WHERE resource.id IN (:ids)")
    fun removeResourceEntitiesByIds(@Param("ids") ids: List<String>)
}
