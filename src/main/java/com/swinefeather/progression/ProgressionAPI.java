package com.swinefeather.progression;

import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Progression Plugin API
 * This class provides a public API for other plugins to access player and town progression data.
 * 
 * Usage example:
 * ProgressionAPI api = ProgressionAPI.getInstance();
 * PlayerLevelInfo levelInfo = api.getPlayerLevel(player);
 * TownLevelInfo townInfo = api.getTownLevel("TownName");
 */
public class ProgressionAPI {
    private static ProgressionAPI instance;
    private final Main plugin;
    private final LevelManager levelManager;
    private final AwardManager awardManager;
    private final TownyManager townyManager;
    private final SupabaseManager supabaseManager;

    private ProgressionAPI(Main plugin) {
        this.plugin = plugin;
        this.levelManager = plugin.getLevelManager();
        this.awardManager = plugin.getAwardManager();
        this.townyManager = plugin.getTownyManager();
        this.supabaseManager = plugin.getSupabaseManager();
    }

    /**
     * Get the singleton instance of the API
     * @return ProgressionAPI instance
     */
    public static ProgressionAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ProgressionAPI not initialized. Make sure the Progression plugin is loaded.");
        }
        return instance;
    }

    /**
     * Initialize the API (called by the main plugin)
     */
    static void initialize(Main plugin) {
        instance = new ProgressionAPI(plugin);
    }

    // ==================== PLAYER LEVEL API ====================

    /**
     * Get player level information
     * @param player The player
     * @return PlayerLevelInfo containing level, XP, and level details
     */
    public PlayerLevelInfo getPlayerLevel(Player player) {
        return getPlayerLevel(player.getUniqueId());
    }

    /**
     * Get player level information by UUID
     * @param playerUUID The player's UUID
     * @return PlayerLevelInfo containing level, XP, and level details
     */
    public PlayerLevelInfo getPlayerLevel(UUID playerUUID) {
        if (levelManager == null) {
            return new PlayerLevelInfo(playerUUID, 1, 0, 0, "Newcomer", "Just starting out");
        }
        return levelManager.getPlayerLevelInfo(playerUUID);
    }

    /**
     * Get player level information by UUID (alias for getPlayerLevel)
     * @param playerUUID The player's UUID
     * @return PlayerLevelInfo containing level, XP, and level details
     */
    public PlayerLevelInfo getPlayerLevelInfo(UUID playerUUID) {
        return getPlayerLevel(playerUUID);
    }

    /**
     * Get player level information by name
     * @param playerName The player's name
     * @return Optional containing PlayerLevelInfo if player exists
     */
    public Optional<PlayerLevelInfo> getPlayerLevelByName(String playerName) {
        if (levelManager == null) {
            return Optional.empty();
        }
        return levelManager.getPlayerLevelInfoByName(playerName);
    }

    /**
     * Get player level information by name (alias for getPlayerLevelByName)
     * @param playerName The player's name
     * @return Optional containing PlayerLevelInfo if player exists
     */
    public Optional<PlayerLevelInfo> getPlayerLevelInfoByName(String playerName) {
        return getPlayerLevelByName(playerName);
    }

    /**
     * Get all players' level information
     * @return List of PlayerLevelInfo for all players
     */
    public List<PlayerLevelInfo> getAllPlayerLevels() {
        if (levelManager == null) {
            return List.of();
        }
        return levelManager.getAllPlayerLevels();
    }

    /**
     * Get top players by level
     * @param limit Number of players to return
     * @return List of PlayerLevelInfo sorted by level (highest first)
     */
    public List<PlayerLevelInfo> getTopPlayersByLevel(int limit) {
        if (levelManager == null) {
            return List.of();
        }
        return levelManager.getTopPlayersByLevel(limit);
    }

    /**
     * Get top players by XP
     * @param limit Number of players to return
     * @return List of PlayerLevelInfo sorted by XP (highest first)
     */
    public List<PlayerLevelInfo> getTopPlayersByXP(int limit) {
        if (levelManager == null) {
            return List.of();
        }
        return levelManager.getTopPlayersByXP(limit);
    }

    // ==================== PLAYER AWARDS API ====================

    /**
     * Get player awards information
     * @param player The player
     * @return PlayerAwardsInfo containing awards, medals, and points
     */
    public PlayerAwardsInfo getPlayerAwards(Player player) {
        return getPlayerAwards(player.getUniqueId());
    }

    /**
     * Get player awards information by UUID
     * @param playerUUID The player's UUID
     * @return PlayerAwardsInfo containing awards, medals, and points
     */
    public PlayerAwardsInfo getPlayerAwards(UUID playerUUID) {
        if (awardManager == null) {
            return new PlayerAwardsInfo(playerUUID, List.of(), 0.0, 0, 0, 0, 0);
        }
        return awardManager.getPlayerAwardsInfo(playerUUID);
    }

    /**
     * Get player awards information by UUID (alias for getPlayerAwards)
     * @param playerUUID The player's UUID
     * @return PlayerAwardsInfo containing awards, medals, and points
     */
    public PlayerAwardsInfo getPlayerAwardsInfo(UUID playerUUID) {
        return getPlayerAwards(playerUUID);
    }

    /**
     * Get player awards information by name
     * @param playerName The player's name
     * @return Optional containing PlayerAwardsInfo if player exists
     */
    public Optional<PlayerAwardsInfo> getPlayerAwardsByName(String playerName) {
        if (awardManager == null) {
            return Optional.empty();
        }
        return awardManager.getPlayerAwardsInfoByName(playerName);
    }

    /**
     * Get player awards information by name (alias for getPlayerAwardsByName)
     * @param playerName The player's name
     * @return Optional containing PlayerAwardsInfo if player exists
     */
    public Optional<PlayerAwardsInfo> getPlayerAwardsInfoByName(String playerName) {
        return getPlayerAwardsByName(playerName);
    }

    /**
     * Get all players' awards information
     * @return List of PlayerAwardsInfo for all players
     */
    public List<PlayerAwardsInfo> getAllPlayerAwards() {
        if (awardManager == null) {
            return List.of();
        }
        return awardManager.getAllPlayerAwardsInfo();
    }

    /**
     * Get all players' awards information (alias for getAllPlayerAwards)
     * @return List of PlayerAwardsInfo for all players
     */
    public List<PlayerAwardsInfo> getAllPlayerAwardsInfo() {
        return getAllPlayerAwards();
    }

    /**
     * Get top players by total points
     * @param limit Number of players to return
     * @return List of PlayerAwardsInfo sorted by total points (highest first)
     */
    public List<PlayerAwardsInfo> getTopPlayersByPoints(int limit) {
        if (awardManager == null) {
            return List.of();
        }
        return awardManager.getTopPlayersByPoints(limit);
    }

    /**
     * Get top players by total medals
     * @param limit Number of players to return
     * @return List of PlayerAwardsInfo sorted by total medals (highest first)
     */
    public List<PlayerAwardsInfo> getTopPlayersByMedals(int limit) {
        if (awardManager == null) {
            return List.of();
        }
        return awardManager.getTopPlayersByMedals(limit);
    }

    /**
     * Get specific award information for a player
     * @param playerUUID The player's UUID
     * @param awardId The award ID
     * @return Optional containing AwardInfo if the player has this award
     */
    public Optional<AwardInfo> getPlayerAward(UUID playerUUID, String awardId) {
        if (awardManager == null) {
            return Optional.empty();
        }
        return awardManager.getPlayerAwardInfo(playerUUID, awardId);
    }

    // ==================== TOWN API ====================

    /**
     * Get town level information
     * @param townName The town name
     * @return Optional containing TownLevelInfo if town exists
     */
    public Optional<TownLevelInfo> getTownLevel(String townName) {
        if (townyManager == null) {
            return Optional.empty();
        }
        return townyManager.getTownLevelInfo(townName);
    }

    /**
     * Get town level information (alias for getTownLevel)
     * @param townName The town name
     * @return Optional containing TownLevelInfo if town exists
     */
    public Optional<TownLevelInfo> getTownLevelInfo(String townName) {
        return getTownLevel(townName);
    }

    /**
     * Get all towns' level information
     * @return List of TownLevelInfo for all towns
     */
    public List<TownLevelInfo> getAllTownLevels() {
        if (townyManager == null) {
            return List.of();
        }
        return townyManager.getAllTownLevels();
    }

    /**
     * Get all towns' level information (alias for getAllTownLevels)
     * @return List of TownLevelInfo for all towns
     */
    public List<TownLevelInfo> getAllTownLevelData() {
        return getAllTownLevels();
    }

    /**
     * Get top towns by level
     * @param limit Number of towns to return
     * @return List of TownLevelInfo sorted by level (highest first)
     */
    public List<TownLevelInfo> getTopTownsByLevel(int limit) {
        if (townyManager == null) {
            return List.of();
        }
        return townyManager.getTopTownsByLevel(limit);
    }

    /**
     * Get top towns by XP
     * @param limit Number of towns to return
     * @return List of TownLevelInfo sorted by XP (highest first)
     */
    public List<TownLevelInfo> getTopTownsByXP(int limit) {
        if (townyManager == null) {
            return List.of();
        }
        return townyManager.getTopTownsByXP(limit);
    }

    /**
     * Get town statistics
     * @param townName The town name
     * @return Optional containing TownStatsInfo if town exists
     */
    public Optional<TownStatsInfo> getTownStats(String townName) {
        if (townyManager == null) {
            return Optional.empty();
        }
        return townyManager.getTownStatsInfo(townName);
    }

    /**
     * Get all towns' statistics
     * @return List of TownStatsInfo for all towns
     */
    public List<TownStatsInfo> getAllTownStats() {
        if (townyManager == null) {
            return List.of();
        }
        return townyManager.getAllTownStats();
    }

    // ==================== AWARD DEFINITIONS API ====================

    /**
     * Get all available award definitions
     * @return List of AwardDefinitionInfo
     */
    public List<AwardDefinitionInfo> getAllAwardDefinitions() {
        if (awardManager == null) {
            return List.of();
        }
        return awardManager.getAllAwardDefinitions();
    }

    /**
     * Get specific award definition
     * @param awardId The award ID
     * @return Optional containing AwardDefinitionInfo if award exists
     */
    public Optional<AwardDefinitionInfo> getAwardDefinition(String awardId) {
        if (awardManager == null) {
            return Optional.empty();
        }
        return awardManager.getAwardDefinitionInfo(awardId);
    }

    /**
     * Get award leaderboard for a specific award
     * @param awardId The award ID
     * @param limit Number of players to return
     * @return List of AwardLeaderboardEntry
     */
    public List<AwardLeaderboardEntry> getAwardLeaderboard(String awardId, int limit) {
        if (awardManager == null) {
            return List.of();
        }
        return awardManager.getAwardLeaderboard(awardId, limit);
    }

    // ==================== LEVEL DEFINITIONS API ====================

    /**
     * Get all available level definitions
     * @param levelType "player" or "town"
     * @return List of LevelDefinitionInfo
     */
    public List<LevelDefinitionInfo> getLevelDefinitions(String levelType) {
        if (levelManager == null) {
            return List.of();
        }
        return levelManager.getLevelDefinitions(levelType);
    }

    /**
     * Get specific level definition
     * @param levelType "player" or "town"
     * @param level The level number
     * @return Optional containing LevelDefinitionInfo if level exists
     */
    public Optional<LevelDefinitionInfo> getLevelDefinition(String levelType, int level) {
        if (levelManager == null) {
            return Optional.empty();
        }
        return levelManager.getLevelDefinition(levelType, level);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Check if the API is available
     * @return true if the API is properly initialized
     */
    public boolean isAvailable() {
        return instance != null && plugin != null;
    }

    /**
     * Get the plugin instance
     * @return Main plugin instance
     */
    public Main getPlugin() {
        return plugin;
    }

    /**
     * Force refresh player data from database
     * @param playerUUID The player's UUID
     */
    public void refreshPlayerData(UUID playerUUID) {
        if (supabaseManager != null) {
            supabaseManager.refreshPlayerData(playerUUID);
        }
    }

    /**
     * Force refresh town data from database
     * @param townName The town name
     */
    public void refreshTownData(String townName) {
        if (townyManager != null) {
            townyManager.refreshTownData(townName);
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Player level information
     */
    public static class PlayerLevelInfo {
        private final UUID playerUUID;
        private final String playerName;
        private final int level;
        private final long currentXP;
        private final long totalXP;
        private final String levelTitle;
        private final String levelDescription;
        private final long lastLevelUp;

        public PlayerLevelInfo(UUID playerUUID, String playerName, int level, long currentXP, long totalXP, 
                             String levelTitle, String levelDescription, long lastLevelUp) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.level = level;
            this.currentXP = currentXP;
            this.totalXP = totalXP;
            this.levelTitle = levelTitle;
            this.levelDescription = levelDescription;
            this.lastLevelUp = lastLevelUp;
        }

        public PlayerLevelInfo(UUID playerUUID, int level, long currentXP, long totalXP, 
                             String levelTitle, String levelDescription) {
            this(playerUUID, null, level, currentXP, totalXP, levelTitle, levelDescription, 0);
        }

        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public int getLevel() { return level; }
        public long getCurrentXP() { return currentXP; }
        public long getTotalXP() { return totalXP; }
        public String getLevelTitle() { return levelTitle; }
        public String getLevelDescription() { return levelDescription; }
        public long getLastLevelUp() { return lastLevelUp; }
    }

    /**
     * Player awards information
     */
    public static class PlayerAwardsInfo {
        private final UUID playerUUID;
        private final String playerName;
        private final List<AwardInfo> awards;
        private final double totalPoints;
        private final int totalMedals;
        private final int goldMedals;
        private final int silverMedals;
        private final int bronzeMedals;

        public PlayerAwardsInfo(UUID playerUUID, String playerName, List<AwardInfo> awards, double totalPoints,
                              int totalMedals, int goldMedals, int silverMedals, int bronzeMedals) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.awards = awards;
            this.totalPoints = totalPoints;
            this.totalMedals = totalMedals;
            this.goldMedals = goldMedals;
            this.silverMedals = silverMedals;
            this.bronzeMedals = bronzeMedals;
        }

        public PlayerAwardsInfo(UUID playerUUID, List<AwardInfo> awards, double totalPoints,
                              int totalMedals, int goldMedals, int silverMedals, int bronzeMedals) {
            this(playerUUID, null, awards, totalPoints, totalMedals, goldMedals, silverMedals, bronzeMedals);
        }

        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public List<AwardInfo> getAwards() { return awards; }
        public double getTotalPoints() { return totalPoints; }
        public int getTotalMedals() { return totalMedals; }
        public int getGoldMedals() { return goldMedals; }
        public int getSilverMedals() { return silverMedals; }
        public int getBronzeMedals() { return bronzeMedals; }
    }

    /**
     * Award information
     */
    public static class AwardInfo {
        private final String awardId;
        private final String awardName;
        private final String awardDescription;
        private final String tier;
        private final String medal;
        private final double points;
        private final long statValue;
        private final String statPath;
        private final long achievedAt;

        public AwardInfo(String awardId, String awardName, String awardDescription, String tier, String medal,
                        double points, long statValue, String statPath, long achievedAt) {
            this.awardId = awardId;
            this.awardName = awardName;
            this.awardDescription = awardDescription;
            this.tier = tier;
            this.medal = medal;
            this.points = points;
            this.statValue = statValue;
            this.statPath = statPath;
            this.achievedAt = achievedAt;
        }

        // Getters
        public String getAwardId() { return awardId; }
        public String getAwardName() { return awardName; }
        public String getAwardDescription() { return awardDescription; }
        public String getTier() { return tier; }
        public String getMedal() { return medal; }
        public double getPoints() { return points; }
        public long getStatValue() { return statValue; }
        public String getStatPath() { return statPath; }
        public long getAchievedAt() { return achievedAt; }
    }

    /**
     * Town level information
     */
    public static class TownLevelInfo {
        private final String townName;
        private final int level;
        private final long currentXP;
        private final long totalXP;
        private final String levelTitle;
        private final String levelDescription;
        private final long lastLevelUp;

        public TownLevelInfo(String townName, int level, long currentXP, long totalXP,
                           String levelTitle, String levelDescription, long lastLevelUp) {
            this.townName = townName;
            this.level = level;
            this.currentXP = currentXP;
            this.totalXP = totalXP;
            this.levelTitle = levelTitle;
            this.levelDescription = levelDescription;
            this.lastLevelUp = lastLevelUp;
        }

        // Getters
        public String getTownName() { return townName; }
        public int getLevel() { return level; }
        public long getCurrentXP() { return currentXP; }
        public long getTotalXP() { return totalXP; }
        public String getLevelTitle() { return levelTitle; }
        public String getLevelDescription() { return levelDescription; }
        public long getLastLevelUp() { return lastLevelUp; }
    }

    /**
     * Town statistics information
     */
    public static class TownStatsInfo {
        private final String townName;
        private final int population;
        private final double balance;
        private final String nation;
        private final int plotCount;
        private final int size;
        private final int age;
        private final String mayor;
        private final boolean isCapital;
        private final boolean isIndependent;
        private final long lastUpdated;

        public TownStatsInfo(String townName, int population, double balance, String nation, int plotCount,
                           int size, int age, String mayor, boolean isCapital, boolean isIndependent, long lastUpdated) {
            this.townName = townName;
            this.population = population;
            this.balance = balance;
            this.nation = nation;
            this.plotCount = plotCount;
            this.size = size;
            this.age = age;
            this.mayor = mayor;
            this.isCapital = isCapital;
            this.isIndependent = isIndependent;
            this.lastUpdated = lastUpdated;
        }

        // Getters
        public String getTownName() { return townName; }
        public int getPopulation() { return population; }
        public double getBalance() { return balance; }
        public String getNation() { return nation; }
        public int getPlotCount() { return plotCount; }
        public int getSize() { return size; }
        public int getAge() { return age; }
        public String getMayor() { return mayor; }
        public boolean isCapital() { return isCapital; }
        public boolean isIndependent() { return isIndependent; }
        public long getLastUpdated() { return lastUpdated; }
    }

    /**
     * Award definition information
     */
    public static class AwardDefinitionInfo {
        private final String awardId;
        private final String name;
        private final String description;
        private final String statPath;
        private final String color;
        private final boolean enabled;

        public AwardDefinitionInfo(String awardId, String name, String description, String statPath, String color, boolean enabled) {
            this.awardId = awardId;
            this.name = name;
            this.description = description;
            this.statPath = statPath;
            this.color = color;
            this.enabled = enabled;
        }

        // Getters
        public String getAwardId() { return awardId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getStatPath() { return statPath; }
        public String getColor() { return color; }
        public boolean isEnabled() { return enabled; }
    }

    /**
     * Level definition information
     */
    public static class LevelDefinitionInfo {
        private final String levelType;
        private final int level;
        private final int xpRequired;
        private final String title;
        private final String description;
        private final String color;

        public LevelDefinitionInfo(String levelType, int level, int xpRequired, String title, String description, String color) {
            this.levelType = levelType;
            this.level = level;
            this.xpRequired = xpRequired;
            this.title = title;
            this.description = description;
            this.color = color;
        }

        // Getters
        public String getLevelType() { return levelType; }
        public int getLevel() { return level; }
        public int getXpRequired() { return xpRequired; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getColor() { return color; }
    }

    /**
     * Award leaderboard entry
     */
    public static class AwardLeaderboardEntry {
        private final UUID playerUUID;
        private final String playerName;
        private final String awardId;
        private final String awardName;
        private final double points;
        private final String medal;
        private final String tier;
        private final long statValue;
        private final long achievedAt;
        private final int rank;

        public AwardLeaderboardEntry(UUID playerUUID, String playerName, String awardId, String awardName,
                                   double points, String medal, String tier, long statValue, long achievedAt, int rank) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.awardId = awardId;
            this.awardName = awardName;
            this.points = points;
            this.medal = medal;
            this.tier = tier;
            this.statValue = statValue;
            this.achievedAt = achievedAt;
            this.rank = rank;
        }

        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getAwardId() { return awardId; }
        public String getAwardName() { return awardName; }
        public double getPoints() { return points; }
        public String getMedal() { return medal; }
        public String getTier() { return tier; }
        public long getStatValue() { return statValue; }
        public long getAchievedAt() { return achievedAt; }
        public int getRank() { return rank; }
    }
} 