package com.nerosec.sono.commons.persistence

import java.util.UUID

object EntityId {

    fun generate(entityType: EntityType): String = "${entityType.name}.${UUID.randomUUID().toString().uppercase()}"
}
