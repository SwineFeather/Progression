package com.swinefeather.playerstatstomysql;

import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class StatSyncTask {
    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;
    private final PlaceholderManager placeholderManager;
    private final long minimumPlaytimeTicks;

    public StatSyncTask(JavaPlugin plugin, DatabaseManager dbManager, PlaceholderManager placeholderManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.placeholderManager = placeholderManager;
        this.minimumPlaytimeTicks = plugin.getConfig().getLong("minimum-playtime-ticks", 0L);
    }

    public void syncAllPlayers(CommandSender sender) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        try {
            for (World world : plugin.getServer().getWorlds()) {
                plugin.getLogger().info(String.format("Checking world: %s", world.getName()));
                File statsFolder = new File(world.getWorldFolder(), "stats");
                if (!statsFolder.exists() || !statsFolder.isDirectory()) {
                    plugin.getLogger().warning(String.format("Stats folder not found in world: %s", world.getName()));
                    continue;
                }

                File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
                if (statFiles == null) {
                    plugin.getLogger().warning(String.format("No stat files found in: %s", statsFolder.getPath()));
                    continue;
                }

                for (File statFile : statFiles) {
                    try {
                        UUID playerUUID = UUID.fromString(statFile.getName().replace(".json", ""));
                        plugin.getLogger().info(String.format("Processing stat file for UUID: %s", playerUUID));
                        if (!hasMinimumPlaytime(statFile)) {
                            plugin.getLogger().info(String.format("Skipping %s: Playtime below %d ticks", playerUUID, minimumPlaytimeTicks));
                            continue;
                        }
                        String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                        if (playerName == null) {
                            playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                            plugin.getLogger().warning(String.format("Player name not found for UUID: %s, using: %s", playerUUID, playerName));
                        }
                        dbManager.savePlayerInfo(playerUUID, playerName);
                        syncPlayerStats(playerUUID, statFile);
                        placeholderManager.syncPlayerPlaceholders(playerUUID);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning(String.format("Invalid UUID in file: %s", statFile.getName()));
                    } catch (Exception e) {
                        plugin.getLogger().warning(String.format("Error processing stat file %s: %s", statFile.getName(), e.getMessage()));
                    }
                }
            }
            if (sender != null) {
                sender.sendMessage("§aFull stat sync completed!");
            }
            plugin.getLogger().info("Full stat sync finished successfully.");
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage(String.format("§cStat sync failed: %s", e.getMessage()));
            }
            plugin.getLogger().severe(String.format("Full stat sync failed: %s", e.getMessage()));
        }
    }

    public void syncOnlinePlayers(CommandSender sender) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        try {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerUUID = player.getUniqueId();
                plugin.getLogger().info(String.format("Syncing stats for online player: %s", playerUUID));
                syncSinglePlayer(playerUUID, sender);
            }
            if (sender != null) {
                sender.sendMessage("§aOnline player stat sync completed!");
            }
            plugin.getLogger().info("Online player stat sync finished successfully.");
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage(String.format("§cOnline player stat sync failed: %s", e.getMessage()));
            }
            plugin.getLogger().severe(String.format("Online player stat sync failed: %s", e.getMessage()));
        }
    }

    public void syncSinglePlayer(UUID playerUUID, CommandSender sender) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        try {
            for (World world : plugin.getServer().getWorlds()) {
                File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
                if (!statFile.exists()) {
                    plugin.getLogger().info(String.format("No stat file found for %s in world: %s", playerUUID, world.getName()));
                    continue;
                }

                if (!hasMinimumPlaytime(statFile)) {
                    plugin.getLogger().info(String.format("Skipping %s: Playtime below %d ticks", playerUUID, minimumPlaytimeTicks));
                    continue;
                }

                String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                if (playerName == null) {
                    playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                    plugin.getLogger().warning(String.format("Player name not found for UUID: %s, using: %s", playerUUID, playerName));
                }
                dbManager.savePlayerInfo(playerUUID, playerName);
                syncPlayerStats(playerUUID, statFile);
                placeholderManager.syncPlayerPlaceholders(playerUUID);
            }
            if (sender != null) {
                sender.sendMessage(String.format("§aStat sync for player %s completed!", playerUUID));
            }
            plugin.getLogger().info(String.format("Stat sync for player %s finished successfully.", playerUUID));
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage(String.format("§cStat sync for player %s failed: %s", playerUUID, e.getMessage()));
            }
            plugin.getLogger().severe(String.format("Stat sync for player %s failed: %s", playerUUID, e.getMessage()));
        }
    }

    private boolean hasMinimumPlaytime(File statFile) {
        try (FileReader reader = new FileReader(statFile)) {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(reader);
            JSONObject statsSection = (JSONObject) json.get("stats");
            if (statsSection != null) {
                JSONObject customStats = (JSONObject) statsSection.get("minecraft:custom");
                if (customStats != null) {
                    Long playTime = (Long) customStats.get("minecraft:play_time");
                    if (playTime != null) {
                        plugin.getLogger().info(String.format("Playtime for %s: %d ticks", statFile.getName(), playTime));
                        return playTime >= minimumPlaytimeTicks;
                    }
                }
            }
            plugin.getLogger().info(String.format("No playtime data for %s, including anyway", statFile.getName()));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("Failed to read playtime from %s: %s", statFile.getName(), e.getMessage()));
            return true;
        }
    }

    private void syncPlayerStats(UUID playerUUID, File statFile) {
        try (FileReader reader = new FileReader(statFile)) {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(reader);
            Map<String, Map<String, Long>> categorizedStats = new HashMap<>();

            JSONObject statsSection = (JSONObject) json.get("stats");
            if (statsSection != null) {
                for (Object categoryObj : statsSection.keySet()) {
                    String category = (String) categoryObj;
                    JSONObject categoryStats = (JSONObject) statsSection.get(category);
                    Map<String, Long> stats = new HashMap<>();
                    for (Object statObj : categoryStats.keySet()) {
                        String statKey = statObj.toString();
                        Object valueObj = categoryStats.get(statKey);
                        Long value = valueObj instanceof Long ? (Long) valueObj : 0L;
                        stats.put(statKey, value);
                    }
                    categorizedStats.put(category, stats);
                }
                plugin.getLogger().info(String.format("Parsed %d stat categories for %s", categorizedStats.size(), playerUUID));
            } else {
                plugin.getLogger().warning(String.format("No stats section in JSON for %s", playerUUID));
            }

            for (Map.Entry<String, Map<String, Long>> entry : categorizedStats.entrySet()) {
                dbManager.savePlayerStats(entry.getKey().replace("minecraft:", ""), playerUUID, entry.getValue());
            }
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("Failed to sync stats for %s: %s", playerUUID, e.getMessage()));
        }
    }

    @SuppressWarnings("deprecation")
    public void viewStats(CommandSender sender, String playerName, String category) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            return;
        }

        try (Connection conn = dbManager.getConnection()) {
            String tableName = "stats_" + category.replaceAll("[^a-zA-Z0-9_]", "_");
            UUID playerUUID = plugin.getServer().getOfflinePlayer(playerName).getUniqueId();
            boolean hasStats = false;
            sender.sendMessage(String.format("§aStats for %s (%s):", playerName, category));

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT stat_key, stat_value FROM " + tableName + " WHERE player_uuid = ?")) {
                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String statKey = rs.getString("stat_key");
                        long value = rs.getLong("stat_value");
                        if (value > 0) {
                            sender.sendMessage(String.format("§7%s: §e%d", statKey, value));
                            hasStats = true;
                        }
                    }
                }
            } catch (SQLException e) {
                sender.sendMessage(String.format("§cCategory not found: %s", category));
                return;
            }

            if (!hasStats) {
                sender.sendMessage(String.format("§cNo stats found for %s in %s", playerName, category));
            }
        } catch (SQLException e) {
            sender.sendMessage(String.format("§cError viewing stats: %s", e.getMessage()));
            plugin.getLogger().severe(String.format("Failed to view stats for %s: %s", playerName, e.getMessage()));
        }
    }
}