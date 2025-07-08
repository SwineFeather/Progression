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
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        try {
            for (World world : plugin.getServer().getWorlds()) {
                plugin.getLogger().info("Checking world: " + world.getName());
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
                        plugin.getLogger().info("Processing stat file for UUID: " + playerUUID);
                        if (!hasMinimumPlaytime(statFile)) {
                            plugin.getLogger().info("Skipping " + playerUUID + ": Playtime below " + minimumPlaytimeTicks + " ticks");
                            continue;
                        }
                        String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                        if (playerName == null) {
                            playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                            plugin.getLogger().warning("Player name not found for UUID: " + playerUUID + ", using: " + playerName);
                        }
                        
                        // Save to MySQL if available
                        if (dbManager != null && dbManager.isConnected()) {
                            dbManager.savePlayerInfo(playerUUID, playerName);
                            syncPlayerStats(playerUUID, statFile);
                        }
                        
                        // Save to Supabase for ALL players (online and offline)
                        if (supabaseManager != null && supabaseManager.isEnabled()) {
                            Map<String, Object> stats = collectPlayerStats(playerUUID, statFile);
                            supabaseManager.syncPlayerStats(playerUUID, playerName, stats);
                        }
                        
                        if (placeholderManager != null) {
                            placeholderManager.syncPlayerPlaceholders(playerUUID);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in file: " + statFile.getName());
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error processing stat file " + statFile.getName() + ": " + e.getMessage());
                    }
                }
            }
            if (sender != null) {
                sender.sendMessage("§aFull stat sync completed!");
            }
            plugin.getLogger().info("Full stat sync finished successfully.");
        } catch (Exception e) {
            if (sender != null) {
                sender.sendMessage("§cStat sync failed: " + e.getMessage());
            }
            plugin.getLogger().severe("Full stat sync failed: " + e.getMessage());
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
                plugin.getLogger().info("Syncing stats for online player: " + playerUUID);
                
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

            // Trigger award calculation after stat sync
            if (plugin instanceof Main) {
                Main main = (Main) plugin;
                if (main.awardManager != null && main.awardManager.isEnabled()) {
                    main.awardManager.calculateAllAwards(allPlayerStats);
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
            for (World world : plugin.getServer().getWorlds()) {
                File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
                if (!statFile.exists()) {
                    plugin.getLogger().info("No stat file found for " + playerUUID + " in world: " + world.getName());
                    continue;
                }

                if (!hasMinimumPlaytime(statFile)) {
                    plugin.getLogger().info("Skipping " + playerUUID + ": Playtime below " + minimumPlaytimeTicks + " ticks");
                    continue;
                }

                String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                if (playerName == null) {
                    playerName = "Unknown_" + playerUUID.toString().substring(0, 8);
                    plugin.getLogger().warning("Player name not found for UUID: " + playerUUID + ", using: " + playerName);
                }
                
                // Save to MySQL if available
                if (dbManager != null && dbManager.isConnected()) {
                    dbManager.savePlayerInfo(playerUUID, playerName);
                    syncPlayerStats(playerUUID, statFile);
                }
                
                // Save to Supabase if available
                if (supabaseManager != null && supabaseManager.isEnabled()) {
                    Player player = plugin.getServer().getPlayer(playerUUID);
                    if (player != null) {
                        Map<String, Object> stats = collectPlayerStats(playerUUID, statFile);
                        supabaseManager.syncPlayerStats(player, stats);
                    }
                }
                
                if (placeholderManager != null) {
                    placeholderManager.syncPlayerPlaceholders(playerUUID);
                }
            }
            if (sender != null) {
                sender.sendMessage("§aStat sync for player " + playerUUID + " completed!");
            }
            plugin.getLogger().info("Stat sync for player " + playerUUID + " finished successfully.");
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
        // Also sync awards, medals, and points if AwardManager is available
        if (plugin instanceof Main) {
            Main main = (Main) plugin;
            if (main.awardManager != null && main.awardManager.isEnabled()) {
                main.awardManager.syncAllAwardsToSupabase();
                main.awardManager.syncAllMedalsToSupabase();
                main.awardManager.syncAllPointsToSupabase();
            }
        }
    }
    
    private Map<String, Object> collectPlayerStatsFromWorlds(UUID playerUUID) {
        Map<String, Object> allStats = new HashMap<>();
        
        for (World world : plugin.getServer().getWorlds()) {
            File statFile = new File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
            if (statFile.exists()) {
                Map<String, Object> worldStats = collectPlayerStats(playerUUID, statFile);
                allStats.putAll(worldStats);
            }
        }
        
        return allStats;
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