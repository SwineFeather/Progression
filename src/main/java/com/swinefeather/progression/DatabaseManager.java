package com.swinefeather.progression;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.java.JavaPlugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private HikariDataSource dataSource;
    private static final String[] STAT_CATEGORIES = {
        "broken", "crafted", "dropped", "killed", "killed_by", "picked_up", "mined", "custom", "used", "towny"
    };

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        setupDataSource();
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(plugin.getConfig().getString("mysql.url"));
        config.setUsername(plugin.getConfig().getString("mysql.user"));
        config.setPassword(plugin.getConfig().getString("mysql.password"));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setConnectionTimeout(60000);
        config.setMaximumPoolSize(10);
        try {
            dataSource = new HikariDataSource(config);
            plugin.getLogger().info("Database connection initialized successfully.");
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to initialize database connection: %s", e.getMessage()));
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    public void setupDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS players (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY," +
                            "player_name VARCHAR(16)," +
                            "first_joined TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            ).executeUpdate();

            for (String category : STAT_CATEGORIES) {
                conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS stats_" + category + " (" +
                                "player_uuid VARCHAR(36)," +
                                "stat_key VARCHAR(255)," +
                                "stat_value BIGINT," +
                                "PRIMARY KEY (player_uuid, stat_key)," +
                                "FOREIGN KEY (player_uuid) REFERENCES players(player_uuid))"
                ).executeUpdate();
            }

            conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS stats_placeholders (" +
                            "player_uuid VARCHAR(36)," +
                            "placeholder_key VARCHAR(255)," +
                            "value LONGTEXT," +
                            "PRIMARY KEY (player_uuid, placeholder_key)," +
                            "FOREIGN KEY (player_uuid) REFERENCES players(player_uuid))"
            ).executeUpdate();

            plugin.getLogger().info("Database tables created successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Failed to setup database: %s", e.getMessage()));
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    public void savePlayerInfo(UUID playerUUID, String playerName) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) return;

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT first_joined FROM players WHERE player_uuid = ?")) {
                checkStmt.setString(1, playerUUID.toString());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    try (PreparedStatement updateStmt = conn.prepareStatement(
                            "UPDATE players SET player_name = ?, last_updated = NOW() WHERE player_uuid = ?")) {
                        plugin.getLogger().info(String.format("Updating player info for: %s", playerUUID));
                        updateStmt.setString(1, playerName);
                        updateStmt.setString(2, playerUUID.toString());
                        updateStmt.executeUpdate();
                        plugin.getLogger().info(String.format("Updated player info for %s", playerUUID));
                    }
                } else {
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO players (player_uuid, player_name, first_joined, last_updated) " +
                                    "VALUES (?, ?, NOW(), NOW())")) {
                        plugin.getLogger().info(String.format("Inserting player info for: %s", playerUUID));
                        insertStmt.setString(1, playerUUID.toString());
                        insertStmt.setString(2, playerName);
                        insertStmt.executeUpdate();
                        plugin.getLogger().info(String.format("Inserted player info for %s", playerUUID));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Failed to save player info for %s: %s", playerUUID, e.getMessage()));
        }
    }

    public void savePlayerStats(String category, UUID playerUUID, Map<String, Long> stats) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) return;

        String tableName = "stats_" + sanitizeTableName(category);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO " + tableName + " (player_uuid, stat_key, stat_value) " +
                             "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE stat_value = ?"
             )) {
            for (Map.Entry<String, Long> entry : stats.entrySet()) {
                String statKey = sanitizeStatKey(entry.getKey());
                Long value = entry.getValue();
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, statKey);
                stmt.setLong(3, value);
                stmt.setLong(4, value);
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            plugin.getLogger().info(String.format("Saved %d stats for %s in %s", results.length, playerUUID, category));
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Failed to save stats for %s in %s: %s", playerUUID, category, e.getMessage()));
        }
    }

    public void savePlaceholderStats(UUID playerUUID, Map<String, String> placeholders) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) return;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO stats_placeholders (player_uuid, placeholder_key, value) " +
                             "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE value = ?"
             )) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue());
                stmt.setString(4, entry.getValue());
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            plugin.getLogger().info(String.format("Saved %d placeholders for %s", results.length, playerUUID));
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Failed to save placeholder stats for %s: %s", playerUUID, e.getMessage()));
        }
    }

    public void saveTownyStats(UUID playerUUID, Map<String, String> townyStats) {
        if (!plugin.getConfig().getBoolean("towny.enabled") || !plugin.getServer().getPluginManager().isPluginEnabled(plugin)) return;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO stats_towny (player_uuid, stat_key, stat_value) " +
                             "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE stat_value = ?"
             )) {
            for (Map.Entry<String, String> entry : townyStats.entrySet()) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue());
                stmt.setString(4, entry.getValue());
                stmt.addBatch();
            }
            int[] results = stmt.executeBatch();
            plugin.getLogger().info(String.format("Saved %d Towny stats for %s", results.length, playerUUID));
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Failed to save Towny stats for %s: %s", playerUUID, e.getMessage()));
        }
    }

    public boolean isConnected() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(1);
        } catch (SQLException e) {
            plugin.getLogger().severe(String.format("Database connection test failed: %s", e.getMessage()));
            return false;
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private String sanitizeTableName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    private String sanitizeStatKey(String key) {
        String sanitized = key.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        // Always remove "minecraft:" prefix for cleaner stat names
        sanitized = sanitized.replace("minecraft_", "");
        if (sanitized.length() > 255) {
            sanitized = sanitized.substring(0, 255);
        }
        return sanitized;
    }
}