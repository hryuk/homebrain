# Testing Guide

## Overview

Homebrain follows **Test-Driven Development (TDD)** practices. Always write tests before implementing features.

## Test Framework Stack

### Dependencies

```kotlin
// build.gradle.kts
testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.wiremock:wiremock-standalone:3.10.0")
testImplementation("io.kotest:kotest-property:5.9.1")      // Property-based testing
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.mockk:mockk:1.13.13")              // Kotlin mocking
```

### Test Runner

JUnit 5 (Jupiter) with Gradle:

```bash
cd agent
gradle test --no-daemon
```

## Current Test Coverage

| Layer | Files | Tests | Types |
|-------|-------|-------|-------|
| **Domain** | 7 | ~120 | Unit + Property-based |
| **Application** | 1 | ~40 | Unit + Mockk + Property-based |
| **API Mappers** | 3 | ~30 | Unit |
| **API Controllers** | 3 | ~20 | Integration (MockMvc) |
| **Infrastructure** | 1 | ~12 | Integration (WireMock) |
| **TOTAL** | **16** | **222** | Mixed |

## Test Organization

```
agent/src/test/kotlin/com/homebrain/agent/
├── domain/
│   ├── automation/
│   │   ├── AutomationIdTest.kt       # Value object validation
│   │   ├── AutomationCodeTest.kt     # Code analysis methods
│   │   └── AutomationTest.kt         # Aggregate behavior
│   ├── topic/
│   │   ├── TopicPathTest.kt          # MQTT wildcard matching (Heavy PBT)
│   │   └── TopicTest.kt              # Entity methods
│   ├── conversation/
│   │   └── MessageTest.kt            # Role parsing & validation
│   └── commit/
│       └── CommitTest.kt             # Git commit value object
├── application/
│   └── AutomationUseCaseTest.kt      # Use case orchestration + PBT
├── api/
│   ├── mapper/
│   │   ├── AutomationMapperTest.kt   # Domain ↔ DTO mapping
│   │   ├── TopicMapperTest.kt
│   │   └── ChatMapperTest.kt
│   └── rest/
│       ├── AutomationControllerTest.kt  # REST endpoints
│       ├── ChatControllerTest.kt
│       └── TopicsControllerTest.kt
└── infrastructure/
    └── engine/
        └── EngineClientTest.kt       # HTTP client with WireMock
```

## When to Use Each Test Type

| Test Type | Use For | Example | Framework |
|-----------|---------|---------|-----------|
| **Unit** | Pure functions, simple logic | `AutomationId` validation | JUnit 5 |
| **Property-based** | Invariants, algorithms | `TopicPath.matches()`, `sanitizeFilename()` | Kotest Property |
| **Integration (MockMvc)** | REST endpoints | `AutomationController` | Spring Test + MockMvc |
| **Integration (WireMock)** | HTTP clients | `EngineClient` | WireMock |
| **Integration (Mockk)** | Use cases with dependencies | `AutomationUseCase` | Mockk |

## TDD Workflow

### 1. Domain Layer - Write Tests First

Domain tests are pure unit tests with no Spring context. They test business logic in isolation.

**Value Objects with Validation:**

```kotlin
package com.homebrain.agent.domain.automation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AutomationIdTest {

    @Nested
    inner class Validation {
        @Test
        fun `should reject blank id`() {
            val exception = assertThrows<IllegalArgumentException> {
                AutomationId("")
            }
            assertEquals("Automation ID cannot be blank", exception.message)
        }

        @Test
        fun `should reject id containing forward slash`() {
            assertThrows<IllegalArgumentException> {
                AutomationId("path/to/automation")
            }
        }
    }

    @Nested
    inner class PropertyBasedTests {
        private val validIdArb = Arb.string(1..50)
            .filter { it.isNotBlank() }
            .filter { !it.contains("/") }
            .filter { !it.contains("\\") }
            .filter { !it.endsWith(".star") }

        @Test
        fun `valid IDs should round-trip through toFilename and fromFilename`() = runBlocking {
            forAll(validIdArb) { idValue ->
                val original = AutomationId(idValue)
                val roundTripped = AutomationId.fromFilename(original.toFilename())
                original.value == roundTripped.value
            }
        }
    }
}
```

**Key Points:**
- Use `@Nested` inner classes to organize tests logically
- Test both happy path and edge cases
- Use property-based tests for invariants that should hold for many inputs

**Entities with Behavior:**

```kotlin
class TopicTest {
    @Test
    fun `should extract device name from standard topic`() {
        val topic = Topic.fromPath("zigbee2mqtt/living_room_light/state")
        assertEquals("living_room_light", topic.deviceName())
    }

    @Test
    fun `should return null for single segment topic`() {
        val topic = Topic.fromPath("status")
        assertNull(topic.deviceName())
    }
}
```

### 2. Application Layer - Write Tests First

Application layer tests use Mockk to mock dependencies (repositories, external services).

```kotlin
package com.homebrain.agent.application

import com.homebrain.agent.domain.automation.*
import com.homebrain.agent.domain.commit.Commit
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

class AutomationUseCaseTest {

    private lateinit var automationRepository: AutomationRepository
    private lateinit var useCase: AutomationUseCase

    private val sampleCommit = Commit(
        hash = "abc123def",
        message = "Test commit",
        author = "homebrain",
        timestamp = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        automationRepository = mockk()
        useCase = AutomationUseCase(automationRepository, ...)
    }

    @Nested
    inner class Create {
        @Test
        fun `should create automation with sanitized filename`() {
            val code = "def on_message(t, p, ctx): pass"
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create(code, "My Cool Automation!")
            
            assertEquals("my_cool_automation", result.automation.id.value)
            assertTrue(result.isNew)
            verify { automationRepository.save(any()) }
        }

        @Test
        fun `should use default filename when null`() {
            every { automationRepository.save(any()) } returns sampleCommit
            
            val result = useCase.create("code", null)
            
            assertEquals("automation", result.automation.id.value)
        }
    }

    @Nested
    inner class SanitizeFilenamePropertyTests {
        @Test
        fun `sanitized filename should never contain invalid characters`() = runBlocking {
            every { automationRepository.save(any()) } returns sampleCommit
            
            forAll(Arb.string(0..100)) { input ->
                val result = useCase.create("code", input)
                val sanitized = result.automation.id.value
                sanitized.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
            }
        }
    }
}
```

**Key Points:**
- Use `mockk()` to create mocks
- Use `every { ... } returns ...` to stub method calls
- Use `verify { ... }` to verify interactions
- Property-based tests are excellent for sanitization/validation logic

### 3. API Layer - Write Tests First

#### Mappers (Unit Tests)

Mappers convert between domain models and DTOs. Test all mapping scenarios.

```kotlin
package com.homebrain.agent.api.mapper

import com.homebrain.agent.domain.automation.*
import com.homebrain.agent.domain.commit.Commit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class AutomationMapperTest {

    private lateinit var mapper: AutomationMapper

    @BeforeEach
    fun setUp() {
        mapper = AutomationMapper()
    }

    @Test
    fun `should map automation to code response`() {
        val automation = Automation(
            id = AutomationId("light_controller"),
            code = AutomationCode("def on_message(t, p, ctx): pass")
        )

        val result = mapper.toCodeResponse(automation)

        assertEquals("light_controller", result.id)
        assertEquals("def on_message(t, p, ctx): pass", result.code)
    }

    @Test
    fun `should map automation to response with commit`() {
        val commit = Commit("abc123", "Add automation", "user", Instant.now())
        val automation = Automation(AutomationId("test"), code, commit)

        val result = mapper.toResponse(automation, "created", commit)

        assertEquals("created", result.status)
        assertEquals("test.star", result.filename)
        assertEquals("abc123", result.commit)
    }
}
```

#### Controllers (Integration Tests with MockMvc)

Controller tests use standalone MockMvc (not full Spring context for speed).

```kotlin
package com.homebrain.agent.api.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.homebrain.agent.api.dto.AutomationRequest
import com.homebrain.agent.api.mapper.AutomationMapper
import com.homebrain.agent.application.*
import com.homebrain.agent.domain.automation.*
import com.homebrain.agent.exception.HomebrainExceptionHandler
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AutomationControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var automationUseCase: AutomationUseCase
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        automationUseCase = mockk()
        val controller = AutomationController(automationUseCase, AutomationMapper())
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HomebrainExceptionHandler())
            .build()
    }

    @Test
    fun `should create automation and return response`() {
        val request = AutomationRequest(filename = "test", code = "code")
        val result = AutomationResult(automation, commit, isNew = true)

        every { automationUseCase.create("code", "test") } returns result

        mockMvc.perform(
            post("/api/automations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("deployed"))
            .andExpect(jsonPath("$.filename").value("test.star"))
    }

    @Test
    fun `should return 404 when automation not found`() {
        every { automationUseCase.getById("missing") } throws 
            AutomationNotFoundException("missing")

        mockMvc.perform(get("/api/automations/missing"))
            .andExpect(status().isNotFound)
    }
}
```

**Key Points:**
- Use `MockMvcBuilders.standaloneSetup()` for faster tests (no Spring context)
- Add `HomebrainExceptionHandler` to test error responses
- Use Jackson `ObjectMapper` to serialize request bodies
- Test both success and error scenarios

### 4. Infrastructure Layer - Write Tests First

Infrastructure tests verify external integrations (HTTP clients, databases, etc.).

**HTTP Client with WireMock:**

```kotlin
package com.homebrain.agent.infrastructure.engine

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.*

class EngineClientTest {

    companion object {
        private lateinit var wireMockServer: WireMockServer

        @JvmStatic
        @BeforeAll
        fun startWireMock() {
            wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMockServer.stop()
        }
    }

    private lateinit var engineClient: EngineClient

    @BeforeEach
    fun setUp() {
        wireMockServer.resetAll()
        engineClient = EngineClient("http://localhost:${wireMockServer.port()}")
    }

    @Test
    fun `should return topics from engine`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/topics"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""["topic1", "topic2"]""")
                )
        )

        val topics = engineClient.getTopics()

        assertEquals(2, topics.size)
        assertEquals("topic1", topics[0])
    }

    @Test
    fun `should return empty list on connection error`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/topics"))
                .willReturn(aResponse().withStatus(500))
        )

        val topics = engineClient.getTopics()

        assertTrue(topics.isEmpty())
    }
}
```

## Property-Based Testing with Kotest

Property-based testing verifies that invariants hold across many randomly generated inputs.

### When to Use Property-Based Tests

- **Validation logic** - Ensure invalid inputs are always rejected
- **Transformations** - Verify roundtrip properties (encode/decode, serialize/deserialize)
- **Algorithms** - Test complex logic like MQTT wildcard matching
- **Sanitization** - Ensure output never contains invalid characters

### Example: MQTT Topic Matching

```kotlin
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll
import kotlinx.coroutines.runBlocking

class TopicPathTest {

    private val segmentArb = Arb.string(1..20)
        .filter { it.isNotBlank() }
        .filter { !it.contains("/") }
        .filter { !it.contains("+") }
        .filter { !it.contains("#") }

    private val nonWildcardPathArb = Arb.list(segmentArb, 1..5)
        .map { segments -> TopicPath(segments.joinToString("/")) }

    @Test
    fun `exact match - topic should always match itself`() = runBlocking {
        forAll(nonWildcardPathArb) { topic ->
            topic.matches(topic)
        }
    }

    @Test
    fun `hash wildcard at end matches any suffix`() = runBlocking {
        forAll(Arb.list(segmentArb, 1..3), Arb.list(segmentArb, 1..3)) { prefix, suffix ->
            if (prefix.isNotEmpty() && suffix.isNotEmpty()) {
                val fullTopic = TopicPath((prefix + suffix).joinToString("/"))
                val pattern = TopicPath(prefix.joinToString("/") + "/#")
                fullTopic.matches(pattern)
            } else {
                true // Skip edge cases
            }
        }
    }

    @Test
    fun `plus matches exactly one segment`() = runBlocking {
        forAll(segmentArb, segmentArb, segmentArb) { seg1, seg2, seg3 ->
            val topic = TopicPath("$seg1/$seg2/$seg3")
            val pattern = TopicPath("$seg1/+/$seg3")
            topic.matches(pattern)
        }
    }
}
```

### Example: Filename Sanitization

```kotlin
@Test
fun `sanitized filename should never contain invalid characters`() = runBlocking {
    every { automationRepository.save(any()) } returns sampleCommit
    
    forAll(Arb.string(0..100)) { input ->
        val result = useCase.create("code", input)
        val sanitized = result.automation.id.value
        
        // Invariant: only lowercase letters, numbers, underscores
        sanitized.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
    }
}

@Test
fun `sanitized filename should never have consecutive underscores`() = runBlocking {
    every { automationRepository.save(any()) } returns sampleCommit
    
    forAll(Arb.string(0..100)) { input ->
        val result = useCase.create("code", input)
        !result.automation.id.value.contains("__")
    }
}
```

### Creating Custom Arbitraries

```kotlin
// Arbitrary for valid automation IDs
private val validIdArb = Arb.string(1..50)
    .filter { it.isNotBlank() }
    .filter { !it.contains("/") }
    .filter { !it.contains("\\") }
    .filter { !it.endsWith(".star") }

// Arbitrary for MQTT topic segments
private val topicSegmentArb = Arb.string(1..20)
    .filter { it.isNotBlank() }
    .filter { !it.contains("/") }

// Arbitrary for alphanumeric strings
private val alphanumericArb = Arb.string(1..50)
    .map { it.filter { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' } }
    .filter { it.isNotEmpty() }
```

## Running Tests

```bash
# Run all tests
gradle test --no-daemon

# Run specific test class
gradle test --tests "AutomationIdTest"

# Run tests matching pattern
gradle test --tests "*Controller*"

# Run with verbose output
gradle test --info

# Run and generate HTML report
gradle test
# Open: build/reports/tests/test/index.html
```

## Test Best Practices

### 1. Test Organization

- Use `@Nested` inner classes to group related tests
- Name test methods descriptively: `` `should do X when Y` ``
- One assertion per test when possible

### 2. Test Independence

- Each test should be independent (no shared state)
- Use `@BeforeEach` to reset mocks and state
- Don't rely on test execution order

### 3. Readable Assertions

```kotlin
// Good: Clear what's being tested
assertEquals("my_automation", result.automation.id.value)

// Bad: Unclear assertion
assertTrue(result.automation.id.value == "my_automation")
```

### 4. Test Data

- Create factory methods for common test objects
- Use meaningful test data (not "foo", "bar")
- Keep test data close to the test

```kotlin
private val sampleCommit = Commit(
    hash = "abc123def",
    message = "Test commit",
    author = "homebrain",
    timestamp = Instant.parse("2024-01-15T10:00:00Z")
)
```

### 5. Property-Based Test Tips

- Start with simple properties (identity, commutativity)
- Use `.filter()` to constrain generated values
- Property tests should be fast (avoid expensive operations)
- Combine with example-based tests for specific cases

### 6. Mocking Best Practices

```kotlin
// Good: Explicit setup
every { repository.save(any()) } returns commit

// Bad: Loose mocking
every { repository.save(any()) } returns mockk()
```

## Adding Tests for New Features

### Domain Entity Example

1. **Write tests first:**
   ```kotlin
   class DeviceTest {
       @Test
       fun `should create device with valid ID`() {
           val device = Device(DeviceId("sensor_123"), "Temperature Sensor")
           assertEquals("sensor_123", device.id.value)
       }
       
       @Test
       fun `should reject blank device ID`() {
           assertThrows<IllegalArgumentException> {
               DeviceId("")
           }
       }
   }
   ```

2. **Implement the domain model:**
   ```kotlin
   @JvmInline
   value class DeviceId(val value: String) {
       init {
           require(value.isNotBlank()) { "Device ID cannot be blank" }
       }
   }
   
   data class Device(val id: DeviceId, val name: String)
   ```

3. **Run tests to verify:**
   ```bash
   gradle test --tests "DeviceTest"
   ```

### API Endpoint Example

1. **Write controller test first:**
   ```kotlin
   @Test
   fun `should return device list`() {
       every { deviceUseCase.listAll() } returns listOf(device1, device2)
       
       mockMvc.perform(get("/api/devices"))
           .andExpect(status().isOk)
           .andExpect(jsonPath("$[0].id").value("device1"))
   }
   ```

2. **Implement controller:**
   ```kotlin
   @GetMapping("/devices")
   fun listDevices() = deviceUseCase.listAll()
   ```

3. **Run tests:**
   ```bash
   gradle test --tests "DeviceControllerTest"
   ```

## Continuous Integration

Tests run automatically on every commit via GitHub Actions (if configured).

```yaml
# .github/workflows/test.yml
- name: Run tests
  run: cd agent && gradle test --no-daemon
```

## Troubleshooting

### Common Issues

**Tests fail with "lateinit property not initialized":**
- Ensure `@BeforeEach` is called
- Check mock setup happens before test execution

**Property tests fail intermittently:**
- Generated values might hit edge cases
- Add `.filter()` to constrain inputs
- Use `.checkAll()` instead of `.forAll()` for debugging

**MockMvc returns 404:**
- Check controller path in `@RequestMapping`
- Verify standalone setup includes the controller
- Check HTTP method (GET vs POST)

**WireMock stub not matched:**
- Use `.urlEqualTo()` for exact match
- Check request headers match stub
- Enable WireMock request logging for debugging

## Further Reading

- [Kotest Property Testing](https://kotest.io/docs/proptest/property-based-testing.html)
- [MockK Documentation](https://mockk.io/)
- [Spring MockMvc Guide](https://spring.io/guides/gs/testing-web/)
- [WireMock Documentation](http://wiremock.org/docs/)
