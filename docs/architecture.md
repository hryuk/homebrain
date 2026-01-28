# Homebrain Architecture

## Overview

Homebrain is a Docker-based, code-first solution for orchestrating MQTT automations using AI-generated code.

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

## Components

### 1. Web UI (`/web`)

**Technology:** SolidJS + Vite + TypeScript

**Purpose:** Browser-based interface for creating and managing automations

**Features:**
- Chat interface for natural language automation descriptions
- Code preview with syntax highlighting
- Automation management (list, edit, delete)
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

**Architecture:**
```
┌─────────────────────────────────────────────────────────────┐
│                      Agent API                               │
├─────────────────────────────────────────────────────────────┤
│  Controllers (REST API)                                      │
│  ├── ChatController      - Conversational AI endpoint        │
│  ├── AutomationController - CRUD for automations            │
│  ├── TopicsController    - MQTT topic discovery             │
│  ├── LogsController      - Log retrieval                    │
│  └── HistoryController   - Git history                      │
├─────────────────────────────────────────────────────────────┤
│  Embabel Agents                                              │
│  ├── ConversationalChatAgent - Main chat interface          │
│  │   └── Uses MqttTools for smart home queries              │
│  └── AutomationCodeAgent     - Code generation pipeline     │
├─────────────────────────────────────────────────────────────┤
│  Tools (@LlmTool annotated)                                  │
│  └── MqttTools - getAllTopics, searchTopics, getAutomations │
├─────────────────────────────────────────────────────────────┤
│  Services                                                    │
│  ├── EngineProxyService - Communicates with Engine API      │
│  └── GitService         - File and version control          │
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
| GET | `/api/logs` | Get recent logs |
| GET | `/api/history` | Get git commit history |
| WS | `/ws/logs` | Real-time log stream |

### 3. Automation Engine (`/engine`)

**Technology:** Go + Starlark

**Purpose:** Execute automation scripts, manage MQTT connections

**Features:**
- MQTT client with auto-reconnect
- Starlark interpreter for sandboxed execution
- File watcher for hot-reload
- Persistent state storage (BoltDB)
- Cron-based scheduling

**Port:** 9000

**Internal Endpoints:**
- `GET /health` - Health check
- `GET /automations` - List running automations
- `GET /topics` - List discovered MQTT topics
- `GET /logs` - Get recent logs

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
