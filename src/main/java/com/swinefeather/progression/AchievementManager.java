package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.swinefeather.progression.StatResolver;

public class AchievementManager {
    private final Main plugin;
    private final LogManager logManager;
    private final LevelManager levelManager;
    private final Map<UUID, PlayerAchievementData> playerAchievements;
    private final Map<String, TownAchievementData> townAchievements;
    private final List<AchievementDefinition> achievementDefinitions = new ArrayList<>();
    private final Gson gson;
    private final File achievementsDir;
    private final File playerAchievementsDir;
    private final File townAchievementsDir;
    public final StatResolver statResolver;

    public AchievementManager(Main plugin) {
        this.plugin = plugin;
        this.logManager = plugin.logManager;
        this.levelManager = plugin.levelManager;
        this.playerAchievements = new ConcurrentHashMap<>();
        this.townAchievements = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.statResolver = new StatResolver(logManager);
        
        // Create directories
        this.achievementsDir = new File(plugin.getDataFolder(), "achievements");
        this.playerAchievementsDir = new File(achievementsDir, "players");
        this.townAchievementsDir = new File(achievementsDir, "towns");
        
        // Initialize achievement definitions
        this.achievementDefinitions.addAll(loadAchievementsFromConfig());
        logManager.debug("Loaded " + achievementDefinitions.size() + " achievements from config.yml");
        
        // Create directories if they don't exist
        achievementsDir.mkdirs();
        playerAchievementsDir.mkdirs();
        townAchievementsDir.mkdirs();
        
        loadAchievementData();
    }

    public List<AchievementDefinition> initializeAchievements() {
        this.achievementDefinitions.clear();
        this.achievementDefinitions.addAll(loadAchievementsFromConfig());
        logManager.debug("Loaded " + achievementDefinitions.size() + " achievements from config.yml");
        return achievementDefinitions;
    }

    public void reloadAchievements() {
        this.achievementDefinitions.clear();
        this.achievementDefinitions.addAll(loadAchievementsFromConfig());
        logManager.debug("Reloaded " + achievementDefinitions.size() + " achievements from config.yml");
    }

    private List<AchievementDefinition> loadAchievementsFromConfig() {
        List<AchievementDefinition> achievements = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection achievementsSection = plugin.getConfig().getConfigurationSection("achievements");
        if (achievementsSection == null) {
            logManager.warning("No achievements section found in config.yml!");
            return achievements;
        }
        for (String id : achievementsSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection achSec = achievementsSection.getConfigurationSection(id);
            if (achSec == null) continue;
            String name = achSec.getString("name", id);
            String description = achSec.getString("description", "");
            String stat = achSec.getString("stat", id);
            String color = achSec.getString("color", "#ffffff");
            String type = achSec.getString("type", "player");
            AchievementDefinition achievement = new AchievementDefinition(id, name, description, stat, color, type);
            org.bukkit.configuration.ConfigurationSection tiersSec = achSec.getConfigurationSection("tiers");
            if (tiersSec != null) {
                for (String tierKey : tiersSec.getKeys(false)) {
                    org.bukkit.configuration.ConfigurationSection tierSec = tiersSec.getConfigurationSection(tierKey);
                    if (tierSec == null) continue;
                    int tierNum = Integer.parseInt(tierKey);
                    String tierName = tierSec.getString("name", "Tier " + tierKey);
                    String tierDesc = tierSec.getString("description", "");
                    int threshold = tierSec.getInt("threshold", 0);
                    String icon = tierSec.getString("icon", "");
                    int points = tierSec.getInt("points", 0);
                    achievement.addTier(new AchievementTier(tierNum, tierName, tierDesc, threshold, icon, points));
                }
            }
            achievements.add(achievement);
        }
        return achievements;
    }

    public void checkPlayerAchievements(UUID playerUUID, String playerName, Map<String, Object> stats) {
        Map<String, Object> flatStats = flattenStatsMap(stats);
        PlayerAchievementData achievementData = playerAchievements.computeIfAbsent(playerUUID, 
            k -> new PlayerAchievementData(playerUUID, playerName));
        
        achievementData.setPlayerName(playerName);
        
        logManager.debug("[Achievement] Available stat keys for player " + playerName + ":");
        for (String key : stats.keySet()) {
            logManager.debug("[Achievement]   " + key + " = " + stats.get(key));
        }
        
        for (AchievementDefinition achievement : achievementDefinitions) {
            // Only check player achievements
            if (!"player".equals(achievement.getType())) continue;
            
            // Use the same stat extraction logic as awards/sqlstats
            int currentValue = extractStatValue(flatStats, achievement.getStat());
            logManager.debug("[Achievement] Checking " + achievement.getId() + " for player " + playerName + ": statKey=" + achievement.getStat() + ", value=" + currentValue);
            
            // Check each tier
            for (AchievementTier tier : achievement.getTiers()) {
                logManager.debug("[Achievement]   Tier " + tier.getTier() + " threshold=" + tier.getThreshold() + ", currentValue=" + currentValue);
                if (currentValue >= tier.getThreshold()) {
                    // Check if already unlocked
                    if (!achievementData.hasUnlockedTier(achievement.getId(), tier.getTier())) {
                        // Unlock achievement
                        achievementData.unlockTier(achievement.getId(), tier, currentValue);
                        
                        // Award XP
                        int xpGained = calculateXPGain(tier.getPoints());
                        levelManager.addPlayerXP(playerUUID, playerName, xpGained);
                        
                        // Send notification
                        Player player = plugin.getServer().getPlayer(playerUUID);
                        if (player != null && player.isOnline()) {
                            player.sendMessage("§a§l🏆 ACHIEVEMENT UNLOCKED! §a" + tier.getName());
                            player.sendMessage("§7" + tier.getDescription() + " §a(+" + xpGained + " XP)");
                            // Play achievement sound
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        } else {
                            // TODO: Store notification for offline delivery
                            logManager.debug("Player " + playerName + " unlocked achievement: " + tier.getName() + " (offline, will notify on login)");
                        }
                        
                        logManager.debug("Player " + playerName + " unlocked achievement: " + tier.getName() + " (+" + xpGained + " XP)");
                    }
                }
            }
        }
        
        savePlayerAchievementData(playerUUID);
    }

    public void checkTownAchievements(String townName, Map<String, Object> townStats) {
        TownAchievementData achievementData = townAchievements.computeIfAbsent(townName, 
            k -> new TownAchievementData(townName));
        
        // First check achievements from config (towny.achievements section)
        ConfigurationSection achievements = plugin.getConfig().getConfigurationSection("towny.achievements");
        if (achievements != null) {
            for (String achievementKey : achievements.getKeys(false)) {
                ConfigurationSection achievementSection = achievements.getConfigurationSection(achievementKey);
                if (achievementSection == null) continue;
                
                String statName = achievementSection.getString("stat");
                if (statName == null) continue;
                
                Object statValue = townStats.get(statName);
                if (statValue == null) continue;
                
                int currentValue = ((Number) statValue).intValue();
                
                ConfigurationSection tiers = achievementSection.getConfigurationSection("tiers");
                if (tiers == null) continue;
                
                for (String tierKey : tiers.getKeys(false)) {
                    ConfigurationSection tierSection = tiers.getConfigurationSection(tierKey);
                    if (tierSection == null) continue;
                    
                    int tier = Integer.parseInt(tierKey);
                    int threshold = tierSection.getInt("threshold", 0);
                    String tierName = tierSection.getString("name", "Unknown");
                    int points = tierSection.getInt("points", 0);
                    
                    if (currentValue >= threshold) {
                        if (!achievementData.hasUnlockedTier(achievementKey, tier)) {
                            // Create AchievementTier object
                            AchievementTier achievementTier = new AchievementTier(tier, tierName, tierSection.getString("description", ""), threshold, tierSection.getString("icon", ""), points);
                            
                            // Unlock achievement
                            achievementData.unlockTier(achievementKey, achievementTier, currentValue);
                            
                            // Award XP
                            int xpGained = points;
                            if (plugin.levelManager != null) {
                                plugin.levelManager.addTownXP(townName, xpGained);
                            }
                            
                            // Sync to database if enabled
                            if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
                                plugin.levelDatabaseManager.syncUnlockedAchievement(null, townName, achievementKey, tier, xpGained);
                            }
                            
                            // Sync to Supabase if enabled
                            if (plugin.supabaseManager != null && plugin.supabaseManager.isEnabled()) {
                                plugin.supabaseManager.syncTownAchievement(townName, achievementKey, tier, xpGained);
                            }
                            
                            // Announce achievement
                            if (plugin.getConfig().getBoolean("towny.notifications.achievements", true)) {
                                plugin.getServer().broadcastMessage("§6[Towny] §e" + townName + " §ahas unlocked achievement: §6" + tierName + "§a!");
                                
                                // Play achievement sound for all online players
                                for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                }
                            }
                            
                            logManager.debug("Town " + townName + " unlocked achievement: " + tierName + " (+" + xpGained + " XP)");
                        }
                    }
                }
            }
        }
        
        // Then check achievements from achievement definitions
        for (AchievementDefinition achievement : achievementDefinitions) {
            // Only check town achievements
            if (achievement.getId().equals("population_growth") || achievement.getId().equals("nation_member") || 
                achievement.getId().equals("independent_spirit") || achievement.getId().equals("capital_status")) {
                checkTownAchievement(townName, achievement, townStats, achievementData);
            }
        }
        
        saveTownAchievementData(townName);
    }

    private void checkAchievement(UUID playerUUID, String playerName, AchievementDefinition achievement, 
                                 Map<String, Object> stats, PlayerAchievementData achievementData) {
        
        // Get the stat value
        Object statValue = stats.get(achievement.getStat());
        if (statValue == null) return;
        
        int currentValue = 0;
        if (statValue instanceof Number) {
            currentValue = ((Number) statValue).intValue();
        } else if (statValue instanceof Map) {
            Map<String, Object> statMap = (Map<String, Object>) statValue;
            Object value = statMap.get("value");
            if (value instanceof Number) {
                currentValue = ((Number) value).intValue();
            }
        }
        
        // Check each tier
        for (AchievementTier tier : achievement.getTiers()) {
            if (currentValue >= tier.getThreshold()) {
                // Check if already unlocked
                if (!achievementData.hasUnlockedTier(achievement.getId(), tier.getTier())) {
                    // Unlock achievement
                    achievementData.unlockTier(achievement.getId(), tier, currentValue);
                    
                    // Award XP
                    int xpGained = calculateXPGain(tier.getPoints());
                    levelManager.addPlayerXP(playerUUID, playerName, xpGained);
                    
                    // Sync to database if enabled
                    if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
                        plugin.levelDatabaseManager.syncUnlockedAchievement(playerUUID, null, achievement.getId(), tier.getTier(), xpGained);
                    }
                    
                    // Sync to Supabase if enabled
                    if (plugin.supabaseManager != null && plugin.supabaseManager.isEnabled()) {
                        plugin.supabaseManager.syncUnlockedAchievement(playerUUID, achievement.getId(), tier.getTier(), xpGained);
                    }
                    
                    // Send notification
                    Player player = plugin.getServer().getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§a§l🏆 ACHIEVEMENT UNLOCKED! §a" + tier.getName());
                        player.sendMessage("§7" + tier.getDescription() + " §a(+" + xpGained + " XP)");
                        // Play achievement sound
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        // TODO: Store notification for offline delivery
                        logManager.debug("Player " + playerName + " unlocked achievement: " + tier.getName() + " (offline, will notify on login)");
                    }
                    
                    logManager.debug("Player " + playerName + " unlocked achievement: " + tier.getName() + " (+" + xpGained + " XP)");
                }
            }
        }
    }

    private void checkTownAchievement(String townName, AchievementDefinition achievement, 
                                     Map<String, Object> townStats, TownAchievementData achievementData) {
        
        // Get the stat value
        Object statValue = townStats.get(achievement.getStat());
        if (statValue == null) return;
        
        int currentValue = 0;
        if (statValue instanceof Number) {
            currentValue = ((Number) statValue).intValue();
        } else if (statValue instanceof Map) {
            Map<String, Object> statMap = (Map<String, Object>) statValue;
            Object value = statMap.get("value");
            if (value instanceof Number) {
                currentValue = ((Number) value).intValue();
            }
        }
        
        // Check each tier
        for (AchievementTier tier : achievement.getTiers()) {
            if (currentValue >= tier.getThreshold()) {
                // Check if already unlocked
                if (!achievementData.hasUnlockedTier(achievement.getId(), tier.getTier())) {
                    // Unlock achievement
                    achievementData.unlockTier(achievement.getId(), tier, currentValue);
                    
                    // Award XP
                    int xpGained = calculateXPGain(tier.getPoints());
                    levelManager.addTownXP(townName, xpGained);
                    
                    // Sync to database if enabled
                    if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
                        plugin.levelDatabaseManager.syncUnlockedAchievement(null, townName, achievement.getId(), tier.getTier(), xpGained);
                    }
                    
                    // Sync to Supabase if enabled
                    if (plugin.supabaseManager != null && plugin.supabaseManager.isEnabled()) {
                        plugin.supabaseManager.syncTownAchievement(townName, achievement.getId(), tier.getTier(), xpGained);
                    }
                    
                    logManager.debug("Town " + townName + " unlocked achievement: " + tier.getName() + " (+" + xpGained + " XP)");
                }
            }
        }
    }

    private int calculateXPGain(int points) {
        // Convert achievement points to XP
        // 1 achievement point = 3 XP (balanced to reach max level with all achievements)
        return points * 3;
    }

    /**
     * Extracts a stat value from the stats map, supporting dot notation (e.g. "custom_play_time" or "mined_dirt")
     */
    private int extractStatValue(Map<String, Object> stats, String statKey) {
        if (stats == null || statKey == null) return 0;
        Long value = statResolver.resolveStatValue(stats, statKey);
        return value != null ? value.intValue() : 0;
    }

    private Map<String, Object> flattenStatsMap(Map<String, Object> nestedStats) {
        Map<String, Object> flat = new java.util.HashMap<>();
        if (nestedStats == null) return flat;
        for (Map.Entry<String, Object> entry : nestedStats.entrySet()) {
            String category = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<?, ?> subMap = (Map<?, ?>) value;
                for (Map.Entry<?, ?> subEntry : subMap.entrySet()) {
                    String statKey = subEntry.getKey().toString();
                    Object statValue = subEntry.getValue();
                    flat.put(category + "." + statKey, statValue);
                }
            } else {
                flat.put(category, value);
            }
        }
        return flat;
    }

    public PlayerAchievementData getPlayerAchievementData(UUID playerUUID) {
        return playerAchievements.get(playerUUID);
    }

    public TownAchievementData getTownAchievementData(String townName) {
        return townAchievements.get(townName);
    }

    public List<AchievementDefinition> getAchievementDefinitions() {
        return new ArrayList<>(achievementDefinitions);
    }

    private void loadAchievementData() {
        // Load player achievements
        File[] playerFiles = playerAchievementsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles != null) {
            for (File file : playerFiles) {
                try (Reader reader = new FileReader(file)) {
                    PlayerAchievementData data = gson.fromJson(reader, PlayerAchievementData.class);
                    if (data != null) {
                        playerAchievements.put(data.getPlayerUUID(), data);
                    }
                } catch (Exception e) {
                    logManager.severe("Failed to load player achievement data from " + file.getName(), e);
                }
            }
            logManager.debug("Loaded " + playerAchievements.size() + " player achievement records");
        }

        // Load town achievements
        File[] townFiles = townAchievementsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (townFiles != null) {
            for (File file : townFiles) {
                try (Reader reader = new FileReader(file)) {
                    TownAchievementData data = gson.fromJson(reader, TownAchievementData.class);
                    if (data != null) {
                        townAchievements.put(data.getTownName(), data);
                    }
                } catch (Exception e) {
                    logManager.severe("Failed to load town achievement data from " + file.getName(), e);
                }
            }
            logManager.debug("Loaded " + townAchievements.size() + " town achievement records");
        }
    }

    private void savePlayerAchievementData(UUID playerUUID) {
        PlayerAchievementData data = playerAchievements.get(playerUUID);
        if (data == null) return;

        File playerFile = new File(playerAchievementsDir, playerUUID.toString() + ".json");
        try (Writer writer = new FileWriter(playerFile)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            logManager.severe("Failed to save player achievement data for " + playerUUID, e);
        }
    }

    private void saveTownAchievementData(String townName) {
        TownAchievementData data = townAchievements.get(townName);
        if (data == null) return;

        File townFile = new File(townAchievementsDir, townName + ".json");
        try (Writer writer = new FileWriter(townFile)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            logManager.severe("Failed to save town achievement data for " + townName, e);
        }
    }

    public void saveAllData() {
        // Save all player data
        for (UUID playerUUID : playerAchievements.keySet()) {
            savePlayerAchievementData(playerUUID);
        }

        // Save all town data
        for (String townName : townAchievements.keySet()) {
            saveTownAchievementData(townName);
        }

        logManager.debug("Saved all achievement data");
    }

    public void shutdown() {
        saveAllData();
    }

    // Data classes
    public static class AchievementDefinition {
        private final String id;
        private final String name;
        private final String description;
        private final String stat;
        private final String color;
        private final String type; // 'player' or 'town'
        private final List<AchievementTier> tiers;

        public AchievementDefinition(String id, String name, String description, String stat, String color, String type) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.stat = stat;
            this.color = color;
            this.type = type;
            this.tiers = new ArrayList<>();
        }

        public void addTier(AchievementTier tier) {
            tiers.add(tier);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getStat() { return stat; }
        public String getColor() { return color; }
        public String getType() { return type; }
        public List<AchievementTier> getTiers() { return tiers; }
    }

    public static class AchievementTier {
        private final int tier;
        private final String name;
        private final String description;
        private final int threshold;
        private final String icon;
        private final int points;

        public AchievementTier(int tier, String name, String description, int threshold, String icon, int points) {
            this.tier = tier;
            this.name = name;
            this.description = description;
            this.threshold = threshold;
            this.icon = icon;
            this.points = points;
        }

        public int getTier() { return tier; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getThreshold() { return threshold; }
        public String getIcon() { return icon; }
        public int getPoints() { return points; }
    }

    public static class PlayerAchievementData {
        private UUID playerUUID;
        private String playerName;
        private Map<String, List<UnlockedTier>> unlockedTiers;
        private long lastUpdated;

        public PlayerAchievementData(UUID playerUUID, String playerName) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.unlockedTiers = new HashMap<>();
            this.lastUpdated = System.currentTimeMillis();
        }

        public void unlockTier(String achievementId, AchievementTier tier, int currentValue) {
            unlockedTiers.computeIfAbsent(achievementId, k -> new ArrayList<>())
                .add(new UnlockedTier(tier, currentValue));
            lastUpdated = System.currentTimeMillis();
        }

        public boolean hasUnlockedTier(String achievementId, int tier) {
            List<UnlockedTier> tiers = unlockedTiers.get(achievementId);
            if (tiers == null) return false;
            
            for (UnlockedTier unlockedTier : tiers) {
                if (unlockedTier.getTier().getTier() == tier) {
                    return true;
                }
            }
            return false;
        }

        // Getters and setters
        public UUID getPlayerUUID() { return playerUUID; }
        public void setPlayerUUID(UUID playerUUID) { this.playerUUID = playerUUID; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Map<String, List<UnlockedTier>> getUnlockedTiers() { return unlockedTiers; }
        public void setUnlockedTiers(Map<String, List<UnlockedTier>> unlockedTiers) { this.unlockedTiers = unlockedTiers; }
        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
        
        public void clearAllUnlockedTiers() {
            unlockedTiers.clear();
            lastUpdated = System.currentTimeMillis();
        }
    }

    public static class TownAchievementData {
        private String townName;
        private Map<String, List<UnlockedTier>> unlockedTiers;
        private long lastUpdated;

        public TownAchievementData(String townName) {
            this.townName = townName;
            this.unlockedTiers = new HashMap<>();
            this.lastUpdated = System.currentTimeMillis();
        }

        public void unlockTier(String achievementId, AchievementTier tier, int currentValue) {
            unlockedTiers.computeIfAbsent(achievementId, k -> new ArrayList<>())
                .add(new UnlockedTier(tier, currentValue));
            lastUpdated = System.currentTimeMillis();
        }

        public boolean hasUnlockedTier(String achievementId, int tier) {
            List<UnlockedTier> tiers = unlockedTiers.get(achievementId);
            if (tiers == null) return false;
            
            for (UnlockedTier unlockedTier : tiers) {
                if (unlockedTier.getTier().getTier() == tier) {
                    return true;
                }
            }
            return false;
        }

        // Getters and setters
        public String getTownName() { return townName; }
        public void setTownName(String townName) { this.townName = townName; }
        public Map<String, List<UnlockedTier>> getUnlockedTiers() { return unlockedTiers; }
        public void setUnlockedTiers(Map<String, List<UnlockedTier>> unlockedTiers) { this.unlockedTiers = unlockedTiers; }
        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class UnlockedTier {
        private AchievementTier tier;
        private int currentValue;
        private long unlockedAt;

        public UnlockedTier(AchievementTier tier, int currentValue) {
            this.tier = tier;
            this.currentValue = currentValue;
            this.unlockedAt = System.currentTimeMillis();
        }

        // Getters and setters
        public AchievementTier getTier() { return tier; }
        public void setTier(AchievementTier tier) { this.tier = tier; }
        public int getCurrentValue() { return currentValue; }
        public void setCurrentValue(int currentValue) { this.currentValue = currentValue; }
        public long getUnlockedAt() { return unlockedAt; }
        public void setUnlockedAt(long unlockedAt) { this.unlockedAt = unlockedAt; }
    }
} 