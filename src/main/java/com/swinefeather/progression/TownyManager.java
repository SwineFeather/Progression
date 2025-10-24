package com.swinefeather.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

public class TownyManager {
    private final Main plugin;
    private final LogManager logManager;
    private final Map<String, TownData> townDataCache;
    private final Map<String, TownLevelData> townLevels;
    private final Map<String, TownAchievementData> townAchievements;
    private boolean enabled = false;
    private boolean levelingEnabled = false;
    private boolean achievementsEnabled = false;
    
    // Towny API classes
    private Class<?> townyUniverseClass;
    private Class<?> townClass;
    private Class<?> nationClass;
    private Class<?> residentClass;
    private Class<?> townBlockClass;
    private Class<?> worldCoordClass;
    
    public TownyManager(Main plugin) {
        this.plugin = plugin;
        this.logManager = plugin.logManager;
        this.townDataCache = new ConcurrentHashMap<>();
        this.townLevels = new ConcurrentHashMap<>();
        this.townAchievements = new ConcurrentHashMap<>();
        
        initialize();
    }
    
    private void initialize() {
        // Check if Towny is enabled in config
        enabled = plugin.getConfig().getBoolean("towny.enabled", false);
        if (!enabled) {
            logManager.debug("Towny integration is disabled in config");
            return;
        }
        
        // Check if Towny plugin is installed
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) {
            logManager.warning("Towny plugin not found! Towny integration disabled.");
            enabled = false;
            return;
        }
        
        // Load Towny API classes
        if (!loadTownyClasses()) {
            logManager.warning("Failed to load Towny API classes! Towny integration disabled.");
            enabled = false;
            return;
        }
        
        // Load configuration
        levelingEnabled = plugin.getConfig().getBoolean("towny.leveling.enabled", true);
        achievementsEnabled = plugin.getConfig().getBoolean("towny.achievements.enabled", true);
        
        logManager.debug("Towny integration initialized");
        
        // Start sync task if enabled
        if (plugin.getConfig().getBoolean("towny.sync.on_startup", false)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Check if we should skip the first sync
                    if (plugin.getConfig().getBoolean("towny.sync.skip_first_sync", true)) {
                        logManager.debug("Skipping first town sync to prevent level-up spam on restart");
                        return;
                    }
                    syncAllTowns();
                }
            }.runTaskLaterAsynchronously(plugin, 100L); // 5 seconds after startup
        }
        
        // Start periodic sync
        long syncInterval = plugin.getConfig().getLong("towny.sync.interval_ticks", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (enabled) {
                    syncAllTowns();
                }
            }
        }.runTaskTimerAsynchronously(plugin, syncInterval, syncInterval);
    }
    
    private boolean loadTownyClasses() {
        try {
            townyUniverseClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            nationClass = Class.forName("com.palmergames.bukkit.towny.object.Nation");
            residentClass = Class.forName("com.palmergames.bukkit.towny.object.Resident");
            townBlockClass = Class.forName("com.palmergames.bukkit.towny.object.TownBlock");
            worldCoordClass = Class.forName("com.palmergames.bukkit.towny.object.WorldCoord");
            return true;
        } catch (ClassNotFoundException e) {
            logManager.warning("Towny classes not found: " + e.getMessage());
            return false;
        }
    }
    
    public void syncAllTowns() {
        if (!enabled) {
            logManager.debug("Towny integration is disabled");
            return;
        }
        
        logManager.debug("Starting town sync...");
        
        try {
            // Get all towns from Towny
            Object townyUniverse = townyUniverseClass.getMethod("getInstance").invoke(null);
            Object towns = townyUniverseClass.getMethod("getTowns").invoke(townyUniverse);
            
            if (towns instanceof Collection) {
                Collection<?> townCollection = (Collection<?>) towns;
                logManager.debug("Found " + townCollection.size() + " towns to sync");
                
                for (Object town : townCollection) {
                    try {
                        String townName = (String) townClass.getMethod("getName").invoke(town);
                        syncTown(townName, town);
                    } catch (Exception e) {
                        logManager.warning("Failed to sync town: " + e.getMessage());
                    }
                }
                
                logManager.debug("Town sync completed");
            }
        } catch (Exception e) {
            logManager.severe("Failed to sync towns", e);
        }
    }
    
    public void syncAllTownsWithNotification() {
        if (!enabled) {
            logManager.debug("Towny integration is disabled");
            return;
        }
        
        logManager.debug("Starting town sync with notifications...");
        
        try {
            // Get all towns from Towny
            Object townyUniverse = townyUniverseClass.getMethod("getInstance").invoke(null);
            Object towns = townyUniverseClass.getMethod("getTowns").invoke(townyUniverse);
            
            if (towns instanceof Collection) {
                Collection<?> townCollection = (Collection<?>) towns;
                logManager.debug("Found " + townCollection.size() + " towns to sync");
                
                for (Object town : townCollection) {
                    try {
                        String townName = (String) townClass.getMethod("getName").invoke(town);
                        syncTown(townName, town);
                    } catch (Exception e) {
                        logManager.warning("Failed to sync town: " + e.getMessage());
                    }
                }
                
                logManager.debug("Town sync completed");
            }
        } catch (Exception e) {
            logManager.severe("Failed to sync towns", e);
        }
    }
    
    public void syncTown(String townName, Object town) {
        if (!enabled) return;
        
        try {
            Map<String, Object> townStats = collectTownStats(town);
            TownData townData = new TownData(townName, townStats);
            townDataCache.put(townName, townData);
            
            // Calculate and update town level
            if (levelingEnabled) {
                updateTownLevel(townName, townStats);
            }
            
            // Check achievements
            if (achievementsEnabled) {
                checkTownAchievements(townName, townStats);
            }
            
            // Sync to database (only if Supabase is not enabled to avoid duplicate syncing)
            if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled() && 
                (plugin.supabaseManager == null || !plugin.supabaseManager.isEnabled())) {
                plugin.levelDatabaseManager.syncTownData(townName, townStats);
            }
            
            // Sync to Supabase if enabled
            if (plugin.supabaseManager != null && plugin.supabaseManager.isEnabled()) {
                plugin.supabaseManager.syncTownStats(townName, townStats);
            }
            
            logManager.debug("Synced town: " + townName);
        } catch (Exception e) {
            logManager.warning("Failed to sync town " + townName + ": " + e.getMessage());
        }
    }
    
    private Map<String, Object> collectTownStats(Object town) {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Basic town info
            String townName = (String) townClass.getMethod("getName").invoke(town);
            stats.put("name", townName);
            
            // Population
            Collection<?> residents = (Collection<?>) townClass.getMethod("getResidents").invoke(town);
            int population = residents.size();
            stats.put("population", population);
            
            // Balance - try multiple methods to get town balance
            double balance = 0.0;
            try {
                // Try getAccount() first (older Towny versions)
                balance = (Double) townClass.getMethod("getAccount").invoke(town);
                logManager.debug("Got balance via getAccount(): " + balance);
            } catch (Exception e1) {
                try {
                    // Try getAccount().getHoldingBalance() (newer Towny versions)
                    Object account = townClass.getMethod("getAccount").invoke(town);
                    if (account != null) {
                        balance = (Double) account.getClass().getMethod("getHoldingBalance").invoke(account);
                        logManager.debug("Got balance via getAccount().getHoldingBalance(): " + balance);
                    }
                } catch (Exception e2) {
                    try {
                        // Try getBalance() (alternative method)
                        balance = (Double) townClass.getMethod("getBalance").invoke(town);
                        logManager.debug("Got balance via getBalance(): " + balance);
                    } catch (Exception e3) {
                        try {
                            // Try getTreasury() (another alternative)
                            Object treasury = townClass.getMethod("getTreasury").invoke(town);
                            if (treasury != null) {
                                balance = (Double) treasury.getClass().getMethod("getBalance").invoke(treasury);
                                logManager.debug("Got balance via getTreasury().getBalance(): " + balance);
                            }
                        } catch (Exception e4) {
                            logManager.warning("Could not get balance for town " + townName + ": " + e4.getMessage());
                            balance = 0.0;
                        }
                    }
                }
            }
            stats.put("balance", balance);
            
            // Nation info
            Object nation = townClass.getMethod("getNationOrNull").invoke(town);
            if (nation != null) {
                String nationName = (String) nationClass.getMethod("getName").invoke(nation);
                stats.put("nation", nationName);
                stats.put("nation_member", 1);
                
                // Check if capital
                Object capital = nationClass.getMethod("getCapital").invoke(nation);
                boolean isCapital = capital != null && capital.equals(town);
                stats.put("capital", isCapital ? 1 : 0);
            } else {
                stats.put("nation", "none");
                stats.put("nation_member", 0);
                stats.put("capital", 0);
                stats.put("independent", 1);
            }
            
            // Plot count
            Collection<?> townBlocks = (Collection<?>) townClass.getMethod("getTownBlocks").invoke(town);
            int plotCount = townBlocks.size();
            stats.put("plot_count", plotCount);
            
            // Town size (in chunks)
            int size = plotCount; // Each plot is typically 16x16 blocks
            stats.put("size", size);
            
            // Town age (in days)
            try {
                long founded = (Long) townClass.getMethod("getRegistered").invoke(town);
                long currentTime = System.currentTimeMillis();
                long ageInMillis = currentTime - founded;
                int ageInDays = (int) (ageInMillis / (1000 * 60 * 60 * 24));
                stats.put("age", ageInDays);
            } catch (Exception e) {
                stats.put("age", 0);
            }
            
            // Mayor info
            Object mayor = townClass.getMethod("getMayor").invoke(town);
            if (mayor != null) {
                String mayorName = (String) residentClass.getMethod("getName").invoke(mayor);
                stats.put("mayor", mayorName);
            } else {
                stats.put("mayor", "none");
            }
            
        } catch (Exception e) {
            logManager.warning("Error collecting town stats: " + e.getMessage());
        }
        
        return stats;
    }
    
    private void updateTownLevel(String townName, Map<String, Object> townStats) {
        // Load existing town level data
        LevelManager.TownLevelData levelData = plugin.levelManager.loadTownLevelData(townName);
        if (levelData == null) {
            levelData = new LevelManager.TownLevelData(townName, 1, 0);
            plugin.levelManager.townLevels.put(townName, levelData);
        }
        
        // Get the last calculated stats from the level data
        Map<String, Object> lastStats = levelData.getLastCalculatedStats();
        if (lastStats == null) {
            lastStats = new HashMap<>();
        }
        
        // Calculate current XP based on current stats
        int currentXP = calculateTownXP(townStats);
        
        // Calculate previous XP based on last stats
        int previousXP = calculateTownXP(lastStats);
        
        // Only add the difference in XP
        int xpDifference = currentXP - previousXP;
        
        if (xpDifference > 0) {
            int oldLevel = levelData.getLevel();
            int oldXP = levelData.getTotalXP();
            
            // Add only the difference
            levelData.addXP(xpDifference);
            
            // Calculate new level
            int newLevel = plugin.levelManager.calculateTownLevel(levelData.getTotalXP());
            levelData.setLevel(newLevel);
            levelData.setLastUpdated(System.currentTimeMillis());
            
            // Update the last calculated stats
            levelData.setLastCalculatedStats(new HashMap<>(townStats));
            
            // Check for level up
            if (newLevel > oldLevel) {
                String levelName = getTownLevelName(newLevel);
                logManager.debug("Town " + townName + " leveled up to " + levelName + " (Level " + newLevel + ")!");
                
                // Announce level up if enabled
                if (plugin.getConfig().getBoolean("towny.notifications.level_ups", true)) {
                    Bukkit.broadcastMessage("§6[Towny] §e" + townName + " §ahas reached level " + newLevel + " - " + levelName + "!");
                    
                    // Play level up sound for all online players
                    for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                }
            }
            
            // Save the updated data immediately
            plugin.levelManager.saveTownLevelData(townName);
            
            logManager.debug("Town " + townName + " gained " + xpDifference + " XP (Total: " + levelData.getTotalXP() + ", Level: " + newLevel + ")");
        } else if (xpDifference < 0) {
            // Handle XP loss (if stats decreased)
            logManager.debug("Town " + townName + " lost " + Math.abs(xpDifference) + " XP due to stat changes");
            levelData.setLastCalculatedStats(new HashMap<>(townStats));
            plugin.levelManager.saveTownLevelData(townName);
        } else {
            // No change in XP, just update the last calculated stats
            levelData.setLastCalculatedStats(new HashMap<>(townStats));
            plugin.levelManager.saveTownLevelData(townName);
        }
        
        // Save to database if enabled (only if Supabase is not enabled to avoid duplicate syncing)
        if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled() && 
            (plugin.supabaseManager == null || !plugin.supabaseManager.isEnabled())) {
            // Convert LevelManager.TownLevelData to TownyManager.TownLevelData for database sync
            TownLevelData dbLevelData = new TownLevelData(townName, levelData.getLevel(), levelData.getTotalXP());
            dbLevelData.setLastUpdated(levelData.getLastUpdated());
            plugin.levelDatabaseManager.syncTownLevel(townName, dbLevelData);
        }
        
        // Sync to Supabase if enabled
        if (plugin.supabaseManager != null && plugin.supabaseManager.isEnabled()) {
            plugin.supabaseManager.syncTownLevel(townName, levelData.getLevel(), levelData.getTotalXP());
        }
    }
    
    private int calculateTownXP(Map<String, Object> townStats) {
        int totalXP = 0;
        ConfigurationSection xpSources = plugin.getConfig().getConfigurationSection("towny.leveling.xp_sources");
        
        if (xpSources == null) return 0;
        
        // Population XP
        Object population = townStats.get("population");
        if (population != null) {
            int residents = ((Number) population).intValue();
            int xpPerResident = xpSources.getInt("population", 10);
            totalXP += residents * xpPerResident;
        }
        
        // Nation member XP
        Object nationMember = townStats.get("nation_member");
        if (nationMember != null && ((Number) nationMember).intValue() > 0) {
            totalXP += xpSources.getInt("nation_member", 50);
        }
        
        // Capital XP
        Object capital = townStats.get("capital");
        if (capital != null && ((Number) capital).intValue() > 0) {
            totalXP += xpSources.getInt("capital", 100);
        }
        
        // Independent XP
        Object independent = townStats.get("independent");
        if (independent != null && ((Number) independent).intValue() > 0) {
            totalXP += xpSources.getInt("independent", 75);
        }
        
        // Plot count XP
        Object plotCount = townStats.get("plot_count");
        if (plotCount != null) {
            int plots = ((Number) plotCount).intValue();
            int xpPerPlot = xpSources.getInt("plot_count", 5);
            totalXP += plots * xpPerPlot;
        }
        
        // Balance XP
        Object balance = townStats.get("balance");
        if (balance != null) {
            double townBalance = ((Number) balance).doubleValue();
            int xpPerThousand = xpSources.getInt("balance", 1);
            totalXP += (int) (townBalance / 1000) * xpPerThousand;
        }
        
        // Age XP
        Object age = townStats.get("age");
        if (age != null) {
            int days = ((Number) age).intValue();
            int xpPerDay = xpSources.getInt("age", 2);
            totalXP += days * xpPerDay;
        }
        
        // Size XP
        Object size = townStats.get("size");
        if (size != null) {
            int townSize = ((Number) size).intValue();
            int xpPerChunk = xpSources.getInt("size", 3);
            totalXP += townSize * xpPerChunk;
        }
        
        return totalXP;
    }
    
    private int calculateTownLevel(int totalXP) {
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("towny.leveling.levels");
        if (levels == null) return 1;
        
        int maxLevel = 1;
        for (String levelKey : levels.getKeys(false)) {
            ConfigurationSection levelSection = levels.getConfigurationSection(levelKey);
            if (levelSection != null) {
                int level = Integer.parseInt(levelKey);
                int xpRequired = levelSection.getInt("xp_required", 0);
                if (totalXP >= xpRequired) {
                    maxLevel = Math.max(maxLevel, level);
                }
            }
        }
        
        return maxLevel;
    }
    
    private String getTownLevelName(int level) {
        ConfigurationSection levels = plugin.getConfig().getConfigurationSection("towny.leveling.levels");
        if (levels == null) return "Unknown";
        
        ConfigurationSection levelSection = levels.getConfigurationSection(String.valueOf(level));
        if (levelSection != null) {
            return levelSection.getString("name", "Unknown");
        }
        
        return "Unknown";
    }
    
    private void checkTownAchievements(String townName, Map<String, Object> townStats) {
        // Use the AchievementManager for proper persistence
        if (plugin.achievementManager != null) {
            plugin.achievementManager.checkTownAchievements(townName, townStats);
        } else {
            // Fallback to old system if AchievementManager is not available
            ConfigurationSection achievements = plugin.getConfig().getConfigurationSection("towny.achievements");
            if (achievements == null) return;
            
            TownAchievementData achievementData = townAchievements.computeIfAbsent(townName, 
                k -> new TownAchievementData(townName));
            
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
                            // Unlock achievement
                            achievementData.unlockTier(achievementKey, tier, currentValue);
                            
                            // Award XP
                            int xpGained = points;
                            if (levelingEnabled) {
                                TownLevelData levelData = townLevels.get(townName);
                                if (levelData != null) {
                                    levelData.addXP(xpGained);
                                }
                            }
                            
                            // Announce achievement
                            if (plugin.getConfig().getBoolean("towny.notifications.achievements", true)) {
                                Bukkit.broadcastMessage("§6[Towny] §e" + townName + " §ahas unlocked achievement: §6" + tierName + "§a!");
                                
                                // Play achievement sound for all online players
                                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                }
                            }
                            
                            logManager.debug("Town " + townName + " unlocked achievement: " + tierName + " (+" + xpGained + " XP)");
                        }
                    }
                }
            }
        }
    }
    
    public TownData getTownData(String townName) {
        return townDataCache.get(townName);
    }
    
    public TownLevelData getTownLevelData(String townName) {
        return townLevels.get(townName);
    }
    
    public TownAchievementData getTownAchievementData(String townName) {
        return townAchievements.get(townName);
    }
    
    public Map<String, TownData> getAllTownData() {
        return new HashMap<>(townDataCache);
    }
    
    public Map<String, TownLevelData> getAllTownLevelData() {
        return new HashMap<>(townLevels);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isLevelingEnabled() {
        return levelingEnabled;
    }
    
    public boolean isAchievementsEnabled() {
        return achievementsEnabled;
    }
    
    // Data classes
    public static class TownData {
        private final String name;
        private final Map<String, Object> stats;
        private final long lastUpdated;
        
        public TownData(String name, Map<String, Object> stats) {
            this.name = name;
            this.stats = new HashMap<>(stats);
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public String getName() { return name; }
        public Map<String, Object> getStats() { return new HashMap<>(stats); }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    public static class TownLevelData {
        private String townName;
        private int level;
        private int totalXP;
        private long lastUpdated;
        private Map<String, Object> lastCalculatedStats; // Added for XP difference calculation
        
        public TownLevelData(String townName, int level, int totalXP) {
            this.townName = townName;
            this.level = level;
            this.totalXP = totalXP;
            this.lastUpdated = System.currentTimeMillis();
            this.lastCalculatedStats = new HashMap<>(); // Initialize lastCalculatedStats
        }
        
        public void addXP(int xp) {
            this.totalXP += xp;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getTownName() { return townName; }
        public void setTownName(String townName) { this.townName = townName; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public int getTotalXP() { return totalXP; }
        public void setTotalXP(int totalXP) { this.totalXP = totalXP; }
        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
        public Map<String, Object> getLastCalculatedStats() { return lastCalculatedStats; } // Added getter
        public void setLastCalculatedStats(Map<String, Object> lastCalculatedStats) { this.lastCalculatedStats = lastCalculatedStats; } // Added setter
    }
    
    public static class TownAchievementData {
        private final String townName;
        private final Map<String, Set<Integer>> unlockedTiers;
        private final long lastUpdated;
        
        public TownAchievementData(String townName) {
            this.townName = townName;
            this.unlockedTiers = new HashMap<>();
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public boolean hasUnlockedTier(String achievementId, int tier) {
            Set<Integer> tiers = unlockedTiers.get(achievementId);
            return tiers != null && tiers.contains(tier);
        }
        
        public void unlockTier(String achievementId, int tier, int value) {
            unlockedTiers.computeIfAbsent(achievementId, k -> new HashSet<>()).add(tier);
        }
        
        public void clearAllUnlockedTiers() {
            unlockedTiers.clear();
        }
        
        public String getTownName() { return townName; }
        public Map<String, Set<Integer>> getUnlockedTiers() { return new HashMap<>(unlockedTiers); }
        public long getLastUpdated() { return lastUpdated; }
    }

    // ==================== API METHODS ====================
    
    /**
     * Get town level information for API
     * @param townName The town name
     * @return Optional containing TownLevelInfo if town exists
     */
    public java.util.Optional<ProgressionAPI.TownLevelInfo> getTownLevelInfo(String townName) {
        // Try to get from Towny first
        if (enabled && townClass != null) {
            try {
                // Use reflection to get town
                Method getTownMethod = townyUniverseClass.getMethod("getTown", String.class);
                Object town = getTownMethod.invoke(null, townName);
                if (town != null) {
                    // Get level data from cache or calculate
                    TownLevelData levelData = townLevels.get(townName);
                    int level = levelData != null ? levelData.getLevel() : 1;
                    long currentXP = levelData != null ? levelData.getTotalXP() : 0;
                    long totalXP = levelData != null ? levelData.getTotalXP() : 0;
                    String levelTitle = getTownLevelName(level);
                    String levelDescription = "Level " + level + " town";
                    long lastLevelUp = levelData != null ? levelData.getLastUpdated() : 0;
                    
                    return java.util.Optional.of(new ProgressionAPI.TownLevelInfo(
                        townName, level, currentXP, totalXP, levelTitle, levelDescription, lastLevelUp
                    ));
                }
            } catch (Exception e) {
                logManager.warning("Failed to get town level info for " + townName + ": " + e.getMessage());
            }
        }
        
        return java.util.Optional.empty();
    }
    
    /**
     * Get all towns' level information for API
     * @return List of TownLevelInfo for all towns
     */
    public java.util.List<ProgressionAPI.TownLevelInfo> getAllTownLevels() {
        java.util.List<ProgressionAPI.TownLevelInfo> townLevels = new java.util.ArrayList<>();
        
        if (enabled && townyUniverseClass != null) {
            try {
                // Use reflection to get all towns
                Method getTownsMethod = townyUniverseClass.getMethod("getTowns");
                java.util.Collection<?> towns = (java.util.Collection<?>) getTownsMethod.invoke(null);
                for (Object town : towns) {
                    Method getNameMethod = town.getClass().getMethod("getName");
                    String townName = (String) getNameMethod.invoke(town);
                    java.util.Optional<ProgressionAPI.TownLevelInfo> levelInfo = getTownLevelInfo(townName);
                    levelInfo.ifPresent(townLevels::add);
                }
            } catch (Exception e) {
                logManager.warning("Failed to get all town levels: " + e.getMessage());
            }
        }
        
        return townLevels;
    }
    
    /**
     * Get top towns by level for API
     * @param limit Number of towns to return
     * @return List of TownLevelInfo sorted by level (highest first)
     */
    public java.util.List<ProgressionAPI.TownLevelInfo> getTopTownsByLevel(int limit) {
        java.util.List<ProgressionAPI.TownLevelInfo> townLevels = getAllTownLevels();
        townLevels.sort((a, b) -> Integer.compare(b.getLevel(), a.getLevel()));
        return townLevels.subList(0, Math.min(limit, townLevels.size()));
    }
    
    /**
     * Get top towns by XP for API
     * @param limit Number of towns to return
     * @return List of TownLevelInfo sorted by XP (highest first)
     */
    public java.util.List<ProgressionAPI.TownLevelInfo> getTopTownsByXP(int limit) {
        java.util.List<ProgressionAPI.TownLevelInfo> townLevels = getAllTownLevels();
        townLevels.sort((a, b) -> Long.compare(b.getTotalXP(), a.getTotalXP()));
        return townLevels.subList(0, Math.min(limit, townLevels.size()));
    }
    
    /**
     * Get town statistics for API
     * @param townName The town name
     * @return Optional containing TownStatsInfo if town exists
     */
    public java.util.Optional<ProgressionAPI.TownStatsInfo> getTownStatsInfo(String townName) {
        if (enabled && townClass != null) {
            try {
                // Use reflection to get town
                Method getTownMethod = townyUniverseClass.getMethod("getTown", String.class);
                Object town = getTownMethod.invoke(null, townName);
                if (town != null) {
                    // Get town data from cache
                    TownData townData = townDataCache.get(townName);
                    Map<String, Object> stats = townData != null ? townData.getStats() : new HashMap<>();
                    
                    int population = (int) stats.getOrDefault("population", 0);
                    double balance = (double) stats.getOrDefault("balance", 0.0);
                    String nation = (String) stats.getOrDefault("nation", null);
                    int plotCount = (int) stats.getOrDefault("plot_count", 0);
                    int size = (int) stats.getOrDefault("size", 0);
                    int age = (int) stats.getOrDefault("age", 0);
                    String mayor = (String) stats.getOrDefault("mayor", "Unknown");
                    boolean isCapital = (boolean) stats.getOrDefault("is_capital", false);
                    boolean isIndependent = (boolean) stats.getOrDefault("is_independent", true);
                    long lastUpdated = townData != null ? townData.getLastUpdated() : System.currentTimeMillis();
                    
                    return java.util.Optional.of(new ProgressionAPI.TownStatsInfo(
                        townName, population, balance, nation, plotCount, size, age, mayor, isCapital, isIndependent, lastUpdated
                    ));
                }
            } catch (Exception e) {
                logManager.warning("Failed to get town stats for " + townName + ": " + e.getMessage());
            }
        }
        
        return java.util.Optional.empty();
    }
    
    /**
     * Get all towns' statistics for API
     * @return List of TownStatsInfo for all towns
     */
    public java.util.List<ProgressionAPI.TownStatsInfo> getAllTownStats() {
        java.util.List<ProgressionAPI.TownStatsInfo> townStats = new java.util.ArrayList<>();
        
        if (enabled && townyUniverseClass != null) {
            try {
                // Use reflection to get all towns
                Method getTownsMethod = townyUniverseClass.getMethod("getTowns");
                java.util.Collection<?> towns = (java.util.Collection<?>) getTownsMethod.invoke(null);
                for (Object town : towns) {
                    Method getNameMethod = town.getClass().getMethod("getName");
                    String townName = (String) getNameMethod.invoke(town);
                    java.util.Optional<ProgressionAPI.TownStatsInfo> statsInfo = getTownStatsInfo(townName);
                    statsInfo.ifPresent(townStats::add);
                }
            } catch (Exception e) {
                logManager.warning("Failed to get all town stats: " + e.getMessage());
            }
        }
        
        return townStats;
    }
    
    /**
     * Force refresh town data from database for API
     * @param townName The town name
     */
    public void refreshTownData(String townName) {
        // This would trigger a refresh of town data from the database
        // Implementation depends on your database structure
        plugin.getLogger().info("Refreshing town data for: " + townName);
    }
} 