# CLAUDE.md - Project Guide for AI Assistants

## Documentation First

**CRITICAL:** Always update documentation after making changes to the codebase.

### Documentation Update Checklist

After any code change, update relevant documentation:

- [ ] **CLAUDE.md** - If architecture, workflows, or development practices change
- [ ] **README.md** - If features, quick start, or configuration changes
- [ ] **docs/architecture.md** - If system design or component interactions change
- [ ] **docs/development.md** - If project structure, setup steps, or dependencies change
- [ ] **docs/testing.md** - If test patterns, examples, or guidelines change
- [ ] **docs/automations.md** - If Starlark API or ctx functions change

### When to Update Documentation

| Change Type | Update |
|-------------|--------|
| New domain entity/value object | `CLAUDE.md` (TDD section), `docs/architecture.md` |
| New API endpoint | `CLAUDE.md` (API endpoints), `docs/architecture.md` |
| New use case | `CLAUDE.md` (application layer), `docs/architecture.md` |
| New test pattern | `docs/testing.md` |
| New ctx function for automations | `docs/automations.md`, `CLAUDE.md` |
| New LLM tool | `CLAUDE.md` (Embabel section) |
| Dependency change | `docs/development.md` |
| Architecture refactoring | `CLAUDE.md`, `docs/architecture.md` |

**Remember:** Good documentation is as important as good code. Future you (and other developers) will thank you.

---

## Project Overview

**Homebrain** is an AI-powered MQTT automation framework. Users describe automations in natural language, an LLM generates Starlark code with access to reusable library functions and shared global state. The system is a composable framework where automations can leverage shared functionality and coordinate through global state.

## Documentation

| Document | Purpose |
|----------|---------|
| [README.md](../README.md) | Quick start, features, example automation |
| [docs/architecture.md](docs/architecture.md) | System design, DDD layers, data flow |
| [docs/development.md](docs/development.md) | Local setup, project structure, dependencies |
| [docs/testing.md](docs/testing.md) | TDD workflow, property-based testing, examples |
| [docs/automations.md](docs/automations.md) | Starlark syntax, ctx functions, examples |

## Architecture

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Web UI    │────►│   Agent API     │────►│ Automation      │
│  (SolidJS)  │     │ (Kotlin/Embabel)│     │ Engine (Go)     │
│   :5173     │     │   :8080         │     │   :9000         │
└─────────────┘     └───────┬─────────┘     └────────┬────────┘
                            │                        │
                     ┌──────▼────────────────────────▼──────┐
                     │  automations/*.star (Git volume)     │
                     │  automations/lib/*.lib.star          │
                     └──────────────────────────────────────┘
```

**Data flow:** User → Web UI → Agent (LLM with tools generates code) → writes .star file → Engine detects change → hot-reloads → subscribes to MQTT topics

**Framework features:**
- **Library modules**: Reusable functions in `lib/*.lib.star` accessible via `ctx.lib.module.function()`
- **Global state**: Shared state across automations with "read-all, write-own" access control
- **Agent intelligence**: LLM sees existing libraries and suggests reuse

## Tech Stack

| Component | Technology | Location |
|-----------|------------|----------|
| Web UI | SolidJS + Vite + TypeScript | `/web` |
| Agent API | Kotlin + Spring Boot + Embabel | `/agent` |
| Automation Engine | Go 1.23 + Starlark | `/engine` |
| State Storage | BoltDB (embedded) | Engine container |
| Vector Storage | DuckDB + VSS (embedded) | Agent container |
| Code Embeddings | CodeRankEmbed-137M (ONNX) | Agent container |
| Container Orchestration | Docker Compose | `/docker-compose.yml` |

## Key Files

### Engine (`/engine`)
- `main.go` - Entry point, HTTP API for internal use
- `internal/mqtt/client.go` - MQTT client with auto-reconnect
- `internal/runner/starlark.go` - Loads and manages automations
- `internal/runner/library.go` - Library module loader and manager
- `internal/runner/context.go` - `ctx.*` functions exposed to Starlark scripts
- `internal/runner/validation.go` - Starlark code validation without deploying
- `internal/watcher/watcher.go` - File watcher for hot-reload (includes lib/ watching)
- `internal/state/state.go` - BoltDB persistence for per-automation and global state

### Agent (`/agent`) - Kotlin/Spring Boot/Embabel (DDD Architecture)
- `build.gradle.kts` - Gradle build with Embabel dependencies
- `src/main/kotlin/com/homebrain/agent/`
  - `AgentApplication.kt` - Spring Boot entry point
  - `domain/` - Pure domain models (no framework dependencies)
    - `automation/` - Automation aggregate
      - `Automation.kt` - Aggregate root
      - `AutomationId.kt`, `AutomationCode.kt` - Value objects
      - `AutomationRepository.kt` - Repository port (interface)
    - `topic/` - Topic entity
      - `Topic.kt` - Entity, `TopicPath.kt` - Value object
      - `TopicRepository.kt` - Repository port (interface)
    - `library/` - Library module domain
      - `LibraryModule.kt` - Module entity
      - `GlobalStateSchema.kt` - State ownership tracking
    - `conversation/` - Chat domain
      - `ChatResponse.kt`, `CodeProposal.kt`, `FileProposal.kt`, `Message.kt`
    - `planning/` - GOAP planning domain (NEW)
      - `UserInput.kt` - Entry point for GOAP planning
      - `ParsedIntent.kt` - Intent classification result
      - `AutomationRequirements.kt` - Extracted requirements
      - `GatheredContext.kt` - Topics and similar code context
      - `GeneratedCode.kt` - Code with attempt tracking
      - `ExtractedCode.kt` - Code after library extraction (forces GOAP sequencing)
      - `ValidatedCode.kt` - Code after validation (forces GOAP sequencing)
      - `ValidationFailure.kt` - Validation errors for GOAP conditions
      - `ConversationalAnswer.kt` - For question/chat responses
      - `AutomationResponse.kt` - Final goal output
    - `validation/` - Code validation domain
      - `ValidationResult.kt` - Validation result value object
    - `commit/` - Git commit value object
    - `embedding/` - Code embedding domain
      - `Embedding.kt` - Value object for embedding vectors (768-dim)
      - `IndexedCode.kt` - Entity for indexed code
      - `CodeSearchResult.kt` - Search result value object
      - `CodeType.kt` - Enum: AUTOMATION, LIBRARY
      - `EmbeddingRepository.kt` - Repository port (interface)
  - `application/` - Use cases (orchestration layer)
    - `AutomationUseCase.kt` - CRUD operations for automations
    - `ChatUseCase.kt` - Chat conversation handling
    - `TopicUseCase.kt` - Topic discovery
    - `LogUseCase.kt` - Log retrieval
    - `LibraryUseCase.kt` - Library module operations (list, get, delete)
    - `GlobalStateUseCase.kt` - Global state retrieval
    - `CodeEmbeddingService.kt` - Code embedding and semantic search (async updates on CRUD)
    - `EmbeddingSyncScheduler.kt` - Startup and periodic embedding sync (fallback)
  - `infrastructure/` - External adapters
    - `persistence/` - Repository implementations
      - `GitAutomationRepository.kt` - Git-based automation storage
      - `EngineTopicRepository.kt` - Engine-based topic discovery
      - `GitOperations.kt` - Low-level git operations
    - `engine/` - Engine HTTP client
      - `EngineClient.kt` - REST client for Go engine
    - `ai/` - LLM integration
      - `HomebrainAgent.kt` - GOAP-based smart home automation agent
      - `MqttLlmTools.kt` - LLM-callable tools
      - `PromptLoader.kt` - Loads prompts from Markdown files
    - `websocket/` - Real-time communication
      - `LogsWebSocketHandler.kt` - Log streaming
      - `MqttWebSocketHandler.kt` - MQTT message streaming
    - `embedding/` - Code embeddings
      - `CodeRankEmbedClient.kt` - DJL + ONNX embedding model
      - `DuckDBVectorStore.kt` - DuckDB vector storage
  - `api/` - Inbound adapters (HTTP layer)
    - `rest/` - REST controllers
      - `ChatController.kt`, `AutomationController.kt`, `LibraryController.kt`, `GlobalStateController.kt`, etc.
    - `dto/` - Request/response DTOs
      - `ChatDto.kt`, `AutomationDto.kt`, `TopicDto.kt`, `LibraryDto.kt`, `GlobalStateDto.kt`
    - `mapper/` - Domain to DTO mappers
      - `AutomationMapper.kt`, `LibraryMapper.kt`, `GlobalStateMapper.kt`, etc.
  - `config/` - Spring configuration
  - `exception/` - Error handling

### Web UI (`/web`)
- `src/App.tsx` - Main app with tab navigation
- `src/components/Chat.tsx` - Chat interface for creating automations
- `src/components/CodePreview.tsx` - Code display with syntax highlighting
- `src/components/AutomationList.tsx` - Automation management
- `src/components/LibraryViewer.tsx` - Library module browser with source code viewer
- `src/components/GlobalStateViewer.tsx` - Global state viewer with ownership info
- `src/components/MqttViewer.tsx` - Real-time MQTT message visualizer
- `src/components/LogViewer.tsx` - Real-time log viewer

## Commands

```bash
# Run the full stack
docker compose up --build

# Run in detached mode
docker compose up -d --build

# View logs
docker compose logs -f [engine|agent|web]

# Rebuild specific service
docker compose build agent

# Local development (without Docker)
cd engine && go run .
cd agent && ./gradlew bootRun
cd web && npm run dev
```

## API Endpoints

### Agent API (`:8080`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Conversational AI chat with tool support |
| GET | `/api/automations` | List all automations |
| POST | `/api/automations` | Deploy new automation (single file) |
| POST | `/api/automations/deploy` | Deploy multiple files (library + automation) |
| GET | `/api/automations/{id}` | Get automation code |
| PUT | `/api/automations/{id}` | Update automation |
| DELETE | `/api/automations/{id}` | Delete automation |
| GET | `/api/topics` | List discovered MQTT topics |
| GET | `/api/libraries` | List library modules with functions |
| GET | `/api/libraries/{name}` | Get library module source code |
| DELETE | `/api/libraries/{name}` | Delete library module |
| GET | `/api/global-state` | Get global state values with ownership |
| GET | `/api/logs` | Get recent logs |
| GET | `/api/mqtt/messages` | Get recent MQTT messages |
| GET | `/api/history` | Git commit history |
| GET | `/health` | Health check (returns "ok") |
| WS | `/ws/logs` | Real-time log stream |
| WS | `/ws/mqtt` | Real-time MQTT message stream |

### Engine API (`:9000`, internal)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/automations` | List running automations |
| GET | `/topics` | Discovered MQTT topics |
| GET | `/messages` | Recent MQTT messages for visualization |
| GET | `/logs` | Recent automation logs |
| GET | `/library` | List library modules with functions |
| GET | `/library/{name}` | Get module source code |
| GET | `/global-state` | Get global state schema (keys and which automations own them) |
| GET | `/global-state-schema` | Get current global state values |
| POST | `/validate` | Validate Starlark code without deploying |

## Starlark Automation Format

### Regular Automation

```python
def on_message(topic, payload, ctx):
    """Called when subscribed MQTT topics receive messages."""
    data = ctx.json_decode(payload)
    
    # Use library functions
    if ctx.lib.timers.debounce_check(ctx, "motion_hallway", 300):
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({"state": "ON"}))
        # Update global state
        ctx.set_global("presence.hallway.last_motion", ctx.now())

def on_schedule(ctx):
    """Optional: Called on cron schedule."""
    pass

config = {
    "name": "Automation Name",
    "description": "What it does",
    "subscribe": ["mqtt/topic/+"],         # MQTT topics to subscribe
    "schedule": "* * * * *",               # Optional cron expression
    "global_state_writes": ["presence.*"], # Keys this automation can write (NEW)
    "enabled": True,
}
```

### Library Module Format

Library modules (`.lib.star` files in `automations/lib/`) contain pure functions:

```python
"""Module description for documentation."""

def my_helper_function(ctx, param1, param2):
    """Function docstring.
    
    Args:
        ctx: Automation context
        param1: Description
        param2: Description
        
    Returns:
        Description
        
    Example:
        result = ctx.lib.mymodule.my_helper_function(ctx, "a", "b")
    """
    # Implementation
    return result
```

### Available `ctx` Functions

**MQTT & Logging:**
- `ctx.publish(topic, payload)` - Publish MQTT message
- `ctx.log(message)` - Log message (visible in UI)

**JSON Handling:**
- `ctx.json_encode(value)` - Convert dict/list to JSON string
- `ctx.json_decode(string)` - Parse JSON string to dict/list

**Per-Automation State:**
- `ctx.get_state(key)` - Get automation's persistent state
- `ctx.set_state(key, value)` - Set automation's persistent state
- `ctx.clear_state(key)` - Clear automation's persistent state

**Global State (NEW):**
- `ctx.get_global(key)` - Read any global state (no restrictions)
- `ctx.set_global(key, value)` - Write to declared keys only
- `ctx.clear_global(key)` - Clear declared keys

**Library Functions (NEW):**
- `ctx.lib.modulename.function(...)` - Call library functions
- Example: `ctx.lib.timers.debounce_check(ctx, "key", 300)`
- Example: `ctx.lib.utils.safe_get(data, "key", default)`
- Example: `ctx.lib.presence.update_room_occupancy(ctx, "bedroom", True)`

**Device State Library (`ctx.lib.devices.*`):**
- `ctx.lib.devices.extract_device_name(topic, position)` - Extract device name from MQTT topic
- `ctx.lib.devices.sync_state(ctx, device_name, payload)` - Sync device state to global state
- `ctx.lib.devices.get_property(ctx, device_name, property, default)` - Get device property from global state
- `ctx.lib.devices.is_on(ctx, device_name)` - Check if device state is "ON"
- `ctx.lib.devices.is_off(ctx, device_name)` - Check if device state is "OFF"
- `ctx.lib.devices.get_last_updated(ctx, device_name)` - Get last sync timestamp

**Utilities:**
- `ctx.now()` - Current Unix timestamp

## Environment Variables

```bash
# Required
MQTT_BROKER=tcp://192.168.1.100:1883
ANTHROPIC_API_KEY=sk-ant-...

# Optional
MQTT_USERNAME=
MQTT_PASSWORD=
LOG_LEVEL=info
ENGINE_URL=http://engine:9000      # For agent
AUTOMATIONS_PATH=/app/automations  # For agent
```

**Note:** The default LLM model is `claude-sonnet-4-5` (configured in `application.yml`).

## Embabel Agent Framework

The Agent uses [Embabel](https://github.com/embabel/embabel-agent) for AI capabilities.

### Creating Agents

```kotlin
@Component
@Agent(description = "Description for the LLM")
class MyAgent {
    @Action(description = "What this action does")
    fun doSomething(input: MyInput, ai: Ai): MyOutput {
        return ai.withDefaultLlm()
            .withToolObject(myTools)
            .createObject("prompt", MyOutput::class.java)
    }
}
```

### Adding LLM Tools

```kotlin
@Component
class MyTools(private val service: SomeService) {
    @LlmTool(description = "What this tool does")
    fun getThing(
        @LlmTool.Param(description = "Parameter description")
        id: String
    ): Thing = service.findById(id)
}
```

**Current LLM Tools** (in `MqttLlmTools.kt`):
- `searchSimilarCode(query, topK)` - **NEW** Semantic search for similar automations/libraries (uses CodeRankEmbed)
- `getAllTopics()` - Get all MQTT topics
- `searchTopics(pattern)` - Search topics by keyword
- `getAutomations()` - List existing automations
- `getLibraryModules()` - List available library modules with functions
- `getLibraryCode(moduleName)` - Get source code for a library module
- `getGlobalStateSchema()` - See which automations write which global state keys

### Library-First Code Generation

The agent enforces library-first code generation through two mechanisms:

1. **System prompt guidance** (`chat-system-prompt.md`) - Instructs the LLM to create library functions for reusable patterns
2. **Automatic extraction** (`extractToLibrary` action) - Analyzes generated code and extracts reusable logic to libraries

**Patterns that get extracted to libraries:**
- **Generic device operations** (blink, fade, pulse, toggle sequences)
- **State management patterns** (history tracking, aggregation, state machines)
- **Multi-device coordination** (scenes, sequences, group operations)
- **Inlined reusable logic** (loops, device iterations, even if not in separate functions)

The extraction action uses `getLibraryModules()` and `getLibraryCode()` tools to check existing libraries and extends them rather than creating duplicates.

**Code Proposal Format:**
```json
{
  "summary": "Blink kitchen light with new library function",
  "files": [
    {"code": "...", "filename": "lib/lights.lib.star", "type": "library"},
    {"code": "...", "filename": "blink_kitchen.star", "type": "automation"}
  ]
}
```

### Device State Synchronization

The LLM will create "state sync" automations when a user's request requires checking the state of devices that are NOT the trigger. This uses the `devices.lib.star` library.

**When to use:**
- Automation reacts to device A, but needs to check device B's state
- Example: "Turn on hallway light when motion detected, but only if living room light is on"

**Pattern:**
1. **State sync automation** - Subscribes to the device whose state needs to be checked, syncs to global state
2. **Reactive automation** - Subscribes to the trigger, reads other device state from global state

**Example sync automation:**
```python
def on_message(topic, payload, ctx):
    device_name = ctx.lib.devices.extract_device_name(topic, 1)
    ctx.lib.devices.sync_state(ctx, device_name, ctx.json_decode(payload))

config = {
    "name": "Sync Living Room Light",
    "subscribe": ["zigbee2mqtt/living_room_light"],
    "global_state_writes": ["devices.*"],
    "enabled": True,
}
```

**Example consumer automation:**
```python
def on_message(topic, payload, ctx):
    if ctx.lib.devices.is_on(ctx, "living_room_light"):
        ctx.publish("zigbee2mqtt/hallway_light/set", ctx.json_encode({"state": "ON"}))

config = {
    "name": "Hallway Light on Motion",
    "subscribe": ["zigbee2mqtt/hallway_motion"],
    "enabled": True,
}
```

### GOAP-Based Code Generation and Validation

The agent uses Embabel's Goal Oriented Action Planning (GOAP) for intelligent automation. Instead of hardcoded orchestration, actions declare their preconditions and effects, and the GOAP planner automatically sequences them.

**GOAP Actions in `HomebrainAgent`:**

| Action | Description | Input | Output |
|--------|-------------|-------|--------|
| `parseIntent` | Classify user message | UserInput | ParsedIntent |
| `extractRequirements` | Extract automation details | ParsedIntent (AUTOMATION_REQUEST) | AutomationRequirements |
| `gatherContext` | Gather topics + similar code | AutomationRequirements | GatheredContext |
| `generateCode` | Generate Starlark code | AutomationRequirements, GatheredContext | GeneratedCode |
| `extractToLibrary` | Extract reusable logic to library modules | GeneratedCode | ExtractedCode |
| `validateCode` | Validate against engine | ExtractedCode | ValidatedCode + CODE_IS_VALID/INVALID |
| `fixInvalidCode` | Fix validation errors | ValidatedCode + CODE_IS_INVALID | ExtractedCode (attempt++) |
| `answerQuestion` | Answer questions | ParsedIntent (QUESTION/CHAT) | ConversationalAnswer |
| `respondWithAutomation` | **Goal**: Return valid code | ValidatedCode + CODE_IS_VALID | AutomationResponse |
| `respondWithFailure` | **Goal**: Max retries hit | ValidatedCode + MAX_RETRIES_EXHAUSTED | AutomationResponse |
| `respondConversationally` | **Goal**: Answer question | ConversationalAnswer | AutomationResponse |

**Conditions:**
- `CODE_IS_VALID` - No validation failures in blackboard
- `CODE_IS_INVALID` - Validation failures exist
- `CAN_STILL_RETRY` - Attempt count < maxFixAttempts (configurable, default 3)
- `MAX_RETRIES_EXHAUSTED` - Attempt count >= maxFixAttempts

**Example GOAP Plan (automation request):**
```
UserInput → parseIntent → extractRequirements → gatherContext → generateCode 
→ extractToLibrary → validateCode [CODE_IS_INVALID] → fixInvalidCode 
→ validateCode [CODE_IS_VALID] → respondWithAutomation (GOAL)
```

**Configuration** (in `application.yml`):
```yaml
homebrain:
  agent:
    max-fix-attempts: 3
```

### Prompt Management

Prompts are stored as Markdown files in `agent/src/main/resources/prompts/` for easier editing and version control:

| File | Purpose |
|------|---------|
| `chat-system-prompt.md` | Main system prompt for the chat agent |
| `code-fixer-prompt.md` | System prompt for fixing Starlark validation errors |
| `library-extractor-prompt.md` | System prompt for extracting reusable logic to library modules |

**PromptLoader** (`infrastructure/ai/PromptLoader.kt`) provides:
- Loading prompts from classpath resources
- Caching for performance (lazy loading)
- Simple variable substitution with `{{variableName}}` syntax

```kotlin
// Load a prompt
val prompt = promptLoader.load("chat-system-prompt.md")

// Load with variable substitution
val prompt = promptLoader.load("my-prompt.md", mapOf("name" to "value"))
```

**Benefits:**
- Prompts are content, not code - easier to edit and review
- Markdown syntax highlighting in IDEs
- Cleaner diffs in version control
- No string escaping issues

## Test-Driven Development (TDD)

**IMPORTANT:** This project follows TDD practices. Always write tests before implementing features.

### Test Framework Stack

```kotlin
// build.gradle.kts dependencies
testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.wiremock:wiremock-standalone:3.10.0")
testImplementation("io.kotest:kotest-property:5.9.1")      // Property-based testing
testImplementation("io.kotest:kotest-assertions-core:5.9.1")
testImplementation("io.mockk:mockk:1.13.13")              // Kotlin mocking
```

### Running Tests

```bash
# Run all tests
cd agent && gradle test --no-daemon

# Run specific test class
gradle test --tests "AutomationIdTest"

# Run with verbose output
gradle test --info
```

### Test Coverage (222 tests across 16 files)

| Layer | Coverage | Test Types |
|-------|----------|------------|
| Domain | 100% | Unit + Property-based |
| Application | 100% | Unit + Mockk + Property-based |
| API Mappers | 100% | Unit |
| API Controllers | 100% | Integration (MockMvc) |
| Infrastructure | Partial | Integration (WireMock) |

### TDD Workflow

When adding new features, follow this workflow:

#### 1. Domain Layer (Write Tests First)

**For value objects with validation:**
```kotlin
class AutomationIdTest {
    @Test
    fun `should reject blank id`() {
        assertThrows<IllegalArgumentException> {
            AutomationId("")
        }
    }
    
    // Property-based test for invariants
    @Test
    fun `valid IDs should round-trip through toFilename`() = runBlocking {
        forAll(validIdArb) { idValue ->
            val original = AutomationId(idValue)
            val roundTripped = AutomationId.fromFilename(original.toFilename())
            original.value == roundTripped.value
        }
    }
}
```

**For entities with behavior:**
```kotlin
class TopicTest {
    @Test
    fun `should extract device name from standard topic`() {
        val topic = Topic.fromPath("zigbee2mqtt/living_room_light/state")
        assertEquals("living_room_light", topic.deviceName())
    }
}
```

#### 2. Application Layer (Write Tests First)

Use Mockk for mocking dependencies:

```kotlin
class AutomationUseCaseTest {
    private lateinit var automationRepository: AutomationRepository
    private lateinit var useCase: AutomationUseCase

    @BeforeEach
    fun setUp() {
        automationRepository = mockk()
        useCase = AutomationUseCase(automationRepository, ...)
    }

    @Test
    fun `should create automation with sanitized filename`() {
        val code = "def on_message(t, p, ctx): pass"
        every { automationRepository.save(any()) } returns sampleCommit
        
        val result = useCase.create(code, "My Cool Automation!")
        
        assertEquals("my_cool_automation", result.automation.id.value)
    }
}
```

#### 3. API Layer (Write Tests First)

Use standalone MockMvc for controller tests:

```kotlin
class AutomationControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var automationUseCase: AutomationUseCase

    @BeforeEach
    fun setUp() {
        automationUseCase = mockk()
        val controller = AutomationController(automationUseCase, mapper)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HomebrainExceptionHandler())
            .build()
    }

    @Test
    fun `should create automation and return response`() {
        every { automationUseCase.create(...) } returns result

        mockMvc.perform(
            post("/api/automations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("deployed"))
    }
}
```

#### 4. Infrastructure Layer (Write Tests First)

Use WireMock for HTTP client tests:

```kotlin
class EngineClientTest {
    private lateinit var wireMockServer: WireMockServer
    private lateinit var engineClient: EngineClient

    @BeforeEach
    fun setUp() {
        wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
        wireMockServer.start()
        engineClient = EngineClient("http://localhost:${wireMockServer.port()}")
    }

    @Test
    fun `should return topics from engine`() {
        wireMockServer.stubFor(
            get(urlEqualTo("/topics"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("""["topic1", "topic2"]""")
                )
        )

        val topics = engineClient.getTopics()
        assertEquals(2, topics.size)
    }
}
```

### Property-Based Testing

Use Kotest Property for testing invariants. Ideal for:
- Value object validation
- Algorithms with complex logic (e.g., MQTT wildcard matching)
- String sanitization
- Parsing logic

**Example:**

```kotlin
@Test
fun `sanitized filename should never contain invalid characters`() = runBlocking {
    forAll(Arb.string(0..100)) { input ->
        val sanitized = sanitizeFilename(input)
        sanitized.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }
    }
}
```

**For comprehensive testing examples and guidelines, see [docs/testing.md](docs/testing.md).**

### When to Use Each Test Type

| Test Type | Use For | Example |
|-----------|---------|---------|
| **Unit** | Pure functions, simple logic | `AutomationId` validation |
| **Property-based** | Invariants, algorithms, parsing | `TopicPath.matches()`, `sanitizeFilename()` |
| **Integration (MockMvc)** | REST endpoints | `AutomationController` |
| **Integration (WireMock)** | HTTP clients | `EngineClient` |
| **Integration (Spring)** | Full context needed | Repository tests (future) |

### Test Organization

```
agent/src/test/kotlin/com/homebrain/agent/
├── domain/
│   ├── automation/
│   │   ├── AutomationIdTest.kt      (Unit + Property-based)
│   │   ├── AutomationCodeTest.kt    (Unit + Property-based)
│   │   └── AutomationTest.kt        (Unit)
│   ├── topic/
│   │   ├── TopicPathTest.kt         (Heavy Property-based)
│   │   └── TopicTest.kt             (Unit)
│   ├── conversation/
│   │   ├── MessageTest.kt           (Unit + Property-based)
│   │   ├── FileProposalTest.kt      (Unit + Property-based)
│   │   └── CodeProposalTest.kt      (Unit + Property-based)
│   └── commit/
│       └── CommitTest.kt            (Unit)
├── application/
│   └── AutomationUseCaseTest.kt     (Unit + Mockk + Property-based)
├── api/
│   ├── mapper/
│   │   ├── AutomationMapperTest.kt  (Unit)
│   │   ├── TopicMapperTest.kt       (Unit)
│   │   └── ChatMapperTest.kt        (Unit)
│   └── rest/
│       ├── AutomationControllerTest.kt  (Integration)
│       ├── ChatControllerTest.kt        (Integration)
│       └── TopicsControllerTest.kt      (Integration)
└── infrastructure/
    ├── engine/
    │   └── EngineClientTest.kt      (Integration with WireMock)
    └── ai/
        └── PromptLoaderTest.kt      (Unit)
```

**See [docs/testing.md](docs/testing.md) for detailed test examples, best practices, and troubleshooting.**

## Common Tasks

### Adding a new LLM tool
1. **Write test first** in `MqttLlmToolsTest.kt` (if it doesn't exist, create it)
2. Add method to `/agent/src/.../infrastructure/ai/MqttLlmTools.kt` with `@LlmTool` annotation
3. Include description for the LLM to understand when to use it
4. Use `@LlmTool.Param` for parameter descriptions
5. Verify test passes

### Adding a new `ctx` function for automations
1. **Write test first** in `/engine/internal/runner/context_test.go`
2. Add method to `/engine/internal/runner/context.go`
3. Register in `ToStarlark()` method
4. Update system prompt in `/agent/src/main/resources/prompts/chat-system-prompt.md`
5. **Update documentation** in `/docs/automations.md`
6. **Update CLAUDE.md** if needed (add to ctx functions list)
7. Verify test passes

### Adding a new API endpoint
1. **Write controller test first** using MockMvc (see [docs/testing.md](docs/testing.md))
2. **Write use case test first** with Mockk (if new use case needed)
3. **Write domain tests first** (if new domain models needed)
4. Create use case in `/agent/src/.../application/` if business logic is needed
5. Create controller in `/agent/src/.../api/rest/`
6. Add DTOs in `/agent/src/.../api/dto/` and mappers in `/agent/src/.../api/mapper/`
7. **Write mapper tests** to verify transformations
8. Verify all tests pass
9. Add corresponding UI call in `/web/src/components/`
10. **Update documentation** in `CLAUDE.md` (API endpoints section) and `docs/architecture.md`

### Adding a new domain entity
1. **Write tests first** for:
   - Value object validation (use property-based tests for invariants)
   - Entity behavior
   - Factory methods
2. Create entity/value objects in `/agent/src/.../domain/<aggregate>/`
3. Define repository interface (port) in the same package
4. **Write repository tests** (integration tests with temp directories for git operations)
5. Implement repository adapter in `/agent/src/.../infrastructure/persistence/`
6. Create use case in `/agent/src/.../application/` if needed
7. **Write use case tests** with Mockk
8. Verify all tests pass
9. **Update documentation** in `CLAUDE.md` and `docs/architecture.md`

### Modifying the Web UI
- Components are in `/web/src/components/`
- Each component has its own `.css` file
- Uses SolidJS (similar to React but with fine-grained reactivity)
- Signals: `const [value, setValue] = createSignal(initial)`
- Effects: `createEffect(() => { /* runs when dependencies change */ })`

## Important Notes

1. **Docker networking:** Web proxies to `http://agent:8080` inside Docker, not `localhost`
2. **Hot reload:** Engine watches `/app/automations` for `.star` file changes
3. **No authentication:** Designed for private networks only
4. **Starlark limitations:** No `while` loops, no recursion, no imports - by design for safety
5. **MQTT only:** Automations cannot make HTTP requests (sandboxed)
6. **Git tracking:** All automations are committed to git in the shared volume
7. **Embabel version:** Currently using 0.3.4-SNAPSHOT (GOAP actions need proper `post` annotations)

## Debugging

```bash
# Check if containers are running
docker compose ps

# Engine logs (automation execution)
docker compose logs -f engine

# Agent logs (API calls, LLM responses, tool calls)
docker compose logs -f agent

# Enter container for debugging
docker compose exec engine sh
docker compose exec agent sh

# Test MQTT manually
mosquitto_pub -h <broker> -t test/topic -m "hello"
```

## File Structure

```
homebrain/
├── docker-compose.yml      # Container orchestration
├── .env.example            # Environment template
├── CLAUDE.md               # This file
├── README.md               # User documentation
├── docs/                   # Additional documentation
│   ├── architecture.md     # System design & DDD architecture
│   ├── automations.md      # Starlark automation guide
│   ├── development.md      # Local setup & project structure
│   └── testing.md          # TDD workflow & examples
├── engine/                 # Automation runtime (Go)
│   ├── Dockerfile
│   ├── go.mod
│   ├── main.go
│   └── internal/
│       ├── mqtt/
│       ├── runner/
│       ├── state/
│       └── watcher/
├── agent/                  # AI Agent service (Kotlin/Embabel/DDD)
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml
│       │   └── prompts/            # LLM prompts as Markdown
│       │       └── chat-system-prompt.md
│       └── kotlin/com/homebrain/agent/
│       ├── AgentApplication.kt
│       ├── domain/           # Pure domain (no dependencies)
│       │   ├── automation/   # Automation aggregate
│       │   ├── topic/        # Topic entity
│       │   ├── library/      # Library module domain
│       │   ├── conversation/ # Chat domain
│       │   └── commit/       # Commit value object
│       ├── application/      # Use cases
│       ├── infrastructure/   # External adapters
│       │   ├── persistence/  # Repository implementations
│       │   ├── engine/       # Engine HTTP client
│       │   ├── ai/           # LLM integration
│       │   └── websocket/    # Real-time handlers
│       ├── api/              # Inbound HTTP adapters
│       │   ├── rest/         # Controllers
│       │   ├── dto/          # DTOs
│       │   └── mapper/       # Domain-DTO mapping
│       ├── config/           # Spring configuration
│       └── exception/        # Error handling
├── web/                    # SolidJS frontend
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx
│       └── components/
└── automations/            # Generated automation scripts
    ├── *.star              # Regular automations
    └── lib/                # Library modules
        └── *.lib.star      # Reusable functions
```
