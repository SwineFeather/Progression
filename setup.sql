-- PlayerStatsToMySQL Database Setup
-- This file contains the basic database setup for PlayerStatsToMySQL plugin

-- Create the database (adjust name as needed)
CREATE DATABASE IF NOT EXISTS playerstats CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Use the database
USE playerstats;

-- Create the main players table
CREATE TABLE IF NOT EXISTS players (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(16) NOT NULL,
    first_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_player_name (player_name),
    INDEX idx_last_updated (last_updated)
);

-- Create stat category tables
-- These will be created automatically by the plugin, but here are the schemas:

-- Stats for broken items
CREATE TABLE IF NOT EXISTS stats_broken (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for crafted items
CREATE TABLE IF NOT EXISTS stats_crafted (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for dropped items
CREATE TABLE IF NOT EXISTS stats_dropped (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for killed entities
CREATE TABLE IF NOT EXISTS stats_killed (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for killed by entities
CREATE TABLE IF NOT EXISTS stats_killed_by (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for picked up items
CREATE TABLE IF NOT EXISTS stats_picked_up (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for mined blocks
CREATE TABLE IF NOT EXISTS stats_mined (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for custom achievements
CREATE TABLE IF NOT EXISTS stats_custom (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- Stats for used items
CREATE TABLE IF NOT EXISTS stats_used (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT DEFAULT 0,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key),
    INDEX idx_stat_value (stat_value)
);

-- PlaceholderAPI data
CREATE TABLE IF NOT EXISTS stats_placeholders (
    player_uuid VARCHAR(36),
    placeholder_key VARCHAR(255),
    value LONGTEXT,
    PRIMARY KEY (player_uuid, placeholder_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_placeholder_key (placeholder_key)
);

-- Towny data
CREATE TABLE IF NOT EXISTS stats_towny (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value VARCHAR(255),
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid) ON DELETE CASCADE,
    INDEX idx_stat_key (stat_key)
);

-- Create a user for the plugin (adjust credentials as needed)
-- CREATE USER 'playerstats'@'localhost' IDENTIFIED BY 'your_secure_password';
-- GRANT ALL PRIVILEGES ON playerstats.* TO 'playerstats'@'localhost';
-- FLUSH PRIVILEGES;

-- Show created tables
SHOW TABLES;

-- Show table structures
DESCRIBE players;
DESCRIBE stats_mined;
DESCRIBE stats_placeholders;
DESCRIBE stats_towny;