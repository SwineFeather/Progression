-- Progression Supabase Database Schema
-- This file contains the complete database setup for the Progression plugin
-- Run this in your Supabase SQL editor to create all required tables

-- Create players table
CREATE TABLE IF NOT EXISTS players (
    uuid UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    last_seen BIGINT NOT NULL
);

-- Create player_stats table
CREATE TABLE IF NOT EXISTS player_stats (
    id SERIAL PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    stats JSONB NOT NULL DEFAULT '{}',
    last_updated BIGINT NOT NULL,
    UNIQUE(player_uuid)
);

-- Create player_awards table
CREATE TABLE IF NOT EXISTS player_awards (
    id SERIAL PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    award_id VARCHAR(255) NOT NULL,
    award_name VARCHAR(255) NOT NULL,
    tier VARCHAR(50) NOT NULL,
    medal VARCHAR(50) NOT NULL,
    points DOUBLE PRECISION NOT NULL,
    stat_value BIGINT,
    stat_path VARCHAR(255),
    achieved_at TIMESTAMP NOT NULL,
    UNIQUE(player_uuid, award_id)
);

-- Create player_medals table
CREATE TABLE IF NOT EXISTS player_medals (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
    bronze_count INTEGER NOT NULL DEFAULT 0,
    silver_count INTEGER NOT NULL DEFAULT 0,
    gold_count INTEGER NOT NULL DEFAULT 0,
    total_medals INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create player_points table
CREATE TABLE IF NOT EXISTS player_points (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
    total_points DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create level_definitions table
CREATE TABLE IF NOT EXISTS level_definitions (
    id SERIAL PRIMARY KEY,
    level_type VARCHAR(50) NOT NULL, -- 'player' or 'town'
    level INTEGER NOT NULL,
    xp_required INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    color VARCHAR(50),
    UNIQUE(level_type, level)
);

-- Create achievement_definitions table
CREATE TABLE IF NOT EXISTS achievement_definitions (
    id SERIAL PRIMARY KEY,
    achievement_id VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    stat VARCHAR(255) NOT NULL,
    color VARCHAR(50),
    achievement_type VARCHAR(50) NOT NULL -- 'player' or 'town'
);

-- Create achievement_tiers table
CREATE TABLE IF NOT EXISTS achievement_tiers (
    id SERIAL PRIMARY KEY,
    achievement_id VARCHAR(255) NOT NULL REFERENCES achievement_definitions(achievement_id) ON DELETE CASCADE,
    tier INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    threshold BIGINT NOT NULL,
    icon VARCHAR(255),
    points INTEGER NOT NULL,
    UNIQUE(achievement_id, tier)
);

-- Create unlocked_achievements table
CREATE TABLE IF NOT EXISTS unlocked_achievements (
    id SERIAL PRIMARY KEY,
    player_uuid UUID REFERENCES players(uuid) ON DELETE CASCADE,
    town_name VARCHAR(255), -- For town achievements
    achievement_id VARCHAR(255) NOT NULL REFERENCES achievement_definitions(achievement_id) ON DELETE CASCADE,
    tier INTEGER NOT NULL,
    unlocked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    xp_awarded INTEGER NOT NULL DEFAULT 0,
    UNIQUE(player_uuid, achievement_id, tier),
    UNIQUE(town_name, achievement_id, tier),
    CHECK ((player_uuid IS NOT NULL AND town_name IS NULL) OR (player_uuid IS NULL AND town_name IS NOT NULL))
);

-- Add level and XP fields to players table
ALTER TABLE players ADD COLUMN IF NOT EXISTS level INTEGER DEFAULT 1;
ALTER TABLE players ADD COLUMN IF NOT EXISTS total_xp INTEGER DEFAULT 0;
ALTER TABLE players ADD COLUMN IF NOT EXISTS last_level_up BIGINT;

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_players_name ON players(name);
CREATE INDEX IF NOT EXISTS idx_player_stats_uuid ON player_stats(player_uuid);
CREATE INDEX IF NOT EXISTS idx_player_awards_uuid ON player_awards(player_uuid);
CREATE INDEX IF NOT EXISTS idx_player_awards_id ON player_awards(award_id);
CREATE INDEX IF NOT EXISTS idx_player_awards_points ON player_awards(points DESC);
CREATE INDEX IF NOT EXISTS idx_player_awards_achieved_at ON player_awards(achieved_at DESC);
CREATE INDEX IF NOT EXISTS idx_player_points_total ON player_points(total_points DESC);

-- New indexes for level and achievement system
CREATE INDEX IF NOT EXISTS idx_level_definitions_type_level ON level_definitions(level_type, level);
CREATE INDEX IF NOT EXISTS idx_achievement_definitions_type ON achievement_definitions(achievement_type);
CREATE INDEX IF NOT EXISTS idx_achievement_tiers_achievement ON achievement_tiers(achievement_id);
CREATE INDEX IF NOT EXISTS idx_unlocked_achievements_player ON unlocked_achievements(player_uuid);
CREATE INDEX IF NOT EXISTS idx_unlocked_achievements_town ON unlocked_achievements(town_name);
CREATE INDEX IF NOT EXISTS idx_players_level_xp ON players(level DESC, total_xp DESC);

-- Enable Row Level Security on all tables
ALTER TABLE players ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_awards ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_medals ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_points ENABLE ROW LEVEL SECURITY;
ALTER TABLE level_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE achievement_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE achievement_tiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE unlocked_achievements ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for anonymous access (for the plugin)
-- Players table policies
DROP POLICY IF EXISTS "Allow anonymous access to players" ON players;
CREATE POLICY "Allow anonymous access to players" ON players
    FOR ALL USING (true);

-- Player stats table policies
DROP POLICY IF EXISTS "Allow anonymous access to player_stats" ON player_stats;
CREATE POLICY "Allow anonymous access to player_stats" ON player_stats
    FOR ALL USING (true);

-- Player awards table policies
DROP POLICY IF EXISTS "Allow anonymous access to player_awards" ON player_awards;
CREATE POLICY "Allow anonymous access to player_awards" ON player_awards
    FOR ALL USING (true);

-- Player medals table policies
DROP POLICY IF EXISTS "Allow anonymous access to player_medals" ON player_medals;
CREATE POLICY "Allow anonymous access to player_medals" ON player_medals
    FOR ALL USING (true);

-- Player points table policies
DROP POLICY IF EXISTS "Allow anonymous access to player_points" ON player_points;
CREATE POLICY "Allow anonymous access to player_points" ON player_points
    FOR ALL USING (true);

-- Level definitions table policies
DROP POLICY IF EXISTS "Allow anonymous access to level_definitions" ON level_definitions;
CREATE POLICY "Allow anonymous access to level_definitions" ON level_definitions
    FOR ALL USING (true);

-- Achievement definitions table policies
DROP POLICY IF EXISTS "Allow anonymous access to achievement_definitions" ON achievement_definitions;
CREATE POLICY "Allow anonymous access to achievement_definitions" ON achievement_definitions
    FOR ALL USING (true);

-- Achievement tiers table policies
DROP POLICY IF EXISTS "Allow anonymous access to achievement_tiers" ON achievement_tiers;
CREATE POLICY "Allow anonymous access to achievement_tiers" ON achievement_tiers
    FOR ALL USING (true);

-- Unlocked achievements table policies
DROP POLICY IF EXISTS "Allow anonymous access to unlocked_achievements" ON unlocked_achievements;
CREATE POLICY "Allow anonymous access to unlocked_achievements" ON unlocked_achievements
    FOR ALL USING (true);

-- Create functions for automatic updates
-- First drop triggers that depend on functions
DROP TRIGGER IF EXISTS trigger_update_medals ON player_awards;
DROP TRIGGER IF EXISTS trigger_update_points ON player_awards;

-- Function to update player_medals when player_awards changes
DROP FUNCTION IF EXISTS update_player_medals() CASCADE;
CREATE OR REPLACE FUNCTION update_player_medals()
RETURNS TRIGGER AS $$
BEGIN
    -- Update medal counts based on the award being inserted/updated
    INSERT INTO player_medals (player_uuid, bronze_count, silver_count, gold_count, total_medals)
    VALUES (
        NEW.player_uuid,
        CASE WHEN NEW.medal = 'bronze' THEN 1 ELSE 0 END,
        CASE WHEN NEW.medal = 'silver' THEN 1 ELSE 0 END,
        CASE WHEN NEW.medal = 'gold' THEN 1 ELSE 0 END,
        1
    )
    ON CONFLICT (player_uuid) DO UPDATE SET
        bronze_count = player_medals.bronze_count + CASE WHEN NEW.medal = 'bronze' THEN 1 ELSE 0 END,
        silver_count = player_medals.silver_count + CASE WHEN NEW.medal = 'silver' THEN 1 ELSE 0 END,
        gold_count = player_medals.gold_count + CASE WHEN NEW.medal = 'gold' THEN 1 ELSE 0 END,
        total_medals = player_medals.total_medals + 1;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update player_points when player_awards changes
DROP FUNCTION IF EXISTS update_player_points() CASCADE;
CREATE OR REPLACE FUNCTION update_player_points()
RETURNS TRIGGER AS $$
BEGIN
    -- Update total points based on the award being inserted/updated
    INSERT INTO player_points (player_uuid, total_points)
    VALUES (NEW.player_uuid, NEW.points)
    ON CONFLICT (player_uuid) DO UPDATE SET
        total_points = player_points.total_points + NEW.points;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers for automatic updates (commented out due to Supabase compatibility issues)
-- CREATE TRIGGER trigger_update_medals
--     AFTER INSERT OR UPDATE ON player_awards
--     FOR EACH ROW
--     EXECUTE FUNCTION update_player_medals();

-- CREATE TRIGGER trigger_update_points
--     AFTER INSERT OR UPDATE ON player_awards
--     FOR EACH ROW
--     EXECUTE FUNCTION update_player_points();

-- Function to update timestamp
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP AT TIME ZONE 'UTC';
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers to automatically update updated_at
DROP TRIGGER IF EXISTS trigger_update_player_medals_updated_at ON player_medals;
CREATE TRIGGER trigger_update_player_medals_updated_at
    BEFORE UPDATE ON player_medals
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trigger_update_player_points_updated_at ON player_points;
CREATE TRIGGER trigger_update_player_points_updated_at
    BEFORE UPDATE ON player_points
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create views for easier querying
-- View for player leaderboards
DROP VIEW IF EXISTS player_leaderboard;
CREATE OR REPLACE VIEW player_leaderboard AS
SELECT 
    p.uuid,
    p.name,
    COALESCE(pp.total_points, 0) as total_points,
    COALESCE(pm.total_medals, 0) as total_medals,
    COALESCE(pm.gold_count, 0) as gold_medals,
    COALESCE(pm.silver_count, 0) as silver_medals,
    COALESCE(pm.bronze_count, 0) as bronze_medals,
    p.last_seen
FROM players p
LEFT JOIN player_points pp ON p.uuid = pp.player_uuid
LEFT JOIN player_medals pm ON p.uuid = pm.player_uuid
ORDER BY COALESCE(pp.total_points, 0) DESC;

-- View for award leaderboards
DROP VIEW IF EXISTS award_leaderboard;
CREATE OR REPLACE VIEW award_leaderboard AS
SELECT 
    pa.award_id,
    pa.award_name,
    pa.player_uuid,
    p.name as player_name,
    pa.points,
    pa.medal,
    pa.tier,
    pa.achieved_at,
    ROW_NUMBER() OVER (PARTITION BY pa.award_id ORDER BY pa.points DESC) as rank
FROM player_awards pa
JOIN players p ON pa.player_uuid = p.uuid
ORDER BY pa.award_id, pa.points DESC;

-- View for level leaderboards
DROP VIEW IF EXISTS level_leaderboard;
CREATE OR REPLACE VIEW level_leaderboard AS
SELECT 
    p.uuid,
    p.name,
    p.level,
    p.total_xp,
    ld.title as level_title,
    ld.description as level_description,
    ld.color as level_color,
    p.last_seen
FROM players p
LEFT JOIN level_definitions ld ON ld.level_type = 'player' AND ld.level = p.level
ORDER BY p.level DESC, p.total_xp DESC;

-- View for achievement progress
DROP VIEW IF EXISTS achievement_progress;
CREATE OR REPLACE VIEW achievement_progress AS
SELECT 
    ad.achievement_id,
    ad.name as achievement_name,
    ad.description as achievement_description,
    ad.stat,
    ad.color as achievement_color,
    ad.achievement_type,
    at.tier,
    at.name as tier_name,
    at.description as tier_description,
    at.threshold,
    at.icon,
    at.points,
    ua.player_uuid,
    ua.town_name,
    ua.unlocked_at,
    ua.xp_awarded,
    CASE WHEN ua.player_uuid IS NOT NULL THEN 'unlocked' ELSE 'locked' END as status
FROM achievement_definitions ad
JOIN achievement_tiers at ON ad.achievement_id = at.achievement_id
LEFT JOIN unlocked_achievements ua ON ad.achievement_id = ua.achievement_id AND at.tier = ua.tier
ORDER BY ad.achievement_id, at.tier;

-- Grant necessary permissions
GRANT USAGE ON SCHEMA public TO anon;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA public TO anon;

-- Add updated_at columns to existing tables if they don't exist
DO $$ 
BEGIN
    -- Add updated_at to player_medals if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'player_medals' AND column_name = 'updated_at') THEN
        ALTER TABLE player_medals ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
    END IF;
    
    -- Add updated_at to player_points if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name = 'player_points' AND column_name = 'updated_at') THEN
        ALTER TABLE player_points ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
    END IF;
END $$;

-- Grant permissions for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO anon;

-- Insert some sample data for testing (optional)
-- INSERT INTO players (uuid, name, last_seen) VALUES 
--     ('123e4567-e89b-12d3-a456-426614174000', 'TestPlayer', EXTRACT(EPOCH FROM NOW()) * 1000);

COMMENT ON TABLE players IS 'Stores basic player information including level and XP';
COMMENT ON TABLE player_stats IS 'Stores player statistics as JSON';
COMMENT ON TABLE player_awards IS 'Stores individual awards earned by players';
COMMENT ON TABLE player_medals IS 'Stores aggregated medal counts for players';
COMMENT ON TABLE player_points IS 'Stores total points for players';
COMMENT ON TABLE level_definitions IS 'Stores level definitions for players and towns';
COMMENT ON TABLE achievement_definitions IS 'Stores achievement definitions';
COMMENT ON TABLE achievement_tiers IS 'Stores achievement tier definitions';
COMMENT ON TABLE unlocked_achievements IS 'Stores unlocked achievements for players and towns';
COMMENT ON VIEW player_leaderboard IS 'View for player leaderboards';
COMMENT ON VIEW award_leaderboard IS 'View for award-specific leaderboards'; 
COMMENT ON VIEW level_leaderboard IS 'View for level leaderboards';
COMMENT ON VIEW achievement_progress IS 'View for achievement progress tracking'; 