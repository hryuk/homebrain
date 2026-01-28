# CLAUDE.md - Project Guide for AI Assistants

## Project Overview

**Homebrain** is an AI-powered MQTT automation orchestrator. Users describe automations in natural language, an LLM generates Starlark code, and the system deploys it automatically with hot-reload.

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
                     └──────────────────────────────────────┘
```

**Data flow:** User → Web UI → Agent (LLM with tools generates code) → writes .star file → Engine detects change → hot-reloads → subscribes to MQTT topics

## Tech Stack

| Component | Technology | Location |
|-----------|------------|----------|
| Web UI | SolidJS + Vite + TypeScript | `/web` |
| Agent API | Kotlin + Spring Boot + Embabel | `/agent` |
| Automation Engine | Go 1.23 + Starlark | `/engine` |
| State Storage | BoltDB (embedded) | Engine container |
| Container Orchestration | Docker Compose | `/docker-compose.yml` |

## Key Files

### Engine (`/engine`)
- `main.go` - Entry point, HTTP API for internal use
- `internal/mqtt/client.go` - MQTT client with auto-reconnect
- `internal/runner/starlark.go` - Loads and manages automations
- `internal/runner/context.go` - `ctx.*` functions exposed to Starlark scripts
- `internal/watcher/watcher.go` - File watcher for hot-reload
- `internal/state/state.go` - BoltDB persistence for automation state

### Agent (`/agent`) - Kotlin/Spring Boot/Embabel
- `build.gradle.kts` - Gradle build with Embabel dependencies
- `src/main/kotlin/com/homebrain/agent/`
  - `AgentApplication.kt` - Spring Boot entry point
  - `controller/` - REST API controllers
    - `ChatController.kt` - Conversational AI chat endpoint
    - `AutomationController.kt` - CRUD operations
    - `TopicsController.kt` - MQTT topic discovery
    - `LogsController.kt` - Log retrieval
    - `HistoryController.kt` - Git history
  - `agent/` - Embabel agents
    - `ConversationalChatAgent.kt` - Main chat with tool support
    - `AutomationCodeAgent.kt` - Code generation pipeline
  - `tools/` - LLM tools
    - `MqttTools.kt` - Smart home query tools (@LlmTool)
  - `service/` - Business logic
    - `EngineProxyService.kt` - Engine API client
    - `GitService.kt` - Git operations with JGit
  - `domain/` - Domain models
  - `dto/` - API DTOs

### Web UI (`/web`)
- `src/App.tsx` - Main app with tab navigation
- `src/components/Chat.tsx` - Chat interface for creating automations
- `src/components/CodePreview.tsx` - Code display with syntax highlighting
- `src/components/AutomationList.tsx` - Automation management
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
| POST | `/api/automations` | Deploy new automation |
| GET | `/api/automations/{id}` | Get automation code |
| PUT | `/api/automations/{id}` | Update automation |
| DELETE | `/api/automations/{id}` | Delete automation |
| GET | `/api/topics` | List discovered MQTT topics |
| GET | `/api/logs` | Get recent logs |
| GET | `/api/history` | Git commit history |
| WS | `/ws/logs` | Real-time log stream |

### Engine API (`:9000`, internal)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/health` | Health check |
| GET | `/automations` | List running automations |
| GET | `/topics` | Discovered MQTT topics |
| GET | `/logs` | Recent automation logs |

## Starlark Automation Format

```python
def on_message(topic, payload, ctx):
    """Called when subscribed MQTT topics receive messages."""
    data = ctx.json_decode(payload)
    # Handle message...
    ctx.publish("output/topic", ctx.json_encode({"key": "value"}))

def on_schedule(ctx):
    """Optional: Called on cron schedule."""
    pass

config = {
    "name": "Automation Name",
    "description": "What it does",
    "subscribe": ["mqtt/topic/+"],  # MQTT topics to subscribe
    "schedule": "* * * * *",        # Optional cron expression
    "enabled": True,
}
```

### Available `ctx` Functions
- `ctx.publish(topic, payload)` - Publish MQTT message
- `ctx.log(message)` - Log message (visible in UI)
- `ctx.json_encode(value)` / `ctx.json_decode(string)` - JSON handling
- `ctx.get_state(key)` / `ctx.set_state(key, value)` / `ctx.clear_state(key)` - Persistent state
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
ENGINE_URL=http://engine:9000  # For agent
```

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

## Common Tasks

### Adding a new LLM tool
1. Add method to `/agent/src/.../tools/MqttTools.kt` with `@LlmTool` annotation
2. Include description for the LLM to understand when to use it
3. Use `@LlmTool.Param` for parameter descriptions

### Adding a new `ctx` function for automations
1. Add method to `/engine/internal/runner/context.go`
2. Register in `ToStarlark()` method
3. Update system prompt in `/agent/.../agent/AutomationCodeAgent.kt`
4. Document in `/docs/automations.md`

### Adding a new API endpoint
1. Create controller in `/agent/src/.../controller/`
2. Add corresponding UI call in `/web/src/components/`

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
7. **Embabel version:** Currently using 0.3.2 (0.3.3 has tool calling issues)

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
│   ├── architecture.md
│   ├── automations.md
│   └── development.md
├── engine/                 # Automation runtime (Go)
│   ├── Dockerfile
│   ├── go.mod
│   ├── main.go
│   └── internal/
│       ├── mqtt/
│       ├── runner/
│       ├── state/
│       └── watcher/
├── agent/                  # AI Agent service (Kotlin/Embabel)
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/homebrain/agent/
│       ├── AgentApplication.kt
│       ├── controller/
│       ├── agent/
│       ├── tools/
│       ├── service/
│       └── domain/
├── web/                    # SolidJS frontend
│   ├── Dockerfile
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx
│       └── components/
└── automations/            # Generated automation scripts
    └── *.star
```
