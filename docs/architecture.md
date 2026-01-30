# Homebrain Architecture

## Overview

Homebrain is a Docker-based, composable automation framework for MQTT systems. It uses AI to generate Starlark code with access to reusable library modules and shared global state, enabling automations to coordinate and share functionality.

## System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Docker Compose Stack                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────────┐  │
│  │   Web UI     │    │   Agent API      │    │ Automation Engine │  │
│  │   (SolidJS)  │◄──►│ (Kotlin/Embabel) │◄──►│      (Go)         │  │
│  │   :5173      │    │   :8080          │    │   :9000           │  │
│  └──────────────┘    └────────┬─────────┘    └─────────┬─────────┘  │
│                               │                        │            │
│                      ┌────────▼────────────────────────▼────────┐   │
│                      │         automations/ (Git Repo)          │   │
│                      │    Mounted Volume - Shared between       │   │
│                      │    Agent and Engine                      │   │
│                      └──────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
                              ┌─────────────────┐
                              │  External MQTT  │
                              │     Broker      │
                              └─────────────────┘
```

## Framework Features

### Library Modules

**Purpose:** Reusable function libraries shared across all automations

**Location:** `automations/lib/*.lib.star`

**Access:** `ctx.lib.modulename.function()`

**Characteristics:**
- Pure functions only (no config, no callbacks)
- Loaded on engine startup
- Hot-reload when library files change
- Automatically available to all automations

**Built-in Libraries:**
- `timers` - Debouncing, cooldowns, time utilities
- `utils` - Data validation, string manipulation, helpers
- `presence` - Occupancy tracking, motion detection

### Global State

**Purpose:** Shared state accessible across all automations

**Access Control:** "Read-all, write-own"
- Any automation can **read** any global state key via `ctx.get_global(key)`
- Automations must declare writable keys in `config.global_state_writes`
- Writes to undeclared keys fail silently with error log

**Storage:** BoltDB "global" bucket (separate from per-automation state)

**Use Cases:**
- Presence/occupancy coordination
- Shared debounce timers
- Cross-automation state synchronization

### Agent Intelligence

**LLM Tools for Framework Awareness:**
- `getLibraryModules()` - Lists available library modules and their functions
- `getLibraryCode(moduleName)` - Returns full source code of library module
- `getGlobalStateSchema()` - Shows which automations write which global keys

**Behavior:**
- Agent checks existing libraries before generating code
- Proactively suggests using library functions
- Can propose creating new library modules for reusable logic

## Components

### 1. Web UI (`/web`)

**Technology:** SolidJS + Vite + TypeScript

**Purpose:** Browser-based interface for creating and managing automations

**Features:**
- Chat interface for natural language automation descriptions
- Code preview with syntax highlighting
- Automation management (list, edit, delete)
- Library module browser with source code viewer
- Global state viewer with ownership information (auto-refresh)
- Real-time log viewer via WebSocket

**Port:** 5173

### 2. Agent API (`/agent`)

**Technology:** Kotlin + Spring Boot + Embabel Agent Framework

**Purpose:** AI-powered conversational interface for automation management

**Key Technologies:**
- **Embabel Agent Framework** - Declarative AI agent development with tool support
- **Spring Boot 3.4** - Web framework and dependency injection
- **Spring AI (via Embabel)** - LLM integration (Anthropic Claude)
- **JGit** - Git operations for version control

**Architecture (DDD/Hexagonal):**
```
┌─────────────────────────────────────────────────────────────┐
│                      Agent API                               │
├─────────────────────────────────────────────────────────────┤
│  API Layer (Inbound Adapters)                                │
│  ├── rest/                                                    │
│  │   ├── ChatController      - Conversational AI endpoint    │
│  │   ├── AutomationController - CRUD for automations        │
│  │   ├── TopicsController    - MQTT topic discovery         │
│  │   ├── LibraryController   - Library module browsing      │
│  │   ├── GlobalStateController - Global state retrieval     │
│  │   ├── LogsController      - Log retrieval                │
│  │   ├── HistoryController   - Git history                  │
│  │   └── HealthController    - Health check endpoint        │
│  ├── dto/            - Request/response DTOs                 │
│  └── mapper/         - Domain ↔ DTO mapping                  │
├─────────────────────────────────────────────────────────────┤
│  Application Layer (Use Cases)                               │
│  ├── AutomationUseCase - CRUD operations, filename sanitize │
│  ├── ChatUseCase      - Chat conversation handling          │
│  ├── TopicUseCase     - Topic discovery                     │
│  ├── LibraryUseCase   - Library module operations           │
│  ├── GlobalStateUseCase - Global state retrieval            │
│  └── LogUseCase       - Log retrieval                       │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer (Pure Business Logic)                          │
│  ├── automation/                                              │
│  │   ├── Automation.kt          - Aggregate root            │
│  │   ├── AutomationId.kt        - Value object              │
│  │   ├── AutomationCode.kt      - Value object              │
│  │   └── AutomationRepository.kt - Port (interface)         │
│  ├── topic/                                                   │
│  │   ├── Topic.kt               - Entity                    │
│  │   ├── TopicPath.kt           - Value object              │
│  │   └── TopicRepository.kt     - Port (interface)          │
│  ├── library/                                                 │
│  │   ├── LibraryModule.kt       - Module entity             │
│  │   └── GlobalStateSchema.kt   - State ownership tracking  │
│  ├── conversation/                                            │
│  │   ├── ChatResponse.kt        - Response model            │
│  │   ├── CodeProposal.kt        - Value object              │
│  │   └── Message.kt             - Value object              │
│  └── commit/                                                  │
│      └── Commit.kt              - Value object              │
├─────────────────────────────────────────────────────────────┤
│  Infrastructure Layer (Outbound Adapters)                    │
│  ├── persistence/                                             │
│  │   ├── GitAutomationRepository  - Git-based storage       │
│  │   ├── EngineTopicRepository    - Engine topic discovery  │
│  │   └── GitOperations            - Low-level git ops       │
│  ├── engine/                                                  │
│  │   └── EngineClient             - HTTP client to Engine   │
│  ├── ai/                                                      │
│  │   ├── EmbabelChatAgent         - Embabel integration     │
│  │   └── MqttLlmTools             - LLM-callable tools      │
│  └── websocket/                                               │
│      └── LogsWebSocketHandler     - Real-time log streaming │
├─────────────────────────────────────────────────────────────┤
│  Configuration & Exception Handling                          │
│  ├── config/          - Spring configuration                 │
│  └── exception/       - Global exception handlers           │
└─────────────────────────────────────────────────────────────┘
```

**Port:** 8080

**Endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Conversational AI chat (supports tool calling) |
| GET | `/api/automations` | List all automations |
| POST | `/api/automations` | Deploy new automation |
| GET | `/api/automations/{id}` | Get automation code |
| PUT | `/api/automations/{id}` | Update automation |
| DELETE | `/api/automations/{id}` | Delete automation |
| GET | `/api/topics` | List discovered MQTT topics |
| GET | `/api/libraries` | List library modules with functions |
| GET | `/api/libraries/{name}` | Get library module source code |
| GET | `/api/global-state` | Get global state values with ownership |
| GET | `/api/logs` | Get recent logs |
| GET | `/api/history` | Get git commit history |
| GET | `/health` | Health check (returns "ok") |
| WS | `/ws/logs` | Real-time log stream |

### 3. Automation Engine (`/engine`)

**Technology:** Go + Starlark

**Purpose:** Execute automation scripts, manage MQTT connections

**Features:**
- MQTT client with auto-reconnect
- Starlark interpreter for sandboxed execution
- Library module loader (`.lib.star` files)
- File watcher for hot-reload (includes lib/ directory)
- Persistent state storage (BoltDB - per-automation + global)
- Cron-based scheduling
- Global state with access control

**Port:** 9000

**Internal Endpoints:**
- `GET /health` - Health check
- `GET /automations` - List running automations
- `GET /topics` - List discovered MQTT topics
- `GET /logs` - Get recent logs
- `GET /library` - List library modules with functions
- `GET /library/{name}` - Get library module source code
- `GET /global-state` - Get current global state values
- `GET /global-state-schema` - Get global state ownership schema

## Data Flow

### Conversational Automation Creation

```
User                Web UI              Agent                    Engine
  │                   │                   │                        │
  │ "Turn on lights   │                   │                        │
  │  when motion"     │                   │                        │
  │──────────────────►│                   │                        │
  │                   │  POST /api/chat   │                        │
  │                   │──────────────────►│                        │
  │                   │                   │  GET /topics           │
  │                   │                   │───────────────────────►│
  │                   │                   │◄───────────────────────│
  │                   │                   │                        │
  │                   │                   │ ┌────────────────────┐ │
  │                   │                   │ │ Embabel Agent      │ │
  │                   │                   │ │ 1. Use MqttTools   │ │
  │                   │                   │ │ 2. Generate code   │ │
  │                   │                   │ │ 3. Return proposal │ │
  │                   │                   │ └────────────────────┘ │
  │                   │◄──────────────────│                        │
  │ Code proposal     │                   │                        │
  │◄──────────────────│                   │                        │
  │                   │                   │                        │
  │ [User confirms]   │                   │                        │
  │──────────────────►│                   │                        │
  │                   │ POST /automations │                        │
  │                   │──────────────────►│                        │
  │                   │                   │ Write .star file       │
  │                   │                   │ Git commit             │
  │                   │                   │                        │
  │                   │                   │        File change ───►│
  │                   │                   │                        │ Hot reload
  │                   │◄──────────────────│                        │
  │ Success           │                   │                        │
  │◄──────────────────│                   │                        │
```

### Automation Execution

1. Engine loads `.star` file at startup or on change
2. Engine parses `config.subscribe` and subscribes to MQTT topics
3. When MQTT message arrives → `on_message()` called immediately
4. If `config.schedule` defined → `on_schedule()` called on cron

## Embabel Agent Framework

The Agent uses [Embabel](https://github.com/embabel/embabel-agent), a JVM agent framework that provides:

### Declarative Agents

```kotlin
@Agent(description = "Conversational smart home assistant")
class ConversationalChatAgent {

    @Action(description = "Chat with the user")
    fun chat(request: Request, ai: Ai): Response {
        return ai.withDefaultLlm()
            .withToolObject(mqttTools)
            .createObject(prompt, Response::class.java)
    }
}
```

### Tool Calling with @LlmTool

```kotlin
@Component
class MqttTools(private val engineService: EngineProxyService) {

    @LlmTool(description = "Get all MQTT topics in the system")
    fun getAllTopics(): List<String> = engineService.getTopics()

    @LlmTool(description = "Search topics by pattern")
    fun searchTopics(pattern: String): List<String> =
        engineService.getTopics().filter { it.contains(pattern, ignoreCase = true) }
}
```

### Benefits

- **Type-safe AI interactions** - Structured inputs/outputs
- **Automatic tool orchestration** - LLM decides when to call tools
- **Spring integration** - Dependency injection, configuration
- **Multi-step workflows** - Chain actions with `@Action` and `@AchievesGoal`

## Shared Volume

The `/automations` directory is mounted to both Agent and Engine:

- **Agent writes:** Saves generated automation files
- **Engine reads:** Loads and executes automations
- **Git tracked:** Full version history

## Security Model

- **Starlark Sandbox:** No network/file I/O from scripts
- **MQTT Only:** Automations can only interact via MQTT
- **No Authentication:** Designed for private networks
- **Controlled Functions:** Only exposed `ctx.*` functions available
