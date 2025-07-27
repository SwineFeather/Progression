# Towny Integration Guide

The Progression plugin now includes comprehensive Towny integration, allowing towns to level up and earn achievements based on their development and activities.

## Features

### Town Leveling System
- **20 Town Levels**: From "Outpost" to "Industrial Town"
- **XP Sources**: Population, balance, nation membership, plot count, age, and more
- **Configurable**: All XP sources and level requirements can be customized
- **Automatic Sync**: Towns are synced periodically and on important events

### Town Achievements
- **Population Milestones**: Reward towns for growing their population
- **Wealth Milestones**: Reward towns for accumulating wealth
- **Age Milestones**: Reward towns for surviving and thriving over time
- **Size Milestones**: Reward towns for expanding their territory

### Database Integration
- **Supabase Support**: All town data is stored in Supabase
- **Real-time Updates**: Town data is synced automatically
- **Leaderboards**: View town rankings and progress

## Configuration

### Basic Setup
```yaml
# Towny integration
towny:
  enabled: true
  
  # Town leveling system
  leveling:
    enabled: true
    xp_sources:
      # XP per resident in the town
      population: 10
      # XP for being in a nation
      nation_member: 50
      # XP for being a capital town
      capital: 100
      # XP for being independent (not in a nation)
      independent: 75
      # XP per plot owned by the town
      plot_count: 5
      # XP for town balance (per 1000 currency)
      balance: 1
      # XP for town age (per day)
      age: 2
      # XP for town size (per chunk)
      size: 3
```

### Town Levels
The plugin includes 20 predefined town levels with increasing XP requirements:

1. **Outpost** (0 XP) - A small gathering of settlers
2. **Camp** (25 XP) - A temporary settlement
3. **Settlement** (60 XP) - A permanent home for pioneers
4. **Hamlet** (110 XP) - A small but growing community
5. **Village** (175 XP) - A thriving rural settlement
6. **Town** (250 XP) - A bustling center of activity
7. **Market Town** (350 XP) - A hub of commerce and trade
8. **Port Town** (475 XP) - A coastal trading center
9. **Mining Town** (625 XP) - A settlement built on resources
10. **Farming Town** (800 XP) - A settlement sustained by agriculture
11. **Trading Post** (1000 XP) - A center of regional commerce
12. **Crossroads** (1250 XP) - A town at the intersection of trade routes
13. **Fortress Town** (1550 XP) - A well-defended settlement
14. **University Town** (1900 XP) - A center of learning and knowledge
15. **Craftsmen Town** (2300 XP) - A settlement of skilled artisans
16. **Merchant Town** (2750 XP) - A wealthy trading center
17. **Guild Town** (3250 XP) - A settlement of organized crafts
18. **Harbor Town** (3800 XP) - A major port settlement
19. **Frontier Town** (4400 XP) - A settlement on the edge of civilization
20. **Industrial Town** (5100 XP) - A center of manufacturing

### Town Achievements
```yaml
towny:
  achievements:
    enabled: true
    
    # Population achievements
    population_milestone:
      name: "Population Growth"
      description: "Reach population milestones"
      stat: "population"
      color: "#10b981"
      tiers:
        1:
          name: "Small Community"
          description: "5 residents"
          threshold: 5
          points: 50
        2:
          name: "Growing Town"
          description: "10 residents"
          threshold: 10
          points: 100
        3:
          name: "Thriving Community"
          description: "25 residents"
          threshold: 25
          points: 200
        4:
          name: "Large Town"
          description: "50 residents"
          threshold: 50
          points: 400
        5:
          name: "Metropolis"
          description: "100 residents"
          threshold: 100
          points: 800
```

### Sync Settings
```yaml
towny:
  sync:
    # Sync town data when players join/leave towns
    on_town_change: true
    # Sync town data periodically
    interval_ticks: 72000  # 1 hour
    # Sync town data on server startup
    on_startup: true
    # Sync town data when town balance changes
    on_balance_change: true
    # Sync town data when town size changes
    on_size_change: true
```

### Notifications
```yaml
towny:
  notifications:
    # Announce town level ups
    level_ups: true
    # Announce town achievements
    achievements: true
    # Show town level in chat
    show_level_in_chat: false
    # Show town level in tab list
    show_level_in_tab: false
```

## Commands

### `/townstats info <town>`
Shows detailed information about a town including:
- Population
- Balance
- Nation membership
- Plot count
- Size
- Age
- Mayor
- Current level and XP

### `/townstats level <town>`
Shows level information for a town including:
- Current level and name
- Total XP
- XP required for next level
- Progress status

### `/townstats achievements <town>`
Shows town achievements with interactive claiming interface including:
- Progress bars for each achievement
- Clickable claim buttons for completed achievements
- Achievement tiers and rewards
- Summary of completed vs total achievements

### `/townstats claim <town> <achievement> <tier>`
Manually claim a town achievement. Used by the interactive interface.

### `/townstats leaderboard` or `/townstats top`
Shows the top 10 towns ranked by total XP.

### `/townstats sync` (Admin)
Manually syncs all towns to the database.

### `/townstats reset <town>` (Admin)
Resets a town's level to 1 and clears all achievements. This is useful for:
- Testing the system
- Correcting data issues
- Starting fresh with a town

**Usage:** `/townstats reset <town_name>`

**Permission:** `progression.towny.admin`

**Example:**
```
/townstats reset SpawnTown
```

### `/townstats reload` (Admin)
Reloads the Towny configuration from config.yml.

## Permissions

- `progression.towny.admin` - Access to admin commands (sync, reload)
- `progression.towny.info` - View town information
- `progression.towny.level` - View town levels
- `progression.towny.leaderboard` - View town leaderboards

## Database Schema

The plugin creates the following tables in Supabase:

### `town_stats`
Stores current town statistics:
- `town_name` - Name of the town
- `population` - Number of residents
- `balance` - Town's bank balance
- `nation` - Nation name (or "none")
- `nation_member` - Whether town is in a nation (0/1)
- `capital` - Whether town is a capital (0/1)
- `independent` - Whether town is independent (0/1)
- `plot_count` - Number of town plots
- `size` - Town size in chunks
- `age` - Town age in days
- `mayor` - Town mayor's name
- `last_updated` - Last update timestamp

### `town_levels`
Stores town level progression:
- `town_name` - Name of the town
- `level` - Current town level
- `total_xp` - Total XP earned
- `last_updated` - Last update timestamp

### `town_achievements`
Stores unlocked town achievements:
- `town_name` - Name of the town
- `achievement_id` - Achievement identifier
- `tier` - Achievement tier
- `unlocked_at` - When achievement was unlocked

### `town_leaderboard`
A view that shows town rankings based on total XP.

## API Integration

The plugin integrates with Towny's API to collect the following data:

### Town Information
- **Name**: Town name
- **Population**: Number of residents
- **Balance**: Town's bank balance
- **Nation**: Nation membership and status
- **Capital Status**: Whether the town is a nation capital
- **Independence**: Whether the town is independent
- **Plot Count**: Number of town plots
- **Size**: Town size in chunks
- **Age**: Days since town founding
- **Mayor**: Current town mayor

### Automatic Events
The plugin automatically syncs town data when:
- Server starts up
- Periodic sync interval (configurable)
- Town balance changes
- Town size changes
- Players join/leave towns

## Troubleshooting

### Towny Not Found
If you see "Towny plugin not found!" in the console:
1. Make sure Towny is installed and enabled
2. Check that Towny is listed in the `softdepend` section of plugin.yml
3. Restart the server

### No Town Data
If towns aren't being synced:
1. Check that `towny.enabled: true` in config.yml
2. Verify Towny is working properly
3. Check the console for error messages
4. Use `/townstats sync` to manually sync towns

### Database Errors
If you see database errors:
1. Make sure Supabase is properly configured
2. Run the updated schema from `supabase_schema_complete_fix.sql`
3. Check that the anon role has proper permissions

### Performance Issues
If you experience performance issues:
1. Increase the sync interval in config.yml
2. Disable real-time updates
3. Reduce the frequency of town data collection

## Customization

### Adding Custom XP Sources
You can add custom XP sources by modifying the `xp_sources` section in config.yml:

```yaml
towny:
  leveling:
    xp_sources:
      # Existing sources...
      custom_stat: 5  # 5 XP per unit of custom_stat
```

### Custom Town Levels
You can modify town levels by editing the `levels` section:

```yaml
towny:
  leveling:
    levels:
      21:
        name: "Mega City"
        description: "A massive urban center"
        xp_required: 10000
        color: "#dc2626"
```

### Custom Achievements
You can add custom achievements by adding them to the `achievements` section:

```yaml
towny:
  achievements:
    custom_achievement:
      name: "Custom Achievement"
      description: "Custom achievement description"
      stat: "custom_stat"
      color: "#3b82f6"
      tiers:
        1:
          name: "First Tier"
          description: "First tier description"
          threshold: 10
          points: 100
```

## Support

For issues or questions about the Towny integration:
1. Check the console for error messages
2. Verify your configuration
3. Test with a fresh config
4. Report issues with detailed information

The Towny integration is designed to be robust and handle various edge cases, but if you encounter issues, the plugin will log detailed information to help with troubleshooting. 