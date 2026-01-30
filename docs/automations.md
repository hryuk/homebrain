# Writing Automations

Homebrain automations are written in Starlark, a Python-like language designed for configuration and scripting. The framework supports composable automations through library modules and global state.

## Framework Features

### Library Modules
Reusable function libraries stored in `automations/lib/*.lib.star` that can be shared across all automations. Access via `ctx.lib.modulename.function()`.

**Built-in Libraries:**
- `timers` - Debouncing, cooldowns, time ranges
- `utils` - String manipulation, data validation, helpers
- `presence` - Occupancy tracking, motion detection
- `devices` - Device state synchronization with global state

### Global State
Shared state accessible across all automations with "read-all, write-own" access control:
- Any automation can **read** any global state key
- Automations must **declare** which keys they can write

## Automation Structure

### Regular Automation

Every automation file (`.star`) must contain:

1. **`config` dict** - Metadata and configuration
2. **`on_message()` function** - Event handler for MQTT messages (optional if using schedule)
3. **`on_schedule()` function** - Periodic task handler (optional)

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
    "name": "Hallway Motion Light",
    "description": "Turn on light on motion with 5-minute debounce",
    "subscribe": ["zigbee2mqtt/hallway_motion"],
    "global_state_writes": ["presence.hallway.*"],  # NEW: Declare writable keys
    "enabled": True,
}
```

### Library Module

Library modules contain only pure functions (no config, no callbacks):

```python
"""Timer and debounce utilities for automations."""

def debounce_check(ctx, key, delay_seconds):
    """Check if enough time has passed since last call.
    
    Args:
        ctx: The automation context
        key: Unique identifier for this debounce timer
        delay_seconds: Minimum seconds that must pass between calls
        
    Returns:
        Boolean - True if the action should proceed
        
    Example:
        if ctx.lib.timers.debounce_check(ctx, "motion_light", 300):
            ctx.publish("light/set", '{"state":"ON"}')
    """
    state_key = "timers.debounce." + key
    last = ctx.get_global(state_key)
    now = ctx.now()
    
    if last == None:
        ctx.set_global(state_key, now)
        return True
    
    elapsed = now - last
    if elapsed >= delay_seconds:
        ctx.set_global(state_key, now)
        return True
    
    return False
```

## Config Options

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Human-readable name |
| `description` | string | Yes | What the automation does |
| `subscribe` | list[string] | No* | MQTT topics to subscribe to |
| `schedule` | string | No* | Cron expression for periodic tasks |
| `global_state_writes` | list[string] | No | Keys this automation can write (supports wildcards) |
| `enabled` | bool | Yes | Whether automation is active |

*At least one of `subscribe` or `schedule` must be defined.

**Global State Write Patterns:**
- Exact: `"presence.home"` - Can only write to this specific key
- Wildcard: `"presence.*"` - Can write to any key starting with `presence.`
- Multiple: `["presence.room.*", "timers.motion.*"]`

## Context Functions (`ctx`)

### MQTT & Logging

```python
# Publish a message
ctx.publish("topic/name", "payload string")

# Publish JSON
ctx.publish("lights/set", ctx.json_encode({"state": "ON"}))

# Log a message (visible in Web UI)
ctx.log("Something happened")
```

### JSON Handling

```python
# Parse incoming JSON payload
data = ctx.json_decode(payload)

# Create JSON string for publishing
json_str = ctx.json_encode({"key": "value"})
```

### Per-Automation State

State persists across messages and restarts, isolated to each automation:

```python
# Store a value
ctx.set_state("last_motion", ctx.now())

# Retrieve a value (returns None if not set)
last = ctx.get_state("last_motion")

# Clear a value
ctx.clear_state("last_motion")
```

### Global State (NEW)

Shared state accessible across all automations:

```python
# Read any global state (no restrictions)
last_motion = ctx.get_global("presence.hallway.last_motion")

# Write to declared keys only (must be in global_state_writes)
ctx.set_global("presence.hallway.last_motion", ctx.now())

# Clear declared keys
ctx.clear_global("presence.hallway.last_motion")
```

**Access Control:**
- ✅ Any automation can READ any global state
- ⚠️ Automations can only WRITE to keys declared in `config.global_state_writes`
- ❌ Attempting to write undeclared keys will log an error and fail silently

### Library Functions (NEW)

Access reusable functions from library modules:

```python
# Timers library
if ctx.lib.timers.debounce_check(ctx, "motion_key", 300):
    # Action happens at most once per 300 seconds
    pass

remaining = ctx.lib.timers.cooldown_remaining(ctx, "motion_key", 300)
duration = ctx.lib.timers.format_duration(325)  # "5m 25s"

# Utils library
temp = ctx.lib.utils.safe_get(data, "temperature", 0)
brightness = ctx.lib.utils.clamp(raw_value, 0, 255)
enabled = ctx.lib.utils.parse_bool(data.get("enabled"), False)

# Presence library
ctx.lib.presence.update_room_occupancy(ctx, "bedroom", True)
is_occupied = ctx.lib.presence.get_room_occupancy(ctx, "living_room")
ctx.lib.presence.update_home_occupancy(ctx, True)
```

### Time

```python
# Get current Unix timestamp (seconds)
now = ctx.now()
```

## Built-in Library Reference

### timers.lib.star

| Function | Description |
|----------|-------------|
| `debounce_check(ctx, key, delay_seconds)` | Returns True if delay has elapsed since last call |
| `cooldown_remaining(ctx, key, delay_seconds)` | Returns seconds remaining in cooldown |
| `format_duration(seconds)` | Format seconds as human-readable (e.g., "5m 30s") |
| `is_within_time_range(ctx, start_hour, end_hour)` | Check if current time is within hour range (24h format) |

### utils.lib.star

| Function | Description |
|----------|-------------|
| `safe_get(data, key, default=None)` | Safely get dict value with fallback |
| `clamp(value, min_val, max_val)` | Clamp value between min and max |
| `map_range(value, in_min, in_max, out_min, out_max)` | Map value from one range to another |
| `parse_bool(value, default=False)` | Parse various formats as boolean |
| `truncate(text, max_length, suffix="...")` | Truncate text with suffix |
| `merge_dicts(dict1, dict2)` | Merge two dicts (dict2 takes precedence) |
| `list_contains(list_val, item)` | Check if list contains item |
| `extract_device_name(topic)` | Extract device from zigbee2mqtt topic |

### presence.lib.star

| Function | Description |
|----------|-------------|
| `update_room_occupancy(ctx, room_name, occupied)` | Update room occupancy state |
| `get_room_occupancy(ctx, room_name)` | Get room occupancy boolean |
| `update_home_occupancy(ctx, anyone_home)` | Update overall home occupancy |
| `is_anyone_home(ctx)` | Check if anyone is home |
| `update_last_motion(ctx, location, timestamp=None)` | Record last motion time |
| `time_since_last_motion(ctx, location)` | Get seconds since last motion |
| `aggregate_presence_score(ctx, sensors, timeout=300)` | Calculate presence confidence (0.0-1.0) |

### devices.lib.star

Enables automations to read the state of devices they're NOT directly subscribed to.

| Function | Description |
|----------|-------------|
| `extract_device_name(topic, position)` | Extract device name from MQTT topic at given position |
| `sync_state(ctx, device_name, payload)` | Sync device state to global state (flat keys) |
| `get_property(ctx, device_name, property, default)` | Get a device property from global state |
| `is_on(ctx, device_name)` | Check if device state is "ON" |
| `is_off(ctx, device_name)` | Check if device state is "OFF" |
| `get_last_updated(ctx, device_name)` | Get last sync timestamp |

**Global State Structure:**

State is stored as flat keys with recursive flattening:
```
devices.<device_name>.state = "ON"
devices.<device_name>.brightness = 100
devices.<device_name>.color.x = 0.5
devices.<device_name>.color.y = 0.3
devices.<device_name>.last_updated = 1706745600
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

### Device State Sync Pattern

Use this pattern when an automation needs to check the state of OTHER devices (not the trigger).

**Scenario:** Turn on bedroom light when living room motion is detected, but only if living room light is already on.

**Step 1: Sync the device whose state you need to check**

```python
def on_message(topic, payload, ctx):
    device_name = ctx.lib.devices.extract_device_name(topic, 1)
    ctx.lib.devices.sync_state(ctx, device_name, ctx.json_decode(payload))

config = {
    "name": "Sync Living Room Light",
    "description": "Sync living room light state to global state for other automations",
    "subscribe": ["zigbee2mqtt/living_room_light"],
    "global_state_writes": ["devices.*"],
    "enabled": True,
}
```

**Step 2: React to trigger and check other device state**

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    
    # React to motion (this is the trigger)
    if data.get("occupancy") == True:
        # Check OTHER device via global state
        if ctx.lib.devices.is_on(ctx, "living_room_light"):
            ctx.publish("zigbee2mqtt/bedroom_light/set", ctx.json_encode({"state": "ON"}))
            ctx.log("Bedroom light on (living room light was on)")

config = {
    "name": "Bedroom Light on Living Room Motion",
    "description": "Turn on bedroom light when motion detected if living room light is on",
    "subscribe": ["zigbee2mqtt/living_room_motion"],
    "enabled": True,
}
```

**When to use this pattern:**
- Automation reacts to device A but needs to check device B's state
- You need to know if another device is on/off before taking action
- Cross-device coordination without subscribing to multiple topics

**When NOT to use this pattern:**
- Simply reacting to a device (just subscribe and react)
- The device state is already in the message you're receiving

### Motion-Activated Light (Framework Style)

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    
    if data.get("occupancy"):
        # Use library debounce instead of manual state tracking
        if ctx.lib.timers.debounce_check(ctx, "hallway_motion", 300):
            ctx.publish("zigbee2mqtt/hallway_light/set", ctx.json_encode({
                "state": "ON",
                "brightness": 254
            }))
            # Update global presence for other automations to use
            ctx.lib.presence.update_last_motion(ctx, "hallway")
            ctx.log("Hallway motion detected - light on")

config = {
    "name": "Hallway Motion Light",
    "description": "Turn on light on motion with 5-minute debounce",
    "subscribe": ["zigbee2mqtt/hallway_motion"],
    "global_state_writes": ["presence.last_motion.hallway"],
    "enabled": True,
}
```

### Presence-Based Automation

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    device = ctx.lib.utils.extract_device_name(topic)
    
    if data.get("occupancy"):
        # Update room occupancy in global state
        room = device.replace("_motion", "")
        ctx.lib.presence.update_room_occupancy(ctx, room, True)
        
        # Check if this is first activity
        if not ctx.lib.presence.is_anyone_home(ctx):
            ctx.lib.presence.update_home_occupancy(ctx, True)
            ctx.log("Welcome home!")

def on_schedule(ctx):
    # Check all motion sensors for timeout
    sensors = ["living_room", "bedroom", "kitchen"]
    score = ctx.lib.presence.aggregate_presence_score(ctx, sensors, 600)
    
    if score == 0:
        ctx.lib.presence.update_home_occupancy(ctx, False)
        ctx.log("No activity detected - marking home as empty")

config = {
    "name": "Presence Manager",
    "description": "Track home and room occupancy from motion sensors",
    "subscribe": ["zigbee2mqtt/+_motion"],
    "schedule": "*/5 * * * *",
    "global_state_writes": ["presence.*"],
    "enabled": True,
}
```

### Night Mode Automation

```python
def on_schedule(ctx):
    # Only run during night hours
    if ctx.lib.timers.is_within_time_range(ctx, 22, 6):
        # Check if anyone is home
        if ctx.lib.presence.is_anyone_home(ctx):
            # Dim all lights to night mode
            ctx.publish("zigbee2mqtt/all_lights/set", ctx.json_encode({
                "state": "ON",
                "brightness": 30,
                "color_temp": 500
            }))
            ctx.log("Night mode activated")

config = {
    "name": "Night Mode",
    "description": "Dim lights between 10pm-6am when someone is home",
    "schedule": "0 22 * * *",  # Run at 10pm
    "enabled": True,
}
```

### Temperature Alert (Classic Style)

```python
def on_message(topic, payload, ctx):
    data = ctx.json_decode(payload)
    temp = ctx.lib.utils.safe_get(data, "temperature", 0)
    
    if temp > 30:
        # Only alert once per hour using per-automation state
        last_alert = ctx.get_state("last_temp_alert")
        if not last_alert or ctx.now() - last_alert > 3600:
            ctx.publish("notifications/send", ctx.json_encode({
                "title": "High Temperature",
                "body": "Temperature is " + str(temp) + "°C"
            }))
            ctx.set_state("last_temp_alert", ctx.now())
            ctx.log("Temperature alert sent")

config = {
    "name": "Temperature Alert",
    "description": "Alert when temperature exceeds 30°C (max once per hour)",
    "subscribe": ["sensors/temperature"],
    "enabled": True,
}
```

## Best Practices

### When to Use Global State vs Per-Automation State

**Use Global State when:**
- Multiple automations need to read the value
- Coordinating behavior across automations
- Tracking presence, occupancy, or shared timers
- Example: `presence.room.occupied`, `timers.debounce.key`

**Use Per-Automation State when:**
- Only this automation needs the value
- Implementation detail, not shared data
- Example: `last_alert_time`, `previous_value`

### Creating Library Modules

Create a library module when:
- Logic is reused across 2+ automations
- Function is pure (no side effects beyond ctx operations)
- Pattern would benefit from standardization

**Good library candidates:**
- Debouncing and throttling
- Data validation and parsing
- Mathematical transformations
- Device name extraction
- Time/date utilities

### Access Control Design

When declaring `global_state_writes`:
- Use specific keys when possible: `["presence.hallway.occupied"]`
- Use wildcards for logical groups: `["timers.*"]`
- Avoid overlapping patterns between automations
- Document your state schema in automation descriptions

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

## Next Steps

- See [Architecture](architecture.md) for system design
- See [Development](development.md) for local setup
- See [Testing](testing.md) for testing practices
