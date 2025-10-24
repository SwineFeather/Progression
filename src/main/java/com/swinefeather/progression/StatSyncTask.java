package com.swinefeather.progression;

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
    private final SupabaseManager supabaseManager;
    private final PlaceholderManager placeholderManager;
    private final long minimumPlaytimeTicks;

    public StatSyncTask(JavaPlugin plugin, DatabaseManager dbManager, SupabaseManager supabaseManager, PlaceholderManager placeholderManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.supabaseManager = supabaseManager;
        this.placeholderManager = placeholderManager;
        this.minimumPlaytimeTicks = plugin.getConfig().getLong("minimum-playtime-ticks", 0L);
    }

    public void syncAllPlayers(CommandSender sender) {
        if (!Main.isSyncEnabled(plugin)) {
            plugin.getLogger().info("[Progression] All syncs are globally disabled. Skipping all syncs.");
            if (sender != null) sender.sendMessage("§eAll syncs are globally disabled. No sync will be performed.");
            return;
        }

        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        long startTime = System.currentTimeMillis();
        int processedPlayers = 0;
        int failedPlayers = 0;
        
        try {
            // Collect all player UUIDs from all worlds first
            Map<UUID, String> allPlayers = new HashMap<>();
            
            for (World world : plugin.getServer().getWorlds()) {
                File statsFolder = new File(world.getWorldFolder(), "stats");
                if (!statsFolder.exists() || !statsFolder.isDirectory()) {
                    plugin.getLogger().warning("Stats folder not found in world: " + world.getName());
                    continue;
                }

                File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
                if (statFiles == null) {
                    plugin.getLogger().warning("No stat files found in: " + statsFolder.getPath());
                    continue;
                }

                for (File statFile : statFiles) {
                    try {
                        UUID playerUUID = UUID.fromString(statFile.getName().replace(".json", ""));
                        String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                        if (playerName == null) {
                            playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                        }
                        allPlayers.put(playerUUID, playerName);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to process stat file: " + statFile.getName() + " - " + e.getMessage());
                    }
                }
            }

            // Now process each player with stats from ALL worlds
            for (Map.Entry<UUID, String> entry : allPlayers.entrySet()) {
                UUID playerUUID = entry.getKey();
                String playerName = entry.getValue();
                
                plugin.getLogger().info("Processing player: " + playerName);
                
                // Collect stats from ALL worlds for this player
                Map<String, Object> combinedStats = collectPlayerStatsFromWorlds(playerUUID);
                
                if (combinedStats.isEmpty()) {
                    continue;
                }
                
                // Save to MySQL if available
                if (dbManager != null && dbManager.isConnected()) {
                    dbManager.savePlayerInfo(playerUUID, playerName);
                    // For MySQL, we need to process each world's stats separately since it uses individual tables
                    for (World world : plugin.getServer().getWorlds()) {
                        File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
                        if (statFile.exists() && hasMinimumPlaytime(statFile)) {
                            syncPlayerStats(playerUUID, statFile);
                        }
                    }
                }
                
                // Save to Supabase for ALL players (online and offline)
                if (supabaseManager != null && supabaseManager.isEnabled()) {
                    supabaseManager.syncPlayerStats(playerUUID, playerName, combinedStats);
                }
                
                if (placeholderManager != null) {
                    placeholderManager.syncPlayerPlaceholders(playerUUID);
                }
                
                // DISABLED: Achievement checking and XP calculation to prevent level-up spam
                // These will only be calculated when explicitly triggered or when players join
                if (plugin instanceof Main) {
                    Main mainPlugin = (Main) plugin;
                    if (mainPlugin.achievementManager != null) {
                        // Only sync stats, don't check achievements or calculate XP to prevent level-up spam
                        plugin.getLogger().info("Skipping achievement checking and XP calculation during sync to prevent level-up spam");
                    }
                }
                
                processedPlayers++;
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            if (sender != null) {
                sender.sendMessage("§aFull sync completed for " + processedPlayers + " players in " + duration + "ms!");
                if (failedPlayers > 0) {
                    sender.sendMessage("§cFailed to process " + failedPlayers + " players.");
                }
            }
            plugin.getLogger().info("Full sync completed: " + processedPlayers + " players processed in " + duration + "ms");
            // Full sync completed
            
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage("§cFull sync failed: " + e.getMessage());
            }
            plugin.getLogger().severe("Full sync failed: " + e.getMessage());
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
            Map<Player, Map<String, Object>> allPlayerStats = new HashMap<>();
            
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerUUID = player.getUniqueId();
                // Syncing stats for online player
                
                // Collect stats for both MySQL and Supabase
                Map<String, Object> stats = collectPlayerStatsFromWorlds(playerUUID);
                
                // Save to MySQL if available
                if (dbManager != null && dbManager.isConnected()) {
                    syncSinglePlayer(playerUUID, sender);
                }
                
                // Prepare for Supabase batch sync
                if (supabaseManager != null && supabaseManager.isEnabled()) {
                    allPlayerStats.put(player, stats);
                }
            }
            
            // Batch sync to Supabase
            if (supabaseManager != null && supabaseManager.isEnabled() && !allPlayerStats.isEmpty()) {
                supabaseManager.syncAllPlayers(allPlayerStats);
            }

            // DISABLED: Award calculation on sync to prevent level-up spam
            // Awards will only be calculated when explicitly triggered or when players join
            if (plugin instanceof Main) {
                Main main = (Main) plugin;
                if (main.awardManager != null && main.awardManager.isEnabled()) {
                    // Only sync stats, don't recalculate awards to prevent level-up spam
                    plugin.getLogger().info("Skipping award calculation during sync to prevent level-up spam");
                }
            }
            
            if (sender != null) {
                sender.sendMessage("§aOnline player stat sync completed!");
            }
            plugin.getLogger().info("Online player stat sync finished successfully.");
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage("§cOnline player stat sync failed: " + e.getMessage());
            }
            plugin.getLogger().severe("Online player stat sync failed: " + e.getMessage());
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
            String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
            if (playerName == null) {
                playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                plugin.getLogger().warning("Player name not found for UUID: " + playerUUID + ", using: " + playerName);
            }
            
            // Collect stats from ALL worlds for this player
            Map<String, Object> combinedStats = collectPlayerStatsFromWorlds(playerUUID);
            
            if (combinedStats.isEmpty()) {
                if (sender != null) {
                    sender.sendMessage("§cNo valid stats found for player: " + playerName);
                }
                // No valid stats found for player
                return;
            }
            
            // Save to MySQL if available
            if (dbManager != null && dbManager.isConnected()) {
                dbManager.savePlayerInfo(playerUUID, playerName);
                // For MySQL, we need to process each world's stats separately since it uses individual tables
                for (World world : plugin.getServer().getWorlds()) {
                    File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
                    if (statFile.exists() && hasMinimumPlaytime(statFile)) {
                        syncPlayerStats(playerUUID, statFile);
                    }
                }
            }
            
            // Save to Supabase if available
            if (supabaseManager != null && supabaseManager.isEnabled()) {
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player != null) {
                    supabaseManager.syncPlayerStats(player, combinedStats);
                } else {
                    supabaseManager.syncPlayerStats(playerUUID, playerName, combinedStats);
                }
            }
            
            if (placeholderManager != null) {
                placeholderManager.syncPlayerPlaceholders(playerUUID);
            }
            
            if (sender != null) {
                sender.sendMessage("§aStat sync for player " + playerName + " completed!");
            }
            // Stat sync for player finished successfully
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage("§cStat sync for player " + playerUUID + " failed: " + e.getMessage());
            }
            plugin.getLogger().severe("Stat sync for player " + playerUUID + " failed: " + e.getMessage());
        }
    }
    
    public void onPlayerQuit(Player player) {
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            UUID playerUUID = player.getUniqueId();
            Map<String, Object> stats = collectPlayerStatsFromWorlds(playerUUID);
            supabaseManager.onPlayerQuit(player, stats);
        }
        // Also sync awards, medals, and points if AwardManager is available (disabled for Minecraft)
        // if (plugin instanceof Main) {
        //     Main main = (Main) plugin;
        //     if (main.awardManager != null && main.awardManager.isEnabled()) {
        //         main.awardManager.syncAllAwardsToSupabase();
        //         main.awardManager.syncAllMedalsToSupabase();
        //         main.awardManager.syncAllPointsToSupabase();
        //     }
        // }
    }
    
    private Map<String, Object> collectPlayerStatsFromWorlds(UUID playerUUID) {
        Map<String, Object> allStats = new HashMap<>();
        // Load possible stats from JSON (if available)
        Map<String, Map<String, Object>> possibleStats = null;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("possible_stats.json");
            if (java.nio.file.Files.exists(path)) {
                String json = new String(java.nio.file.Files.readAllBytes(path));
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, Map<String, Object>>>(){}.getType();
                possibleStats = gson.fromJson(json, type);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load possible_stats.json: " + e.getMessage());
        }
        for (World world : plugin.getServer().getWorlds()) {
            File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
            if (statFile.exists()) {
                Map<String, Object> worldStats = collectPlayerStats(playerUUID, statFile);
                allStats.putAll(worldStats);
            }
        }
        // Fill in missing stats with zero if possibleStats is available
        if (possibleStats != null) {
            for (Map.Entry<String, Map<String, Object>> entry : possibleStats.entrySet()) {
                String category = entry.getKey();
                Map<String, Object> possibleCategoryStats = entry.getValue();
                Map<String, Object> playerCategoryStats = (Map<String, Object>) allStats.getOrDefault(category, new HashMap<>());
                for (String statKey : possibleCategoryStats.keySet()) {
                    if (!playerCategoryStats.containsKey(statKey)) {
                        playerCategoryStats.put(statKey, 0L);
                        plugin.getLogger().warning("Player " + playerUUID + " missing stat: " + category + "." + statKey);
                    }
                }
                allStats.put(category, playerCategoryStats);
            }
        }
        // Flatten the nested stats map
        Map<String, Object> flatStats = flattenStatsMap(allStats);
        return flatStats;
    }

    /**
     * Flattens a nested stats map (e.g. {"custom": {"play_time": 123}}) to flat keys ("custom_play_time": 123)
     */
    private Map<String, Object> flattenStatsMap(Map<String, Object> nestedStats) {
        Map<String, Object> flat = new HashMap<>();
        for (Map.Entry<String, Object> entry : nestedStats.entrySet()) {
            String category = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<?, ?> subMap = (Map<?, ?>) value;
                for (Map.Entry<?, ?> subEntry : subMap.entrySet()) {
                    String statKey = subEntry.getKey().toString();
                    Object statValue = subEntry.getValue();
                    flat.put(category + "_" + statKey, statValue);
                }
            } else {
                flat.put(category, value);
            }
        }
        return flat;
    }
    
    private Map<String, Object> collectPlayerStats(UUID playerUUID, File statFile) {
        Map<String, Object> stats = new HashMap<>();
        try (FileReader reader = new FileReader(statFile)) {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(reader);
            
            // Collect stats
            JSONObject statsSection = (JSONObject) json.get("stats");
            if (statsSection != null) {
                for (Object categoryObj : statsSection.keySet()) {
                    String category = (String) categoryObj;
                    JSONObject categoryStats = (JSONObject) statsSection.get(category);
                    Map<String, Object> categoryData = new HashMap<>();
                    
                    for (Object statObj : categoryStats.keySet()) {
                        String statKey = statObj.toString();
                        Object valueObj = categoryStats.get(statKey);
                        String cleanStatKey = statKey.replace("minecraft:", "");
                        
                        if (valueObj instanceof Long) {
                            categoryData.put(cleanStatKey, (Long) valueObj);
                        } else if (valueObj instanceof Integer) {
                            categoryData.put(cleanStatKey, ((Integer) valueObj).longValue());
                        } else {
                            categoryData.put(cleanStatKey, valueObj.toString());
                        }
                    }
                    
                    String cleanCategory = category.replace("minecraft:", "");
                    stats.put(cleanCategory, categoryData);
                }
            }
            
            // Collect advancements
            JSONObject advancementsSection = (JSONObject) json.get("advancements");
            if (advancementsSection != null) {
                Map<String, Object> advancementsData = new HashMap<>();
                for (Object advancementObj : advancementsSection.keySet()) {
                    String advancementKey = advancementObj.toString();
                    Object advancementValue = advancementsSection.get(advancementKey);
                    String cleanAdvancementKey = advancementKey.replace("minecraft:", "");
                    
                    if (advancementValue instanceof JSONObject) {
                        JSONObject criteria = (JSONObject) advancementValue;
                        if (!criteria.isEmpty()) {
                            advancementsData.put(cleanAdvancementKey, true);
                        }
                    } else if (advancementValue instanceof Boolean) {
                        advancementsData.put(cleanAdvancementKey, (Boolean) advancementValue);
                    }
                }
                stats.put("advancements", advancementsData);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to collect stats from " + statFile.getName() + ": " + e.getMessage());
        }
        return stats;
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
                        plugin.getLogger().info("Playtime for " + statFile.getName() + ": " + playTime + " ticks");
                        return playTime >= minimumPlaytimeTicks;
                    }
                }
            }
            plugin.getLogger().info("No playtime data for " + statFile.getName() + ", including anyway");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to read playtime from " + statFile.getName() + ": " + e.getMessage());
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
            }

            if (dbManager != null && dbManager.isConnected()) {
                for (Map.Entry<String, Map<String, Long>> entry : categorizedStats.entrySet()) {
                    String category = entry.getKey().replace("minecraft:", "");
                    Map<String, Long> stats = entry.getValue();
                    dbManager.savePlayerStats(category, playerUUID, stats);
                }
            }
            
            // Save placeholder stats to Supabase if available
            if (supabaseManager != null && supabaseManager.isEnabled()) {
                // This will be handled by the Supabase sync
                plugin.getLogger().info("Supabase sync will handle placeholder stats");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to sync player stats for " + playerUUID + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    public void viewStats(CommandSender sender, String playerName, String category) {
        if (dbManager == null || !dbManager.isConnected()) {
            sender.sendMessage("§cDatabase not connected!");
            return;
        }

        try {
            Player player = plugin.getServer().getPlayer(playerName);
            UUID playerUUID = player != null ? player.getUniqueId() : plugin.getServer().getOfflinePlayer(playerName).getUniqueId();

            if (playerUUID == null) {
                sender.sendMessage("§cPlayer not found: " + playerName);
                return;
            }

            Connection connection = dbManager.getConnection();
            if (connection == null) {
                sender.sendMessage("§cDatabase connection failed!");
                return;
            }

            String sql = "SELECT stat_name, stat_value FROM player_stats WHERE player_uuid = ? AND stat_category = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, category);

                try (ResultSet rs = stmt.executeQuery()) {
                    sender.sendMessage("§aStats for " + playerName + " in category " + category + ":");
                    boolean found = false;
                    while (rs.next()) {
                        String statName = rs.getString("stat_name");
                        Long statValue = rs.getLong("stat_value");
                        sender.sendMessage("§7" + statName + ": " + statValue);
                        found = true;
                    }
                    if (!found) {
                        sender.sendMessage("§eNo stats found for this category.");
                    }
                }
            }
        } catch (Exception e) {
            sender.sendMessage("§cError viewing stats: " + e.getMessage());
            plugin.getLogger().severe("Error viewing stats: " + e.getMessage());
        }
    }
}