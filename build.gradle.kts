plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    java
}

group = "de.chojo"
version = "1.4.0"

repositories {
    mavenCentral()
    maven("https://eldonexus.de/repository/maven-public")
    maven("https://eldonexus.de/repository/maven-proxies")
}

dependencies {
    //discord
    implementation("de.chojo", "cjda-util", "2.8.5+beta.5") {
        exclude(group = "club.minnced", module = "opus-java")
    }

    // database
    implementation("org.postgresql", "postgresql", "42.6.0")
    implementation("de.chojo.sadu", "sadu-queries", "1.3.0")
    implementation("de.chojo.sadu", "sadu-updater", "1.3.0")
    implementation("de.chojo.sadu", "sadu-postgresql", "1.3.0")
    implementation("de.chojo.sadu", "sadu-datasource", "1.3.0")

    // Download api
    implementation("de.chojo", "nexus-api-wrapper", "1.0.5")

    // Mailing
    implementation("org.eclipse.angus", "angus-mail", "2.0.1")
    implementation("org.jsoup", "jsoup", "1.16.1")


    // Logging
    implementation("org.slf4j", "slf4j-api", "2.0.7")
    implementation("org.apache.logging.log4j", "log4j-core", "2.20.0")
    implementation("org.apache.logging.log4j", "log4j-slf4j2-impl", "2.20.0")
    implementation("de.chojo", "log-util", "1.0.1") {
        exclude("org.apache.logging.log4j")
    }

    // unit testing
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter", "junit-jupiter")
    testImplementation("org.mockito", "mockito-core", "3.+")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(18))
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
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "de.chojo.lyna.Lyna"))
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
