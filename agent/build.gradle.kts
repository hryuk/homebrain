plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
}

group = "com.homebrain"
version = "0.0.1-SNAPSHOT"

val embabelVersion = "0.3.2"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven {
        name = "embabel-releases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
        mavenContent { releasesOnly() }
    }
    maven {
        name = "embabel-snapshots"
        url = uri("https://repo.embabel.com/artifactory/libs-snapshot")
        mavenContent { snapshotsOnly() }
    }
    maven {
        name = "Spring Milestones"
        url = uri("https://repo.spring.io/milestone")
    }
}

dependencyManagement {
    imports {
        mavenBom("com.embabel.agent:embabel-agent-dependencies:$embabelVersion")
    }
}

dependencies {
    // Embabel Agent Framework
    implementation("com.embabel.agent:embabel-agent-starter")
    implementation("com.embabel.agent:embabel-agent-starter-anthropic")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")

    // JGit for Git operations
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
