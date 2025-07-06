# PlayerStatsToMySQL

A powerful Minecraft plugin that synchronizes player statistics to a MySQL database, providing comprehensive data tracking and export capabilities.

## 🚀 Features

- **Automatic Stat Sync**: Automatically syncs player statistics to MySQL database
- **Real-time Updates**: Syncs stats when players leave the server
- **PlaceholderAPI Integration**: Tracks custom placeholders and plugin data
- **Towny Integration**: Syncs Towny data including town membership, nation info, and balances
- **Data Export**: Export player data to JSON format with compression support
- **Clean Stat Names**: Removes "minecraft:" prefix for cleaner database storage
- **Configurable Sync Intervals**: Customizable sync timing and playtime requirements
- **Comprehensive Logging**: Detailed logging for debugging and monitoring

## 📋 Requirements

- **Minecraft Server**: 1.16.5 - 1.21.1 (Paper/Spigot recommended)
- **Java**: 17 or higher
- **MySQL Database**: 5.7 or higher
- **Plugins** (optional but recommended):
  - PlaceholderAPI
  - Towny (for town/nation data)

## 📦 Installation

1. **Download the Plugin**
   - Download `PlayerStatsToMySQL-1.0.jar` from the releases
   - Place it in your server's `plugins` folder

2. **Database Setup**
   - Create a MySQL database for the plugin
   - Note down the database URL, username, and password

3. **Configure the Plugin**
   - Start your server once to generate the config file
   - Edit `plugins/PlayerStatsToMySQL/config.yml`
   - Update the MySQL connection details

4. **Restart the Server**
   - Restart your server to load the plugin
   - Check the console for any connection errors

## ⚙️ Configuration

### Basic Configuration

```yaml
mysql:
  enabled: true
  url: "jdbc:mysql://localhost:3306/playerstats"
  user: "your_username"
  password: "your_password"

# Sync settings
sync-interval-ticks: 1728000  # 24 hours in ticks
minimum-playtime-ticks: 72000 # 1 hour minimum playtime
sync-on-join: true

# PlaceholderAPI integration
placeholderapi:
  enabled: true
  placeholders:
    - vault_eco_balance
  blacklist:
    - townyadvanced_townboard
    - townyadvanced_nationboard

# Towny integration
towny:
  enabled: true

# Export settings
export:
  interval-ticks: 72000
  compression: true
  cleanup:
    enabled: false
    active_days: 30
    keep_files_days: 90

# Logging
logging:
  level: verbose
```

### Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `mysql.enabled` | Enable MySQL connection | `true` |
| `mysql.url` | MySQL connection URL | - |
| `mysql.user` | MySQL username | - |
| `mysql.password` | MySQL password | - |
| `sync-interval-ticks` | How often to sync online players (in ticks) | `1728000` (24h) |
| `minimum-playtime-ticks` | Minimum playtime required for sync | `72000` (1h) |
| `sync-on-join` | Sync stats when players join | `true` |
| `placeholderapi.enabled` | Enable PlaceholderAPI integration | `true` |
| `towny.enabled` | Enable Towny integration | `true` |
| `export.interval-ticks` | How often to export data (in ticks) | `72000` (1h) |
| `export.compression` | Compress exported files | `true` |
| `logging.level` | Logging level (minimal/verbose/debug) | `verbose` |

## 🗄️ Database Schema

The plugin creates the following tables:

### `players`
- `player_uuid` (VARCHAR(36)) - Player's UUID
- `player_name` (VARCHAR(16)) - Player's username
- `first_joined` (TIMESTAMP) - First time player was synced
- `last_updated` (TIMESTAMP) - Last sync time

### `stats_[category]`
- `player_uuid` (VARCHAR(36)) - Player's UUID
- `stat_key` (VARCHAR(255)) - Stat name (without "minecraft:" prefix)
- `stat_value` (BIGINT) - Stat value

Categories: `broken`, `crafted`, `dropped`, `killed`, `killed_by`, `picked_up`, `mined`, `custom`, `used`, `towny`

### `stats_placeholders`
- `player_uuid` (VARCHAR(36)) - Player's UUID
- `placeholder_key` (VARCHAR(255)) - Placeholder name
- `value` (LONGTEXT) - Placeholder value

## 📊 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sqlstats sync` | `playerstatstomysql.sqlstats.sync` | Sync all player stats to database |
| `/sqlstats export [json]` | `playerstatstomysql.sqlstats.export` | Export stats to JSON file |
| `/sqlstats view <player> <category>` | `playerstatstomysql.sqlstats.view` | View player stats for a category |
| `/sqlstats reload` | `playerstatstomysql.sqlstats.reload` | Reload configuration |
| `/sqlstats placeholder <list\|add\|blacklist>` | `playerstatstomysql.sqlstats.placeholder` | Manage placeholders |
| `/sqlstats status` | `playerstatstomysql.sqlstats.status` | Check plugin status |
| `/sqlstats versioncheck` | `playerstatstomysql.sqlstats.versioncheck` | Check server version compatibility |
| `/sqlstats cleanup` | `playerstatstomysql.sqlstats.cleanup` | Clean up old data |
| `/sqlstats stop` | `playerstatstomysql.sqlstats.stop` | Disable plugin |
| `/sqlstats start` | `playerstatstomysql.sqlstats.start` | Enable plugin |
| `/sqlstats help` | `playerstatstomysql.sqlstats.help` | Show help message |

## 🔧 Usage Examples

### Initial Setup
```bash
# First, sync all existing player stats
/sqlstats sync

# Check plugin status
/sqlstats status

# View a player's stats
/sqlstats view PlayerName mined
```

### Exporting Data
```bash
# Export current data to JSON
/sqlstats export

# Export with specific format (only JSON supported)
/sqlstats export json
```

### Managing Placeholders
```bash
# List available placeholders
/sqlstats placeholder list

# Add a new placeholder
/sqlstats placeholder add vault_eco_balance

# Blacklist a placeholder
/sqlstats placeholder blacklist some_placeholder
```

## 📈 Data Export

The plugin automatically exports data to JSON files in the `plugins/PlayerStatsToMySQL/exports/` directory. Files are named with timestamps and can be compressed.

Example export structure:
```json
{
  "timestamp": "2024-01-01T12:00:00",
  "players": [
    {
      "uuid": "player-uuid",
      "name": "PlayerName",
      "first_joined": "2024-01-01T10:00:00",
      "last_updated": "2024-01-01T12:00:00",
      "stats_mined": {
        "stone": 1000,
        "dirt": 500
      },
      "stats_placeholders": [
        {"key": "vault_eco_balance", "value": "1000.0"}
      ]
    }
  ]
}
```

## 🏘️ Towny Integration

When Towny integration is enabled, the plugin tracks:
- Town membership
- Nation membership
- Mayor/King status
- Town and nation balances

Towny data is stored in the `stats_towny` table with keys like:
- `town` - Town name
- `nation` - Nation name
- `is_mayor` - Whether player is town mayor
- `is_king` - Whether player is nation king
- `town_balance` - Town's balance
- `nation_balance` - Nation's balance

## 🐛 Troubleshooting

### Common Issues

**Database Connection Failed**
- Check MySQL credentials in config.yml
- Ensure MySQL server is running
- Verify database exists and user has proper permissions

**Stats Not Syncing**
- Check if players meet minimum playtime requirement
- Verify plugin is enabled (`/sqlstats status`)
- Check console for error messages

**Towny Data Not Appearing**
- Ensure Towny plugin is installed and enabled
- Check `towny.enabled` in config.yml
- Verify Towny API is accessible

### Logging

Set `logging.level` to `debug` for detailed information:
```yaml
logging:
  level: debug
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Built for Minecraft servers using Paper/Spigot
- Integrates with PlaceholderAPI for extended functionality
- Supports Towny for town/nation data tracking
- Uses HikariCP for efficient database connections

## 📞 Support

If you encounter any issues or have questions:
1. Check the troubleshooting section above
2. Review the console logs for error messages
3. Open an issue on GitHub with detailed information

---

**Made with ❤️ for the Minecraft community**