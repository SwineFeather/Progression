-- Quick Fix for PlayerStatsToMySQL Supabase Tables
-- Run this in your Supabase SQL Editor to fix the missing tables

-- Drop existing tables if they exist (WARNING: This will delete existing data)
DROP TABLE IF EXISTS player_awards CASCADE;
DROP TABLE IF EXISTS player_medals CASCADE;
DROP TABLE IF EXISTS player_points CASCADE;
DROP TABLE IF EXISTS webhook_logs CASCADE;
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

-- Create player_awards table for tracking award achievements
CREATE TABLE player_awards (
    id SERIAL PRIMARY KEY,
    player_uuid UUID NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    award_id VARCHAR(255) NOT NULL,
    award_name VARCHAR(255) NOT NULL,
    award_description TEXT,
    tier VARCHAR(50) NOT NULL, -- stone, iron, diamond
    medal VARCHAR(50) NOT NULL, -- bronze, silver, gold
    points DECIMAL(10,2) NOT NULL DEFAULT 0,
    stat_value BIGINT,
    stat_path VARCHAR(255),
    achieved_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(player_uuid, award_id)
);

-- Create player_medals table for tracking medal counts
CREATE TABLE player_medals (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
    bronze_count INTEGER DEFAULT 0,
    silver_count INTEGER DEFAULT 0,
    gold_count INTEGER DEFAULT 0,
    total_medals INTEGER DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create player_points table for tracking total points
CREATE TABLE player_points (
    player_uuid UUID PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
    total_points DECIMAL(10,2) DEFAULT 0,
    stone_points DECIMAL(10,2) DEFAULT 0,
    iron_points DECIMAL(10,2) DEFAULT 0,
    diamond_points DECIMAL(10,2) DEFAULT 0,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create webhook_logs table for tracking webhook notifications
CREATE TABLE webhook_logs (
    id SERIAL PRIMARY KEY,
    webhook_type VARCHAR(100) NOT NULL, -- award_achieved, medal_change, etc.
    player_uuid UUID REFERENCES players(uuid) ON DELETE SET NULL,
    player_name VARCHAR(255),
    message TEXT NOT NULL,
    data JSONB,
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    success BOOLEAN DEFAULT true,
    error_message TEXT
);

-- Create indexes for better performance
CREATE INDEX idx_players_name ON players(name);
CREATE INDEX idx_players_last_seen ON players(last_seen);
CREATE INDEX idx_player_stats_last_updated ON player_stats(last_updated);
CREATE INDEX idx_player_stats_gin ON player_stats USING GIN (stats);

-- Award system indexes
CREATE INDEX idx_player_awards_player_uuid ON player_awards(player_uuid);
CREATE INDEX idx_player_awards_award_id ON player_awards(award_id);
CREATE INDEX idx_player_awards_tier ON player_awards(tier);
CREATE INDEX idx_player_awards_medal ON player_awards(medal);
CREATE INDEX idx_player_awards_achieved_at ON player_awards(achieved_at);

CREATE INDEX idx_player_medals_bronze ON player_medals(bronze_count);
CREATE INDEX idx_player_medals_silver ON player_medals(silver_count);
CREATE INDEX idx_player_medals_gold ON player_medals(gold_count);
CREATE INDEX idx_player_medals_total ON player_medals(total_medals);

CREATE INDEX idx_player_points_total ON player_points(total_points);
CREATE INDEX idx_player_points_stone ON player_points(stone_points);
CREATE INDEX idx_player_points_iron ON player_points(iron_points);
CREATE INDEX idx_player_points_diamond ON player_points(diamond_points);

CREATE INDEX idx_webhook_logs_type ON webhook_logs(webhook_type);
CREATE INDEX idx_webhook_logs_player ON webhook_logs(player_uuid);
CREATE INDEX idx_webhook_logs_sent_at ON webhook_logs(sent_at);
CREATE INDEX idx_webhook_logs_success ON webhook_logs(success);

-- Enable Row Level Security (RLS)
ALTER TABLE players ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_awards ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_medals ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_points ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_logs ENABLE ROW LEVEL SECURITY;

-- Create policies for anonymous access (for the plugin)
-- Players table policies
CREATE POLICY "Allow anonymous insert on players" ON players FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on players" ON players FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on players" ON players FOR SELECT USING (true);

-- Player_stats table policies
CREATE POLICY "Allow anonymous insert on player_stats" ON player_stats FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on player_stats" ON player_stats FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on player_stats" ON player_stats FOR SELECT USING (true);

-- Player_awards table policies
CREATE POLICY "Allow anonymous insert on player_awards" ON player_awards FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on player_awards" ON player_awards FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on player_awards" ON player_awards FOR SELECT USING (true);

-- Player_medals table policies
CREATE POLICY "Allow anonymous insert on player_medals" ON player_medals FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on player_medals" ON player_medals FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on player_medals" ON player_medals FOR SELECT USING (true);

-- Player_points table policies
CREATE POLICY "Allow anonymous insert on player_points" ON player_points FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on player_points" ON player_points FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on player_points" ON player_points FOR SELECT USING (true);

-- Webhook_logs table policies
CREATE POLICY "Allow anonymous insert on webhook_logs" ON webhook_logs FOR INSERT WITH CHECK (true);
CREATE POLICY "Allow anonymous update on webhook_logs" ON webhook_logs FOR UPDATE USING (true);
CREATE POLICY "Allow anonymous select on webhook_logs" ON webhook_logs FOR SELECT USING (true);

-- Grant necessary permissions
GRANT USAGE ON SCHEMA public TO anon;
GRANT ALL ON ALL TABLES IN SCHEMA public TO anon;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO anon;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO anon;

-- Grant permissions for future tables
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO anon;

-- Success message
SELECT 'Supabase tables recreated successfully! Plugin should now work.' as status; 