# Reduced Logging Changes

## Overview
Reduced the number of debug messages during server startup and normal operation to make the console output cleaner and less verbose.

## Changes Made

### 1. Main.java - Startup Messages
- **Removed**: Database configuration debug messages
- **Changed**: Initial sync and award calculation moved to background (10 seconds after startup)
- **Reduced**: Supabase connection messages to debug level
- **Simplified**: Startup success message to show only essential info

### 2. SupabaseManager.java - Connection Messages
- **Reduced**: Configuration loading messages to debug level
- **Simplified**: Connection attempt messages
- **Changed**: Success messages to debug level

### 3. TownyManager.java - Integration Messages
- **Reduced**: Initialization messages to debug level
- **Changed**: Town sync messages to debug level

### 4. StatSyncTask.java - Sync Messages
- **Removed**: World checking messages
- **Reduced**: Player processing messages to debug level
- **Simplified**: Achievement and XP calculation messages
- **Changed**: Sync completion messages to debug level

### 5. AwardManager.java - Processing Messages
- **Reduced**: Player processing count messages to debug level

## Current Logging Behavior

### Startup (Minimal Mode)
The plugin now shows only essential messages during startup:
```
[Progression] Progression v1.0 starting up...
[Progression] Progression v1.0 enabled successfully!
[Progression] Supabase: Connected
```

### Background Operations
- Initial sync runs 10 seconds after startup (debug level)
- Award calculations run in background (debug level)
- Town sync runs in background (debug level)

### Debug Mode
To see detailed messages, change the logging level in config.yml:
```yaml
logging:
  level: debug  # Options: minimal, verbose, debug, max
```

## Benefits
1. **Cleaner Console**: Much less verbose output during startup
2. **Better Performance**: Background operations don't block startup
3. **Maintained Functionality**: All operations still work, just with less logging
4. **Configurable**: Users can still enable debug mode if needed

## Configuration
The logging level can be controlled in `config.yml`:
- `minimal`: Only essential messages (default)
- `verbose`: Basic progress info
- `debug`: Detailed debugging info
- `max`: Everything including stat details

All debug messages are now properly controlled by the logging configuration, making the plugin much quieter by default while still providing detailed information when needed. 