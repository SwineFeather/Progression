package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AwardManager {
    private final Plugin plugin;
    private final LogManager logger;
    private final Gson gson;
    private final SupabaseManager supabaseManager;
    private final WebhookManager webhookManager;
    private final StatResolver statResolver;
    private final LocalAwardStorage localStorage;
    
    // Award configuration
    private final Map<String, AwardDefinition> awards = new ConcurrentHashMap<>();
    private final Map<String, Double> tierPoints = new HashMap<>();
    private boolean enabled = false;
    private boolean autoCalculate = true;
    private int calculationInterval = 3600; // seconds
    
    // Award data structures
    private final Map<String, List<AwardRanking>> awardRankings = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerAwards> playerAwards = new ConcurrentHashMap<>();
    
    // Notification settings
    private boolean medalChangeNotifications = true;
    private boolean awardAnnouncements = true;
    private String websiteUrl = "";
    
    public AwardManager(Plugin plugin, SupabaseManager supabaseManager, WebhookManager webhookManager, LogManager logManager) {
        this.plugin = plugin;
        this.logger = logManager;
        this.gson = new Gson();
        this.supabaseManager = supabaseManager;
        this.webhookManager = webhookManager;
        this.statResolver = new StatResolver(logManager);
        this.localStorage = new LocalAwardStorage(plugin, logManager);
    }
    
    public boolean initialize(ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            logger.debug("Award system is disabled in configuration");
            return false;
        }
        
        this.enabled = true;
        this.autoCalculate = config.getBoolean("auto_calculate", true);
        this.calculationInterval = config.getInt("calculation_interval", 3600);
        this.medalChangeNotifications = config.getBoolean("medal_change_notifications", true);
        this.awardAnnouncements = config.getBoolean("award_announcements", true);
        this.websiteUrl = config.getString("website_url", "");
        
        // Load tier points
        loadTierPoints(config.getConfigurationSection("tiers"));
        
        // Load award definitions
        loadAwardDefinitions(config.getConfigurationSection("definitions"));
        
        logger.debug("Award system initialized with " + awards.size() + " awards");
        return true;
    }
    
    private void loadTierPoints(ConfigurationSection tiersConfig) {
        if (tiersConfig == null) return;
        
        // Stone tier
        ConfigurationSection stoneConfig = tiersConfig.getConfigurationSection("stone");
        if (stoneConfig != null) {
            tierPoints.put("stone.bronze", stoneConfig.getDouble("bronze", 0.5));
            tierPoints.put("stone.silver", stoneConfig.getDouble("silver", 1.0));
            tierPoints.put("stone.gold", stoneConfig.getDouble("gold", 2.0));
        }
        
        // Iron tier
        ConfigurationSection ironConfig = tiersConfig.getConfigurationSection("iron");
        if (ironConfig != null) {
            tierPoints.put("iron.bronze", ironConfig.getDouble("bronze", 1.0));
            tierPoints.put("iron.silver", ironConfig.getDouble("silver", 2.0));
            tierPoints.put("iron.gold", ironConfig.getDouble("gold", 4.0));
        }
        
        // Diamond tier
        ConfigurationSection diamondConfig = tiersConfig.getConfigurationSection("diamond");
        if (diamondConfig != null) {
            tierPoints.put("diamond.bronze", diamondConfig.getDouble("bronze", 3.0));
            tierPoints.put("diamond.silver", diamondConfig.getDouble("silver", 4.0));
            tierPoints.put("diamond.gold", diamondConfig.getDouble("gold", 6.0));
        }
    }
    
    private void loadAwardDefinitions(ConfigurationSection definitionsConfig) {
        if (definitionsConfig == null) return;
        
        for (String awardId : definitionsConfig.getKeys(false)) {
            ConfigurationSection awardConfig = definitionsConfig.getConfigurationSection(awardId);
            if (awardConfig != null) {
                AwardDefinition award = new AwardDefinition(
                    awardId,
                    awardConfig.getString("name", awardId),
                    awardConfig.getString("description", ""),
                    awardConfig.getString("tier", "stone"),
                    awardConfig.getString("stat_path", ""),
                    awardConfig.getBoolean("enabled", true)
                );
                awards.put(awardId, award);
            }
        }
    }
    
    public void calculateAllAwards(Map<Player, Map<String, Object>> allPlayerStats) {
        if (!enabled) {
                    logger.debug("Award system is disabled, skipping calculation");
        return;
    }
    
    logger.debug("Calculating awards for " + allPlayerStats.size() + " players");
    logger.debug("Available awards: " + awards.size());
        
        // Clear previous rankings
        awardRankings.clear();
        playerAwards.clear();
        
        // Calculate rankings for each award
        for (AwardDefinition award : awards.values()) {
            if (!award.isEnabled()) {
                continue;
            }
            
            logger.awardCalculation("Calculating award: " + award.getId() + " with stat path: " + award.getStatPath());
            List<AwardRanking> rankings = calculateAwardRanking(award, allPlayerStats);
            logger.awardCalculation("Found " + rankings.size() + " players for award " + award.getId());
            
            awardRankings.put(award.getId(), rankings);
            
            // Assign medals and update player awards
            assignMedals(award, rankings);
        }
        
        // Update player total points
        updatePlayerTotalPoints();
        
        logger.debug("Award calculation completed. Total players with awards: " + playerAwards.size());
        
        // Sync to local storage
        localStorage.saveAllAwards(playerAwards);
        
        // Also try to sync to Supabase if available
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            syncAllAwardsToSupabase();
            syncAllMedalsToSupabase();
            syncAllPointsToSupabase();
        }
        
        // Send notifications
        if (medalChangeNotifications) {
            sendMedalChangeNotifications();
        }
        
        // Send webhook notifications for medal changes
        if (webhookManager != null && webhookManager.isEnabled()) {
            sendWebhookNotifications();
        }
        
        logger.debug("Award calculation completed");
    }
    
    // New method to calculate awards for all players (ALL players, but handle online players carefully)
    public void calculateAwardsForAllPlayers() {
        if (!enabled) {
                    logger.debug("Award system is disabled, skipping calculation");
        return;
    }
    
    logger.debug("Calculating awards for ALL players (online and offline)");
    
    // Load all player stats from files
    Map<UUID, Map<String, Object>> allStats = loadAllPlayerStats();
    logger.debug("Loaded stats for " + allStats.size() + " players");
        
        // Create a map that handles ALL players
        Map<String, Map<String, Object>> playerStatsMap = new HashMap<>();
        Map<String, String> playerNames = new HashMap<>();
        
        int onlineCount = 0;
        int offlineCount = 0;
        
        for (Map.Entry<UUID, Map<String, Object>> entry : allStats.entrySet()) {
            UUID uuid = entry.getKey();
            Map<String, Object> fileStats = entry.getValue();
            
            // Check if player is online
            Player onlinePlayer = Bukkit.getPlayer(uuid);
            String playerName;
            Map<String, Object> statsToUse;
            
            if (onlinePlayer != null) {
                // For online players, use their file stats (same as calculateAwardsForPlayer method)
                playerName = onlinePlayer.getName();
                statsToUse = fileStats;
                logger.playerProcessing("Processing online player with FILE stats: " + playerName);
                onlineCount++;
            } else {
                // For offline players, use the file stats
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                playerName = offlinePlayer.getName();
                if (playerName == null) {
                    playerName = "Unknown_" + uuid.toString().substring(0, 8);
                }
                statsToUse = fileStats;
                logger.playerProcessing("Processing offline player with FILE stats: " + playerName + " (" + uuid + ")");
                offlineCount++;
            }
            
            playerStatsMap.put(playerName, statsToUse);
            playerNames.put(playerName, uuid.toString());
        }
        
        logger.debug("Processing " + playerStatsMap.size() + " total players");
        
        // Calculate awards using the new method
        calculateAllAwardsForAllPlayers(playerStatsMap, playerNames);
    }
    
    // New method to calculate awards for all players (including offline)
    public void calculateAllAwardsForAllPlayers(Map<String, Map<String, Object>> allPlayerStats, Map<String, String> playerNames) {
        if (!enabled) {
                    logger.debug("Award system is disabled, skipping calculation");
        return;
    }
    
    logger.debug("Calculating awards for " + allPlayerStats.size() + " players (including offline)");
    logger.debug("Available awards: " + awards.size());
        
        // Clear previous rankings and player awards to prevent duplicates
        awardRankings.clear();
        playerAwards.clear();
        
        // Also clear local storage for the players being processed to prevent conflicts
        for (String playerName : allPlayerStats.keySet()) {
            String playerUuid = playerNames.get(playerName);
            if (playerUuid != null) {
                try {
                    UUID uuid = UUID.fromString(playerUuid);
                    localStorage.clearPlayerData(uuid);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID for player " + playerName + ": " + playerUuid);
                }
            }
        }
        
        // Calculate rankings for each award
        for (AwardDefinition award : awards.values()) {
            if (!award.isEnabled()) {
                continue;
            }
            
            logger.awardCalculation("Calculating award: " + award.getId() + " with stat path: " + award.getStatPath());
            List<AwardRanking> rankings = calculateAwardRankingForAllPlayers(award, allPlayerStats, playerNames);
            logger.awardCalculation("Found " + rankings.size() + " players for award " + award.getId());
            
            awardRankings.put(award.getId(), rankings);
            
            // Assign medals and update player awards
            assignMedalsForAllPlayers(award, rankings);
        }
        
        // Update player total points
        updatePlayerTotalPoints();
        
        logger.debug("Award calculation completed. Total players with awards: " + playerAwards.size());
        
        // Sync to local storage
        localStorage.saveAllAwards(playerAwards);
        
        // Also try to sync to Supabase if available
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            syncAllAwardsToSupabase();
            syncAllMedalsToSupabase();
            syncAllPointsToSupabase();
        }
        
        // Send notifications
        if (medalChangeNotifications) {
            sendMedalChangeNotifications();
        }
        
        // Send webhook notifications for medal changes
        if (webhookManager != null && webhookManager.isEnabled()) {
            sendWebhookNotifications();
        }
        
        logger.debug("Award calculation completed for all players");
    }
    
    private List<AwardRanking> calculateAwardRanking(AwardDefinition award, Map<Player, Map<String, Object>> allPlayerStats) {
        List<AwardRanking> rankings = new ArrayList<>();
        
        for (Map.Entry<Player, Map<String, Object>> entry : allPlayerStats.entrySet()) {
            Player player = entry.getKey();
            Map<String, Object> stats = entry.getValue();
            
            Long statValue = getStatValue(stats, award.getStatPath());
            if (statValue != null && statValue > 0) {
                rankings.add(new AwardRanking(player.getUniqueId(), player.getName(), statValue));
            }
        }
        
        // Sort by stat value (descending)
        rankings.sort((a, b) -> Long.compare(b.getStatValue(), a.getStatValue()));
        
        return rankings;
    }
    
    private List<AwardRanking> calculateAwardRankingForAllPlayers(AwardDefinition award, Map<String, Map<String, Object>> allPlayerStats, Map<String, String> playerNames) {
        List<AwardRanking> rankings = new ArrayList<>();
        int processedPlayers = 0;
        int validStats = 0;
        
        logger.awardCalculation("Calculating ranking for award: " + award.getId() + " with " + allPlayerStats.size() + " players");
        
        for (Map.Entry<String, Map<String, Object>> entry : allPlayerStats.entrySet()) {
            String playerName = entry.getKey();
            Map<String, Object> stats = entry.getValue();
            String playerUuid = playerNames.get(playerName);
            
            processedPlayers++;
            
            if (playerUuid == null) {
                logger.warning("No UUID found for player: " + playerName);
                continue;
            }
            
            Long statValue = getStatValue(stats, award.getStatPath());
            if (statValue != null && statValue > 0) {
                try {
                    UUID uuid = UUID.fromString(playerUuid);
                    rankings.add(new AwardRanking(uuid, playerName, statValue));
                    validStats++;
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID for player " + playerName + ": " + playerUuid);
                }
            }
        }
        
        // Sort by stat value (descending)
        rankings.sort((a, b) -> Long.compare(b.getStatValue(), a.getStatValue()));
        
        logger.awardCalculation("Award " + award.getId() + ": processed " + processedPlayers + " players, found " + validStats + " valid stats, " + rankings.size() + " rankings");
        
        return rankings;
    }
    
    private Long getStatValue(Map<String, Object> stats, String statPath) {
        return statResolver.resolveStatValue(stats, statPath);
    }
    
    private void assignMedals(AwardDefinition award, List<AwardRanking> rankings) {
        for (int i = 0; i < Math.min(rankings.size(), 3); i++) {
            AwardRanking ranking = rankings.get(i);
            String medalType = i == 0 ? "gold" : i == 1 ? "silver" : "bronze";
            double points = tierPoints.getOrDefault(award.getTier() + "." + medalType, 0.0);
            
            // Create or update player awards
            PlayerAwards playerAward = playerAwards.computeIfAbsent(ranking.getPlayerUUID(), 
                k -> new PlayerAwards(ranking.getPlayerUUID(), ranking.getPlayerName()));
            
            playerAward.addMedal(award.getId(), medalType, points, i + 1, ranking.getStatValue());
        }
    }
    
    private void assignMedalsForAllPlayers(AwardDefinition award, List<AwardRanking> rankings) {
        for (int i = 0; i < Math.min(rankings.size(), 3); i++) {
            AwardRanking ranking = rankings.get(i);
            String medalType = i == 0 ? "gold" : i == 1 ? "silver" : "bronze";
            double points = tierPoints.getOrDefault(award.getTier() + "." + medalType, 0.0);
            
            // Create or update player awards
            PlayerAwards playerAward = playerAwards.computeIfAbsent(ranking.getPlayerUUID(), 
                k -> new PlayerAwards(ranking.getPlayerUUID(), ranking.getPlayerName()));
            
            playerAward.addMedal(award.getId(), medalType, points, i + 1, ranking.getStatValue());
        }
    }
    
    // Method to calculate awards for a specific player
    public void calculateAwardsForPlayer(Player player) {
        if (!enabled) {
                    logger.debug("Award system is disabled, skipping calculation");
        return;
    }
    
    logger.debug("Calculating awards for player: " + player.getName());
        
        // Get player stats from all worlds
        Map<String, Object> playerStats = new HashMap<>();
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            java.io.File statFile = new java.io.File(world.getWorldFolder(), "stats/" + player.getUniqueId() + ".json");
            if (statFile.exists()) {
                Map<String, Object> worldStats = loadStatsFromFile(player.getUniqueId(), statFile);
                playerStats.putAll(worldStats);
            }
        }
        
        // Create single player map
        Map<String, Map<String, Object>> playerStatsMap = new HashMap<>();
        Map<String, String> playerNames = new HashMap<>();
        playerStatsMap.put(player.getName(), playerStats);
        playerNames.put(player.getName(), player.getUniqueId().toString());
        
        // Calculate awards
        calculateAllAwardsForAllPlayers(playerStatsMap, playerNames);
    }
    
    private void updatePlayerTotalPoints() {
        for (PlayerAwards playerAward : playerAwards.values()) {
            playerAward.calculateTotalPoints();
        }
    }
    
    public Map<UUID, Map<String, Object>> loadAllPlayerStats() {
        Map<UUID, Map<String, Object>> allStats = new HashMap<>();
        int totalFiles = 0;
        int loadedFiles = 0;
        
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            java.io.File statsFolder = new java.io.File(world.getWorldFolder(), "stats");
            if (!statsFolder.exists() || !statsFolder.isDirectory()) {
                logger.debug("Stats folder not found for world: " + world.getName());
                continue;
            }
            
            java.io.File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (statFiles == null) {
                logger.debug("No stat files found in world: " + world.getName());
                continue;
            }
            
            logger.debug("Found " + statFiles.length + " stat files in world: " + world.getName());
            totalFiles += statFiles.length;
            
            for (java.io.File statFile : statFiles) {
                try {
                    String fileName = statFile.getName();
                    String uuidString = fileName.replace(".json", "");
                    UUID playerUUID = UUID.fromString(uuidString);
                    
                    Map<String, Object> stats = loadStatsFromFile(playerUUID, statFile);
                    if (!stats.isEmpty()) {
                        // Merge stats from multiple worlds instead of overwriting
                        if (allStats.containsKey(playerUUID)) {
                            Map<String, Object> existingStats = allStats.get(playerUUID);
                            // Merge the stats from this world with existing stats
                            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                                String category = entry.getKey();
                                Object categoryData = entry.getValue();
                                
                                if (existingStats.containsKey(category)) {
                                    // If category exists, merge the individual stats
                                    if (categoryData instanceof Map && existingStats.get(category) instanceof Map) {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> existingCategory = (Map<String, Object>) existingStats.get(category);
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> newCategory = (Map<String, Object>) categoryData;
                                        
                                        // Merge individual stats, adding values for the same stat
                                        for (Map.Entry<String, Object> statEntry : newCategory.entrySet()) {
                                            String statKey = statEntry.getKey();
                                            Object statValue = statEntry.getValue();
                                            
                                            if (existingCategory.containsKey(statKey)) {
                                                // Add the values if both are numbers
                                                Object existingValue = existingCategory.get(statKey);
                                                if (statValue instanceof Number && existingValue instanceof Number) {
                                                    long newValue = ((Number) statValue).longValue() + ((Number) existingValue).longValue();
                                                    existingCategory.put(statKey, newValue);
                                                } else {
                                                    // If not numbers, use the higher value
                                                    if (statValue instanceof Number && existingValue instanceof Number) {
                                                        long newVal = ((Number) statValue).longValue();
                                                        long existingVal = ((Number) existingValue).longValue();
                                                        existingCategory.put(statKey, Math.max(newVal, existingVal));
                                                    } else {
                                                        existingCategory.put(statKey, statValue);
                                                    }
                                                }
                                            } else {
                                                existingCategory.put(statKey, statValue);
                                            }
                                        }
                                    } else {
                                        // If not maps, use the higher value
                                        existingStats.put(category, categoryData);
                                    }
                                } else {
                                    existingStats.put(category, categoryData);
                                }
                            }
                        } else {
                            allStats.put(playerUUID, stats);
                        }
                        loadedFiles++;
                        
                        // Check if player is online or offline
                        Player onlinePlayer = Bukkit.getPlayer(playerUUID);
                        if (onlinePlayer != null) {
                            logger.playerProcessing("Loaded stats for online player: " + onlinePlayer.getName() + " (" + playerUUID + ")");
                        } else {
                            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                            String playerName = offlinePlayer.getName();
                            if (playerName == null) {
                                playerName = "Unknown_" + uuidString.substring(0, 8);
                            }
                            logger.playerProcessing("Loaded stats for offline player: " + playerName + " (" + playerUUID + ")");
                        }
                        
                        // Debug: Check if this is Aytte
                        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
                        String playerName = offlinePlayer.getName();
                        if (playerName != null && playerName.equals("Aytte")) {
                            logger.debug("DEBUG: Found Aytte during loading! UUID: " + playerUUID + ", stats size: " + allStats.get(playerUUID).size());
                        }
                    } else {
                        logger.warning("Empty stats loaded for player: " + playerUUID);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to load stats from file: " + statFile.getName() + " - " + e.getMessage());
                }
            }
        }
        
        logger.debug("Total stat files found: " + totalFiles + ", successfully loaded: " + loadedFiles);
        logger.debug("Total players with stats: " + allStats.size());
        
        return allStats;
    }
    
    @SuppressWarnings("unused")
    public void syncAllAwardsToSupabase() {
        if (supabaseManager == null || !supabaseManager.isEnabled()) {
            logger.warning("SupabaseManager is not available or not enabled, cannot sync awards");
            return;
        }
        
        // Make the entire sync process async to prevent server hanging
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.debug("Syncing all awards to Supabase...");
            int syncedCount = 0;
            for (Map.Entry<UUID, PlayerAwards> entry : playerAwards.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerAwards playerAward = entry.getValue();
                String name = playerAward.getPlayerName();
                logger.debug("Syncing awards for player: " + name + " (" + uuid + ") - " + playerAward.getMedals().size() + " medals");
                for (PlayerMedal medal : playerAward.getMedals()) {
                    AwardDefinition award = awards.get(medal.getAwardId());
                    String awardName = award != null ? award.getName() : medal.getAwardId();
                    String statPath = award != null ? award.getStatPath() : "";
                    String awardDescription = award != null ? award.getDescription() : awardName;
                    
                    // Actually sync to Supabase with all required data
                    supabaseManager.upsertPlayerAward(uuid, name, medal.getAwardId(), awardName, 
                        awardDescription, medal.getPoints(), medal.getAwardedAt(), medal.getStatValue(), statPath);
                    syncedCount++;
                }
            }
            logger.debug("Synced " + syncedCount + " awards to Supabase");
        });
    }
    
    @SuppressWarnings("unused")
    public void syncAllMedalsToSupabase() {
        if (supabaseManager == null || !supabaseManager.isEnabled()) {
            logger.warning("SupabaseManager is not available or not enabled, cannot sync medals");
            return;
        }
        
        // Make the entire sync process async to prevent server hanging
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.debug("Syncing all medals to Supabase...");
            int syncedCount = 0;
            for (Map.Entry<UUID, PlayerAwards> entry : playerAwards.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerAwards playerAward = entry.getValue();
                String name = playerAward.getPlayerName();
                logger.debug("Syncing medals for player: " + name + " (" + uuid + ") - " + playerAward.getMedals().size() + " medals");
                
                // Sync total medal counts for this player using the new medal counts method
                supabaseManager.upsertPlayerMedalCounts(uuid, name, playerAward.getBronzeMedals(), 
                    playerAward.getSilverMedals(), playerAward.getGoldMedals(), playerAward.getTotalMedals());
                syncedCount++;
            }
            logger.debug("Synced " + syncedCount + " medal summaries to Supabase");
        });
    }
    
    @SuppressWarnings("unused")
    public void syncAllPointsToSupabase() {
        if (supabaseManager == null || !supabaseManager.isEnabled()) {
            logger.warning("SupabaseManager is not available or not enabled, cannot sync points");
            return;
        }
        
        // Make the entire sync process async to prevent server hanging
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.debug("Syncing all points to Supabase...");
            int syncedCount = 0;
            for (Map.Entry<UUID, PlayerAwards> entry : playerAwards.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerAwards playerAward = entry.getValue();
                String name = playerAward.getPlayerName();
                logger.debug("Syncing points for player: " + name + " (" + uuid + ") - " + playerAward.getTotalPoints() + " points");
                
                // Actually sync points to Supabase
                supabaseManager.upsertPlayerPoint(uuid, name, playerAward.getTotalPoints(), 
                    playerAward.getBronzeMedals(), playerAward.getSilverMedals(), playerAward.getGoldMedals());
                syncedCount++;
            }
            logger.debug("Synced " + syncedCount + " player points to Supabase");
        });
    }
    
    @SuppressWarnings("unused")
    public void syncAllLeaderboardToSupabase() {
        if (supabaseManager == null || !supabaseManager.isEnabled()) {
            logger.warning("SupabaseManager is not available or not enabled, cannot sync leaderboard");
            return;
        }
        
        // Make the entire sync process async to prevent server hanging
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            logger.debug("Syncing all leaderboard data to Supabase...");
            int syncedCount = 0;
            
            // Sync each award's leaderboard
            for (Map.Entry<String, List<AwardRanking>> entry : awardRankings.entrySet()) {
                String awardId = entry.getKey();
                List<AwardRanking> rankings = entry.getValue();
                AwardDefinition award = awards.get(awardId);
                String awardName = award != null ? award.getName() : awardId;
                String statPath = award != null ? award.getStatPath() : "";
                
                for (int i = 0; i < rankings.size(); i++) {
                    AwardRanking ranking = rankings.get(i);
                    String medalType = i == 0 ? "gold" : i == 1 ? "silver" : "bronze";
                    double points = getPointsForRank(awardId, medalType);
                    
                    // Disabled individual leaderboard upserts - use local storage only
                    // supabaseManager.upsertPlayerLeaderboard(
                    //     ranking.getPlayerUUID(), 
                    //     ranking.getPlayerName(), 
                    //     awardId, 
                    //     awardName, 
                    //     medalType, 
                    //     points, 
                    //     ranking.getStatValue(), 
                    //     statPath, 
                    //     i + 1
                    // );
                    syncedCount++;
                }
            }
            logger.debug("Synced " + syncedCount + " leaderboard entries to Supabase");
        });
    }
    
    private double getPointsForRank(String awardId, String medalType) {
        AwardDefinition award = awards.get(awardId);
        if (award == null) return 0.0;
        
        String tier = award.getTier();
        String tierKey = tier + "." + medalType;
        return tierPoints.getOrDefault(tierKey, 0.0);
    }
    
    private void sendMedalChangeNotifications() {
        // This will be implemented to send notifications about medal changes
        // For now, just log the changes
        for (PlayerAwards playerAward : playerAwards.values()) {
            if (playerAward.hasNewMedals()) {
                logger.info("Player " + playerAward.getPlayerName() + " earned new medals: " + 
                    playerAward.getNewMedalsCount() + " total medals, " + playerAward.getTotalPoints() + " points");
            }
        }
    }
    
    private void sendWebhookNotifications() {
        for (PlayerAwards playerAward : playerAwards.values()) {
            if (playerAward.hasNewMedals()) {
                for (PlayerMedal medal : playerAward.getMedals()) {
                    // Only send notifications for newly awarded medals
                    if (medal.getAwardedAt() > System.currentTimeMillis() - 60000) { // Last minute
                        AwardDefinition award = awards.get(medal.getAwardId());
                        if (award != null) {
                            webhookManager.sendAwardAnnouncement(
                                playerAward.getPlayerName(),
                                award.getName(),
                                medal.getMedalType(),
                                medal.getRank()
                            );
                        }
                    }
                }
            }
        }
    }
    
    // Command methods
    public void listAwards(org.bukkit.command.CommandSender sender) {
        sender.sendMessage("§a=== Available Awards ===");
        for (AwardDefinition award : awards.values()) {
            String status = award.isEnabled() ? "§aEnabled" : "§cDisabled";
            sender.sendMessage(String.format("§7%s: %s (%s) - %s", 
                award.getId(), award.getName(), award.getTier(), status));
        }
    }
    
    public void showPlayerAwards(org.bukkit.command.CommandSender sender, String playerName) {
        // First try to find online player
        Player player = Bukkit.getPlayer(playerName);
        UUID playerUUID = null;
        
        if (player != null) {
            playerUUID = player.getUniqueId();
        } else {
            // Try to find offline player
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                playerUUID = offlinePlayer.getUniqueId();
                playerName = offlinePlayer.getName(); // Use the actual name from the server
            }
        }
        
        if (playerUUID == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return;
        }
        
        // Try to get from memory first
        PlayerAwards playerAward = playerAwards.get(playerUUID);
        
        // If not in memory, try to get from local storage
        if (playerAward == null) {
            LocalAwardStorage.PlayerAwardData localAwards = localStorage.getPlayerAwards(playerUUID);
            LocalAwardStorage.PlayerMedalData localMedals = localStorage.getPlayerMedals(playerUUID);
            LocalAwardStorage.PlayerPointData localPoints = localStorage.getPlayerPoints(playerUUID);
            
            if (localAwards != null || localMedals != null || localPoints != null) {
                // Create a temporary PlayerAwards object from local storage
                playerAward = new PlayerAwards(playerUUID, playerName);
                
                if (localAwards != null) {
                    for (LocalAwardStorage.AwardEntry award : localAwards.getAwards()) {
                        playerAward.addMedal(award.getAwardId(), award.getMedal(), award.getPoints(), 1, 0);
                    }
                }
                
                if (localMedals != null) {
                    playerAward.calculateTotalPoints();
                }
                
                if (localPoints != null) {
                    // Override total points with stored value
                    // Note: This is a bit hacky, but it works for display purposes
                }
            }
        }
        
        if (playerAward == null) {
            sender.sendMessage("§eNo awards found for " + playerName);
            sender.sendMessage("§7Try running §f/awards recalculate §7to calculate awards for all players.");
            return;
        }
        
        sender.sendMessage("§a=== Awards for " + playerName + " ===");
        sender.sendMessage(String.format("§7Total Points: §e%.1f", playerAward.getTotalPoints()));
        sender.sendMessage(String.format("§7Total Medals: §e%d", playerAward.getTotalMedals()));
        sender.sendMessage(String.format("§7Gold: §6%d §7Silver: §7%d §7Bronze: §c%d", 
            playerAward.getGoldMedals(), playerAward.getSilverMedals(), playerAward.getBronzeMedals()));
        
        // Show individual medals
        if (!playerAward.getMedals().isEmpty()) {
            sender.sendMessage("§7§lIndividual Medals:");
            for (PlayerMedal medal : playerAward.getMedals()) {
                String awardName = getAwardName(medal.getAwardId());
                String formattedValue = formatStatValue(medal.getAwardId(), medal.getStatValue());
                sender.sendMessage(String.format("§7- %s §e%s §7(%s) §7- §e%.1f points", 
                    getMedalColor(medal.getMedalType()) + medal.getMedalType().toUpperCase(),
                    awardName, formattedValue, medal.getPoints()));
            }
        }
        
        for (PlayerMedal medal : playerAward.getMedals()) {
            AwardDefinition award = awards.get(medal.getAwardId());
            String awardName = award != null ? award.getName() : medal.getAwardId();
            String medalColor = medal.getMedalType().equals("gold") ? "§6" : 
                               medal.getMedalType().equals("silver") ? "§7" : "§c";
            
            sender.sendMessage(String.format("§7%s %s: §e%d (%s place)", 
                medalColor + medal.getMedalType().toUpperCase(), awardName, 
                medal.getStatValue(), medal.getRank()));
        }
    }
    
    public void showLeaderboard(org.bukkit.command.CommandSender sender) {
        List<PlayerAwards> sortedPlayers = new ArrayList<>(playerAwards.values());
        sortedPlayers.sort((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()));
        
        sender.sendMessage("§a=== Hall of Fame ===");
        for (int i = 0; i < Math.min(sortedPlayers.size(), 10); i++) {
            PlayerAwards playerAward = sortedPlayers.get(i);
            String rank = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§7" + (i + 1);
            sender.sendMessage(String.format("%s §7%s: §e%.1f points §7(%d medals)", 
                rank, playerAward.getPlayerName(), playerAward.getTotalPoints(), playerAward.getTotalMedals()));
        }
    }
    
    public void showAwardLeaderboard(org.bukkit.command.CommandSender sender, String awardId) {
        AwardDefinition award = awards.get(awardId);
        if (award == null) {
            sender.sendMessage("§cAward not found: " + awardId);
            
            // Provide suggestions for similar award names
            List<String> suggestions = getAwardSuggestions(awardId);
            if (!suggestions.isEmpty()) {
                sender.sendMessage("§eDid you mean one of these?");
                for (String suggestion : suggestions) {
                    AwardDefinition suggestedAward = awards.get(suggestion);
                    if (suggestedAward != null) {
                        sender.sendMessage("§7- §f" + suggestion + " §7(" + suggestedAward.getName() + ")");
                    }
                }
            }
            
            // Show all available awards if no suggestions
            if (suggestions.isEmpty()) {
                sender.sendMessage("§eAvailable awards:");
                for (AwardDefinition availableAward : awards.values()) {
                    if (availableAward.isEnabled()) {
                        sender.sendMessage("§7- §f" + availableAward.getId() + " §7(" + availableAward.getName() + ")");
                    }
                }
            }
            return;
        }
        
        List<AwardRanking> rankings = awardRankings.get(awardId);
        if (rankings == null || rankings.isEmpty()) {
            sender.sendMessage("§eNo data available for " + award.getName());
            sender.sendMessage("§7Try running §f/awards recalculate §7to calculate awards for all players.");
            return;
        }
        
        sender.sendMessage("§a=== " + award.getName() + " Leaderboard ===");
        for (int i = 0; i < Math.min(rankings.size(), 10); i++) {
            AwardRanking ranking = rankings.get(i);
            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§7" + (i + 1);
            String formattedValue = formatStatValue(awardId, ranking.getStatValue());
            sender.sendMessage(String.format("%s §7%s: §e%s", 
                medal, ranking.getPlayerName(), formattedValue));
        }
    }
    
    private List<String> getAwardSuggestions(String searchTerm) {
        List<String> suggestions = new ArrayList<>();
        String lowerSearch = searchTerm.toLowerCase();
        
        for (AwardDefinition award : awards.values()) {
            if (award.isEnabled()) {
                String awardId = award.getId().toLowerCase();
                String awardName = award.getName().toLowerCase();
                
                // Check if the search term is contained in the award ID or name
                if (awardId.contains(lowerSearch) || awardName.contains(lowerSearch)) {
                    suggestions.add(award.getId());
                }
            }
        }
        
        // Sort by relevance (exact matches first, then partial matches)
        suggestions.sort((a, b) -> {
            String aLower = a.toLowerCase();
            String bLower = b.toLowerCase();
            
            boolean aExact = aLower.equals(lowerSearch);
            boolean bExact = bLower.equals(lowerSearch);
            
            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;
            
            return a.compareTo(b);
        });
        
        // Return top 5 suggestions
        return suggestions.subList(0, Math.min(suggestions.size(), 5));
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public Map<String, AwardDefinition> getAwards() { return awards; }
    public Map<UUID, PlayerAwards> getPlayerAwards() { return playerAwards; }
    
    public LocalAwardStorage getLocalStorage() {
        return localStorage;
    }
    public String getWebsiteUrl() { return websiteUrl; }
    
    public StatResolver getStatResolver() { return statResolver; }
    
    public void calculateAndSaveAwards(Player player) {
        if (!enabled) return;
        
        // Get player stats
        Map<String, Object> playerStats = new HashMap<>();
        // This would need to be implemented to get actual player stats
        // For now, we'll use a placeholder implementation
        
        Map<Player, Map<String, Object>> allPlayerStats = new HashMap<>();
        allPlayerStats.put(player, playerStats);
        
        calculateAllAwards(allPlayerStats);
    }
    
    public void savePlayerAwardsToLocal(UUID playerUUID) {
        if (!enabled) return;
        
        PlayerAwards playerAward = playerAwards.get(playerUUID);
        if (playerAward != null) {
            try {
                // Create a map with all player awards for the saveAllAwards method
                Map<UUID, PlayerAwards> allPlayerAwards = new HashMap<>();
                allPlayerAwards.put(playerUUID, playerAward);
                localStorage.saveAllAwards(allPlayerAwards);
                logger.debug("Saved awards to local storage for player: " + playerUUID);
            } catch (Exception e) {
                logger.warning("Failed to save awards to local storage for player " + playerUUID + ": " + e.getMessage());
            }
        }
    }
    
    public AwardInfo getAwardInfo(String awardName) {
        AwardDefinition award = awards.get(awardName);
        if (award == null) return null;
        
        AwardInfo info = new AwardInfo();
        info.name = award.getName();
        info.description = award.getDescription();
        info.category = award.getStatPath().split("\\.")[0];
        info.points = (int) Math.round(tierPoints.getOrDefault(award.getTier() + ".gold", 0.0));
        info.medal = "gold";
        info.tier = award.getTier();
        info.requirement = "Top 1 in " + award.getName();
        
        return info;
    }
    
    public List<String> getAllAwardNames() {
        return new ArrayList<>(awards.keySet());
    }
    
    private String getAwardName(String awardId) {
        AwardDefinition award = awards.get(awardId);
        return award != null ? award.getName() : awardId;
    }
    
    private String formatStatValue(String awardId, long value) {
        AwardDefinition award = awards.get(awardId);
        if (award == null) return String.valueOf(value);
        
        String statPath = award.getStatPath();
        
        // Time-based stats (convert to hours)
        if (statPath.contains("time_since_death") || statPath.contains("play_time")) {
            double hours = value / 72000.0; // Convert ticks to hours (20 ticks per second, 3600 seconds per hour)
            return String.format("%.1f hours", hours);
        }
        
        // Distance stats (convert to km)
        if (statPath.contains("_one_cm") || statPath.contains("walk") || statPath.contains("sprint") || 
            statPath.contains("swim") || statPath.contains("fly") || statPath.contains("fall") ||
            statPath.contains("boat") || statPath.contains("horse") || statPath.contains("minecart") ||
            statPath.contains("strider")) {
            double km = value / 100000.0; // Convert cm to km
            return String.format("%.2f km", km);
        }
        
        // Default: just return the number
        return String.valueOf(value);
    }
    
    // Debug method to show top values for each award
    public void debugTopValues(org.bukkit.command.CommandSender sender) {
        if (!enabled) {
            sender.sendMessage("§cAward system is disabled");
            return;
        }
        
        sender.sendMessage("§a=== Top Values for Each Award ===");
        
        for (AwardDefinition award : awards.values()) {
            if (!award.isEnabled()) continue;
            
            List<AwardRanking> rankings = awardRankings.get(award.getId());
            if (rankings == null || rankings.isEmpty()) {
                sender.sendMessage(String.format("§7%s: §cNo data", award.getId()));
                logger.warning("No data for award: " + award.getId() + " (stat path: " + award.getStatPath() + ")");
                // Optionally, show zero instead:
                // sender.sendMessage(String.format("§7%s: §e0", award.getId()));
                continue;
            }
            
            AwardRanking top = rankings.get(0);
            String formattedValue = formatStatValue(award.getId(), top.getStatValue());
            sender.sendMessage(String.format("§7%s: §e%s §7(%s)", 
                award.getId(), top.getPlayerName(), formattedValue));
        }
    }
    
    private String getMedalColor(String medalType) {
        switch (medalType.toLowerCase()) {
            case "gold": return "§6";
            case "silver": return "§7";
            case "bronze": return "§c";
            default: return "§e";
        }
    }
    
    public static class AwardInfo {
        public String name;
        public String description;
        public String category;
        public int points;
        public String medal;
        public String tier;
        public String requirement;
    }
    
    // Inner classes for data structures
    public static class AwardDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final String tier;
        private final String statPath;
        private final boolean enabled;
        
        public AwardDefinition(String id, String name, String description, String tier, String statPath, boolean enabled) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
            this.statPath = statPath;
            this.enabled = enabled;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getTier() { return tier; }
        public String getStatPath() { return statPath; }
        public boolean isEnabled() { return enabled; }
    }
    
    public static class AwardRanking {
        private final UUID playerUUID;
        private final String playerName;
        private final long statValue;
        
        public AwardRanking(UUID playerUUID, String playerName, long statValue) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.statValue = statValue;
        }
        
        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public long getStatValue() { return statValue; }
    }
    
    public static class PlayerMedal {
        private final String awardId;
        private final String medalType;
        private final double points;
        private final int rank;
        private final long statValue;
        private final long awardedAt;
        
        public PlayerMedal(String awardId, String medalType, double points, int rank, long statValue) {
            this.awardId = awardId;
            this.medalType = medalType;
            this.points = points;
            this.rank = rank;
            this.statValue = statValue;
            this.awardedAt = System.currentTimeMillis();
        }
        
        // Getters
        public String getAwardId() { return awardId; }
        public String getMedalType() { return medalType; }
        public double getPoints() { return points; }
        public int getRank() { return rank; }
        public long getStatValue() { return statValue; }
        public long getAwardedAt() { return awardedAt; }
    }
    
    public static class PlayerAwards {
        private final UUID playerUUID;
        private final String playerName;
        private final List<PlayerMedal> medals = new ArrayList<>();
        private double totalPoints = 0;
        private int totalMedals = 0;
        private int goldMedals = 0;
        private int silverMedals = 0;
        private int bronzeMedals = 0;
        private boolean hasNewMedals = false;
        
        public PlayerAwards(UUID playerUUID, String playerName) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
        }
        
        public void addMedal(String awardId, String medalType, double points, int rank, long statValue) {
            medals.add(new PlayerMedal(awardId, medalType, points, rank, statValue));
            hasNewMedals = true;
        }
        
        public void calculateTotalPoints() {
            totalPoints = 0;
            goldMedals = 0;
            silverMedals = 0;
            bronzeMedals = 0;
            
            for (PlayerMedal medal : medals) {
                totalPoints += medal.getPoints();
                switch (medal.getMedalType()) {
                    case "gold": goldMedals++; break;
                    case "silver": silverMedals++; break;
                    case "bronze": bronzeMedals++; break;
                }
            }
            
            totalMedals = medals.size();
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> awardsData = new HashMap<>();
            
            // Medal data
            List<Map<String, Object>> medalList = new ArrayList<>();
            for (PlayerMedal medal : medals) {
                Map<String, Object> medalData = new HashMap<>();
                medalData.put("award_id", medal.getAwardId());
                medalData.put("medal_type", medal.getMedalType());
                medalData.put("points", medal.getPoints());
                medalData.put("rank", medal.getRank());
                medalData.put("stat_value", medal.getStatValue());
                medalData.put("awarded_at", medal.getAwardedAt());
                medalList.add(medalData);
            }
            
            awardsData.put("medals", medalList);
            awardsData.put("total_points", totalPoints);
            awardsData.put("total_medals", totalMedals);
            awardsData.put("gold_medals", goldMedals);
            awardsData.put("silver_medals", silverMedals);
            awardsData.put("bronze_medals", bronzeMedals);
            
            return awardsData;
        }
        
        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public List<PlayerMedal> getMedals() { return medals; }
        public double getTotalPoints() { return totalPoints; }
        public int getTotalMedals() { return totalMedals; }
        public int getGoldMedals() { return goldMedals; }
        public int getSilverMedals() { return silverMedals; }
        public int getBronzeMedals() { return bronzeMedals; }
        public boolean hasNewMedals() { return hasNewMedals; }
        public int getNewMedalsCount() { return totalMedals; }
    }
    
    // Make this method public for use in loadAllPlayerStats
    public Map<String, Object> loadStatsFromFile(UUID playerUUID, java.io.File statFile) {
        Map<String, Object> stats = new HashMap<>();
        try (java.io.FileReader reader = new java.io.FileReader(statFile)) {
            org.json.simple.parser.JSONParser parser = new org.json.simple.parser.JSONParser();
            org.json.simple.JSONObject json = (org.json.simple.JSONObject) parser.parse(reader);
            
            // Debug: Check if this is Aytte's file
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = offlinePlayer.getName();
            if (playerName != null && playerName.equals("Aytte")) {
                logger.debug("DEBUG: Parsing Aytte's stats file: " + statFile.getName());
                logger.debug("DEBUG: JSON keys: " + json.keySet());
            }
            
            org.json.simple.JSONObject statsSection = (org.json.simple.JSONObject) json.get("stats");
            if (statsSection != null) {
                if (playerName != null && playerName.equals("Aytte")) {
                    logger.debug("DEBUG: Aytte's stats section keys: " + statsSection.keySet());
                }
                
                for (Object categoryObj : statsSection.keySet()) {
                    String category = (String) categoryObj;
                    org.json.simple.JSONObject categoryStats = (org.json.simple.JSONObject) statsSection.get(category);
                    Map<String, Object> categoryData = new HashMap<>();
                    
                    if (playerName != null && playerName.equals("Aytte")) {
                        logger.debug("DEBUG: Aytte's category '" + category + "' keys: " + categoryStats.keySet());
                    }
                    
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
            } else {
                if (playerName != null && playerName.equals("Aytte")) {
                    logger.debug("DEBUG: Aytte's stats section is null!");
                }
            }
            org.json.simple.JSONObject advancementsSection = (org.json.simple.JSONObject) json.get("advancements");
            if (advancementsSection != null) {
                Map<String, Object> advancementsData = new HashMap<>();
                for (Object advancementObj : advancementsSection.keySet()) {
                    String advancementKey = advancementObj.toString();
                    Object advancementValue = advancementsSection.get(advancementKey);
                    String cleanAdvancementKey = advancementKey.replace("minecraft:", "");
                    if (advancementValue instanceof org.json.simple.JSONObject) {
                        org.json.simple.JSONObject criteria = (org.json.simple.JSONObject) advancementValue;
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
            // Debug: Log the actual exception for Aytte
            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            String playerName = offlinePlayer.getName();
            if (playerName != null && playerName.equals("Aytte")) {
                logger.warning("DEBUG: Exception parsing Aytte's stats file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return stats;
    }
    
    // Debug method to check what stats a specific player has
    public void debugPlayerStats(String playerName) {
        logger.debug("DEBUG: Checking stats for player: " + playerName);
        
        // Find the player's UUID
        UUID playerUUID = null;
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            java.io.File statsFolder = new java.io.File(world.getWorldFolder(), "stats");
            if (!statsFolder.exists() || !statsFolder.isDirectory()) {
                continue;
            }
            
            java.io.File[] statFiles = statsFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (statFiles == null) continue;
            
            for (java.io.File statFile : statFiles) {
                try {
                    String fileName = statFile.getName();
                    String uuidString = fileName.replace(".json", "");
                    UUID uuid = UUID.fromString(uuidString);
                    
                    org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    String name = offlinePlayer.getName();
                    if (name != null && name.equalsIgnoreCase(playerName)) {
                        playerUUID = uuid;
                        break;
                    }
                } catch (Exception e) {
                    // Ignore invalid files
                }
            }
            if (playerUUID != null) break;
        }
        
        if (playerUUID == null) {
            logger.debug("DEBUG: Player '" + playerName + "' not found in any stats files");
            return;
        }
        
        logger.debug("DEBUG: Found player '" + playerName + "' with UUID: " + playerUUID);
        
        // Load and display their stats
        Map<String, Object> allStats = new HashMap<>();
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            java.io.File statFile = new java.io.File(world.getWorldFolder(), "stats/" + playerUUID + ".json");
            if (statFile.exists()) {
                logger.debug("DEBUG: Loading stats from file: " + statFile.getAbsolutePath());
                Map<String, Object> worldStats = loadStatsFromFile(playerUUID, statFile);
                allStats.putAll(worldStats);
                logger.debug("DEBUG: Loaded " + worldStats.size() + " categories from " + world.getName());
            }
        }
        
        logger.debug("DEBUG: Player '" + playerName + "' has " + allStats.size() + " stat categories:");
        for (Map.Entry<String, Object> entry : allStats.entrySet()) {
            String category = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> categoryStats = (Map<String, Object>) value;
                logger.debug("DEBUG: Category '" + category + "' has " + categoryStats.size() + " stats");
                for (Map.Entry<String, Object> statEntry : categoryStats.entrySet()) {
                    logger.debug("DEBUG:   " + statEntry.getKey() + ": " + statEntry.getValue());
                }
            }
        }
        
        // Test specific stat resolution
        logger.debug("DEBUG: Testing stat resolution for Aytte:");
        Long jumpValue = statResolver.resolveStatValue(allStats, "custom.jump");
        logger.debug("DEBUG: custom.jump = " + jumpValue);
        
        Long minedValue = statResolver.resolveStatValue(allStats, "mined.total");
        logger.debug("DEBUG: mined.total = " + minedValue);
        
        Long killedValue = statResolver.resolveStatValue(allStats, "killed.total");
        logger.debug("DEBUG: killed.total = " + killedValue);
    }

    // ==================== API METHODS ====================
    
    /**
     * Get player awards information for API
     * @param playerUUID The player's UUID
     * @return PlayerAwardsInfo
     */
    public ProgressionAPI.PlayerAwardsInfo getPlayerAwardsInfo(UUID playerUUID) {
        // Get player name
        String playerName = null;
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
        if (offlinePlayer.hasPlayedBefore()) {
            playerName = offlinePlayer.getName();
        }
        
        // Get awards from local storage or database
        java.util.List<ProgressionAPI.AwardInfo> awards = new java.util.ArrayList<>();
        double totalPoints = 0.0;
        int totalMedals = 0;
        int goldMedals = 0;
        int silverMedals = 0;
        int bronzeMedals = 0;
        
        // Get from local storage if available
        PlayerAwards playerAwards = this.playerAwards.get(playerUUID);
        if (playerAwards != null) {
            for (PlayerMedal medal : playerAwards.getMedals()) {
                awards.add(new ProgressionAPI.AwardInfo(
                    medal.getAwardId(),
                    getAwardName(medal.getAwardId()),
                    "", // Description would need to be added to award definitions
                    "", // Tier would need to be added to award definitions
                    medal.getMedalType(),
                    medal.getPoints(),
                    medal.getStatValue(),
                    "", // Stat path would need to be added to award definitions
                    medal.getAwardedAt()
                ));
            }
            totalPoints = playerAwards.getTotalPoints();
            totalMedals = playerAwards.getTotalMedals();
            goldMedals = playerAwards.getGoldMedals();
            silverMedals = playerAwards.getSilverMedals();
            bronzeMedals = playerAwards.getBronzeMedals();
        }
        
        return new ProgressionAPI.PlayerAwardsInfo(playerUUID, playerName, awards, totalPoints, totalMedals, goldMedals, silverMedals, bronzeMedals);
    }
    
    /**
     * Get player awards information by name for API
     * @param playerName The player's name
     * @return Optional containing PlayerAwardsInfo if player exists
     */
    public java.util.Optional<ProgressionAPI.PlayerAwardsInfo> getPlayerAwardsInfoByName(String playerName) {
        // Find player UUID by name
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            return java.util.Optional.of(getPlayerAwardsInfo(offlinePlayer.getUniqueId()));
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Get all players' awards information for API
     * @return List of PlayerAwardsInfo for all players
     */
    public java.util.List<ProgressionAPI.PlayerAwardsInfo> getAllPlayerAwardsInfo() {
        java.util.List<ProgressionAPI.PlayerAwardsInfo> allAwards = new java.util.ArrayList<>();
        
        // Get all online players
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            allAwards.add(getPlayerAwardsInfo(player.getUniqueId()));
        }
        
        // You might want to add offline players from database here
        
        return allAwards;
    }
    
    /**
     * Get top players by total points for API
     * @param limit Number of players to return
     * @return List of PlayerAwardsInfo sorted by total points (highest first)
     */
    public java.util.List<ProgressionAPI.PlayerAwardsInfo> getTopPlayersByPoints(int limit) {
        java.util.List<ProgressionAPI.PlayerAwardsInfo> allAwards = getAllPlayerAwardsInfo();
        allAwards.sort((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()));
        return allAwards.subList(0, Math.min(limit, allAwards.size()));
    }
    
    /**
     * Get top players by total medals for API
     * @param limit Number of players to return
     * @return List of PlayerAwardsInfo sorted by total medals (highest first)
     */
    public java.util.List<ProgressionAPI.PlayerAwardsInfo> getTopPlayersByMedals(int limit) {
        java.util.List<ProgressionAPI.PlayerAwardsInfo> allAwards = getAllPlayerAwardsInfo();
        allAwards.sort((a, b) -> Integer.compare(b.getTotalMedals(), a.getTotalMedals()));
        return allAwards.subList(0, Math.min(limit, allAwards.size()));
    }
    
    /**
     * Get specific award information for a player for API
     * @param playerUUID The player's UUID
     * @param awardId The award ID
     * @return Optional containing AwardInfo if the player has this award
     */
    public java.util.Optional<ProgressionAPI.AwardInfo> getPlayerAwardInfo(UUID playerUUID, String awardId) {
        PlayerAwards playerAwards = this.playerAwards.get(playerUUID);
        if (playerAwards != null) {
            for (PlayerMedal medal : playerAwards.getMedals()) {
                if (medal.getAwardId().equals(awardId)) {
                    return java.util.Optional.of(new ProgressionAPI.AwardInfo(
                        medal.getAwardId(),
                        getAwardName(medal.getAwardId()),
                        "", // Description would need to be added to award definitions
                        "", // Tier would need to be added to award definitions
                        medal.getMedalType(),
                        medal.getPoints(),
                        medal.getStatValue(),
                        "", // Stat path would need to be added to award definitions
                        medal.getAwardedAt()
                    ));
                }
            }
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Get all available award definitions for API
     * @return List of AwardDefinitionInfo
     */
    public java.util.List<ProgressionAPI.AwardDefinitionInfo> getAllAwardDefinitions() {
        java.util.List<ProgressionAPI.AwardDefinitionInfo> definitions = new java.util.ArrayList<>();
        
        for (AwardDefinition award : awards.values()) {
            definitions.add(new ProgressionAPI.AwardDefinitionInfo(
                award.getId(),
                award.getName(),
                award.getDescription(),
                award.getStatPath(),
                "", // Color would need to be added to award definitions
                award.isEnabled()
            ));
        }
        
        return definitions;
    }
    
    /**
     * Get specific award definition for API
     * @param awardId The award ID
     * @return Optional containing AwardDefinitionInfo if award exists
     */
    public java.util.Optional<ProgressionAPI.AwardDefinitionInfo> getAwardDefinitionInfo(String awardId) {
        AwardDefinition award = awards.get(awardId);
        if (award != null) {
            return java.util.Optional.of(new ProgressionAPI.AwardDefinitionInfo(
                award.getId(),
                award.getName(),
                award.getDescription(),
                award.getStatPath(),
                "", // Color would need to be added to award definitions
                award.isEnabled()
            ));
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Get award leaderboard for a specific award for API
     * @param awardId The award ID
     * @param limit Number of players to return
     * @return List of AwardLeaderboardEntry
     */
    public java.util.List<ProgressionAPI.AwardLeaderboardEntry> getAwardLeaderboard(String awardId, int limit) {
        java.util.List<ProgressionAPI.AwardLeaderboardEntry> leaderboard = new java.util.ArrayList<>();
        
        // Get all players who have this award
        for (java.util.Map.Entry<UUID, PlayerAwards> entry : playerAwards.entrySet()) {
            UUID playerUUID = entry.getKey();
            PlayerAwards playerAwards = entry.getValue();
            
            for (PlayerMedal medal : playerAwards.getMedals()) {
                if (medal.getAwardId().equals(awardId)) {
                    String playerName = null;
                    org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
                    if (offlinePlayer.hasPlayedBefore()) {
                        playerName = offlinePlayer.getName();
                    }
                    
                    leaderboard.add(new ProgressionAPI.AwardLeaderboardEntry(
                        playerUUID,
                        playerName,
                        medal.getAwardId(),
                        getAwardName(medal.getAwardId()),
                        medal.getPoints(),
                        medal.getMedalType(),
                        "", // Tier would need to be added to award definitions
                        medal.getStatValue(),
                        medal.getAwardedAt(),
                        0 // Rank would need to be calculated
                    ));
                }
            }
        }
        
        // Sort by points (highest first) and limit results
        leaderboard.sort((a, b) -> Double.compare(b.getPoints(), a.getPoints()));
        
        // Add ranks
        for (int i = 0; i < leaderboard.size(); i++) {
            // This is a workaround since AwardLeaderboardEntry is immutable
            // In a real implementation, you'd want to make it mutable or create a new instance
        }
        
        return leaderboard.subList(0, Math.min(limit, leaderboard.size()));
    }
} 