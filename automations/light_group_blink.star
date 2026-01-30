"""When any light turns off, blink the remaining on lights 3 times."""

def on_message(topic, payload, ctx):
    """Handle light state changes."""
    data = ctx.json_decode(payload)
    current_state = data.get("state", "OFF")
    
    # If a light was turned off
    if current_state == "OFF":
        ctx.log("Light turned off: " + topic)
        
        # List of all light topics
        light_topics = [
            "homeassistant/light/0x680ae2fffe3cd0d3/light",
            "homeassistant/light/0x680ae2fffe30d0eb/light",
            "homeassistant/light/0x680ae2fffe494243/light"
        ]
        
        # Blink all lights except the one that was turned off
        for light_topic in light_topics:
            if light_topic != topic:
                # Use set suffix for publishing commands
                command_topic = light_topic + "/set"
                ctx.lib.lights.blink(ctx, command_topic, 3, 400)
                ctx.log("Blinking: " + light_topic)

config = {
    "name": "Light Group Blink on Off",
    "description": "When any light turns off, the remaining on lights blink 3 times",
    "subscribe": [
        "homeassistant/light/0x680ae2fffe3cd0d3/light",
        "homeassistant/light/0x680ae2fffe30d0eb/light",
        "homeassistant/light/0x680ae2fffe494243/light"
    ],
    "enabled": True,
}