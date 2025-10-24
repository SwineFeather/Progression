-- Migration script to add missing columns to player_awards table
-- Run this in your Supabase SQL Editor

-- Add missing columns to player_awards table
ALTER TABLE player_awards 
ADD COLUMN IF NOT EXISTS award_description TEXT,
ADD COLUMN IF NOT EXISTS stat_value BIGINT,
ADD COLUMN IF NOT EXISTS stat_path VARCHAR(255);

-- Add indexes for the new columns
CREATE INDEX IF NOT EXISTS idx_player_awards_stat_value ON player_awards(stat_value DESC);
CREATE INDEX IF NOT EXISTS idx_player_awards_stat_path ON player_awards(stat_path);

-- Update the schema to match what the plugin expects
COMMENT ON TABLE player_awards IS 'Stores individual awards earned by players with additional metadata'; 

-- Ensure player_name defaults exist to avoid NOT NULL violations on upsert
ALTER TABLE player_points ALTER COLUMN player_name SET DEFAULT 'Unknown';
ALTER TABLE player_medals ALTER COLUMN player_name SET DEFAULT 'Unknown';

-- Backfill any existing NULL player_name values to avoid NOT NULL violations
UPDATE player_points SET player_name = 'Unknown' WHERE player_name IS NULL;
UPDATE player_medals SET player_name = 'Unknown' WHERE player_name IS NULL;

-- Add defensive triggers to ensure player_name is never NULL on writes
CREATE OR REPLACE FUNCTION ensure_player_points_name()
RETURNS trigger AS $$
BEGIN
  IF NEW.player_name IS NULL OR NEW.player_name = '' THEN
    SELECT COALESCE(name, 'Unknown') INTO NEW.player_name FROM players WHERE uuid = NEW.player_uuid;
    IF NEW.player_name IS NULL OR NEW.player_name = '' THEN
      NEW.player_name := 'Unknown';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_player_points_name ON player_points;
CREATE TRIGGER trg_player_points_name
BEFORE INSERT OR UPDATE ON player_points
FOR EACH ROW EXECUTE FUNCTION ensure_player_points_name();

CREATE OR REPLACE FUNCTION ensure_player_medals_name()
RETURNS trigger AS $$
BEGIN
  IF NEW.player_name IS NULL OR NEW.player_name = '' THEN
    SELECT COALESCE(name, 'Unknown') INTO NEW.player_name FROM players WHERE uuid = NEW.player_uuid;
    IF NEW.player_name IS NULL OR NEW.player_name = '' THEN
      NEW.player_name := 'Unknown';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_player_medals_name ON player_medals;
CREATE TRIGGER trg_player_medals_name
BEFORE INSERT OR UPDATE ON player_medals
FOR EACH ROW EXECUTE FUNCTION ensure_player_medals_name();