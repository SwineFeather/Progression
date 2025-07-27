# Local Award Storage System

The Progression plugin now includes a local JSON storage system for awards, medals, and points data. This provides a reliable backup and alternative to Supabase when database connectivity issues occur.

## How It Works

The local storage system automatically saves all award data to JSON files in the plugin's data folder:

```
plugins/Progression/awards/
├── awards/          # Individual player awards
│   ├── player-uuid-1.json
│   ├── player-uuid-2.json
│   └── ...
├── medals/          # Player medal summaries
│   ├── player-uuid-1.json
│   ├── player-uuid-2.json
│   └── ...
└── points/          # Player total points
    ├── player-uuid-1.json
    ├── player-uuid-2.json
    └── ...
```

## Features

### Automatic Storage
- **Awards**: Individual awards earned by players with details like medal type, points, and timestamp
- **Medals**: Summary of total medals (gold, silver, bronze) for each player
- **Points**: Total points accumulated by each player

### Data Persistence
- Data is automatically saved when awards are calculated
- Data persists across server restarts
- Works independently of Supabase connectivity

### Fallback System
- If Supabase is unavailable, awards are still saved locally
- Player award commands work with local data
- No data loss when database is down

## File Format Examples

### Awards File (`awards/player-uuid.json`)
```json
{
  "playerUUID": "123e4567-e89b-12d3-a456-426614174000",
  "playerName": "SwineFeather",
  "awards": [
    {
      "awardId": "most_kills",
      "awardName": "Warrior",
      "tier": "stone",
      "medal": "gold",
      "points": 2.0,
      "awardedAt": 1640995200000
    }
  ],
  "lastUpdated": 1640995200000
}
```

### Medals File (`medals/player-uuid.json`)
```json
{
  "playerUUID": "123e4567-e89b-12d3-a456-426614174000",
  "playerName": "SwineFeather",
  "bronzeCount": 5,
  "silverCount": 3,
  "goldCount": 2,
  "totalMedals": 10,
  "lastUpdated": 1640995200000
}
```

### Points File (`points/player-uuid.json`)
```json
{
  "playerUUID": "123e4567-e89b-12d3-a456-426614174000",
  "playerName": "SwineFeather",
  "totalPoints": 25.5,
  "lastUpdated": 1640995200000
}
```

## Commands

The following commands work with local storage:

- `/awards debug <player>` - Debug player stats and test stat resolution
- `/awards medals <player>` - Show player medals (works with local data)
- `/awards recalculate <player>` - Recalculate awards and save to local storage

## Benefits

1. **Reliability**: No dependency on external database connectivity
2. **Performance**: Fast local file access
3. **Backup**: Automatic backup of all award data
4. **Compatibility**: Works alongside Supabase when available
5. **Debugging**: Easy to inspect and debug award data

## Troubleshooting

### No Awards Found
If you see "No awards found" messages:

1. Run `/awards debug <player>` to check if stats are being found
2. Run `/awards recalculate <player>` to force recalculation
3. Check the console for any error messages

### Data Not Saving
If data isn't being saved:

1. Check that the plugin has write permissions to its data folder
2. Look for any file I/O errors in the console
3. Ensure the awards system is enabled in config.yml

### File Corruption
If JSON files become corrupted:

1. Stop the server
2. Delete the corrupted files from the awards folder
3. Restart the server and recalculate awards

## Migration

If you're migrating from a previous version:

1. The local storage system is automatically enabled
2. Existing Supabase data will continue to work
3. New awards will be saved both locally and to Supabase (if available)
4. No configuration changes required

## Configuration

The local storage system requires no additional configuration. It's automatically enabled when the awards system is enabled in your `config.yml`:

```yaml
awards:
  enabled: true
  auto_calculate: true
  # ... other settings
```

## Support

If you encounter issues with the local storage system:

1. Check the server console for error messages
2. Verify file permissions in the plugin data folder
3. Use the debug command to troubleshoot stat resolution
4. Ensure the plugin has sufficient disk space for file storage 