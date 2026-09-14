plugins {
    application
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

    // Auth
    implementation(libs.bcrypt)
    implementation(libs.java.jwt)

    // Mailing
    implementation("org.eclipse.angus", "angus-mail", "2.0.3")
    implementation("org.jsoup", "jsoup", "1.17.2")


    // Logging
    implementation(libs.bundles.log4j)
    implementation("de.chojo", "log-util", "1.0.1") {
        exclude("org.apache.logging.log4j")
    }

    // testing
    testImplementation(libs.bundles.junit)
    testRuntimeOnly(libs.junit.platform)
    testImplementation(libs.mockito)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.greenmail)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass.set("de.chojo.lyna.Lyna")
    applicationName = "lyna"
}

/**
 * Number of JVMs a test task may fork.
 *
 * Every fork starts its own database container, and rootless Docker allocates the host port in a
 * check-then-bind that races every outbound socket on the machine. Disabling the Testcontainers
 * reaper halves the containers a fork starts and removes the one that lost that race by far the most
 * often, which is what keeps one fork per two cores workable. Override with `-PtestForks=N` when a
 * machine needs a different balance.
 */
fun testForks(): Int {
    val configured = providers.gradleProperty("testForks").orNull?.toIntOrNull()
    return configured ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

tasks {
    withType<Test>().configureEach {
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    }

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
        maxParallelForks = testForks()
    }

    register<Test>("testRepositories") {
        group = "verification"
        description = "Runs repository tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("*.repository.*") }
        maxParallelForks = testForks()
    }

    register<Test>("testServices") {
        group = "verification"
        description = "Runs service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter { includeTestsMatching("*.service.*") }
        maxParallelForks = testForks()
    }

    register<Test>("testOther") {
        group = "verification"
        description = "Runs non-repository, non-service tests"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
        filter {
            excludeTestsMatching("*.repository.*")
            excludeTestsMatching("*.service.*")
        }
        maxParallelForks = testForks()
    }
}
