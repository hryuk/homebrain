"""Presence detection and tracking utilities.

This library provides functions for tracking occupancy, detecting presence
patterns, and managing room/home states.
"""

def update_room_occupancy(ctx, room_name, occupied):
    """Update the occupancy state for a room.
    
    Stores in global state as "presence.rooms.{room_name}"
    
    Args:
        ctx: The automation context
        room_name: Name of the room
        occupied: Boolean occupancy state
        
    Example:
        ctx.lib.presence.update_room_occupancy(ctx, "bedroom", True)
    """
    key = "presence.rooms." + room_name
    ctx.set_global(key, occupied)

def get_room_occupancy(ctx, room_name):
    """Get the current occupancy state for a room.
    
    Args:
        ctx: The automation context
        room_name: Name of the room
        
    Returns:
        Boolean - True if occupied, False otherwise
        
    Example:
        if ctx.lib.presence.get_room_occupancy(ctx, "living_room"):
            ctx.log("Living room is occupied")
    """
    key = "presence.rooms." + room_name
    value = ctx.get_global(key)
    if value == None:
        return False
    return value

def get_occupied_rooms(ctx):
    """Get a list of currently occupied room names.
    
    Note: This requires automations to use update_room_occupancy()
    to maintain room states.
    
    Args:
        ctx: The automation context
        
    Returns:
        List of room names that are currently occupied
        
    Example:
        rooms = ctx.lib.presence.get_occupied_rooms(ctx)
        ctx.log("Occupied rooms: " + str(len(rooms)))
    """
    # This is a simplified version - in a real implementation
    # we would need to query all keys with "presence.rooms." prefix
    # Starlark limitations make this difficult, so this returns
    # an empty list as a placeholder
    # Users should track this themselves or use update_home_occupancy
    return []

def update_home_occupancy(ctx, anyone_home):
    """Update the overall home occupancy state.
    
    Stores in global state as "presence.home"
    
    Args:
        ctx: The automation context
        anyone_home: Boolean - True if anyone is home
        
    Example:
        ctx.lib.presence.update_home_occupancy(ctx, True)
    """
    ctx.set_global("presence.home", anyone_home)

def is_anyone_home(ctx):
    """Check if anyone is currently home.
    
    Args:
        ctx: The automation context
        
    Returns:
        Boolean - True if anyone is home
        
    Example:
        if ctx.lib.presence.is_anyone_home(ctx):
            # Run automations that require someone home
    """
    value = ctx.get_global("presence.home")
    if value == None:
        return False
    return value

def update_last_motion(ctx, location, timestamp=None):
    """Record the last motion detection time for a location.
    
    Stores in global state as "presence.last_motion.{location}"
    
    Args:
        ctx: The automation context
        location: Location identifier (e.g., room name)
        timestamp: Unix timestamp (uses ctx.now() if not provided)
        
    Example:
        ctx.lib.presence.update_last_motion(ctx, "hallway")
    """
    if timestamp == None:
        timestamp = ctx.now()
    
    key = "presence.last_motion." + location
    ctx.set_global(key, timestamp)

def time_since_last_motion(ctx, location):
    """Get seconds since last motion was detected in a location.
    
    Args:
        ctx: The automation context
        location: Location identifier
        
    Returns:
        Seconds since last motion, or None if never detected
        
    Example:
        elapsed = ctx.lib.presence.time_since_last_motion(ctx, "hallway")
        if elapsed != None and elapsed > 300:
            ctx.publish("lights/hallway/set", '{"state":"OFF"}')
    """
    key = "presence.last_motion." + location
    last = ctx.get_global(key)
    
    if last == None:
        return None
    
    now = ctx.now()
    return now - last

def aggregate_presence_score(ctx, sensors, timeout_seconds=300):
    """Calculate an overall presence score from multiple sensors.
    
    Returns a number from 0.0 to 1.0 indicating confidence that
    someone is present, based on recent motion across sensors.
    
    Args:
        ctx: The automation context
        sensors: List of sensor location names
        timeout_seconds: Consider sensor stale after this many seconds
        
    Returns:
        Float 0.0-1.0 presence confidence score
        
    Example:
        score = ctx.lib.presence.aggregate_presence_score(
            ctx,
            ["living_room", "kitchen", "hallway"],
            300
        )
        if score > 0.5:
            ctx.log("High confidence someone is home")
    """
    active_count = 0
    total_count = len(sensors)
    
    if total_count == 0:
        return 0.0
    
    for sensor in sensors:
        elapsed = time_since_last_motion(ctx, sensor)
        if elapsed != None and elapsed < timeout_seconds:
            active_count = active_count + 1
    
    return float(active_count) / float(total_count)
