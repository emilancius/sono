package com.nerosec.sono.storage.settings

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties("service.settings.storage")
class StorageSettings {

    lateinit var path: String
}
