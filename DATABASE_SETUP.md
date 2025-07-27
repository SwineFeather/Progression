# Database Setup for Progression

This document explains how to set up the database for the Progression plugin.

## Supabase Setup

### 1. Create a Supabase Project
1. Go to [supabase.com](https://supabase.com)
2. Create a new project
3. Wait for the project to be ready

### 2. Set Up the Database Schema
1. Go to your Supabase project dashboard
2. Navigate to the **SQL Editor**
3. Copy the contents of `supabase_schema.sql`
4. Paste it into the SQL editor
5. Click **Run** to execute the script

### 3. Get Your API Credentials
1. Go to **Settings** → **API**
2. Copy your **Project URL** (looks like: `https://your-project.supabase.co`)
3. Copy your **anon public** key (starts with `eyJ...`)

### 4. Configure the Plugin
1. Edit your `plugins/Progression/config.yml`
2. Update the Supabase section:

```yaml
supabase:
  enabled: true
  url: "https://your-project.supabase.co"  # Your project URL
  key: "your-anon-key-here"               # Your anon public key
```

### 5. Test the Setup
1. Start your Minecraft server
2. Check the console for any database connection errors
3. Run `/sqlstats status` to verify the connection

## Database Tables

The plugin uses the following tables:

- **`players`** - Basic player information (UUID, name, last seen)
- **`player_stats`** - Player statistics stored as JSON
- **`player_awards`** - Individual awards earned by players
- **`player_medals`** - Aggregated medal counts for players
- **`player_points`** - Total points for players

## Features

- **Automatic Updates**: Triggers automatically update medal counts and total points when awards are earned
- **Performance Indexes**: Optimized for fast queries
- **Row Level Security**: Secure access policies
- **Views**: Pre-built views for leaderboards

## Troubleshooting

### Common Issues

1. **"record has no field" errors**: Make sure you ran the complete `supabase_schema.sql` script
2. **Permission errors**: Check that RLS policies are enabled and configured correctly
3. **Connection errors**: Verify your project URL and API key are correct

### JWT Secret Permission Error

If you encounter this error:
```
ERROR: 42501: permission denied to set parameter "app.jwt_secret"
```

**This is normal and expected!** The plugin doesn't need to set JWT secrets. This error typically occurs when:

1. **Supabase CLI is trying to configure JWT settings** - You can safely ignore this if you're using the web interface
2. **You're running database migrations** - The plugin doesn't require JWT configuration

**Solutions:**

**Option 1: Ignore the Error (Recommended)**
- If you're setting up the database through the Supabase web interface, you can ignore this error
- The plugin will work fine without JWT configuration

**Option 2: Use Service Role Key (Advanced)**
- If you need to run migrations, use the **service_role** key instead of the **anon** key
- Go to **Settings** → **API** → **service_role** key
- Only use this for administrative tasks, not for the plugin configuration

**Option 3: Contact Supabase Support**
- If you're on a free plan, some parameters may be restricted
- Consider upgrading to a paid plan for full access

### Reset Database
If you need to start fresh:
1. Go to Supabase SQL Editor
2. Run: `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`
3. Re-run the `supabase_schema.sql` script

## Support

If you encounter issues:
1. Check the server console for error messages
2. Verify your Supabase project settings
3. Ensure all tables were created successfully
4. For JWT errors, see the troubleshooting section above 