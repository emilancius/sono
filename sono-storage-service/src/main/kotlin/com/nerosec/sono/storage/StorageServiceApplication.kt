package com.nerosec.sono.storage

import com.nerosec.sono.storage.settings.StorageSettings
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@SpringBootApplication
class StorageServiceApplication

fun main(args: Array<String>) {
    val context = runApplication<StorageServiceApplication>(*args)
    if (!createSystemStorageDirectory(context)) {
        SpringApplication.exit(context)
    }
}

private fun createSystemStorageDirectory(context: ApplicationContext): Boolean {
    val storageSettings = context.getBean(StorageSettings::class.java)
    val storage = Paths.get(storageSettings.path)
    if (!storage.exists()) {
        try {
            storage.createDirectories()
        } catch (exception: Exception) {
            // ignore
        }
    }
    return storage.exists()
}
