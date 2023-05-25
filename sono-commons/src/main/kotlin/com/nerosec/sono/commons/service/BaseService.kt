package com.nerosec.sono.commons.service

import com.nerosec.sono.commons.exception.ArgumentException
import com.nerosec.sono.commons.persistence.SortOrder
import com.nerosec.sono.commons.persistence.query.Condition
import com.nerosec.sono.commons.persistence.query.Operation
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireArgumentIsInCollection
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireIntArgumentInInIncRange
import com.nerosec.sono.commons.prerequisites.Prerequisites.requireIntArgumentIsGreaterThan
import jakarta.persistence.metamodel.SingularAttribute
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

abstract class BaseService<T> {

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 100
        val DEFAULT_SORT_ORDER = SortOrder.ASC
        val DEFAULT_PROPERTIES_TO_QUERY_BY = emptyMap<String, Any>()
        const val MIN_PAGE = 1
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 1000
    }

    protected abstract fun getEntityPropertyTypes(): Map<String, SingularAttribute<T, out Any>>

    protected abstract fun getPropertiesToSortBy(): List<String>

    protected abstract fun getPropertiesToQueryBy(): List<String>

    protected fun list(
        specExecutor: JpaSpecificationExecutor<T>,
        page: Int,
        pageSize: Int,
        propertyToSortBy: String,
        sortOrder: SortOrder,
        propertiesToQueryBy: Map<String, Any>
    ): Page<T> {
        requireIntArgumentIsGreaterThan(page, "page", MIN_PAGE - 1)
        requireIntArgumentInInIncRange(pageSize, "pageSize", MIN_PAGE_SIZE, MAX_PAGE_SIZE)
        val propertiesToSortByThatAreSupported = getPropertiesToSortBy()
        requireArgumentIsInCollection(propertyToSortBy, propertiesToSortByThatAreSupported) {
            "Argument's 'propertyToSortBy' value ($propertyToSortBy) is not supported. Supported properties to sort by are: $propertiesToSortByThatAreSupported."
        }
        val propertiesToQueryByThatAreSupported = getPropertiesToQueryBy()
        propertiesToQueryBy.forEach { (property, _) ->
            requireArgumentIsInCollection(property, propertiesToQueryByThatAreSupported) {
                "Argument 'propertiesToQueryBy' contains property, that is not supported ($property). Supported properties to query by are: $propertiesToQueryByThatAreSupported."
            }
        }
        val conditions = propertiesToQueryBy.map { (property, value) -> Condition(property, value, Operation.EQUALS) }
        val spec = createListSpec(conditions)
        val pageRequest = createListPageRequest(page, pageSize, propertyToSortBy, sortOrder)
        return specExecutor.findAll(spec, pageRequest)
    }

    private fun createListSpec(conditions: List<Condition>): Specification<T> =
        Specification { root, _, c ->
            val predicates = conditions.map { (property, value, operation) ->
                val prop = getEntityPropertyTypes()[property]
                    ?: throw ArgumentException("Argument 'conditions' contains a condition for a property '$property', that is not supported.")
                when (operation) {
                    Operation.EQUALS -> c.equal(root[prop], value)
                }
            }
            c.and(*predicates.toTypedArray())
        }

    private fun createListPageRequest(
        page: Int,
        pageSize: Int,
        propertyToSortBy: String,
        sortOrder: SortOrder
    ): PageRequest {
        var sort = Sort.by(propertyToSortBy)
        sort = if (sortOrder == SortOrder.ASC) sort.ascending() else sort.descending()
        return PageRequest.of(page - 1, pageSize, sort)
    }
}
