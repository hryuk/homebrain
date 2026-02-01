plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
}

group = "com.homebrain"
version = "0.0.1-SNAPSHOT"

val embabelVersion = "0.3.4-SNAPSHOT"

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

    // DJL (Deep Java Library) for ML inference
    implementation("ai.djl:api:0.31.0")
    implementation("ai.djl.huggingface:tokenizers:0.31.0")
    
    // ONNX Runtime engine for DJL
    implementation("ai.djl.onnxruntime:onnxruntime-engine:0.31.0")
    runtimeOnly("com.microsoft.onnxruntime:onnxruntime:1.19.2")
    
    // DuckDB for vector storage
    implementation("org.duckdb:duckdb_jdbc:1.1.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // WireMock for HTTP mocking (simulating slow API responses)
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    
    // Property-based testing
    testImplementation("io.kotest:kotest-property:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    
    // Mocking
    testImplementation("io.mockk:mockk:1.13.13")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
