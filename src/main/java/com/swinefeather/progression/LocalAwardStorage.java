package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LocalAwardStorage {
    private final Plugin plugin;
    private final LogManager logger;
    private final Gson gson;
    private final File dataFolder;
    
    // Data structures
    private final Map<UUID, PlayerAwardData> playerAwards = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerMedalData> playerMedals = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerPointData> playerPoints = new ConcurrentHashMap<>();
    
    public LocalAwardStorage(Plugin plugin, LogManager logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataFolder = new File(plugin.getDataFolder(), "awards");
        
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        // Create subdirectories
        new File(dataFolder, "awards").mkdirs();
        new File(dataFolder, "medals").mkdirs();
        new File(dataFolder, "points").mkdirs();
        
        loadAllData();
    }
    
    public void savePlayerAward(UUID playerUUID, String playerName, String awardId, String awardName, 
                               String tier, String medal, double points, long awardedAt) {
        PlayerAwardData awardData = playerAwards.computeIfAbsent(playerUUID, 
            k -> new PlayerAwardData(playerUUID, playerName));
        
        AwardEntry award = new AwardEntry(awardId, awardName, tier, medal, points, awardedAt);
        awardData.addAward(award);
        
        savePlayerAwardData(playerUUID, awardData);
        logger.verbose("Saved award for " + playerName + ": " + awardName + " (" + medal + ")");
    }
    
    public void savePlayerMedals(UUID playerUUID, String playerName, int bronzeCount, int silverCount, 
                                int goldCount, int totalMedals) {
        PlayerMedalData medalData = new PlayerMedalData(playerUUID, playerName, bronzeCount, silverCount, goldCount, totalMedals);
        playerMedals.put(playerUUID, medalData);
        
        savePlayerMedalData(playerUUID, medalData);
        logger.verbose("Saved medals for " + playerName + ": " + totalMedals + " total (" + goldCount + "G, " + silverCount + "S, " + bronzeCount + "B)");
    }
    
    public void savePlayerPoints(UUID playerUUID, String playerName, double totalPoints) {
        PlayerPointData pointData = new PlayerPointData(playerUUID, playerName, totalPoints);
        playerPoints.put(playerUUID, pointData);
        
        savePlayerPointData(playerUUID, pointData);
        logger.verbose("Saved points for " + playerName + ": " + totalPoints);
    }
    
    public void saveAllAwards(Map<UUID, AwardManager.PlayerAwards> allPlayerAwards) {
        for (Map.Entry<UUID, AwardManager.PlayerAwards> entry : allPlayerAwards.entrySet()) {
            UUID playerUUID = entry.getKey();
            AwardManager.PlayerAwards playerAward = entry.getValue();
            
            // Save individual awards
            for (AwardManager.PlayerMedal medal : playerAward.getMedals()) {
                savePlayerAward(playerUUID, playerAward.getPlayerName(), 
                    medal.getAwardId(), medal.getAwardId(), // Using awardId as name for now
                    "stone", medal.getMedalType(), medal.getPoints(), medal.getAwardedAt());
            }
            
            // Save medal summary
            savePlayerMedals(playerUUID, playerAward.getPlayerName(), 
                playerAward.getBronzeMedals(), playerAward.getSilverMedals(), 
                playerAward.getGoldMedals(), playerAward.getTotalMedals());
            
            // Save total points
            savePlayerPoints(playerUUID, playerAward.getPlayerName(), playerAward.getTotalPoints());
        }
        
        logger.verbose("Saved all award data for " + allPlayerAwards.size() + " players");
    }
    
    private void savePlayerAwardData(UUID playerUUID, PlayerAwardData awardData) {
        try {
            File file = new File(dataFolder, "awards/" + playerUUID.toString() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(awardData, writer);
            }
        } catch (IOException e) {
            logger.severe("Failed to save award data for " + playerUUID, e);
        }
    }
    
    private void savePlayerMedalData(UUID playerUUID, PlayerMedalData medalData) {
        try {
            File file = new File(dataFolder, "medals/" + playerUUID.toString() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(medalData, writer);
            }
        } catch (IOException e) {
            logger.severe("Failed to save medal data for " + playerUUID, e);
        }
    }
    
    private void savePlayerPointData(UUID playerUUID, PlayerPointData pointData) {
        try {
            File file = new File(dataFolder, "points/" + playerUUID.toString() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(pointData, writer);
            }
        } catch (IOException e) {
            logger.severe("Failed to save point data for " + playerUUID, e);
        }
    }
    
    private void loadAllData() {
        loadAwardsData();
        loadMedalsData();
        loadPointsData();
        logger.debug("Loaded local award data: " + playerAwards.size() + " awards, " + 
                   playerMedals.size() + " medals, " + playerPoints.size() + " points");
    }
    
    private void loadAwardsData() {
        File awardsFolder = new File(dataFolder, "awards");
        if (!awardsFolder.exists()) return;
        
        File[] files = awardsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        for (File file : files) {
            try {
                String uuidString = file.getName().replace(".json", "");
                UUID playerUUID = UUID.fromString(uuidString);
                
                try (FileReader reader = new FileReader(file)) {
                    PlayerAwardData awardData = gson.fromJson(reader, PlayerAwardData.class);
                    if (awardData != null) {
                        playerAwards.put(playerUUID, awardData);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load award data from " + file.getName() + ": " + e.getMessage());
            }
        }
    }
    
    private void loadMedalsData() {
        File medalsFolder = new File(dataFolder, "medals");
        if (!medalsFolder.exists()) return;
        
        File[] files = medalsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        for (File file : files) {
            try {
                String uuidString = file.getName().replace(".json", "");
                UUID playerUUID = UUID.fromString(uuidString);
                
                try (FileReader reader = new FileReader(file)) {
                    PlayerMedalData medalData = gson.fromJson(reader, PlayerMedalData.class);
                    if (medalData != null) {
                        playerMedals.put(playerUUID, medalData);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load medal data from " + file.getName() + ": " + e.getMessage());
            }
        }
    }
    
    private void loadPointsData() {
        File pointsFolder = new File(dataFolder, "points");
        if (!pointsFolder.exists()) return;
        
        File[] files = pointsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        for (File file : files) {
            try {
                String uuidString = file.getName().replace(".json", "");
                UUID playerUUID = UUID.fromString(uuidString);
                
                try (FileReader reader = new FileReader(file)) {
                    PlayerPointData pointData = gson.fromJson(reader, PlayerPointData.class);
                    if (pointData != null) {
                        playerPoints.put(playerUUID, pointData);
                    }
                }
            } catch (Exception e) {
                logger.warning("Failed to load point data from " + file.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public PlayerAwardData getPlayerAwards(UUID playerUUID) {
        return playerAwards.get(playerUUID);
    }
    
    public PlayerMedalData getPlayerMedals(UUID playerUUID) {
        return playerMedals.get(playerUUID);
    }
    
    public PlayerPointData getPlayerPoints(UUID playerUUID) {
        return playerPoints.get(playerUUID);
    }
    
    public List<PlayerPointData> getTopPlayers(int limit) {
        return playerPoints.values().stream()
            .sorted((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()))
            .limit(limit)
            .toList();
    }
    
    public List<PlayerMedalData> getTopMedalPlayers(int limit) {
        return playerMedals.values().stream()
            .sorted((a, b) -> Integer.compare(b.getTotalMedals(), a.getTotalMedals()))
            .limit(limit)
            .toList();
    }
    
    public Map<UUID, PlayerPointData> getAllPlayerPoints() {
        return new HashMap<>(playerPoints);
    }
    
    public void clearPlayerData(UUID playerUUID) {
        // Remove from memory
        playerAwards.remove(playerUUID);
        playerMedals.remove(playerUUID);
        playerPoints.remove(playerUUID);
        
        // Delete files
        try {
            File awardFile = new File(dataFolder, "awards/" + playerUUID.toString() + ".json");
            if (awardFile.exists()) {
                awardFile.delete();
            }
            
            File medalFile = new File(dataFolder, "medals/" + playerUUID.toString() + ".json");
            if (medalFile.exists()) {
                medalFile.delete();
            }
            
            File pointFile = new File(dataFolder, "points/" + playerUUID.toString() + ".json");
            if (pointFile.exists()) {
                pointFile.delete();
            }
            
            logger.debug("Cleared all data for player: " + playerUUID);
        } catch (Exception e) {
            logger.warning("Failed to clear data for player " + playerUUID + ": " + e.getMessage());
        }
    }
    
    // Data classes
    public static class PlayerAwardData {
        private final UUID playerUUID;
        private final String playerName;
        private final List<AwardEntry> awards;
        private final long lastUpdated;
        
        public PlayerAwardData(UUID playerUUID, String playerName) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.awards = new ArrayList<>();
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public void addAward(AwardEntry award) {
            awards.add(award);
        }
        
        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public List<AwardEntry> getAwards() { return awards; }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    public static class AwardEntry {
        private final String awardId;
        private final String awardName;
        private final String tier;
        private final String medal;
        private final double points;
        private final long awardedAt;
        
        public AwardEntry(String awardId, String awardName, String tier, String medal, double points, long awardedAt) {
            this.awardId = awardId;
            this.awardName = awardName;
            this.tier = tier;
            this.medal = medal;
            this.points = points;
            this.awardedAt = awardedAt;
        }
        
        // Getters
        public String getAwardId() { return awardId; }
        public String getAwardName() { return awardName; }
        public String getTier() { return tier; }
        public String getMedal() { return medal; }
        public double getPoints() { return points; }
        public long getAwardedAt() { return awardedAt; }
    }
    
    public static class PlayerMedalData {
        private final UUID playerUUID;
        private final String playerName;
        private final int bronzeCount;
        private final int silverCount;
        private final int goldCount;
        private final int totalMedals;
        private final long lastUpdated;
        
        public PlayerMedalData(UUID playerUUID, String playerName, int bronzeCount, int silverCount, int goldCount, int totalMedals) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.bronzeCount = bronzeCount;
            this.silverCount = silverCount;
            this.goldCount = goldCount;
            this.totalMedals = totalMedals;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public int getBronzeCount() { return bronzeCount; }
        public int getSilverCount() { return silverCount; }
        public int getGoldCount() { return goldCount; }
        public int getTotalMedals() { return totalMedals; }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    public static class PlayerPointData {
        private final UUID playerUUID;
        private final String playerName;
        private final double totalPoints;
        private final long lastUpdated;
        
        public PlayerPointData(UUID playerUUID, String playerName, double totalPoints) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.totalPoints = totalPoints;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        // Getters
        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public double getTotalPoints() { return totalPoints; }
        public long getLastUpdated() { return lastUpdated; }
    }
} 