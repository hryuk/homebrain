"""General utility functions for automations.

This library provides common utility functions for string manipulation,
data validation, and general-purpose helpers.
"""

def safe_get(data, key, default=None):
    """Safely get a value from a dict with a default fallback.
    
    Args:
        data: Dictionary to query
        key: Key to look up
        default: Value to return if key doesn't exist (default: None)
        
    Returns:
        The value at key, or default if not found
        
    Example:
        temp = ctx.lib.utils.safe_get(data, "temperature", 0)
    """
    if data == None:
        return default
    
    value = data.get(key)
    if value == None:
        return default
    
    return value

def clamp(value, min_val, max_val):
    """Clamp a value between min and max bounds.
    
    Args:
        value: The value to clamp
        min_val: Minimum allowed value
        max_val: Maximum allowed value
        
    Returns:
        The clamped value
        
    Example:
        brightness = ctx.lib.utils.clamp(raw_brightness, 0, 255)
    """
    if value < min_val:
        return min_val
    if value > max_val:
        return max_val
    return value

def map_range(value, in_min, in_max, out_min, out_max):
    """Map a value from one range to another.
    
    Args:
        value: Input value
        in_min: Input range minimum
        in_max: Input range maximum
        out_min: Output range minimum
        out_max: Output range maximum
        
    Returns:
        Value mapped to output range
        
    Example:
        # Map sensor reading (0-1023) to percentage (0-100)
        pct = ctx.lib.utils.map_range(sensor_value, 0, 1023, 0, 100)
    """
    in_range = in_max - in_min
    out_range = out_max - out_min
    
    if in_range == 0:
        return out_min
    
    normalized = (value - in_min) / in_range
    return out_min + (normalized * out_range)

def parse_bool(value, default=False):
    """Parse a value as boolean, handling various string formats.
    
    Recognizes: true/false, yes/no, on/off, 1/0 (case-insensitive)
    
    Args:
        value: Value to parse (can be bool, string, or number)
        default: Default value if parsing fails
        
    Returns:
        Boolean value
        
    Example:
        enabled = ctx.lib.utils.parse_bool(data.get("enabled"), False)
    """
    if type(value) == "bool":
        return value
    
    if type(value) == "int" or type(value) == "float":
        return value != 0
    
    if type(value) != "string":
        return default
    
    lower = value.lower()
    if lower in ["true", "yes", "on", "1"]:
        return True
    if lower in ["false", "no", "off", "0"]:
        return False
    
    return default

def truncate(text, max_length, suffix="..."):
    """Truncate text to maximum length, adding suffix if truncated.
    
    Args:
        text: String to truncate
        max_length: Maximum length including suffix
        suffix: String to append if truncated (default: "...")
        
    Returns:
        Truncated string
        
    Example:
        short_msg = ctx.lib.utils.truncate(long_message, 50)
    """
    if len(text) <= max_length:
        return text
    
    return text[:max_length - len(suffix)] + suffix

def merge_dicts(dict1, dict2):
    """Merge two dictionaries, with dict2 values taking precedence.
    
    Args:
        dict1: Base dictionary
        dict2: Dictionary with override values
        
    Returns:
        New merged dictionary
        
    Example:
        defaults = {"brightness": 100, "color": "white"}
        custom = {"color": "red"}
        settings = ctx.lib.utils.merge_dicts(defaults, custom)
        # Result: {"brightness": 100, "color": "red"}
    """
    result = {}
    
    # Copy dict1
    for key in dict1:
        result[key] = dict1[key]
    
    # Override with dict2
    for key in dict2:
        result[key] = dict2[key]
    
    return result

def list_contains(list_val, item):
    """Check if a list contains an item.
    
    Args:
        list_val: List to search
        item: Item to find
        
    Returns:
        Boolean - True if item is in list
        
    Example:
        if ctx.lib.utils.list_contains(active_rooms, "bedroom"):
            ctx.log("Bedroom is active")
    """
    for element in list_val:
        if element == item:
            return True
    return False

def extract_device_name(topic):
    """Extract device name from a zigbee2mqtt topic.
    
    Handles topics like "zigbee2mqtt/living_room_light/state"
    
    Args:
        topic: MQTT topic string
        
    Returns:
        Device name or None if not a zigbee2mqtt topic
        
    Example:
        device = ctx.lib.utils.extract_device_name(topic)
        # "zigbee2mqtt/bedroom_sensor/state" -> "bedroom_sensor"
    """
    parts = topic.split("/")
    if len(parts) >= 2 and parts[0] == "zigbee2mqtt":
        return parts[1]
    return None
