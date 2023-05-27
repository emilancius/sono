package com.nerosec.sono.storage.settings

import com.nerosec.sono.commons.exception.mapping.ErrorResponseExceptionListener
import com.nerosec.sono.storage.contract.controller.ResourceController
import com.nerosec.sono.storage.contract.controller.StorageController
import org.glassfish.jersey.media.multipart.MultiPartFeature
import org.glassfish.jersey.server.ResourceConfig
import org.springframework.context.annotation.Configuration

@Configuration
class JerseySettings : ResourceConfig() {

    companion object {
        private val FEATURE_CLASSES = listOf(MultiPartFeature::class.java)
        private val CONTROLLER_CLASS = listOf(
            StorageController::class.java,
            ResourceController::class.java
        )
        private val EXCEPTION_LISTENER_CLASSES = listOf(ErrorResponseExceptionListener::class.java)
    }

    init {
        registerFeatures()
        registerControllers()
        registerExceptionListeners()
    }

    private fun registerFeatures() = register(FEATURE_CLASSES)

    private fun registerControllers() = register(CONTROLLER_CLASS)

    private fun registerExceptionListeners() = register(EXCEPTION_LISTENER_CLASSES)

    private fun register(classes: List<Class<*>>) = classes.forEach { register(it) }
}
