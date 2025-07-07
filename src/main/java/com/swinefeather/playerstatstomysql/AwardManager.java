package com.swinefeather.playerstatstomysql;

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
    private final Logger logger;
    private final Gson gson;
    private final SupabaseManager supabaseManager;
    private final WebhookManager webhookManager;
    
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
    
    public AwardManager(Plugin plugin, SupabaseManager supabaseManager, WebhookManager webhookManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        this.supabaseManager = supabaseManager;
        this.webhookManager = webhookManager;
    }
    
    public boolean initialize(ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            logger.info("Award system is disabled in configuration");
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
        
        logger.info("Award system initialized with " + awards.size() + " awards");
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
        if (!enabled) return;
        
        logger.info("Calculating awards for " + allPlayerStats.size() + " players");
        
        // Clear previous rankings
        awardRankings.clear();
        playerAwards.clear();
        
        // Calculate rankings for each award
        for (AwardDefinition award : awards.values()) {
            if (!award.isEnabled()) continue;
            
            List<AwardRanking> rankings = calculateAwardRanking(award, allPlayerStats);
            awardRankings.put(award.getId(), rankings);
            
            // Assign medals and update player awards
            assignMedals(award, rankings);
        }
        
        // Update player total points
        updatePlayerTotalPoints();
        
        // Sync to Supabase
        syncAwardsToSupabase(allPlayerStats);
        
        // Send notifications
        if (medalChangeNotifications) {
            sendMedalChangeNotifications();
        }
        
        // Send webhook notifications
        if (webhookManager != null && webhookManager.isEnabled()) {
            sendWebhookNotifications();
        }
        
        logger.info("Award calculation completed");
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
    
    private Long getStatValue(Map<String, Object> stats, String statPath) {
        String[] pathParts = statPath.split("\\.");
        if (pathParts.length != 2) return null;
        
        String category = pathParts[0];
        String statName = pathParts[1];
        
        Object categoryData = stats.get(category);
        if (categoryData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categoryMap = (Map<String, Object>) categoryData;
            Object value = categoryMap.get(statName);
            
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            }
        }
        
        return null;
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
    
    private void updatePlayerTotalPoints() {
        for (PlayerAwards playerAward : playerAwards.values()) {
            playerAward.calculateTotalPoints();
        }
    }
    
    private void syncAwardsToSupabase(Map<Player, Map<String, Object>> allPlayerStats) {
        if (supabaseManager == null || !supabaseManager.isEnabled()) return;
        
        for (Map.Entry<Player, Map<String, Object>> entry : allPlayerStats.entrySet()) {
            Player player = entry.getKey();
            Map<String, Object> stats = entry.getValue();
            PlayerAwards playerAward = playerAwards.get(player.getUniqueId());
            
            if (playerAward != null) {
                // Add awards data to stats
                Map<String, Object> enhancedStats = new HashMap<>(stats);
                enhancedStats.put("awards", playerAward.toMap());
                
                // Sync to Supabase
                supabaseManager.syncPlayerStats(player, enhancedStats);
            }
        }
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
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return;
        }
        
        PlayerAwards playerAward = playerAwards.get(player.getUniqueId());
        if (playerAward == null) {
            sender.sendMessage("§eNo awards found for " + playerName);
            return;
        }
        
        sender.sendMessage("§a=== Awards for " + playerName + " ===");
        sender.sendMessage(String.format("§7Total Points: §e%.1f", playerAward.getTotalPoints()));
        sender.sendMessage(String.format("§7Total Medals: §e%d", playerAward.getTotalMedals()));
        sender.sendMessage(String.format("§7Gold: §6%d §7Silver: §7%d §7Bronze: §c%d", 
            playerAward.getGoldMedals(), playerAward.getSilverMedals(), playerAward.getBronzeMedals()));
        
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
            return;
        }
        
        List<AwardRanking> rankings = awardRankings.get(awardId);
        if (rankings == null || rankings.isEmpty()) {
            sender.sendMessage("§eNo data available for " + award.getName());
            return;
        }
        
        sender.sendMessage("§a=== " + award.getName() + " Leaderboard ===");
        for (int i = 0; i < Math.min(rankings.size(), 10); i++) {
            AwardRanking ranking = rankings.get(i);
            String medal = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§7" + (i + 1);
            sender.sendMessage(String.format("%s §7%s: §e%d", 
                medal, ranking.getPlayerName(), ranking.getStatValue()));
        }
    }
    
    // Getters
    public boolean isEnabled() { return enabled; }
    public Map<String, AwardDefinition> getAwards() { return awards; }
    public Map<UUID, PlayerAwards> getPlayerAwards() { return playerAwards; }
    public String getWebsiteUrl() { return websiteUrl; }
    
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
} 