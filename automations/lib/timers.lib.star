"""Timer and debounce utilities for automations.

This library provides functions for time-based logic including debouncing,
cooldowns, and duration formatting.
"""

def debounce_check(ctx, key, delay_seconds):
    """Check if enough time has passed since last call (debounce).
    
    Returns True if at least delay_seconds have passed since the last
    successful debounce check for this key, or if this is the first call.
    Automatically updates the timestamp on success.
    
    Args:
        ctx: The automation context
        key: Unique identifier for this debounce timer
        delay_seconds: Minimum seconds that must pass between calls
        
    Returns:
        Boolean - True if the action should proceed, False if still in cooldown
        
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

def cooldown_remaining(ctx, key, delay_seconds):
    """Get the remaining cooldown time in seconds.
    
    Returns the number of seconds remaining before the debounce timer
    expires, or 0 if no cooldown is active.
    
    Args:
        ctx: The automation context
        key: Unique identifier for the debounce timer
        delay_seconds: Total cooldown duration
        
    Returns:
        Number of seconds remaining (0 if ready)
        
    Example:
        remaining = ctx.lib.timers.cooldown_remaining(ctx, "motion_light", 300)
        if remaining > 0:
            ctx.log("Motion light on cooldown for " + str(remaining) + " more seconds")
    """
    state_key = "timers.debounce." + key
    last = ctx.get_global(state_key)
    
    if last == None:
        return 0
    
    now = ctx.now()
    elapsed = now - last
    remaining = delay_seconds - elapsed
    
    if remaining < 0:
        return 0
    
    return int(remaining)

def format_duration(seconds):
    """Format seconds as human-readable duration string.
    
    Args:
        seconds: Duration in seconds
        
    Returns:
        String like "5m 30s", "2h 15m", or "45s"
        
    Example:
        duration = ctx.lib.timers.format_duration(325)  # Returns "5m 25s"
        ctx.log("Light will turn off in " + duration)
    """
    if seconds < 60:
        return str(int(seconds)) + "s"
    
    minutes = int(seconds / 60)
    remaining_seconds = int(seconds % 60)
    
    if minutes < 60:
        if remaining_seconds > 0:
            return str(minutes) + "m " + str(remaining_seconds) + "s"
        return str(minutes) + "m"
    
    hours = int(minutes / 60)
    remaining_minutes = minutes % 60
    
    if remaining_minutes > 0:
        return str(hours) + "h " + str(remaining_minutes) + "m"
    return str(hours) + "h"

def is_within_time_range(ctx, start_hour, end_hour):
    """Check if current time is within specified hour range.
    
    Uses 24-hour format. Handles ranges that cross midnight.
    
    Args:
        ctx: The automation context
        start_hour: Start hour (0-23)
        end_hour: End hour (0-23)
        
    Returns:
        Boolean - True if current time is in range
        
    Example:
        # Only run between 10pm and 6am
        if ctx.lib.timers.is_within_time_range(ctx, 22, 6):
            ctx.publish("night_mode/set", '{"enabled":true}')
    """
    now = int(ctx.now())
    # Get hour of day (0-23) from Unix timestamp
    # 86400 seconds in a day
    seconds_today = now % 86400
    current_hour = int(seconds_today / 3600)
    
    # Handle range that crosses midnight
    if start_hour > end_hour:
        return current_hour >= start_hour or current_hour < end_hour
    
    return current_hour >= start_hour and current_hour < end_hour
