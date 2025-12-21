import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    alias(libs.plugins.shadow)
    java
    id("org.openrewrite.rewrite") version "7.18.0"
}

group = "de.chojo"
version = "1.6.1"

repositories {
    mavenCentral()
    maven("https://eldonexus.de/repository/maven-public")
    maven("https://eldonexus.de/repository/maven-proxies")
}

dependencies {
    //discord
    implementation("de.chojo", "cjda-util", "2.12.0+jda-6.0.0") {
        exclude(group = "club.minnced", module = "opus-java")
    }

    // database
    implementation("org.postgresql", "postgresql", "42.7.8")
    implementation(libs.bundles.sadu)

    // Download api
    implementation("de.chojo", "nexus-api-wrapper", "1.0.5")

    val openapi = "6.7.0-2"

    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$openapi")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$openapi") // for /openapi route with JSON scheme
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$openapi") // for Swagger UI

    // Mailing
    implementation("org.eclipse.angus", "angus-mail", "2.0.3")
    implementation("org.jsoup", "jsoup", "1.17.2")


    // Logging
    implementation(libs.bundles.log4j)
    implementation("de.chojo", "log-util", "1.0.1") {
        exclude("org.apache.logging.log4j")
    }

    // unit testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.mockito", "mockito-core", "5.+")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks {
    processResources {
        from(sourceSets.main.get().resources.srcDirs) {
            filesMatching("version") {
                expand(
                        "version" to project.version
                )
            }
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    javadoc {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    shadowJar {
        transform(Log4j2PluginsCacheFileTransformer::class.java)
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "de.chojo.lyna.Lyna"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
