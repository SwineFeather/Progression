-- Complete Supabase Setup Script for PlayerStatsToMySQL
-- Run this in your Supabase SQL Editor
-- This script creates ALL tables needed for the plugin including awards system

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

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

-- Drop existing policies if they exist
DROP POLICY IF EXISTS "Allow anonymous insert on players" ON players;
DROP POLICY IF EXISTS "Allow anonymous update on players" ON players;
DROP POLICY IF EXISTS "Allow anonymous select on players" ON players;

DROP POLICY IF EXISTS "Allow anonymous insert on player_stats" ON player_stats;
DROP POLICY IF EXISTS "Allow anonymous update on player_stats" ON player_stats;
DROP POLICY IF EXISTS "Allow anonymous select on player_stats" ON player_stats;

DROP POLICY IF EXISTS "Allow anonymous insert on player_awards" ON player_awards;
DROP POLICY IF EXISTS "Allow anonymous update on player_awards" ON player_awards;
DROP POLICY IF EXISTS "Allow anonymous select on player_awards" ON player_awards;

DROP POLICY IF EXISTS "Allow anonymous insert on player_medals" ON player_medals;
DROP POLICY IF EXISTS "Allow anonymous update on player_medals" ON player_medals;
DROP POLICY IF EXISTS "Allow anonymous select on player_medals" ON player_medals;

DROP POLICY IF EXISTS "Allow anonymous insert on player_points" ON player_points;
DROP POLICY IF EXISTS "Allow anonymous update on player_points" ON player_points;
DROP POLICY IF EXISTS "Allow anonymous select on player_points" ON player_points;

DROP POLICY IF EXISTS "Allow anonymous insert on webhook_logs" ON webhook_logs;
DROP POLICY IF EXISTS "Allow anonymous update on webhook_logs" ON webhook_logs;
DROP POLICY IF EXISTS "Allow anonymous select on webhook_logs" ON webhook_logs;

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
DROP TRIGGER IF EXISTS update_player_medals_updated_at ON player_medals;
DROP TRIGGER IF EXISTS update_player_points_updated_at ON player_points;

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_players_updated_at 
    BEFORE UPDATE ON players 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_player_stats_updated_at 
    BEFORE UPDATE ON player_stats 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_player_medals_updated_at 
    BEFORE UPDATE ON player_medals 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_player_points_updated_at 
    BEFORE UPDATE ON player_points 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Drop existing functions if they exist
DROP FUNCTION IF EXISTS get_player_stats_by_category(UUID, TEXT);
DROP FUNCTION IF EXISTS get_top_players_by_stat(TEXT, INTEGER);
DROP FUNCTION IF EXISTS get_award_leaderboard(TEXT, INTEGER);
DROP FUNCTION IF EXISTS get_medal_leaderboard(TEXT, INTEGER);
DROP FUNCTION IF EXISTS get_points_leaderboard(INTEGER);
DROP FUNCTION IF EXISTS update_player_medals(UUID);
DROP FUNCTION IF EXISTS update_player_points(UUID);

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

-- Create function to get award leaderboard
CREATE OR REPLACE FUNCTION get_award_leaderboard(award_id_param TEXT, limit_count INTEGER DEFAULT 10)
RETURNS TABLE(player_name TEXT, medal VARCHAR(50), points DECIMAL(10,2), achieved_at TIMESTAMP WITH TIME ZONE) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.name::TEXT as player_name,
        pa.medal::VARCHAR(50),
        pa.points::DECIMAL(10,2),
        pa.achieved_at
    FROM players p
    JOIN player_awards pa ON p.uuid = pa.player_uuid
    WHERE pa.award_id = award_id_param
    ORDER BY pa.points DESC, pa.achieved_at ASC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- Create function to get medal leaderboard
CREATE OR REPLACE FUNCTION get_medal_leaderboard(medal_type TEXT, limit_count INTEGER DEFAULT 10)
RETURNS TABLE(player_name TEXT, medal_count INTEGER, total_points DECIMAL(10,2)) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.name::TEXT as player_name,
        pm.bronze_count + pm.silver_count + pm.gold_count as medal_count,
        pp.total_points::DECIMAL(10,2)
    FROM players p
    JOIN player_medals pm ON p.uuid = pm.player_uuid
    JOIN player_points pp ON p.uuid = pp.player_uuid
    WHERE 
        CASE 
            WHEN medal_type = 'bronze' THEN pm.bronze_count > 0
            WHEN medal_type = 'silver' THEN pm.silver_count > 0
            WHEN medal_type = 'gold' THEN pm.gold_count > 0
            ELSE true
        END
    ORDER BY 
        CASE 
            WHEN medal_type = 'bronze' THEN pm.bronze_count
            WHEN medal_type = 'silver' THEN pm.silver_count
            WHEN medal_type = 'gold' THEN pm.gold_count
            ELSE pm.total_medals
        END DESC,
        pp.total_points DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- Create function to get points leaderboard
CREATE OR REPLACE FUNCTION get_points_leaderboard(limit_count INTEGER DEFAULT 10)
RETURNS TABLE(player_name TEXT, total_points DECIMAL(10,2), bronze_count INTEGER, silver_count INTEGER, gold_count INTEGER) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.name::TEXT as player_name,
        pp.total_points::DECIMAL(10,2),
        pm.bronze_count,
        pm.silver_count,
        pm.gold_count
    FROM players p
    JOIN player_points pp ON p.uuid = pp.player_uuid
    JOIN player_medals pm ON p.uuid = pm.player_uuid
    ORDER BY pp.total_points DESC, pm.gold_count DESC, pm.silver_count DESC, pm.bronze_count DESC
    LIMIT limit_count;
END;
$$ LANGUAGE plpgsql;

-- Create function to update player medals count
CREATE OR REPLACE FUNCTION update_player_medals(player_uuid_param UUID)
RETURNS VOID AS $$
BEGIN
    INSERT INTO player_medals (player_uuid, bronze_count, silver_count, gold_count, total_medals, last_updated)
    SELECT 
        player_uuid_param,
        COUNT(CASE WHEN medal = 'bronze' THEN 1 END) as bronze_count,
        COUNT(CASE WHEN medal = 'silver' THEN 1 END) as silver_count,
        COUNT(CASE WHEN medal = 'gold' THEN 1 END) as gold_count,
        COUNT(*) as total_medals,
        NOW()
    FROM player_awards
    WHERE player_uuid = player_uuid_param
    ON CONFLICT (player_uuid) DO UPDATE SET
        bronze_count = EXCLUDED.bronze_count,
        silver_count = EXCLUDED.silver_count,
        gold_count = EXCLUDED.gold_count,
        total_medals = EXCLUDED.total_medals,
        last_updated = NOW();
END;
$$ LANGUAGE plpgsql;

-- Create function to update player points
CREATE OR REPLACE FUNCTION update_player_points(player_uuid_param UUID)
RETURNS VOID AS $$
BEGIN
    INSERT INTO player_points (player_uuid, total_points, stone_points, iron_points, diamond_points, last_updated)
    SELECT 
        player_uuid_param,
        SUM(points) as total_points,
        SUM(CASE WHEN tier = 'stone' THEN points ELSE 0 END) as stone_points,
        SUM(CASE WHEN tier = 'iron' THEN points ELSE 0 END) as iron_points,
        SUM(CASE WHEN tier = 'diamond' THEN points ELSE 0 END) as diamond_points,
        NOW()
    FROM player_awards
    WHERE player_uuid = player_uuid_param
    ON CONFLICT (player_uuid) DO UPDATE SET
        total_points = EXCLUDED.total_points,
        stone_points = EXCLUDED.stone_points,
        iron_points = EXCLUDED.iron_points,
        diamond_points = EXCLUDED.diamond_points,
        last_updated = NOW();
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

-- Success message
SELECT 'Complete Supabase setup finished! Tables created: players, player_stats, player_awards, player_medals, player_points, webhook_logs' as status; 