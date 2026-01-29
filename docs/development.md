# Development Guide

## Prerequisites

- Docker and Docker Compose
- JDK 21+ (for Agent local development)
- Go 1.23+ (for Engine local development)
- Node.js 22+ (for Web UI local development)
- An MQTT broker (e.g., Mosquitto)

## Quick Start

1. **Clone and configure:**
   ```bash
   cp .env.example .env
   # Edit .env with your MQTT broker and Anthropic API key
   ```

2. **Start the stack:**
   ```bash
   docker compose up --build
   ```

3. **Access the UI:**
   Open http://localhost:5173

## Tech Stack Overview

| Component | Technology | Language |
|-----------|------------|----------|
| Web UI | SolidJS + Vite | TypeScript |
| Agent API | Spring Boot + Embabel | Kotlin |
| Engine | Go + Starlark | Go |

## Local Development

### Agent (Kotlin/Spring Boot)

The Agent uses the [Embabel Agent Framework](https://github.com/embabel/embabel-agent) for AI capabilities.

```bash
cd agent

# Using Gradle wrapper (recommended)
./gradlew bootRun

# Or with system Gradle
gradle bootRun
```

Required environment variables:
- `ANTHROPIC_API_KEY` - Anthropic API key for Claude
- `ENGINE_URL` - Engine API URL (default: `http://localhost:9000`)
- `AUTOMATIONS_PATH` - Path to automations directory (default: `../automations`)

Configuration in `application.yml`:
```yaml
# Embabel configuration
embabel:
  models:
    default-llm: claude-sonnet-4-5

# Application configuration
app:
  engine:
    url: ${ENGINE_URL:http://localhost:9000}
  automations:
    path: ${AUTOMATIONS_PATH:../automations}
```

### Engine (Go)

```bash
cd engine
go mod download
go run .
```

Required environment variables:
- `MQTT_BROKER` - MQTT broker URL (e.g., `tcp://localhost:1883`)

### Web UI (SolidJS)

```bash
cd web
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to the Agent.

## Project Structure

```
homebrain/
├── docker-compose.yml          # Container orchestration
├── .env.example                # Environment template
│
├── agent/                      # Agent API (Kotlin/Embabel)
│   ├── build.gradle.kts        # Gradle build with Embabel deps
│   ├── src/main/kotlin/com/homebrain/agent/
│   │   ├── AgentApplication.kt          # Spring Boot entry point
│   │   ├── controller/                  # REST endpoints
│   │   │   ├── ChatController.kt        # AI chat endpoint
│   │   │   ├── AutomationController.kt  # CRUD operations
│   │   │   ├── TopicsController.kt      # MQTT topics
│   │   │   ├── LogsController.kt        # Log retrieval
│   │   │   ├── HistoryController.kt     # Git history
│   │   │   └── HealthController.kt      # Health check
│   │   ├── agent/                       # Embabel agents
│   │   │   ├── ConversationalChatAgent.kt  # Main chat agent
│   │   │   └── AutomationCodeAgent.kt      # Code generation
│   │   ├── tools/                       # LLM tools
│   │   │   └── MqttTools.kt             # Smart home queries
│   │   ├── service/                     # Business logic
│   │   │   ├── EngineProxyService.kt    # Engine communication
│   │   │   └── GitService.kt            # Git operations
│   │   ├── domain/                      # Domain models
│   │   │   ├── ChatModels.kt            # Chat response models
│   │   │   └── CodeGenerationModels.kt  # Code gen pipeline models
│   │   ├── dto/                         # API DTOs
│   │   │   └── DTOs.kt                  # Request/response DTOs
│   │   └── config/                      # Spring configuration
│   └── src/main/resources/
│       └── application.yml
│
├── engine/                     # Automation Engine (Go)
│   ├── main.go                 # Entry point
│   └── internal/
│       ├── mqtt/client.go      # MQTT client
│       ├── runner/             # Starlark execution
│       │   ├── starlark.go     # Automation loader
│       │   └── context.go      # ctx.* functions
│       ├── watcher/watcher.go  # File change detection
│       └── state/state.go      # BoltDB persistence
│
├── web/                        # Web UI (SolidJS)
│   ├── src/
│   │   ├── App.tsx             # Main app
│   │   └── components/
│   │       ├── Chat.tsx        # Chat interface
│   │       ├── CodePreview.tsx # Code display/edit
│   │       ├── AutomationList.tsx
│   │       └── LogViewer.tsx
│   └── vite.config.ts
│
├── automations/                # Automation scripts
│   └── *.star
│
└── docs/                       # Documentation
```

## Embabel Agent Development

### Creating an Agent

Agents are Spring components annotated with `@Agent`:

```kotlin
@Component
@Agent(description = "Description for the LLM")
class MyAgent {

    @Action(description = "What this action does")
    fun doSomething(input: MyInput, ai: Ai): MyOutput {
        return ai.withDefaultLlm()
            .createObject("prompt here", MyOutput::class.java)
    }
}
```

### Adding LLM Tools

Tools let the LLM call your code to gather information:

```kotlin
@Component
class MyTools(private val someService: SomeService) {

    @LlmTool(description = "Describe what this tool does for the LLM")
    fun getThing(
        @LlmTool.Param(description = "Parameter description")
        id: String
    ): Thing {
        return someService.findById(id)
    }
}
```

Use tools in an agent:

```kotlin
@Action
fun chat(request: Request, ai: Ai): Response {
    return ai.withDefaultLlm()
        .withSystemPrompt(SYSTEM_PROMPT)
        .withToolObject(myTools)  // Add tool object
        .createObject(request.message, Response::class.java)
}
```

### Invoking Agents

From a controller:

```kotlin
@RestController
class MyController(private val agentPlatform: AgentPlatform) {

    @PostMapping("/my-endpoint")
    fun handle(@RequestBody request: MyRequest): MyResponse {
        val invocation = AgentInvocation.create(
            agentPlatform,
            MyOutput::class.java
        )
        return invocation.invoke(request)
    }
}
```

## Adding Context Functions (Engine)

To add new functions available in automations:

1. Edit `engine/internal/runner/context.go`:

```go
func (c *Context) myFunction(thread *starlark.Thread, fn *starlark.Builtin, args starlark.Tuple, kwargs []starlark.Tuple) (starlark.Value, error) {
    // Implementation
    return starlark.None, nil
}
```

2. Register in `ToStarlark()`:

```go
"my_function": starlark.NewBuiltin("my_function", c.myFunction),
```

3. Update the system prompt in the agent's `AutomationCodeAgent.kt`

## Testing

### Manual Testing

1. Start the stack
2. Open Web UI at http://localhost:5173
3. Create an automation via chat
4. Verify it appears in Automations tab
5. Check Logs tab for execution logs

### Testing Automations Locally

Create a test automation in `automations/test.star`:

```python
def on_message(topic, payload, ctx):
    ctx.log("Received: " + payload)

config = {
    "name": "Test",
    "description": "Test automation",
    "subscribe": ["test/#"],
    "enabled": True,
}
```

Then publish to MQTT:
```bash
mosquitto_pub -t test/hello -m "world"
```

### Running Agent Tests

```bash
cd agent
./gradlew test
```

## Debugging

### Agent Logs
```bash
docker compose logs -f agent
```

Key log prefixes:
- `ConversationalChatAgent` - Chat processing
- `AutomationCodeAgent` - Code generation
- `MqttTools` - Tool invocations

### Engine Logs
```bash
docker compose logs -f engine
```

### Starlark Errors

Errors are logged and visible in:
- Engine container logs
- Web UI Logs tab

### Common Issues

**Agent fails to start:**
- Check `ANTHROPIC_API_KEY` is set
- Verify Engine is reachable at `ENGINE_URL`

**Tools not being called:**
- Check `@LlmTool` annotation has a description
- Verify tool object is passed with `withToolObject()`

**Code generation fails:**
- Check Engine is returning topics via `/topics`
- Review system prompt in agent code

## Building for Production

```bash
# Build all images
docker compose build

# Run in detached mode
docker compose up -d

# View logs
docker compose logs -f
```

## Dependencies

### Agent (Gradle)

Key dependencies in `build.gradle.kts`:

```kotlin
val embabelVersion = "0.3.2"

dependencies {
    // Embabel Agent Framework
    implementation("com.embabel.agent:embabel-agent-starter")
    implementation("com.embabel.agent:embabel-agent-starter-anthropic")

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    // JGit for Git operations
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
}
```

### Embabel Repositories

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "embabel-releases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
    }
}
```
