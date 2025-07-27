package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LevelManager {
    private final Main plugin;
    private final LogManager logManager;
    private final Map<UUID, PlayerLevelData> playerLevels;
    private final Map<String, TownLevelData> townLevels;
    private final List<LevelDefinition> playerLevelDefinitions;
    private final List<LevelDefinition> townLevelDefinitions;
    private final Gson gson;
    private final File playerLevelsFile;
    private final File townLevelsFile;
    private final File playerLevelsDir;
    private final File townLevelsDir;

    public LevelManager(Main plugin) {
        this.plugin = plugin;
        this.logManager = plugin.logManager;
        this.playerLevels = new ConcurrentHashMap<>();
        this.townLevels = new ConcurrentHashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Create directories
        this.playerLevelsDir = new File(plugin.getDataFolder(), "levels/players");
        this.townLevelsDir = new File(plugin.getDataFolder(), "levels/towns");
        this.playerLevelsFile = new File(playerLevelsDir, "player_levels.json");
        this.townLevelsFile = new File(townLevelsDir, "town_levels.json");
        
        // Initialize level definitions
        this.playerLevelDefinitions = initializePlayerLevels();
        this.townLevelDefinitions = initializeTownLevels();
        
        // Create directories if they don't exist
        playerLevelsDir.mkdirs();
        townLevelsDir.mkdirs();
        
        loadLevelData();
    }

    private List<LevelDefinition> initializePlayerLevels() {
        List<LevelDefinition> levels = new ArrayList<>();
        
        // Player levels (35 levels total) - Much easier XP requirements for faster progression!
        levels.add(new LevelDefinition(1, 0, "Newcomer", "Welcome to the server!", "#6b7280"));
        levels.add(new LevelDefinition(2, 100, "Novice", "Getting started", "#10b981"));
        levels.add(new LevelDefinition(3, 250, "Apprentice", "Learning the ropes", "#3b82f6"));
        levels.add(new LevelDefinition(4, 500, "Journeyman", "Making progress", "#8b5cf6"));
        levels.add(new LevelDefinition(5, 800, "Adventurer", "Exploring the world", "#f59e0b"));
        levels.add(new LevelDefinition(6, 1200, "Explorer", "Discovering new places", "#06b6d4"));
        levels.add(new LevelDefinition(7, 1700, "Veteran", "Experienced player", "#84cc16"));
        levels.add(new LevelDefinition(8, 2300, "Expert", "Skilled and knowledgeable", "#f97316"));
        levels.add(new LevelDefinition(9, 3000, "Master", "Mastered the game", "#ef4444"));
        levels.add(new LevelDefinition(10, 3800, "Champion", "Elite player", "#a855f7"));
        levels.add(new LevelDefinition(11, 4700, "Legend", "Legendary status", "#dc2626"));
        levels.add(new LevelDefinition(12, 5700, "Mythic", "Mythical prowess", "#9333ea"));
        levels.add(new LevelDefinition(13, 6800, "Ascended", "Beyond mortal limits", "#1d4ed8"));
        levels.add(new LevelDefinition(14, 8000, "Divine", "Divine power", "#059669"));
        levels.add(new LevelDefinition(15, 9300, "Transcendent", "Transcended reality", "#be123c"));
        levels.add(new LevelDefinition(16, 10700, "Cosmic", "Cosmic significance", "#7c3aed"));
        levels.add(new LevelDefinition(17, 12200, "Ethereal", "Beyond mortal understanding", "#d97706"));
        levels.add(new LevelDefinition(18, 13800, "Celestial", "Celestial power", "#ec4899"));
        levels.add(new LevelDefinition(19, 15500, "Astral", "Master of the stars", "#6366f1"));
        levels.add(new LevelDefinition(20, 17300, "Galactic", "Ruler of galaxies", "#10b981"));
        levels.add(new LevelDefinition(21, 19200, "Universal", "Universal conqueror", "#f59e0b"));
        levels.add(new LevelDefinition(22, 21200, "Infinite", "Infinite potential", "#ef4444"));
        levels.add(new LevelDefinition(23, 23300, "Eternal", "Timeless legend", "#a855f7"));
        levels.add(new LevelDefinition(24, 25500, "Omnipotent", "Ultimate power", "#dc2626"));
        levels.add(new LevelDefinition(25, 27800, "Omniscient", "All-knowing entity", "#9333ea"));
        levels.add(new LevelDefinition(26, 30200, "Primordial", "Origin of all", "#1d4ed8"));
        levels.add(new LevelDefinition(27, 32700, "Voidwalker", "Master of the void", "#059669"));
        levels.add(new LevelDefinition(28, 35300, "Starforger", "Creator of stars", "#be123c"));
        levels.add(new LevelDefinition(29, 38000, "Reality Weaver", "Shaper of reality", "#7c3aed"));
        levels.add(new LevelDefinition(30, 40800, "Time Lord", "Master of time", "#d97706"));
        levels.add(new LevelDefinition(31, 43700, "Dimension Lord", "Ruler of dimensions", "#ec4899"));
        levels.add(new LevelDefinition(32, 46700, "Cosmic Sovereign", "Sovereign of the cosmos", "#6366f1"));
        levels.add(new LevelDefinition(33, 49800, "Eternal Monarch", "Eternal ruler", "#10b981"));
        levels.add(new LevelDefinition(34, 53000, "Transcendent King", "King beyond reality", "#f59e0b"));
        levels.add(new LevelDefinition(35, 56300, "Nexus Overlord", "Ultimate overlord of existence", "#ef4444"));
        
        return levels;
    }

    private List<LevelDefinition> initializeTownLevels() {
        List<LevelDefinition> levels = new ArrayList<>();
        
        // Town levels (20 levels total) - Much easier XP requirements for faster progression!
        levels.add(new LevelDefinition(1, 0, "Outpost", "A small gathering of settlers", "#6b7280"));
        levels.add(new LevelDefinition(2, 25, "Camp", "A temporary settlement", "#6b7280"));
        levels.add(new LevelDefinition(3, 60, "Settlement", "A permanent home for pioneers", "#10b981"));
        levels.add(new LevelDefinition(4, 110, "Hamlet", "A small but growing community", "#10b981"));
        levels.add(new LevelDefinition(5, 175, "Village", "A thriving rural settlement", "#10b981"));
        levels.add(new LevelDefinition(6, 250, "Town", "A bustling center of activity", "#3b82f6"));
        levels.add(new LevelDefinition(7, 350, "Market Town", "A hub of commerce and trade", "#3b82f6"));
        levels.add(new LevelDefinition(8, 475, "Port Town", "A coastal trading center", "#3b82f6"));
        levels.add(new LevelDefinition(9, 625, "Mining Town", "A settlement built on resources", "#3b82f6"));
        levels.add(new LevelDefinition(10, 800, "Farming Town", "A settlement sustained by agriculture", "#3b82f6"));
        levels.add(new LevelDefinition(11, 1000, "Trading Post", "A center of regional commerce", "#8b5cf6"));
        levels.add(new LevelDefinition(12, 1250, "Crossroads", "A town at the intersection of trade routes", "#8b5cf6"));
        levels.add(new LevelDefinition(13, 1550, "Fortress Town", "A well-defended settlement", "#8b5cf6"));
        levels.add(new LevelDefinition(14, 1900, "University Town", "A center of learning and knowledge", "#8b5cf6"));
        levels.add(new LevelDefinition(15, 2300, "Craftsmen Town", "A settlement of skilled artisans", "#8b5cf6"));
        levels.add(new LevelDefinition(16, 2750, "Merchant Town", "A wealthy trading center", "#f59e0b"));
        levels.add(new LevelDefinition(17, 3250, "Guild Town", "A settlement of organized crafts", "#f59e0b"));
        levels.add(new LevelDefinition(18, 3800, "Harbor Town", "A major port settlement", "#f59e0b"));
        levels.add(new LevelDefinition(19, 4400, "Frontier Town", "A settlement on the edge of civilization", "#f59e0b"));
        levels.add(new LevelDefinition(20, 5100, "Industrial Town", "A center of manufacturing", "#f59e0b"));
        
        return levels;
    }

    public void addPlayerXP(UUID playerUUID, String playerName, int xp) {
        PlayerLevelData levelData = playerLevels.computeIfAbsent(playerUUID, 
            k -> new PlayerLevelData(playerUUID, playerName, 1, 0));
        
        int oldLevel = levelData.getLevel();
        int oldXP = levelData.getTotalXP();
        
        levelData.addXP(xp);
        levelData.setPlayerName(playerName);
        levelData.setLastUpdated(System.currentTimeMillis());
        
        // Recalculate level
        int newLevel = calculatePlayerLevel(levelData.getTotalXP());
        levelData.setLevel(newLevel);
        
        int newXP = levelData.getTotalXP();
        
        // Check for level up
        if (newLevel > oldLevel) {
            LevelDefinition levelDef = getPlayerLevelDefinition(newLevel);
            if (levelDef != null) {
                logManager.debug("Player " + playerName + " leveled up to " + levelDef.getTitle() + " (Level " + newLevel + ")!");
                
                // Send level up message to player if online
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player != null && player.isOnline()) {
                    player.sendMessage("§a§l🎉 LEVEL UP! §aYou are now " + levelDef.getTitle() + " (Level " + newLevel + ")");
                    player.sendMessage("§7" + levelDef.getDescription());
                    
                    // Play special level up sound for the player
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    
                    // Play additional celebratory sounds
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.3f, 1.5f);
                }
                
                // Send server-wide announcement if enabled
                boolean broadcastLevelUps = plugin.getConfig().getBoolean("level.broadcast_level_ups", true);
                if (broadcastLevelUps) {
                    String announcement = "§6§l🎉 " + playerName + " §6has reached Level " + newLevel + " - " + levelDef.getTitle() + "! §6🎉";
                    plugin.getServer().broadcastMessage(announcement);
                    
                    // Play level up sound for all online players
                    float soundVolume = (float) plugin.getConfig().getDouble("level.level_up_sound_volume", 0.3);
                    for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                        if (!onlinePlayer.getUniqueId().equals(playerUUID)) {
                            // Play a softer sound for other players
                            onlinePlayer.playSound(onlinePlayer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, soundVolume, 1.0f);
                        }
                    }
                }
            }
        }
        
        // Save the updated data
        savePlayerLevelData(playerUUID);
        
        // Sync to database if enabled
        if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
            plugin.levelDatabaseManager.syncPlayerLevel(playerUUID, playerName, newLevel, newXP);
        }
        
        logManager.debug("Added " + xp + " XP to " + playerName + " (Total: " + newXP + ", Level: " + newLevel + ")");
    }

    public void addTownXP(String townName, int xp) {
        TownLevelData levelData = townLevels.computeIfAbsent(townName, 
            k -> new TownLevelData(townName, 1, 0));
        
        int oldLevel = levelData.getLevel();
        int oldXP = levelData.getTotalXP();
        
        levelData.addXP(xp);
        levelData.setLastUpdated(System.currentTimeMillis());
        
        // Recalculate level
        int newLevel = calculateTownLevel(levelData.getTotalXP());
        levelData.setLevel(newLevel);
        
        int newXP = levelData.getTotalXP();
        
        // Check for level up
        if (newLevel > oldLevel) {
            LevelDefinition levelDef = getTownLevelDefinition(newLevel);
            if (levelDef != null) {
                logManager.debug("Town " + townName + " leveled up to " + levelDef.getTitle() + " (Level " + newLevel + ")!");
            }
        }
        
        // Save the updated data
        saveTownLevelData(townName);
        
        logManager.debug("Added " + xp + " XP to town " + townName + " (Total: " + newXP + ", Level: " + newLevel + ")");
    }

    public PlayerLevelData getPlayerLevelData(UUID playerUUID) {
        return playerLevels.get(playerUUID);
    }

    public TownLevelData getTownLevelData(String townName) {
        return townLevels.get(townName);
    }

    public LevelDefinition getPlayerLevelDefinition(int level) {
        for (LevelDefinition def : playerLevelDefinitions) {
            if (def.getLevel() == level) {
                return def;
            }
        }
        return null;
    }

    public LevelDefinition getTownLevelDefinition(int level) {
        for (LevelDefinition def : townLevelDefinitions) {
            if (def.getLevel() == level) {
                return def;
            }
        }
        return null;
    }

    public List<LevelDefinition> getPlayerLevelDefinitions() {
        return new ArrayList<>(playerLevelDefinitions);
    }

    public List<LevelDefinition> getTownLevelDefinitions() {
        return new ArrayList<>(townLevelDefinitions);
    }

    public List<PlayerLevelData> getAllPlayerLevelData() {
        return new ArrayList<>(playerLevels.values());
    }

    public int calculatePlayerLevel(int totalXP) {
        for (int i = playerLevelDefinitions.size() - 1; i >= 0; i--) {
            LevelDefinition def = playerLevelDefinitions.get(i);
            if (totalXP >= def.getXpRequired()) {
                return def.getLevel();
            }
        }
        return 1;
    }

    public int calculateTownLevel(int totalXP) {
        for (int i = townLevelDefinitions.size() - 1; i >= 0; i--) {
            LevelDefinition def = townLevelDefinitions.get(i);
            if (totalXP >= def.getXpRequired()) {
                return def.getLevel();
            }
        }
        return 1;
    }

    public int getXPToNextLevel(UUID playerUUID) {
        PlayerLevelData data = getPlayerLevelData(playerUUID);
        if (data == null) return 0;
        
        int currentLevel = data.getLevel();
        int currentXP = data.getTotalXP();
        
        // Find next level requirement
        for (LevelDefinition def : playerLevelDefinitions) {
            if (def.getLevel() > currentLevel) {
                return def.getXpRequired() - currentXP;
            }
        }
        
        return 0; // Max level reached
    }

    public int getXPToNextTownLevel(String townName) {
        TownLevelData data = getTownLevelData(townName);
        if (data == null) return 0;
        
        int currentLevel = data.getLevel();
        int currentXP = data.getTotalXP();
        
        // Find next level requirement
        for (LevelDefinition def : townLevelDefinitions) {
            if (def.getLevel() > currentLevel) {
                return def.getXpRequired() - currentXP;
            }
        }
        
        return 0; // Max level reached
    }

    private void loadLevelData() {
        // Load player levels
        if (playerLevelsFile.exists()) {
            try (Reader reader = new FileReader(playerLevelsFile)) {
                Type type = new TypeToken<Map<UUID, PlayerLevelData>>(){}.getType();
                Map<UUID, PlayerLevelData> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    playerLevels.putAll(loaded);
                }
                logManager.debug("Loaded " + playerLevels.size() + " player level records");
            } catch (Exception e) {
                logManager.severe("Failed to load player level data", e);
            }
        }

        // Load town levels
        if (townLevelsFile.exists()) {
            try (Reader reader = new FileReader(townLevelsFile)) {
                Type type = new TypeToken<Map<String, TownLevelData>>(){}.getType();
                Map<String, TownLevelData> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    townLevels.putAll(loaded);
                }
                logManager.debug("Loaded " + townLevels.size() + " town level records");
            } catch (Exception e) {
                logManager.severe("Failed to load town level data", e);
            }
        }
    }

    private void savePlayerLevelData(UUID playerUUID) {
        PlayerLevelData data = playerLevels.get(playerUUID);
        if (data == null) return;

        File playerFile = new File(playerLevelsDir, playerUUID.toString() + ".json");
        try (Writer writer = new FileWriter(playerFile)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            logManager.severe("Failed to save player level data for " + playerUUID, e);
        }
    }

    private void saveTownLevelData(String townName) {
        TownLevelData data = townLevels.get(townName);
        if (data == null) return;

        File townFile = new File(townLevelsDir, townName + ".json");
        try (Writer writer = new FileWriter(townFile)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            logManager.severe("Failed to save town level data for " + townName, e);
        }
    }

    public void saveAllData() {
        // Save all player data
        for (UUID playerUUID : playerLevels.keySet()) {
            savePlayerLevelData(playerUUID);
        }

        // Save all town data
        for (String townName : townLevels.keySet()) {
            saveTownLevelData(townName);
        }

        logManager.debug("Saved all level data");
    }

    public void shutdown() {
        saveAllData();
    }

    public void reloadLevelDefinitions() {
        // Re-read level definitions from config
        loadLevelDefinitions();
    }

    private void loadLevelDefinitions() {
        // Example: Load level definitions from config
        // (You should replace this with your actual config parsing logic)
        // For now, just clear and re-add a dummy level definition
        playerLevelDefinitions.clear();
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();
        org.bukkit.configuration.ConfigurationSection levelsSection = config.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String key : levelsSection.getKeys(false)) {
                // Parse each level definition from config
                // ...
            }
        }
    }

    // ==================== API METHODS ====================
    
    /**
     * Get player level information for API
     * @param playerUUID The player's UUID
     * @return PlayerLevelInfo
     */
    public ProgressionAPI.PlayerLevelInfo getPlayerLevelInfo(UUID playerUUID) {
        // Get player name
        String playerName = null;
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
        if (offlinePlayer.hasPlayedBefore()) {
            playerName = offlinePlayer.getName();
        }
        
        // Get level data from database or cache
        int level = 1;
        long currentXP = 0;
        long totalXP = 0;
        String levelTitle = "Newcomer";
        String levelDescription = "Just starting out";
        long lastLevelUp = 0;
        
        // Try to get from database first
        if (plugin.getSupabaseManager() != null && plugin.getSupabaseManager().isEnabled()) {
            try {
                String response = plugin.getSupabaseManager().getPlayerStats(playerUUID);
                if (response != null && !response.equals("[]")) {
                    // Parse response and extract level info
                    // This is a simplified version - you might want to enhance this
                    level = 1; // Default for now
                    currentXP = 0;
                    totalXP = 0;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get player level info from database: " + e.getMessage());
            }
        }
        
        return new ProgressionAPI.PlayerLevelInfo(playerUUID, playerName, level, currentXP, totalXP, levelTitle, levelDescription, lastLevelUp);
    }
    
    /**
     * Get player level information by name for API
     * @param playerName The player's name
     * @return Optional containing PlayerLevelInfo if player exists
     */
    public java.util.Optional<ProgressionAPI.PlayerLevelInfo> getPlayerLevelInfoByName(String playerName) {
        // Find player UUID by name
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
        if (offlinePlayer.hasPlayedBefore()) {
            return java.util.Optional.of(getPlayerLevelInfo(offlinePlayer.getUniqueId()));
        }
        return java.util.Optional.empty();
    }
    
    /**
     * Get all players' level information for API
     * @return List of PlayerLevelInfo for all players
     */
    public java.util.List<ProgressionAPI.PlayerLevelInfo> getAllPlayerLevels() {
        java.util.List<ProgressionAPI.PlayerLevelInfo> levels = new java.util.ArrayList<>();
        
        // Get all online players
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            levels.add(getPlayerLevelInfo(player.getUniqueId()));
        }
        
        // You might want to add offline players from database here
        
        return levels;
    }
    
    /**
     * Get top players by level for API
     * @param limit Number of players to return
     * @return List of PlayerLevelInfo sorted by level (highest first)
     */
    public java.util.List<ProgressionAPI.PlayerLevelInfo> getTopPlayersByLevel(int limit) {
        java.util.List<ProgressionAPI.PlayerLevelInfo> levels = getAllPlayerLevels();
        levels.sort((a, b) -> Integer.compare(b.getLevel(), a.getLevel()));
        return levels.subList(0, Math.min(limit, levels.size()));
    }
    
    /**
     * Get top players by XP for API
     * @param limit Number of players to return
     * @return List of PlayerLevelInfo sorted by XP (highest first)
     */
    public java.util.List<ProgressionAPI.PlayerLevelInfo> getTopPlayersByXP(int limit) {
        java.util.List<ProgressionAPI.PlayerLevelInfo> levels = getAllPlayerLevels();
        levels.sort((a, b) -> Long.compare(b.getTotalXP(), a.getTotalXP()));
        return levels.subList(0, Math.min(limit, levels.size()));
    }
    
    /**
     * Get all available level definitions for API
     * @param levelType "player" or "town"
     * @return List of LevelDefinitionInfo
     */
    public java.util.List<ProgressionAPI.LevelDefinitionInfo> getLevelDefinitions(String levelType) {
        java.util.List<ProgressionAPI.LevelDefinitionInfo> definitions = new java.util.ArrayList<>();
        
        // Add some default level definitions
        if ("player".equals(levelType)) {
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("player", 1, 0, "Newcomer", "Just starting out", "§7"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("player", 5, 1000, "Explorer", "Getting familiar with the world", "§a"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("player", 10, 5000, "Adventurer", "Experienced traveler", "§b"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("player", 20, 15000, "Veteran", "Seasoned player", "§6"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("player", 50, 100000, "Legend", "Minecraft master", "§5"));
        } else if ("town".equals(levelType)) {
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("town", 1, 0, "Settlement", "A small settlement", "§7"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("town", 5, 1000, "Village", "Growing village", "§a"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("town", 10, 5000, "Town", "Established town", "§b"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("town", 20, 15000, "City", "Major city", "§6"));
            definitions.add(new ProgressionAPI.LevelDefinitionInfo("town", 50, 100000, "Metropolis", "Great metropolis", "§5"));
        }
        
        return definitions;
    }
    
    /**
     * Get specific level definition for API
     * @param levelType "player" or "town"
     * @param level The level number
     * @return Optional containing LevelDefinitionInfo if level exists
     */
    public java.util.Optional<ProgressionAPI.LevelDefinitionInfo> getLevelDefinition(String levelType, int level) {
        return getLevelDefinitions(levelType).stream()
                .filter(def -> def.getLevel() == level)
                .findFirst();
    }

    // Data classes
    public static class LevelDefinition {
        private final int level;
        private final int xpRequired;
        private final String title;
        private final String description;
        private final String color;

        public LevelDefinition(int level, int xpRequired, String title, String description, String color) {
            this.level = level;
            this.xpRequired = xpRequired;
            this.title = title;
            this.description = description;
            this.color = color;
        }

        public int getLevel() { return level; }
        public int getXpRequired() { return xpRequired; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getColor() { return color; }
    }

    public static class PlayerLevelData {
        private UUID playerUUID;
        private String playerName;
        private int level;
        private int totalXP;
        private long lastUpdated;

        public PlayerLevelData(UUID playerUUID, String playerName, int level, int totalXP) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.level = level;
            this.totalXP = totalXP;
            this.lastUpdated = System.currentTimeMillis();
        }

        public void addXP(int xp) {
            this.totalXP += xp;
            this.lastUpdated = System.currentTimeMillis();
        }

        // Getters and setters
        public UUID getPlayerUUID() { return playerUUID; }
        public void setPlayerUUID(UUID playerUUID) { this.playerUUID = playerUUID; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public int getTotalXP() { return totalXP; }
        public void setTotalXP(int totalXP) { this.totalXP = totalXP; }
        public long getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
    }

    public static class TownLevelData {
        private String townName;
        private int level;
        private int totalXP;
        private long lastUpdated;

        public TownLevelData(String townName, int level, int totalXP) {
            this.townName = townName;
            this.level = level;
            this.totalXP = totalXP;
            this.lastUpdated = System.currentTimeMillis();
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
    }
} 