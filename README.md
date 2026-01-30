# Homebrain

AI-powered MQTT automation framework. Describe automations in plain English, let an LLM generate composable code with shared libraries and global state.

## Features

- **AI Code Generation** - Describe automations in natural language
- **Composable Framework** - Reusable library modules and shared global state
- **Intelligent Agent** - LLM suggests existing functions and creates reusable modules
- **Event-Driven** - React to MQTT messages instantly (no polling)
- **Code-First** - Automations are git-tracked Starlark scripts
- **Hot Reload** - Changes deploy automatically
- **Sandboxed** - Safe execution with controlled MQTT-only access
- **Powered by Claude** - Uses Anthropic's Claude Sonnet 4.5

## Quick Start

1. **Configure:**
   ```bash
   cp .env.example .env
   # Edit .env with your settings:
   # - MQTT_BROKER (required)
   # - ANTHROPIC_API_KEY (required)
   ```

2. **Run:**
   ```bash
   docker compose up
   ```

3. **Open:** http://localhost:5173

4. **Create an automation:**
   > "When motion is detected in the hallway, turn on the light for 2 minutes"

## Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────┐
│   Web UI    │◄───►│  Agent API  │◄───►│ Automation      │
│  (SolidJS)  │     │  (Kotlin)   │     │ Engine (Go)     │
│   :5173     │     │   :8080     │     │   :9000         │
└─────────────┘     └──────┬──────┘     └────────┬────────┘
                           │                     │
                    ┌──────▼─────────────────────▼──────┐
                    │     automations/ (Git repo)       │
                    └───────────────────────────────────┘
                                    │
                                    ▼
                           External MQTT Broker
```

## Documentation

- [Architecture](docs/architecture.md) - System design and DDD architecture
- [Writing Automations](docs/automations.md) - Starlark syntax and examples
- [Development Guide](docs/development.md) - Local setup and contributing
- [Testing Guide](docs/testing.md) - TDD practices and property-based testing

## Example Automation

**With Framework Features:**

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)

    if data.get("occupancy"):
        # Use library function for debouncing
        if ctx.lib.timers.debounce_check(ctx, "hallway_motion", 300):
            ctx.publish("zigbee2mqtt/hallway_light/set", ctx.json_encode({
                "state": "ON"
            }))
            # Update global presence state
            ctx.set_global("presence.hallway.last_motion", ctx.now())
            ctx.log("Hallway light activated by motion")

config = {
    "name": "Hallway Motion Light",
    "description": "Light on with motion, 5-minute debounce",
    "subscribe": ["zigbee2mqtt/hallway_motion"],
    "global_state_writes": ["presence.hallway.*"],  # Declare writable keys
    "enabled": True,
}
```

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `MQTT_BROKER` | MQTT broker URL | Required |
| `MQTT_USERNAME` | MQTT username | - |
| `MQTT_PASSWORD` | MQTT password | - |
| `ANTHROPIC_API_KEY` | Anthropic API key | Required |
| `LOG_LEVEL` | Logging level | `info` |

## License

MIT
