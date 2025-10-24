-- SQL script to recreate the players table with all necessary columns
-- This script will drop and recreate the players table with the correct structure
-- Run this in your database SQL editor (MySQL or Supabase)

-- Drop the existing players table if it exists
DROP TABLE IF EXISTS players CASCADE;

-- Recreate the players table with all necessary columns
CREATE TABLE players (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    first_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen BIGINT NOT NULL DEFAULT 0
);

-- Add indexes for better performance
CREATE INDEX idx_players_name ON players(player_name);
CREATE INDEX idx_players_first_joined ON players(first_joined);
CREATE INDEX idx_players_last_updated ON players(last_updated);

-- Add comments for documentation
COMMENT ON TABLE players IS 'Stores basic player information including UUID, name, and timestamps';
COMMENT ON COLUMN players.player_uuid IS 'Primary key - unique identifier for the player';
COMMENT ON COLUMN players.player_name IS 'Player''s display name (max 16 characters)';
COMMENT ON COLUMN players.first_joined IS 'Timestamp when player first joined the server';
COMMENT ON COLUMN players.last_updated IS 'Timestamp when player info was last updated';
COMMENT ON COLUMN players.last_seen IS 'Unix timestamp of when player was last seen online';

-- Create trigger to automatically update last_updated timestamp (PostgreSQL compatibility)
CREATE OR REPLACE FUNCTION update_last_updated_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_players_last_updated 
    BEFORE UPDATE ON players 
    FOR EACH ROW EXECUTE FUNCTION update_last_updated_column();

-- Insert a default player record if needed (optional)
-- INSERT INTO players (player_uuid, player_name) VALUES ('00000000-0000-0000-0000-000000000000', 'System');

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
