-- Safe Supabase Setup Script for PlayerStatsToMySQL
-- Run this in your Supabase SQL Editor
-- This script handles existing tables and columns safely

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop existing tables if they exist (WARNING: This will delete existing data)
DROP TABLE IF EXISTS player_stats CASCADE;
DROP TABLE IF EXISTS players CASCADE;

-- Create players table
CREATE TABLE players (
    uuid UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    last_seen BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create player_stats table with JSONB for flexible stats storage
CREATE TABLE player_stats (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
    stats JSONB NOT NULL DEFAULT '{}',
    last_updated BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX idx_players_name ON players(name);
CREATE INDEX idx_players_last_seen ON players(last_seen);
CREATE INDEX idx_player_stats_last_updated ON player_stats(last_updated);
CREATE INDEX idx_player_stats_gin ON player_stats USING GIN (stats);

-- Enable Row Level Security (RLS)
ALTER TABLE players ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_stats ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Allow anonymous insert on players" ON players;
DROP POLICY IF EXISTS "Allow anonymous update on players" ON players;
DROP POLICY IF EXISTS "Allow anonymous select on players" ON players;
DROP POLICY IF EXISTS "Allow anonymous insert on player_stats" ON player_stats;
DROP POLICY IF EXISTS "Allow anonymous update on player_stats" ON player_stats;
DROP POLICY IF EXISTS "Allow anonymous select on player_stats" ON player_stats;

-- Create policies for anonymous access (for the plugin)
-- Players table policies
CREATE POLICY "Allow anonymous insert on players" ON players
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow anonymous update on players" ON players
    FOR UPDATE USING (true);

CREATE POLICY "Allow anonymous select on players" ON players
    FOR SELECT USING (true);

-- Player_stats table policies
CREATE POLICY "Allow anonymous insert on player_stats" ON player_stats
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Allow anonymous update on player_stats" ON player_stats
    FOR UPDATE USING (true);

CREATE POLICY "Allow anonymous select on player_stats" ON player_stats
    FOR SELECT USING (true);

-- Drop existing function if it exists
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Drop existing triggers if they exist
DROP TRIGGER IF EXISTS update_players_updated_at ON players;
DROP TRIGGER IF EXISTS update_player_stats_updated_at ON player_stats;

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_players_updated_at 
    BEFORE UPDATE ON players 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_player_stats_updated_at 
    BEFORE UPDATE ON player_stats 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Drop existing functions if they exist
DROP FUNCTION IF EXISTS get_player_stats_by_category(UUID, TEXT);
DROP FUNCTION IF EXISTS get_top_players_by_stat(TEXT, INTEGER);

-- Create function to get player stats by category
CREATE OR REPLACE FUNCTION get_player_stats_by_category(player_uuid_param UUID, category_param TEXT)
RETURNS TABLE(stat_name TEXT, stat_value BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        key::TEXT as stat_name,
        (value::TEXT)::BIGINT as stat_value
    FROM player_stats ps,
         jsonb_each(ps.stats) as stats(key, value)
    WHERE ps.player_uuid = player_uuid_param
      AND key LIKE category_param || '.%';
END;
$$ LANGUAGE plpgsql;

-- Create function to get top players by stat
CREATE OR REPLACE FUNCTION get_top_players_by_stat(stat_name_param TEXT, limit_count INTEGER DEFAULT 10)
RETURNS TABLE(player_name TEXT, stat_value BIGINT) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.name::TEXT as player_name,
        (ps.stats->>stat_name_param)::BIGINT as stat_value
    FROM players p
    JOIN player_stats ps ON p.uuid = ps.player_uuid
    WHERE ps.stats ? stat_name_param
    ORDER BY (ps.stats->>stat_name_param)::BIGINT DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- Grant necessary permissions
GRANT USAGE ON SCHEMA public TO anon;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon;

-- Grant permissions for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon;

-- Insert sample data for testing (optional - uncomment if you want test data)
-- INSERT INTO players (uuid, name, last_seen) VALUES 
--     ('550e8400-e29b-41d4-a716-446655440000', 'TestPlayer1', EXTRACT(EPOCH FROM NOW()) * 1000),
--     ('550e8400-e29b-41d4-a716-446655440001', 'TestPlayer2', EXTRACT(EPOCH FROM NOW()) * 1000);

-- INSERT INTO player_stats (player_uuid, stats, last_updated) VALUES 
--     ('550e8400-e29b-41d4-a716-446655440000', '{"minecraft.custom.minecraft:play_time": 1000, "minecraft.mined.minecraft:stone": 500}', EXTRACT(EPOCH FROM NOW()) * 1000),
--     ('550e8400-e29b-41d4-a716-446655440001', '{"minecraft.custom.minecraft:play_time": 2000, "minecraft.mined.minecraft:diamond_ore": 10}', EXTRACT(EPOCH FROM NOW()) * 1000);

-- Success message
SELECT 'Supabase setup completed successfully! Tables created: players, player_stats' as status; 