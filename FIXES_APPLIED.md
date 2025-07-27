# Fixes Applied to Resolve Progression Plugin Issues

## Issues Identified and Fixed

### 1. NullPointerException in ExportTask
**Problem**: The export task was trying to use `dbManager` when it was null (Supabase-only mode).

**Fix**: Modified `Main.java` to check if `dbManager` is available before running the export task:
```java
// Only run export if we have a database manager (MySQL mode)
if (dbManager != null && dbManager.isConnected()) {
    new ExportTask(Main.this, dbManager).exportStats(null, "json");
} else {
    logManager.info("Skipping export task - no MySQL database available (Supabase-only mode)");
}
```

### 2. Date/Time Field Value Out of Range Error
**Problem**: The code was sending timestamp strings to fields expecting BIGINT values.

**Fix**: Modified `SupabaseManager.java` to send the correct data types:
- `last_seen` field: Changed from timestamp string to `System.currentTimeMillis()` (BIGINT)
- `last_updated` field: Changed from timestamp string to `System.currentTimeMillis()` (BIGINT)

### 3. Missing 'stats' Column in player_stats Table
**Problem**: The database schema cache couldn't find the 'stats' column, indicating a schema mismatch.

**Fix**: Created comprehensive database migration script `fix_all_supabase_issues.sql` that:
- Recreates all tables with the correct JSONB structure
- Ensures proper column types and constraints
- Adds all necessary indexes and triggers
- Includes town-related tables for Towny integration

### 4. 404 Errors for Town Sync
**Problem**: Town sync was trying to access tables (`town_stats`, `town_levels`) that didn't exist.

**Fix**: Added town-related tables to the migration script:
- `town_stats` - for storing town statistics
- `town_levels` - for storing town level progression
- `town_achievements` - for storing town achievements

## Files Modified

### 1. `src/main/java/com/swinefeather/progression/Main.java`
- Added null check for `dbManager` in export task

### 2. `src/main/java/com/swinefeather/progression/SupabaseManager.java`
- Fixed timestamp format for `last_seen` field
- Fixed timestamp format for `last_updated` field

### 3. `fix_all_supabase_issues.sql` (New)
- Comprehensive database migration script
- Fixes all schema issues
- Adds missing tables and indexes
- Sets up proper RLS policies

### 4. `fix_player_stats_schema.sql` (New)
- Specific migration for player_stats table
- Converts from key-value to JSONB structure

## How to Apply the Fixes

### 1. Database Migration
Run the comprehensive migration script in your Supabase SQL editor:
```sql
-- Copy and paste the contents of fix_all_supabase_issues.sql
-- This will recreate all tables with the correct structure
```

### 2. Plugin Restart
After applying the database migration:
1. Stop your Minecraft server
2. The plugin will automatically use the fixed code on restart
3. Start your Minecraft server

### 3. Verification
Check the console for:
- No more NullPointerException in ExportTask
- No more "date/time field value out of range" errors
- No more "Could not find the 'stats' column" errors
- Successful town sync operations

## Expected Results

After applying these fixes:
1. ✅ Export task will skip gracefully in Supabase-only mode
2. ✅ Player data will sync without timestamp errors
3. ✅ Stats data will sync using the correct JSONB structure
4. ✅ Town sync will work with the new town tables
5. ✅ All database operations will use proper data types

## Troubleshooting

If you still see errors after applying the fixes:

1. **Database Migration Issues**: Make sure you ran the complete migration script
2. **Permission Issues**: Verify RLS policies are set up correctly
3. **Connection Issues**: Check your Supabase URL and API key
4. **Rate Limiting**: The plugin is configured for conservative rate limiting

## Configuration Notes

The plugin is configured for Supabase-only mode with:
- Conservative rate limiting (1 request at a time, 5-second delays)
- Batch processing disabled for better reliability
- Export task disabled for Supabase-only mode

These settings ensure stable operation with Supabase's rate limits and prevent the issues that were occurring. 