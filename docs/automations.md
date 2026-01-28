# Writing Automations

Homebrain automations are written in Starlark, a Python-like language designed for configuration and scripting.

## Automation Structure

Every automation file (`.star`) must contain:

1. **`config` dict** - Metadata and configuration
2. **`on_message()` function** - Event handler for MQTT messages (optional if using schedule)
3. **`on_schedule()` function** - Periodic task handler (optional)

## Basic Example

```python
def on_message(topic, payload, ctx):
    """Called when subscribed MQTT topics receive messages."""
    data = ctx.json_decode(payload)

    if data.get("state") == "ON":
        ctx.log("Light turned on")

config = {
    "name": "Light Monitor",
    "description": "Log when lights are turned on",
    "subscribe": ["zigbee2mqtt/light"],
    "enabled": True,
}
```

## Config Options

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Human-readable name |
| `description` | string | Yes | What the automation does |
| `subscribe` | list[string] | No* | MQTT topics to subscribe to |
| `schedule` | string | No* | Cron expression for periodic tasks |
| `enabled` | bool | Yes | Whether automation is active |

*At least one of `subscribe` or `schedule` must be defined.

## Context Functions (`ctx`)

### MQTT

```python
# Publish a message
ctx.publish("topic/name", "payload string")

# Publish JSON
ctx.publish("lights/set", ctx.json_encode({"state": "ON"}))
```

### JSON

```python
# Parse incoming JSON payload
data = ctx.json_decode(payload)

# Create JSON string for publishing
json_str = ctx.json_encode({"key": "value"})
```

### Logging

```python
# Log a message (visible in Web UI)
ctx.log("Something happened")
```

### Persistent State

State persists across messages and restarts.

```python
# Store a value
ctx.set_state("last_motion", ctx.now())

# Retrieve a value (returns None if not set)
last = ctx.get_state("last_motion")

# Clear a value
ctx.clear_state("last_motion")
```

### Time

```python
# Get current Unix timestamp (seconds)
now = ctx.now()
```

## Trigger Model

### Event-Driven (Primary)

Automations react to MQTT messages immediately:

```python
def on_message(topic, payload, ctx):
    # Called IMMEDIATELY when a subscribed topic receives a message
    # No polling - the engine subscribes to MQTT topics
    pass

config = {
    "subscribe": ["sensor/temperature", "sensor/humidity"],
    # ...
}
```

### Scheduled (Secondary)

For periodic tasks like timeouts:

```python
def on_schedule(ctx):
    # Called according to cron schedule
    # Useful for cleanup, timeout checks, aggregations
    pass

config = {
    "schedule": "*/5 * * * *",  # Every 5 minutes
    # ...
}
```

### Cron Format

```
┌───────────── minute (0-59)
│ ┌───────────── hour (0-23)
│ │ ┌───────────── day of month (1-31)
│ │ │ ┌───────────── month (1-12)
│ │ │ │ ┌───────────── day of week (0-6, Sunday=0)
│ │ │ │ │
* * * * *
```

Examples:
- `* * * * *` - Every minute
- `*/5 * * * *` - Every 5 minutes
- `0 * * * *` - Every hour
- `0 0 * * *` - Daily at midnight
- `0 8 * * 1` - Mondays at 8am

## Complete Examples

### Motion-Activated Light

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)

    if data.get("occupancy"):
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({
            "state": "ON",
            "brightness": 254
        }))
        ctx.set_state("light_on_time", ctx.now())
        ctx.log("Motion detected - light on")

def on_schedule(ctx):
    light_on = ctx.get_state("light_on_time")
    if light_on and ctx.now() - light_on > 300:  # 5 minutes
        ctx.publish("zigbee2mqtt/light/set", ctx.json_encode({
            "state": "OFF"
        }))
        ctx.clear_state("light_on_time")
        ctx.log("No motion for 5 min - light off")

config = {
    "name": "Motion Light",
    "description": "Turn on light on motion, off after 5 min",
    "subscribe": ["zigbee2mqtt/motion_sensor"],
    "schedule": "* * * * *",
    "enabled": True,
}
```

### Temperature Alert

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    temp = data.get("temperature")

    if temp and temp > 30:
        last_alert = ctx.get_state("last_temp_alert")
        # Only alert once per hour
        if not last_alert or ctx.now() - last_alert > 3600:
            ctx.publish("notifications/send", ctx.json_encode({
                "title": "High Temperature",
                "body": "Temperature is " + str(temp) + "°C"
            }))
            ctx.set_state("last_temp_alert", ctx.now())

config = {
    "name": "Temperature Alert",
    "description": "Alert when temperature exceeds 30°C",
    "subscribe": ["sensors/temperature"],
    "enabled": True,
}
```

### Scheduled Scene

```python
def on_schedule(ctx):
    ctx.publish("zigbee2mqtt/living_room_light/set", ctx.json_encode({
        "state": "ON",
        "brightness": 50,
        "color_temp": 400
    }))
    ctx.log("Evening scene activated")

config = {
    "name": "Evening Scene",
    "description": "Dim lights at 8pm",
    "schedule": "0 20 * * *",
    "enabled": True,
}
```

## Starlark Limitations

Starlark is intentionally limited for safety:

| Allowed | Not Allowed |
|---------|-------------|
| `for x in range(n)` | `while True` |
| `for item in list` | Infinite loops |
| Functions | Recursion |
| Dicts, lists | Classes |
| Basic math | Imports |

These limitations ensure automations are safe and predictable.
