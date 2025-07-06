# Changelog

All notable changes to PlayerStatsToMySQL will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0] - 2024-01-01

### Added
- Initial release of PlayerStatsToMySQL
- Automatic player statistics synchronization to MySQL database
- Real-time stat updates when players leave the server
- PlaceholderAPI integration for custom plugin data
- Towny integration for town and nation data tracking
- JSON data export with compression support
- Comprehensive command system for managing the plugin
- Configurable sync intervals and playtime requirements
- Clean stat names (removes "minecraft:" prefix)
- Detailed logging system with multiple levels
- Database cleanup functionality
- Plugin enable/disable commands

### Features
- **Database Integration**: Full MySQL support with HikariCP connection pooling
- **Stat Categories**: Support for all Minecraft stat categories (broken, crafted, dropped, killed, etc.)
- **Placeholder Support**: Track custom placeholders from other plugins
- **Towny Data**: Sync town membership, nation info, mayor/king status, and balances
- **Export System**: Automated and manual JSON exports with compression
- **Command Interface**: Comprehensive command system for all plugin operations
- **Configuration**: Flexible configuration system with sensible defaults

### Technical Details
- Built for Minecraft 1.16.5 - 1.21.1
- Requires Java 17 or higher
- Uses Maven for build management
- Implements proper error handling and logging
- Follows Bukkit/Spigot plugin development best practices

### Commands
- `/sqlstats sync` - Sync all player stats
- `/sqlstats export` - Export data to JSON
- `/sqlstats view <player> <category>` - View specific stats
- `/sqlstats reload` - Reload configuration
- `/sqlstats status` - Check plugin status
- `/sqlstats help` - Show help

### Database Schema
- `players` table for player information
- `stats_[category]` tables for different stat types
- `stats_placeholders` table for PlaceholderAPI data
- `stats_towny` table for Towny integration data

---

## Version History

### Version 1.0
- Complete rewrite and modernization of the plugin
- Fixed package structure and naming
- Resolved null pointer exceptions
- Improved player quit synchronization
- Enhanced Towny integration
- Simplified configuration
- Added comprehensive documentation 