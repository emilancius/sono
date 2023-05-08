package com.nerosec.sono.commons.service

import com.nerosec.sono.commons.persistence.SortOrder

abstract class BaseService {

    companion object {
        const val DEFAULT_PAGE = 1
        const val DEFAULT_PAGE_SIZE = 100
        val DEFAULT_SORT_ORDER = SortOrder.ASC
        val DEFAULT_PROPERTIES_TO_QUERY_BY = emptyMap<String, Any>()
        const val MIN_PAGE = 1
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 1000
    }
}
