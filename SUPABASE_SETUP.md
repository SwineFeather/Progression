# Supabase Setup Guide

This guide will help you set up Supabase for use with PlayerStatsToMySQL plugin.

## Prerequisites

- A Supabase account (free at [supabase.com](https://supabase.com))
- Basic knowledge of SQL (optional, we provide the setup script)

## Step 1: Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and sign up/login
2. Click "New Project"
3. Choose your organization
4. Enter project details:
   - **Name**: `playerstats` (or your preferred name)
   - **Database Password**: Choose a strong password
   - **Region**: Select closest to your server
5. Click "Create new project"
6. Wait for the project to be created (usually 1-2 minutes)

## Step 2: Get Your Project Credentials

1. In your project dashboard, go to **Settings** → **API**
2. Copy the following values:
   - **Project URL**: `https://your-project.supabase.co`
   - **Anon Key**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

## Step 3: Set Up Database Tables

1. In your Supabase dashboard, go to **SQL Editor**
2. Click "New Query"
3. Copy and paste the contents of `supabase_setup.sql`
4. Click "Run" to execute the script

This will create:
- `players` table for player information
- `player_stats` table with JSONB for flexible stat storage
- Proper indexes for performance
- Row Level Security (RLS) policies
- Helper functions for querying stats

## Step 4: Configure the Plugin

1. Edit your `plugins/PlayerStatsToMySQL/config.yml`
2. Update the Supabase section:

```yaml
database:
  type: "supabase"  # or "both" if using MySQL too

supabase:
  enabled: true
  url: "https://your-project.supabase.co"  # Your project URL
  key: "your-anon-key"  # Your anon key
  
  # Performance settings (recommended for most servers)
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

## Step 5: Test the Connection

1. Start your Minecraft server
2. Check the console for Supabase connection messages
3. Use `/sqlstats status` to verify both databases are connected
4. Use `/sqlstats sync` to perform initial sync

## Performance Optimization

### For Small Servers (< 50 players)
```yaml
supabase:
  performance:
    batch_size: 1
    batch_delay_ms: 500  # 0.5 seconds
    max_concurrent_requests: 1
    timeout_seconds: 30
```

### For Medium Servers (50-200 players)
```yaml
supabase:
  performance:
    batch_size: 1
    batch_delay_ms: 1000  # 1 second
    max_concurrent_requests: 1
    timeout_seconds: 30
```

### For Large Servers (> 200 players)
```yaml
supabase:
  performance:
    batch_size: 1
    batch_delay_ms: 2000  # 2 seconds
    max_concurrent_requests: 1
    timeout_seconds: 60
```

## Database Schema

### Players Table
```sql
CREATE TABLE players (
    uuid UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    last_seen BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Player Stats Table
```sql
CREATE TABLE player_stats (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid),
    stats JSONB NOT NULL DEFAULT '{}',
    last_updated BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

## Example Queries

### Get Player Stats
```sql
-- Get all stats for a player
SELECT stats FROM player_stats WHERE player_uuid = 'player-uuid-here';

-- Get specific stat
SELECT stats->>'minecraft.custom.play_time' as play_time 
FROM player_stats WHERE player_uuid = 'player-uuid-here';
```

### Leaderboards
```sql
-- Top players by diamonds mined
SELECT p.name, (ps.stats->>'minecraft.mined.diamond_ore')::int as diamonds
FROM players p 
JOIN player_stats ps ON p.uuid = ps.player_uuid
WHERE ps.stats ? 'minecraft.mined.diamond_ore'
ORDER BY diamonds DESC LIMIT 10;

-- Top players by play time
SELECT p.name, (ps.stats->>'minecraft.custom.play_time')::bigint as play_time
FROM players p 
JOIN player_stats ps ON p.uuid = ps.player_uuid
WHERE ps.stats ? 'minecraft.custom.play_time'
ORDER BY play_time DESC LIMIT 10;
```

### Recent Activity
```sql
-- Players active in last 24 hours
SELECT p.name, p.last_seen
FROM players p
WHERE p.last_seen > EXTRACT(EPOCH FROM NOW() - INTERVAL '24 hours') * 1000
ORDER BY p.last_seen DESC;
```

## Troubleshooting

### Connection Issues
- **Error**: "Failed to connect to Supabase"
  - Check your project URL and anon key
  - Ensure you copied the anon key (not service role key)
  - Verify your project is active in Supabase dashboard

### Rate Limit Issues
- **Error**: "429 Too Many Requests"
  - Increase `batch_delay_ms` in config
  - Reduce `batch_size` to 1
  - Check Supabase dashboard for rate limit usage

### Permission Issues
- **Error**: "403 Forbidden"
  - Ensure RLS policies are set up correctly
  - Run the setup script again
  - Check if your anon key is correct

### Data Not Syncing
- Check if players meet minimum playtime requirement
- Verify tables exist in Supabase dashboard
- Check console for specific error messages
- Use `/sqlstats status` to verify connection

## Advanced Features

### Real-time Subscriptions
Supabase supports real-time subscriptions. You can listen for changes:

```javascript
// In your web app
const subscription = supabase
  .channel('player_stats')
  .on('postgres_changes', 
    { event: '*', schema: 'public', table: 'player_stats' },
    (payload) => {
      console.log('Stats updated:', payload)
    }
  )
  .subscribe()
```

### Edge Functions
Create serverless functions for custom logic:

```sql
-- Example: Calculate player rank
CREATE OR REPLACE FUNCTION get_player_rank(player_uuid_param UUID)
RETURNS INTEGER AS $$
BEGIN
  RETURN (
    SELECT COUNT(*) + 1
    FROM player_stats ps1
    WHERE (ps1.stats->>'minecraft.custom.play_time')::bigint > 
          (SELECT (ps2.stats->>'minecraft.custom.play_time')::bigint 
           FROM player_stats ps2 
           WHERE ps2.player_uuid = player_uuid_param)
  );
END;
$$ LANGUAGE plpgsql;
```

## Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review your Supabase dashboard for errors
3. Check the plugin console logs
4. Open an issue on GitHub with detailed information

## Next Steps

Once Supabase is set up, you can:
1. Create a web dashboard using Supabase's built-in tools
2. Set up real-time notifications
3. Create custom analytics with SQL queries
4. Integrate with other services via webhooks 