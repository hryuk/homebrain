"""Light control utilities for common operations."""

def blink(ctx, topic, count, interval_ms):
    """Blink a light by toggling it on and off.
    
    Args:
        ctx: The automation context
        topic: MQTT topic for the light (e.g., "homeassistant/light/0x680ae2fffe3cd0d3/light/set")
        count: Number of times to blink
        interval_ms: Milliseconds between state changes
        
    Example:
        ctx.lib.lights.blink(ctx, "homeassistant/light/0x680ae2fffe3cd0d3/light/set", 3, 500)
    """
    # Initialize blink state machine
    state_key = "blink_" + topic.replace("/", "_")
    blink_state = ctx.get_state(state_key)
    
    if not blink_state:
        # Start new blink sequence
        ctx.set_state(state_key, {"count": count, "interval_ms": interval_ms, "step": 0, "on": True})
        ctx.publish(topic, ctx.json_encode({"state": "OFF"}))
        ctx.log("Blink: Starting sequence for " + topic + " (" + str(count) + " blinks)")
    else:
        # Continue blink sequence
        state = ctx.json_decode(blink_state) if isinstance(blink_state, str) else blink_state
        current_step = state["step"] + 1
        
        if current_step < state["count"] * 2:
            # Toggle state
            new_state = "ON" if not state["on"] else "OFF"
            ctx.publish(topic, ctx.json_encode({"state": new_state}))
            state["step"] = current_step
            state["on"] = not state["on"]
            ctx.set_state(state_key, state)
        else:
            # End sequence - turn light back on
            ctx.publish(topic, ctx.json_encode({"state": "ON"}))
            ctx.clear_state(state_key)
            ctx.log("Blink: Completed sequence for " + topic)