# Supabase Implementation Guide

## Overview

The Progression plugin now has full Supabase support with comprehensive database integration. This document outlines all the implemented features and how to use them.

## Database Schema

The plugin uses the following Supabase tables and views:

### Core Tables

1. **players** - Basic player information
   - `uuid` (UUID, Primary Key)
   - `name` (VARCHAR)
   - `level` (INTEGER)
   - `total_xp` (INTEGER)
   - `last_seen` (BIGINT)
   - `last_level_up` (BIGINT)

2. **player_stats** - Player statistics as JSONB
   - `id` (SERIAL, Primary Key)
   - `player_uuid` (UUID, Foreign Key)
   - `stats` (JSONB)
   - `last_updated` (BIGINT)

3. **player_awards** - Individual awards earned by players
   - `id` (SERIAL, Primary Key)
   - `player_uuid` (UUID, Foreign Key)
   - `award_id` (VARCHAR)
   - `award_name` (VARCHAR)
   - `tier` (VARCHAR)
   - `medal` (VARCHAR)
   - `points` (DOUBLE PRECISION)
   - `stat_value` (BIGINT)
   - `stat_path` (VARCHAR)
   - `achieved_at` (TIMESTAMP)

4. **player_medals** - Aggregated medal counts
   - `player_uuid` (UUID, Primary Key)
   - `bronze_count` (INTEGER)
   - `silver_count` (INTEGER)
   - `gold_count` (INTEGER)
   - `total_medals` (INTEGER)
   - `updated_at` (TIMESTAMP)

5. **player_points** - Total points for players
   - `player_uuid` (UUID, Primary Key)
   - `total_points` (DOUBLE PRECISION)
   - `updated_at` (TIMESTAMP)

### Level and Achievement Tables

6. **level_definitions** - Level definitions for players and towns
   - `id` (SERIAL, Primary Key)
   - `level_type` (VARCHAR) - 'player' or 'town'
   - `level` (INTEGER)
   - `xp_required` (INTEGER)
   - `title` (VARCHAR)
   - `description` (TEXT)
   - `color` (VARCHAR)

7. **achievement_definitions** - Achievement definitions
   - `id` (SERIAL, Primary Key)
   - `achievement_id` (VARCHAR, Unique)
   - `name` (VARCHAR)
   - `description` (TEXT)
   - `stat` (VARCHAR)
   - `color` (VARCHAR)
   - `achievement_type` (VARCHAR) - 'player' or 'town'

8. **achievement_tiers** - Achievement tier definitions
   - `id` (SERIAL, Primary Key)
   - `achievement_id` (VARCHAR, Foreign Key)
   - `tier` (INTEGER)
   - `name` (VARCHAR)
   - `description` (TEXT)
   - `threshold` (BIGINT)
   - `icon` (VARCHAR)
   - `points` (INTEGER)

9. **unlocked_achievements** - Unlocked achievements for players and towns
   - `id` (SERIAL, Primary Key)
   - `player_uuid` (UUID, Foreign Key)
   - `town_name` (VARCHAR) - For town achievements
   - `achievement_id` (VARCHAR, Foreign Key)
   - `tier` (INTEGER)
   - `unlocked_at` (TIMESTAMP)
   - `xp_awarded` (INTEGER)

### Views

10. **player_leaderboard** - Player leaderboards with points and medals
11. **award_leaderboard** - Award-specific leaderboards
12. **level_leaderboard** - Level-based leaderboards
13. **achievement_progress** - Achievement progress tracking

## Implemented Features

### 1. Data Synchronization

#### Player Stats Sync
- **Method**: `syncPlayerStats(UUID, String, Map<String, Object>)`
- **Description**: Syncs player statistics to Supabase
- **Usage**: Called automatically on player quit and manual sync

#### Player Awards Sync
- **Method**: `upsertPlayerAward(...)`
- **Description**: Syncs individual awards to Supabase
- **Usage**: Called when awards are calculated

#### Player Medals Sync
- **Method**: `upsertPlayerMedal(...)`
- **Description**: Syncs medal counts to Supabase
- **Usage**: Called when medals are updated

#### Player Points Sync
- **Method**: `upsertPlayerPoint(...)`
- **Description**: Syncs total points to Supabase
- **Usage**: Called when points are calculated

### 2. Level System Integration

#### Level Definitions Sync
- **Method**: `syncLevelDefinitions(List<LevelDefinition>, List<LevelDefinition>)`
- **Description**: Syncs player and town level definitions to Supabase
- **Usage**: Called on plugin startup

#### Player Level Sync
- **Method**: `syncPlayerLevel(UUID, String, int, int)`
- **Description**: Syncs individual player levels and XP to Supabase
- **Usage**: Called when players gain XP or level up

### 3. Achievement System Integration

#### Achievement Definitions Sync
- **Method**: `syncAchievementDefinitions(List<AchievementDefinition>)`
- **Description**: Syncs achievement definitions and tiers to Supabase
- **Usage**: Called on plugin startup

#### Unlocked Achievements Sync
- **Method**: `syncUnlockedAchievement(UUID, String, int, int)`
- **Description**: Syncs unlocked achievements to Supabase
- **Usage**: Called when achievements are unlocked

### 4. Query Methods

#### Leaderboard Queries
- **Method**: `getLeaderboard(String, int)`
- **Description**: Gets player leaderboards by points
- **Returns**: JSON array of player data

#### Level Leaderboard Queries
- **Method**: `getLevelLeaderboard(int)`
- **Description**: Gets level-based leaderboards
- **Returns**: JSON array of player level data

#### Award Leaderboard Queries
- **Method**: `getAwardLeaderboard(String, int)`
- **Description**: Gets award-specific leaderboards
- **Returns**: JSON array of award data

#### Player Data Queries
- **Method**: `getPlayerStats(UUID)`
- **Description**: Gets player statistics
- **Returns**: JSON array of player stats

- **Method**: `getPlayerAwards(UUID)`
- **Description**: Gets player awards
- **Returns**: JSON array of player awards

- **Method**: `getPlayerMedals(UUID)`
- **Description**: Gets player medal counts
- **Returns**: JSON array of player medals

- **Method**: `getPlayerPoints(UUID)`
- **Description**: Gets player total points
- **Returns**: JSON array of player points

#### Achievement Progress Queries
- **Method**: `getAchievementProgress(UUID)`
- **Description**: Gets player achievement progress
- **Returns**: JSON array of achievement progress

#### Stat-based Queries
- **Method**: `getTopPlayersByStat(String, int)`
- **Description**: Gets top players by specific stat
- **Returns**: JSON array of player stat data

## Configuration

### Basic Supabase Configuration
```yaml
supabase:
  enabled: true
  url: "https://your-project.supabase.co"
  key: "your-anon-key"
```

### Performance Settings
```yaml
supabase:
  performance:
    batch_size: 1  # Sync one player at a time
    batch_delay_ms: 5000  # 5 seconds between batches
    max_concurrent_requests: 1  # Sequential processing
    timeout_seconds: 30  # Request timeout
```

### Sync Settings
```yaml
supabase:
  sync:
    on_player_quit: true  # Update when player leaves
    batch_sync_interval: 300000  # 5 minutes between batch syncs
    real_time_updates: false  # Disable for performance
```

## Commands

### Test Command
```bash
/sqlstats test [player_name]
```
Tests Supabase connectivity and queries. If a player name is provided, tests player-specific queries.

### Sync Commands
```bash
/sqlstats sync all      # Full sync including Supabase
/sqlstats sync stats    # Stats only sync
```

## Error Handling

The implementation includes comprehensive error handling:

1. **Connection Errors**: Logged with retry logic
2. **Rate Limiting**: Built-in delays and queue management
3. **Data Validation**: Checks for required fields
4. **Fallback Support**: Falls back to MySQL if Supabase fails

## Performance Considerations

1. **Rate Limiting**: Conservative settings to respect Supabase limits
2. **Batch Processing**: Processes players sequentially to avoid overwhelming the API
3. **Connection Pooling**: Efficient HTTP client configuration
4. **Async Operations**: All database operations run asynchronously

## Troubleshooting

### Common Issues

1. **Connection Failed**
   - Check Supabase URL and API key
   - Verify RLS policies are set up correctly
   - Check network connectivity

2. **Rate Limit Errors**
   - Increase `batch_delay_ms` in config
   - Reduce `batch_size` to 1
   - Check Supabase dashboard for rate limit errors

3. **Data Not Syncing**
   - Check if Supabase is enabled in config
   - Verify database schema is set up correctly
   - Check console logs for error messages

### Debug Commands
```bash
/sqlstats test          # Test Supabase connectivity
/sqlstats status        # Check plugin status
```

## API Usage Examples

### Web Application Integration

```javascript
// Get player leaderboard
const response = await fetch('https://your-project.supabase.co/rest/v1/player_leaderboard?order=total_points.desc&limit=10', {
  headers: {
    'apikey': 'your-anon-key',
    'Authorization': 'Bearer your-anon-key'
  }
});

// Get specific player stats
const playerStats = await fetch('https://your-project.supabase.co/rest/v1/player_stats?player_uuid=eq.player-uuid', {
  headers: {
    'apikey': 'your-anon-key',
    'Authorization': 'Bearer your-anon-key'
  }
});
```

## Future Enhancements

1. **Real-time Updates**: WebSocket integration for live updates
2. **Advanced Analytics**: Complex queries and aggregations
3. **Caching Layer**: Redis integration for improved performance
4. **Backup System**: Automated database backups
5. **API Rate Limiting**: More sophisticated rate limiting strategies

## Support

For issues with the Supabase implementation:
1. Check the console logs for error messages
2. Use the test command to verify connectivity
3. Verify your Supabase configuration
4. Check the database schema matches the provided SQL 