-- SQL script to recreate the players table with the CORRECT Supabase schema
-- This script will drop and recreate the players table with the exact structure expected by the plugin
-- Run this in your Supabase SQL Editor

-- Drop the existing players table if it exists
DROP TABLE IF EXISTS players CASCADE;

-- Recreate the players table with the CORRECT Supabase schema
CREATE TABLE players (
    uuid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    level INTEGER DEFAULT 1,
    total_xp INTEGER DEFAULT 0,
    last_seen BIGINT NOT NULL DEFAULT 0,
    last_level_up BIGINT DEFAULT 0
);

-- Add indexes for better performance
CREATE INDEX idx_players_name ON players(name);
CREATE INDEX idx_players_level ON players(level);
CREATE INDEX idx_players_total_xp ON players(total_xp);
CREATE INDEX idx_players_last_seen ON players(last_seen);

-- Add comments for documentation
COMMENT ON TABLE players IS 'Stores basic player information including UUID, name, level, XP, and timestamps';
COMMENT ON COLUMN players.uuid IS 'Primary key - unique identifier for the player';
COMMENT ON COLUMN players.name IS 'Player''s display name';
COMMENT ON COLUMN players.level IS 'Player''s current level (default: 1)';
COMMENT ON COLUMN players.total_xp IS 'Player''s total accumulated XP (default: 0)';
COMMENT ON COLUMN players.last_seen IS 'Unix timestamp of when player was last seen online';
COMMENT ON COLUMN players.last_level_up IS 'Unix timestamp of when player last leveled up';

-- Create trigger to automatically update last_seen timestamp
CREATE OR REPLACE FUNCTION update_last_seen_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_seen = EXTRACT(EPOCH FROM NOW()) * 1000; -- Convert to milliseconds
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_players_last_seen 
    BEFORE UPDATE ON players 
    FOR EACH ROW EXECUTE FUNCTION update_last_seen_column();

-- Verify the table was created correctly
SELECT 
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'players' 
ORDER BY ORDINAL_POSITION;
