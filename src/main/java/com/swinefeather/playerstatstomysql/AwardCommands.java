package com.swinefeather.playerstatstomysql;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;

public class AwardCommands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final AwardManager awardManager;
    private final DatabaseManager databaseManager;
    private final SupabaseManager supabaseManager;

    public AwardCommands(Main plugin, AwardManager awardManager, DatabaseManager databaseManager, SupabaseManager supabaseManager) {
        this.plugin = plugin;
        this.awardManager = awardManager;
        this.databaseManager = databaseManager;
        this.supabaseManager = supabaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "leaderboard":
            case "top":
                handleLeaderboard(sender, args);
                break;
            case "stats":
            case "player":
                handlePlayerStats(sender, args);
                break;
            case "awards":
            case "medals":
                handlePlayerAwards(sender, args);
                break;
            case "recalculate":
                handleRecalculate(sender, args);
                break;
            case "info":
                handleAwardInfo(sender, args);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== PlayerStats Awards Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/awards leaderboard [category] [page]" + ChatColor.WHITE + " - View leaderboards");
        sender.sendMessage(ChatColor.YELLOW + "/awards stats <player>" + ChatColor.WHITE + " - View player statistics");
        sender.sendMessage(ChatColor.YELLOW + "/awards medals <player>" + ChatColor.WHITE + " - View player awards");
        sender.sendMessage(ChatColor.YELLOW + "/awards info <award>" + ChatColor.WHITE + " - View award information");
        sender.sendMessage(ChatColor.YELLOW + "/awards recalculate <player>" + ChatColor.WHITE + " - Recalculate player awards");
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstatstomysql.awards.leaderboard")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view leaderboards!");
            return;
        }
        String category = args.length > 1 ? args[1] : "total";
        int page = args.length > 2 ? Math.max(1, Integer.parseInt(args[2])) : 1;
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            CompletableFuture.runAsync(() -> {
                String result;
                if (category.equals("total")) {
                    // Use player_points table for total points leaderboard
                    result = supabaseManager.rawGet("/rest/v1/player_points?select=player_uuid,total_points&order=total_points.desc.nullslast&limit=10");
                } else {
                    // Use player_stats table for stat-based leaderboard
                    result = supabaseManager.getLeaderboard(category, 10);
                }
                
                // Parse JSON and display formatted leaderboard
                try {
                    // Create a lenient JsonReader to handle malformed JSON
                    JsonReader reader = new JsonReader(new StringReader(result));
                    reader.setLenient(true);
                    com.google.gson.JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                    sender.sendMessage(ChatColor.GOLD + "=== Supabase Leaderboard for " + category + " ===");
                    int rank = 1;
                    for (com.google.gson.JsonElement el : arr) {
                        com.google.gson.JsonObject obj = el.getAsJsonObject();
                        String playerUuid = obj.has("player_uuid") ? obj.get("player_uuid").getAsString() : "?";
                        String name = "Unknown";
                        
                        // Get player name from UUID
                        if (!playerUuid.equals("?")) {
                            try {
                                UUID uuid = UUID.fromString(playerUuid);
                                org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                                name = offline.getName() != null ? offline.getName() : playerUuid.substring(0, 8);
                            } catch (Exception e) {
                                name = playerUuid.substring(0, 8);
                            }
                        }
                        
                        double points = 0;
                        if (category.equals("total")) {
                            points = obj.has("total_points") ? obj.get("total_points").getAsDouble() : 0.0;
                        } else {
                            // For stat-based leaderboards, the value is in the stats JSON
                            String statValue = obj.has(category) ? obj.get(category).getAsString() : "0";
                            try {
                                points = Double.parseDouble(statValue);
                            } catch (NumberFormatException e) {
                                points = 0.0;
                            }
                        }
                        
                        sender.sendMessage(ChatColor.YELLOW + "#" + rank + ChatColor.WHITE + ": " + ChatColor.AQUA + name + ChatColor.GRAY + " | " + ChatColor.GREEN + String.format("%.1f", points) + " pts");
                        rank++;
                    }
                    if (arr.size() == 0) {
                        sender.sendMessage(ChatColor.RED + "No leaderboard data found in Supabase.");
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error parsing Supabase leaderboard: " + e.getMessage());
                    plugin.getLogger().warning("Raw Supabase response: " + result);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    List<LeaderboardEntry> entries = getLeaderboard(category, page);
                    displayLeaderboard(sender, category, page, entries);
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error loading leaderboard: " + e.getMessage());
                    plugin.getLogger().warning("Error loading leaderboard: " + e.getMessage());
                }
            });
        }
    }

    private void handlePlayerStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstatstomysql.awards.stats")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view player statistics!");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards stats <player>");
            return;
        }
        String playerName = args[1];
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            sender.sendMessage(ChatColor.GREEN + "Getting stats for online player: " + playerName);
            // Show in-memory stats from AwardManager
            awardManager.showPlayerAwards(sender, playerName);
            return;
        }
        // Player is offline, try to get from Supabase if enabled
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            CompletableFuture.runAsync(() -> {
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(playerName);
                } catch (Exception ignored) {
                    org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
                    if (offline != null) uuid = offline.getUniqueId();
                }
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                    return;
                }
                
                // Get points from player_points table
                String pointsResult = supabaseManager.rawGet("/rest/v1/player_points?player_uuid=eq." + uuid + "&select=*");
                // Get medals from player_medals table
                String medalsResult = supabaseManager.rawGet("/rest/v1/player_medals?player_uuid=eq." + uuid + "&select=*");
                
                try {
                    // Parse points data
                    JsonReader pointsReader = new JsonReader(new StringReader(pointsResult));
                    pointsReader.setLenient(true);
                    com.google.gson.JsonArray pointsArr = JsonParser.parseReader(pointsReader).getAsJsonArray();
                    
                    // Parse medals data
                    JsonReader medalsReader = new JsonReader(new StringReader(medalsResult));
                    medalsReader.setLenient(true);
                    com.google.gson.JsonArray medalsArr = JsonParser.parseReader(medalsReader).getAsJsonArray();
                    
                    double points = 0.0;
                    int gold = 0, silver = 0, bronze = 0;
                    
                    if (pointsArr.size() > 0) {
                        com.google.gson.JsonObject pointsObj = pointsArr.get(0).getAsJsonObject();
                        points = pointsObj.has("total_points") ? pointsObj.get("total_points").getAsDouble() : 0.0;
                    }
                    
                    if (medalsArr.size() > 0) {
                        com.google.gson.JsonObject medalsObj = medalsArr.get(0).getAsJsonObject();
                        gold = medalsObj.has("gold_count") ? medalsObj.get("gold_count").getAsInt() : 0;
                        silver = medalsObj.has("silver_count") ? medalsObj.get("silver_count").getAsInt() : 0;
                        bronze = medalsObj.has("bronze_count") ? medalsObj.get("bronze_count").getAsInt() : 0;
                    }
                    
                    if (points == 0.0 && gold == 0 && silver == 0 && bronze == 0) {
                        sender.sendMessage(ChatColor.RED + "No stats found for " + playerName + ".");
                        return;
                    }
                    
                    sender.sendMessage(ChatColor.GOLD + "=== Supabase Stats for " + playerName + " ===");
                    sender.sendMessage(ChatColor.GREEN + "Points: " + String.format("%.1f", points));
                    sender.sendMessage(ChatColor.GOLD + "Gold Medals: " + gold);
                    sender.sendMessage(ChatColor.GRAY + "Silver Medals: " + silver);
                    sender.sendMessage(ChatColor.YELLOW + "Bronze Medals: " + bronze);
                    
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error parsing Supabase stats: " + e.getMessage());
                    plugin.getLogger().warning("Raw Supabase points response: " + pointsResult);
                    plugin.getLogger().warning("Raw Supabase medals response: " + medalsResult);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    PlayerStats stats = getPlayerStats(playerName);
                    if (stats != null) {
                        displayPlayerStats(sender, playerName, stats);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                        sender.sendMessage(ChatColor.YELLOW + "Note: Player must be online to view detailed stats, or data must exist in database.");
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error loading player stats: " + e.getMessage());
                    plugin.getLogger().warning("Error loading player stats: " + e.getMessage());
                }
            });
        }
    }

    private void handlePlayerAwards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstatstomysql.awards.medals")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view player awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards medals <player>");
            return;
        }

        String playerName = args[1];
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            sender.sendMessage(ChatColor.GREEN + "Getting awards for online player: " + playerName);
            // Show in-memory awards from AwardManager
            awardManager.showPlayerAwards(sender, playerName);
            return;
        }
        
        // Player is offline, try to get from Supabase if enabled
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            CompletableFuture.runAsync(() -> {
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(playerName);
                } catch (Exception ignored) {
                    org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
                    if (offline != null) uuid = offline.getUniqueId();
                }
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + playerName);
                    return;
                }
                
                // Get player awards from Supabase
                String result = supabaseManager.rawGet("/rest/v1/player_awards?player_uuid=eq." + uuid + "&select=*&order=achieved_at.desc");
                try {
                    // Create a lenient JsonReader to handle malformed JSON
                    JsonReader reader = new JsonReader(new StringReader(result));
                    reader.setLenient(true);
                    com.google.gson.JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                    if (arr.size() == 0) {
                        sender.sendMessage(ChatColor.RED + "No awards found for " + playerName + ".");
                        return;
                    }
                    
                    sender.sendMessage(ChatColor.GOLD + "=== Supabase Awards for " + playerName + " ===");
                    for (com.google.gson.JsonElement el : arr) {
                        com.google.gson.JsonObject obj = el.getAsJsonObject();
                        String awardName = obj.has("award_name") ? obj.get("award_name").getAsString() : "Unknown";
                        String medal = obj.has("medal") ? obj.get("medal").getAsString() : "Unknown";
                        double points = obj.has("points") ? obj.get("points").getAsDouble() : 0.0;
                        String medalColor = medal.equals("gold") ? "§6" : medal.equals("silver") ? "§7" : "§c";
                        
                        sender.sendMessage(ChatColor.AQUA + awardName + ChatColor.GRAY + " - " + medalColor + medal.toUpperCase() + ChatColor.GRAY + " - " + ChatColor.GREEN + String.format("%.1f", points) + " points");
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error parsing Supabase awards: " + e.getMessage());
                    plugin.getLogger().warning("Raw Supabase response: " + result);
                }
            });
        } else {
            CompletableFuture.runAsync(() -> {
                try {
                    PlayerAwards awards = getPlayerAwards(playerName);
                    if (awards != null) {
                        displayPlayerAwards(sender, playerName, awards);
                    } else {
                        sender.sendMessage(ChatColor.RED + "No awards found for " + playerName + ".");
                        sender.sendMessage(ChatColor.YELLOW + "Note: Player must be online to view awards, or data must exist in database.");
                    }
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error loading player awards: " + e.getMessage());
                    plugin.getLogger().warning("Error loading player awards: " + e.getMessage());
                }
            });
        }
    }

    private void handleRecalculate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstatstomysql.awards.recalculate")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to recalculate awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards recalculate <player>");
            return;
        }

        String playerName = args[1];
        Player player = Bukkit.getPlayer(playerName);
        
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online to recalculate awards");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                awardManager.calculateAndSaveAwards(player);
                sender.sendMessage(ChatColor.GREEN + "Awards recalculated for " + playerName);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error recalculating awards: " + e.getMessage());
                plugin.getLogger().warning("Error recalculating awards: " + e.getMessage());
            }
        });
    }

    private void handleAwardInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstatstomysql.awards.info")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view award information!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards info <award>");
            return;
        }

        String awardName = args[1];
        AwardManager.AwardInfo info = awardManager.getAwardInfo(awardName);
        
        if (info != null) {
            displayAwardInfo(sender, info);
        } else {
            sender.sendMessage(ChatColor.RED + "Award not found: " + awardName);
        }
    }

    private List<LeaderboardEntry> getLeaderboard(String category, int page) throws Exception {
        List<LeaderboardEntry> entries = new ArrayList<>();
        
        // Use Supabase if available, otherwise fall back to MySQL
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            // For now, return empty list - we'll implement Supabase queries later
            plugin.getLogger().info("Supabase leaderboard queries not yet implemented");
            return entries;
        } else if (databaseManager != null && databaseManager.isConnected()) {
            // Fallback to MySQL
            int limit = 10;
            int offset = (page - 1) * limit;

            String sql = "SELECT player_name, total_points, gold_medals, silver_medals, bronze_medals, " +
                        "stone_tier, iron_tier, diamond_tier, last_updated " +
                        "FROM player_awards ORDER BY total_points DESC LIMIT ? OFFSET ?";

            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, limit);
                stmt.setInt(2, offset);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LeaderboardEntry entry = new LeaderboardEntry();
                        entry.playerName = rs.getString("player_name");
                        entry.totalPoints = rs.getInt("total_points");
                        entry.goldMedals = rs.getInt("gold_medals");
                        entry.silverMedals = rs.getInt("silver_medals");
                        entry.bronzeMedals = rs.getInt("bronze_medals");
                        entry.stoneTier = rs.getInt("stone_tier");
                        entry.ironTier = rs.getInt("iron_tier");
                        entry.diamondTier = rs.getInt("diamond_tier");
                        entry.lastUpdated = rs.getTimestamp("last_updated");
                        entries.add(entry);
                    }
                }
            }
        } else {
            throw new Exception("No database connection available");
        }

        return entries;
    }

    private PlayerStats getPlayerStats(String playerName) throws Exception {
        // Use Supabase if available, otherwise fall back to MySQL
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            // For now, return null - we'll implement Supabase queries later
            plugin.getLogger().info("Supabase player stats queries not yet implemented");
            return null;
        } else if (databaseManager != null && databaseManager.isConnected()) {
            // Fallback to MySQL
            String sql = "SELECT stats FROM player_stats WHERE player_name = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String statsJson = rs.getString("stats");
                        return PlayerStats.fromJson(statsJson);
                    }
                }
            }
        } else {
            throw new Exception("No database connection available");
        }

        return null;
    }

    private PlayerAwards getPlayerAwards(String playerName) throws Exception {
        // Use Supabase if available, otherwise fall back to MySQL
        if (supabaseManager != null && supabaseManager.isEnabled()) {
            // For now, return null - we'll implement Supabase queries later
            plugin.getLogger().info("Supabase player awards queries not yet implemented");
            return null;
        } else if (databaseManager != null && databaseManager.isConnected()) {
            // Fallback to MySQL
            String sql = "SELECT * FROM player_awards WHERE player_name = ?";
            
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PlayerAwards awards = new PlayerAwards();
                        awards.playerName = rs.getString("player_name");
                        awards.totalPoints = rs.getInt("total_points");
                        awards.goldMedals = rs.getInt("gold_medals");
                        awards.silverMedals = rs.getInt("silver_medals");
                        awards.bronzeMedals = rs.getInt("bronze_medals");
                        awards.stoneTier = rs.getInt("stone_tier");
                        awards.ironTier = rs.getInt("iron_tier");
                        awards.diamondTier = rs.getInt("diamond_tier");
                        awards.lastUpdated = rs.getTimestamp("last_updated");
                        return awards;
                    }
                }
            }
        } else {
            throw new Exception("No database connection available");
        }

        return null;
    }

    private void displayLeaderboard(CommandSender sender, String category, int page, List<LeaderboardEntry> entries) {
        sender.sendMessage(ChatColor.GOLD + "=== Leaderboard (Page " + page + ") ===");
        
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No entries found.");
            return;
        }

        int rank = (page - 1) * 10 + 1;
        for (LeaderboardEntry entry : entries) {
            String rankColor = rank <= 3 ? ChatColor.GOLD.toString() : 
                              rank <= 10 ? ChatColor.YELLOW.toString() : ChatColor.WHITE.toString();
            
            sender.sendMessage(rankColor + "#" + rank + " " + entry.playerName);
            sender.sendMessage(ChatColor.GRAY + "  Points: " + ChatColor.GREEN + entry.totalPoints);
            sender.sendMessage(ChatColor.GRAY + "  Medals: " + 
                             ChatColor.GOLD + entry.goldMedals + "G " +
                             ChatColor.GRAY + entry.silverMedals + "S " +
                             ChatColor.YELLOW + entry.bronzeMedals + "B");
            sender.sendMessage(ChatColor.GRAY + "  Tiers: " + 
                             ChatColor.GREEN + entry.stoneTier + "S " +
                             ChatColor.AQUA + entry.ironTier + "I " +
                             ChatColor.LIGHT_PURPLE + entry.diamondTier + "D");
            sender.sendMessage("");
            rank++;
        }
    }

    private void displayPlayerStats(CommandSender sender, String playerName, PlayerStats stats) {
        sender.sendMessage(ChatColor.GOLD + "=== " + playerName + "'s Statistics ===");
        
        // Combat stats
        sender.sendMessage(ChatColor.RED + "Combat:");
        sender.sendMessage(ChatColor.GRAY + "  Kills: " + ChatColor.WHITE + stats.getCombat().getKills());
        sender.sendMessage(ChatColor.GRAY + "  Deaths: " + ChatColor.WHITE + stats.getCombat().getDeaths());
        sender.sendMessage(ChatColor.GRAY + "  K/D Ratio: " + ChatColor.WHITE + 
                          String.format("%.2f", stats.getCombat().getKDRatio()));
        
        // Movement stats
        sender.sendMessage(ChatColor.GREEN + "Movement:");
        sender.sendMessage(ChatColor.GRAY + "  Distance Walked: " + ChatColor.WHITE + 
                          String.format("%.1f", stats.getMovement().getDistanceWalked()) + " blocks");
        sender.sendMessage(ChatColor.GRAY + "  Distance Sprinted: " + ChatColor.WHITE + 
                          String.format("%.1f", stats.getMovement().getDistanceSprinted()) + " blocks");
        sender.sendMessage(ChatColor.GRAY + "  Distance Swum: " + ChatColor.WHITE + 
                          String.format("%.1f", stats.getMovement().getDistanceSwum()) + " blocks");
        
        // Mining stats
        sender.sendMessage(ChatColor.DARK_GRAY + "Mining:");
        sender.sendMessage(ChatColor.GRAY + "  Blocks Mined: " + ChatColor.WHITE + stats.getMining().getBlocksMined());
        sender.sendMessage(ChatColor.GRAY + "  Diamonds Mined: " + ChatColor.WHITE + stats.getMining().getDiamondsMined());
        sender.sendMessage(ChatColor.GRAY + "  Iron Mined: " + ChatColor.WHITE + stats.getMining().getIronMined());
        
        // Time stats
        sender.sendMessage(ChatColor.BLUE + "Time:");
        sender.sendMessage(ChatColor.GRAY + "  Playtime: " + ChatColor.WHITE + 
                          formatPlaytime(stats.getTime().getPlaytime()));
        sender.sendMessage(ChatColor.GRAY + "  Days Played: " + ChatColor.WHITE + stats.getTime().getDaysPlayed());
    }

    private void displayPlayerAwards(CommandSender sender, String playerName, PlayerAwards awards) {
        sender.sendMessage(ChatColor.GOLD + "=== " + playerName + "'s Awards ===");
        sender.sendMessage(ChatColor.GREEN + "Total Points: " + ChatColor.WHITE + awards.totalPoints);
        
        sender.sendMessage(ChatColor.GOLD + "Medals:");
        sender.sendMessage(ChatColor.GRAY + "  Gold: " + ChatColor.GOLD + awards.goldMedals);
        sender.sendMessage(ChatColor.GRAY + "  Silver: " + ChatColor.GRAY + awards.silverMedals);
        sender.sendMessage(ChatColor.GRAY + "  Bronze: " + ChatColor.YELLOW + awards.bronzeMedals);
        
        sender.sendMessage(ChatColor.AQUA + "Tiers:");
        sender.sendMessage(ChatColor.GRAY + "  Stone: " + ChatColor.GREEN + awards.stoneTier);
        sender.sendMessage(ChatColor.GRAY + "  Iron: " + ChatColor.AQUA + awards.ironTier);
        sender.sendMessage(ChatColor.GRAY + "  Diamond: " + ChatColor.LIGHT_PURPLE + awards.diamondTier);
        
        sender.sendMessage(ChatColor.GRAY + "Last Updated: " + ChatColor.WHITE + 
                          (awards.lastUpdated != null ? awards.lastUpdated.toString() : "Never"));
    }

    private void displayAwardInfo(CommandSender sender, AwardManager.AwardInfo info) {
        sender.sendMessage(ChatColor.GOLD + "=== " + info.name + " ===");
        sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + info.description);
        sender.sendMessage(ChatColor.GRAY + "Category: " + ChatColor.WHITE + info.category);
        sender.sendMessage(ChatColor.GRAY + "Points: " + ChatColor.GREEN + info.points);
        sender.sendMessage(ChatColor.GRAY + "Medal: " + getMedalColor(info.medal) + info.medal);
        sender.sendMessage(ChatColor.GRAY + "Tier: " + getTierColor(info.tier) + info.tier);
        sender.sendMessage(ChatColor.GRAY + "Requirement: " + ChatColor.WHITE + info.requirement);
    }

    private String getMedalColor(String medal) {
        switch (medal.toLowerCase()) {
            case "gold": return ChatColor.GOLD.toString();
            case "silver": return ChatColor.GRAY.toString();
            case "bronze": return ChatColor.YELLOW.toString();
            default: return ChatColor.WHITE.toString();
        }
    }

    private String getTierColor(String tier) {
        switch (tier.toLowerCase()) {
            case "stone": return ChatColor.GREEN.toString();
            case "iron": return ChatColor.AQUA.toString();
            case "diamond": return ChatColor.LIGHT_PURPLE.toString();
            default: return ChatColor.WHITE.toString();
        }
    }

    private String formatPlaytime(long playtime) {
        long hours = playtime / 3600;
        long minutes = (playtime % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("leaderboard", "top", "stats", "player", "awards", "medals", "recalculate", "info"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "leaderboard":
                case "top":
                    completions.addAll(Arrays.asList("total", "combat", "mining", "movement", "time"));
                    break;
                case "stats":
                case "player":
                case "medals":
                case "awards":
                case "recalculate":
                    // Add online players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                    break;
                case "info":
                    completions.addAll(awardManager.getAllAwardNames());
                    break;
            }
        }
        
        return completions;
    }

    // Data classes
    public static class LeaderboardEntry {
        public String playerName;
        public int totalPoints;
        public int goldMedals;
        public int silverMedals;
        public int bronzeMedals;
        public int stoneTier;
        public int ironTier;
        public int diamondTier;
        public java.sql.Timestamp lastUpdated;
    }

    public static class PlayerAwards {
        public String playerName;
        public int totalPoints;
        public int goldMedals;
        public int silverMedals;
        public int bronzeMedals;
        public int stoneTier;
        public int ironTier;
        public int diamondTier;
        public java.sql.Timestamp lastUpdated;
    }


} 