# Progression v1.0

A Minecraft plugin that syncs player statistics to MySQL and/or Supabase databases. Perfect for creating websites, leaderboards, and analytics dashboards.

## Features

### 🎯 Core Features
- **Dual Database Support**: Sync to MySQL, Supabase, or both simultaneously
- **Real-time Sync**: Automatic stat synchronization on player join/quit
- **Batch Processing**: Efficient bulk operations with rate limiting
- **PlaceholderAPI Integration**: Sync custom placeholders (Vault, Towny, etc.)
- **Towny Integration**: Complete town leveling and achievement system
- **Export System**: Export stats to JSON files
- **Clean Stat Names**: Removes "minecraft:" prefixes for cleaner data

### 🚀 Supabase Features (New!)
- **JSONB Storage**: Flexible stat storage using PostgreSQL JSONB
- **REST API**: Uses Supabase REST API for efficient data transfer
- **Rate Limiting**: Built-in rate limiting to respect Supabase limits
- **Batch Processing**: Processes players one at a time with configurable delays
- **UPSERT Operations**: Efficient insert/update operations
- **Real-time Webhooks**: Ready for future real-time features

## Quick Start

### 1. Download & Install
1. Download the latest JAR from releases
2. Place it in your `plugins/` folder
3. Start your server to generate config files
4. Stop the server and configure your database

### 2. Choose Your Database

#### Option A: MySQL Only
```yaml
database:
  type: "mysql"

mysql:
  enabled: true
  url: "jdbc:mysql://localhost:3306/playerstats"
  user: "your_username"
  password: "your_password"
```

#### Option B: Supabase Only
```yaml
database:
  type: "supabase"

supabase:
  enabled: true
  url: "https://your-project.supabase.co"
  key: "your-anon-key"
```

#### Option C: Both Databases
```yaml
database:
  type: "both"

mysql:
  enabled: true
  url: "jdbc:mysql://localhost:3306/playerstats"
  user: "your_username"
  password: "your_password"

supabase:
  enabled: true
  url: "https://your-project.supabase.co"
  key: "your-anon-key"
```

### 3. Database Setup

#### MySQL Setup
Run the provided `setup.sql` script in your MySQL database.

#### Supabase Setup
1. Go to your Supabase project dashboard
2. Navigate to SQL Editor
3. Run the provided `supabase_setup.sql` script
4. Copy your project URL and anon key from Settings > API

### 4. Configure & Start
1. Edit `plugins/Progression/config.yml`
2. Set your database credentials
3. Start your server
4. Use `/sqlstats sync` for initial sync

## Configuration

### Database Configuration
```yaml
# Choose database type: mysql, supabase, or both
database:
  type: "mysql"

# MySQL Configuration
mysql:
  enabled: true
  url: "jdbc:mysql://localhost:3306/playerstats"
  user: "your_username"
  password: "your_password"

# Supabase Configuration
supabase:
  enabled: false
  url: "https://your-project.supabase.co"
  key: "your-anon-key"
  
  # Performance settings for rate limiting
  performance:
    batch_size: 1  # Sync one player at a time
    batch_delay_ms: 1000  # 1 second between batches
    max_concurrent_requests: 1  # Sequential processing
    timeout_seconds: 30  # Request timeout
    
  # Sync settings
  sync:
    on_player_quit: true  # Update when player leaves
    batch_sync_interval: 300000  # 5 minutes between batch syncs
    real_time_updates: false  # Disable for performance
```

### Sync Settings
```yaml
# Sync intervals and settings
sync-interval-ticks: 1728000  # 24 hours in ticks
minimum-playtime-ticks: 72000 # 1 hour minimum playtime required
sync-on-join: true
```

### PlaceholderAPI Integration
```yaml
placeholderapi:
  enabled: true
  placeholders: # Enter placeholders without % (e.g., vault_balance, not %vault_balance%)
    - vault_eco_balance
  blacklist: # Enter placeholders to exclude without %
    - townyadvanced_townboard
    - townyadvanced_nationboard
```

### Towny Integration
```yaml
towny:
  enabled: true
  
  # Town leveling system
  leveling:
    enabled: true
    xp_sources:
      population: 10      # XP per resident
      nation_member: 50   # XP for being in a nation
      capital: 100        # XP for being a capital
      plot_count: 5       # XP per plot
      balance: 1          # XP per 1000 currency
      age: 2              # XP per day of age
      size: 3             # XP per chunk
```

**Commands:**
- `/townstats info <town>` - Show town information
- `/townstats level <town>` - Show town level
- `/townstats achievements <town>` - Show town achievements with progress
- `/townstats claim <town> <achievement> <tier>` - Claim town achievement
- `/townstats leaderboard` - Show town rankings
- `/townstats sync` - Manually sync towns (admin)
- `/townstats reset <town>` - Reset town level and achievements (admin)

See [TOWNY_INTEGRATION.md](TOWNY_INTEGRATION.md) for complete documentation.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sqlstats sync` | `progression.sqlstats.sync` | Sync all player stats |
| `/sqlstats export [json]` | `progression.sqlstats.export` | Export stats to file |
| `/sqlstats view <player> <category>` | `progression.sqlstats.view` | View player stats |
| `/sqlstats reload` | `progression.sqlstats.reload` | Reload configuration |
| `/sqlstats status` | `progression.sqlstats.status` | Check plugin status |
| `/sqlstats help` | `progression.sqlstats.help` | Show help |
| `/townstats info <town>` | `progression.towny.info` | Show town information |
| `/townstats level <town>` | `progression.towny.level` | Show town level |
| `/townstats achievements <town>` | `progression.towny.achievements` | Show town achievements |
| `/townstats claim <town> <achievement> <tier>` | `progression.towny.admin` | Claim town achievement |
| `/townstats leaderboard` | `progression.towny.leaderboard` | Show town rankings |
| `/townstats sync` | `progression.towny.admin` | Manually sync towns |
| `/townstats reset <town>` | `progression.towny.admin` | Reset town level and achievements |

## Database Schema

### MySQL Schema
```sql
-- Players table
CREATE TABLE players (
    uuid VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    last_seen BIGINT NOT NULL
);

-- Stats tables (one per category)
CREATE TABLE stats_minecraft_custom (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(uuid)
);
```

### Supabase Schema
```sql
-- Players table
CREATE TABLE players (
    uuid UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    last_seen BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Player stats with JSONB
CREATE TABLE player_stats (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid),
    stats JSONB NOT NULL DEFAULT '{}',
    last_updated BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

## Supabase Integration

### Advantages
- **JSONB Storage**: Flexible schema for any stat type
- **Real-time Ready**: Built for real-time web applications
- **Built-in Auth**: Easy integration with Supabase Auth
- **Edge Functions**: Serverless functions for custom logic
- **Auto-scaling**: Handles traffic spikes automatically

### Performance Optimization
- **Rate Limiting**: Respects Supabase rate limits
- **Batch Processing**: Processes one player at a time
- **Efficient UPSERTs**: Uses PostgreSQL's ON CONFLICT
- **Connection Pooling**: Reuses HTTP connections

### Example Queries
```sql
-- Get player stats
SELECT stats FROM player_stats WHERE player_uuid = 'uuid-here';

-- Get specific stat
SELECT stats->>'minecraft.custom.play_time' as play_time 
FROM player_stats WHERE player_uuid = 'uuid-here';

-- Get top players by stat
SELECT p.name, (ps.stats->>'minecraft.mined.diamond_ore')::int as diamonds
FROM players p 
JOIN player_stats ps ON p.uuid = ps.player_uuid
ORDER BY diamonds DESC LIMIT 10;
```

## Troubleshooting

### Common Issues

#### Plugin Won't Start
- Check database connection settings
- Ensure database exists and user has permissions
- Verify Supabase URL and API key

#### Stats Not Syncing
- Check if players meet minimum playtime requirement
- Verify database tables exist
- Check console for error messages

#### Supabase Rate Limits
- Increase `batch_delay_ms` in config
- Reduce `batch_size` to 1
- Check Supabase dashboard for rate limit errors

### Debug Commands
```bash
# Check plugin status
/sqlstats status

# View specific player stats
/sqlstats view PlayerName minecraft.custom

# Force sync all players
/sqlstats sync

# Check server version compatibility
/sqlstats versioncheck
```

## Development

### Building from Source
```bash
git clone https://github.com/your-username/PlayerStatsToMySQL.git
cd PlayerStatsToMySQL
mvn clean package
```

### Dependencies
- **Spigot API**: Minecraft server API
- **HikariCP**: Database connection pooling
- **OkHttp**: HTTP client for Supabase
- **Gson**: JSON processing
- **PostgreSQL JDBC**: Supabase database driver

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: [GitHub Issues](https://github.com/your-username/PlayerStatsToMySQL/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-username/PlayerStatsToMySQL/discussions)
- **Wiki**: [GitHub Wiki](https://github.com/your-username/PlayerStatsToMySQL/wiki)

## Changelog

### v1.0 (Current)
- ✨ Added Supabase support
- 🔄 Dual database support (MySQL + Supabase)
- 🚀 Improved performance with rate limiting
- 🧹 Cleaner stat names (removed "minecraft:" prefix)
- 🔧 Better error handling and logging
- 📊 Enhanced configuration options

### v2.0 (Previous)
- Initial MySQL-only release
- Basic stat syncing
- PlaceholderAPI integration
- Towny integration