rootProject.name = "lyna"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // misc
            library("jetbrains-annotations", "org.jetbrains:annotations:26.1.0")
            version("sadu", "2.3.9")
            library("sadu-queries", "de.chojo.sadu", "sadu-queries").versionRef("sadu")
            library("sadu-updater", "de.chojo.sadu", "sadu-updater").versionRef("sadu")
            library("sadu-postgresql", "de.chojo.sadu", "sadu-postgresql").versionRef("sadu")
            library("sadu-datasource", "de.chojo.sadu", "sadu-datasource").versionRef("sadu")
            bundle("sadu", listOf("sadu-queries", "sadu-updater", "sadu-postgresql", "sadu-datasource"))

            version("log4j", "2.26.1")

            library("guice", "com.google.inject:guice:7.0.0")

            library("ocular", "dev.chojo:ocular:2.2.1")
            version("jackson", "3.2.1")
            library("jackson-yaml", "tools.jackson.dataformat", "jackson-dataformat-yaml").versionRef("jackson")
            bundle("config", listOf("ocular", "jackson-yaml"))

            library("pebble", "io.pebbletemplates:pebble:4.1.2")
            library("thumbnailator", "net.coobird:thumbnailator:0.4.20")
            library("imageio-webp", "com.twelvemonkeys.imageio:imageio-webp:3.12.0")
            bundle("images", listOf("thumbnailator", "imageio-webp"))
            library("bcrypt", "at.favre.lib:bcrypt:0.10.2")
            library("java-jwt", "com.auth0:java-jwt:4.5.0")

            library("slf4j-api", "org.slf4j:slf4j-api:2.0.19")
            library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")
            library("log4j-slf4j2", "org.apache.logging.log4j", "log4j-slf4j2-impl").versionRef("log4j")
            library("log4j-jsontemplate","org.apache.logging.log4j", "log4j-layout-template-json").versionRef("log4j")
            bundle("log4j", listOf("slf4j-api", "log4j-core", "log4j-slf4j2", "log4j-jsontemplate"))

            // testing
            version("junit", "6.1.3")
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit")
            library("junit-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit")
            library("junit-platform", "org.junit.platform:junit-platform-launcher:6.1.3")
            bundle("junit", listOf("junit-jupiter", "junit-params"))
            library("mockito", "org.mockito:mockito-core:5.+")
            library("greenmail", "com.icegreen:greenmail:2.1.9")

            version("testcontainers", "2.0.5")
            library("testcontainers-core", "org.testcontainers", "testcontainers").versionRef("testcontainers")
            library("testcontainers-junit", "org.testcontainers", "testcontainers-junit-jupiter").versionRef("testcontainers")
            library("testcontainers-postgres", "org.testcontainers", "testcontainers-postgresql").versionRef("testcontainers")
            bundle("testcontainers", listOf("testcontainers-core", "testcontainers-junit", "testcontainers-postgres"))

            // plugins
            plugin("spotless", "com.diffplug.spotless").version("8.10.0")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("1.0.0")
}
