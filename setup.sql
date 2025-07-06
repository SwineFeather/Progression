-- Setup MySQL database for PlayerStatsToMySQL v2.0
-- Run this in Bloom.host's MySQL database (e.g., via MySQL Workbench)

CREATE DATABASE IF NOT EXISTS s68391_playerstatstomysql;
USE s68391_playerstatstomysql;

-- Main player table
CREATE TABLE IF NOT EXISTS players (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(16),
    first_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Stats tables
CREATE TABLE IF NOT EXISTS stats_broken (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_crafted (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_dropped (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_killed (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_killed_by (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_picked_up (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_mined (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_custom (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

CREATE TABLE IF NOT EXISTS stats_used (
    player_uuid VARCHAR(36),
    stat_key VARCHAR(255),
    stat_value BIGINT,
    PRIMARY KEY (player_uuid, stat_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);

-- PlaceholderAPI stats table
CREATE TABLE IF NOT EXISTS stats_placeholders (
    player_uuid VARCHAR(36),
    placeholder_key VARCHAR(255),
    value LONGTEXT,
    PRIMARY KEY (player_uuid, placeholder_key),
    FOREIGN KEY (player_uuid) REFERENCES players(player_uuid)
);