# Homebrain

AI-powered MQTT automation orchestrator. Describe automations in plain English, let an LLM generate the code, and deploy instantly.

## Features

- **AI Code Generation** - Describe automations in natural language
- **Event-Driven** - React to MQTT messages instantly (no polling)
- **Code-First** - Automations are git-tracked Starlark scripts
- **Hot Reload** - Changes deploy automatically
- **Sandboxed** - Safe execution with controlled MQTT-only access
- **Powered by Claude** - Uses Anthropic's Claude for intelligent code generation

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

- [Architecture](docs/architecture.md) - System design and components
- [Writing Automations](docs/automations.md) - Starlark syntax and examples
- [Development Guide](docs/development.md) - Local setup and contributing

## Example Automation

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)

    if data.get("occupancy"):
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({
            "state": "ON"
        }))
        ctx.set_state("motion_time", ctx.now())

def on_schedule(ctx):
    last = ctx.get_state("motion_time")
    if last and ctx.now() - last > 120:
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({
            "state": "OFF"
        }))
        ctx.clear_state("motion_time")

config = {
    "name": "Motion Light",
    "description": "Light on with motion, off after 2 min",
    "subscribe": ["zigbee2mqtt/motion_sensor"],
    "schedule": "* * * * *",
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
