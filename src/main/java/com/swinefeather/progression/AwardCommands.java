package com.swinefeather.progression;

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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;

public class AwardCommands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final AwardManager awardManager;
    private final DatabaseManager databaseManager;
    private final SupabaseManager supabaseManager;
    private final LocalAwardStorage localAwardStorage;

    public AwardCommands(Main plugin, AwardManager awardManager, DatabaseManager databaseManager, SupabaseManager supabaseManager, LocalAwardStorage localAwardStorage) {
        this.plugin = plugin;
        this.awardManager = awardManager;
        this.databaseManager = databaseManager;
        this.supabaseManager = supabaseManager;
        this.localAwardStorage = localAwardStorage;
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
            case "list":
                handleListAwards(sender, args);
                break;
            case "recalculate":
                handleRecalculate(sender, args);
                break;
            case "sync":
                handleSync(sender, args);
                break;
            case "info":
                handleAwardInfo(sender, args);
                break;
            case "debug":
                handleDebug(sender, args);
                break;
            case "debugstats":
                handleDebugStats(sender, args);
                break;
            case "topvalues":
                handleTopValues(sender, args);
                break;
            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Awards Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/awards leaderboard <award> - Show leaderboard for specific award");
        sender.sendMessage(ChatColor.YELLOW + "/awards top - Show overall leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/awards player <name> - Show awards for specific player");
        sender.sendMessage(ChatColor.YELLOW + "/awards list - List all available awards");
        sender.sendMessage(ChatColor.YELLOW + "/awards recalculate <player|all> - Recalculate awards");
        sender.sendMessage(ChatColor.YELLOW + "/awards sync - Sync awards to database");
        sender.sendMessage(ChatColor.YELLOW + "/awards info <award> - Show award information");
        sender.sendMessage(ChatColor.YELLOW + "/awards debug <player> - Debug player stats");
        sender.sendMessage(ChatColor.YELLOW + "/awards debugstats <player> - Debug available stat keys for player");
        sender.sendMessage(ChatColor.YELLOW + "/awards topvalues - Show top values for each award");
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.leaderboard")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view leaderboards!");
            return;
        }
        
            String category = args.length > 1 ? args[1] : "total";
        
        // Use AwardManager for fresh leaderboard data (for all players, online and offline)
        if (awardManager != null && awardManager.isEnabled()) {
            if (category.equals("total")) {
                // Get fresh data from AwardManager
                Map<UUID, AwardManager.PlayerAwards> allAwards = awardManager.getPlayerAwards();
                
                if (allAwards.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No award data found. Run /awards recalculate all to calculate awards for all players.");
                    return;
                }
                
                // Sort by total points
                List<AwardManager.PlayerAwards> sortedAwards = allAwards.values().stream()
                    .sorted((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()))
                    .limit(10)
                    .collect(java.util.stream.Collectors.toList());
                
                sender.sendMessage(ChatColor.GOLD + "=== Fresh Leaderboard (Total Points) ===");
                
                for (int i = 0; i < sortedAwards.size(); i++) {
                    AwardManager.PlayerAwards playerAward = sortedAwards.get(i);
                    String rank = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§7" + (i + 1);
                    sender.sendMessage(String.format("%s §7%s: §e%.1f points", 
                        rank, playerAward.getPlayerName(), playerAward.getTotalPoints()));
                }
                return;
            } else {
                // Try to show specific award leaderboard
                awardManager.showAwardLeaderboard(sender, category);
                return;
            }
        }
        
        // Fallback to local storage if AwardManager is not available
        if (localAwardStorage != null) {
            if (category.equals("total")) {
                List<LocalAwardStorage.PlayerPointData> topPlayers = localAwardStorage.getTopPlayers(10);
                sender.sendMessage(ChatColor.GOLD + "=== Local Leaderboard (Total Points) ===");
                
                for (int i = 0; i < topPlayers.size(); i++) {
                    LocalAwardStorage.PlayerPointData player = topPlayers.get(i);
                    String rank = i == 0 ? "§6🥇" : i == 1 ? "§7🥈" : i == 2 ? "§c🥉" : "§7" + (i + 1);
                    sender.sendMessage(String.format("%s §7%s: §e%.1f points", 
                        rank, player.getPlayerName(), player.getTotalPoints()));
                }
                
                if (topPlayers.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No leaderboard data found in local storage.");
                }
                return;
            } else {
                // For specific award leaderboards, we'll need to implement this
                sender.sendMessage(ChatColor.RED + "Specific award leaderboards not yet implemented for local storage.");
                return;
            }
        }
        
        // Fallback to AwardManager if available
        if (awardManager != null && awardManager.isEnabled()) {
            if (category.equals("total")) {
                awardManager.showLeaderboard(sender);
                return;
            } else {
                // Try to show specific award leaderboard
                awardManager.showAwardLeaderboard(sender, category);
                return;
            }
        }
        
        // Fallback to Supabase/MySQL
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
        if (!sender.hasPermission("progression.awards.stats")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view player statistics!");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards stats <player>");
            return;
        }
        String playerName = args[1];
        
        // First try to get from local storage (for all players, online and offline)
        if (localAwardStorage != null) {
            // Try to find player by name in local storage
            for (Map.Entry<UUID, LocalAwardStorage.PlayerPointData> entry : localAwardStorage.getAllPlayerPoints().entrySet()) {
                LocalAwardStorage.PlayerPointData pointData = entry.getValue();
                if (pointData.getPlayerName().equalsIgnoreCase(playerName)) {
                    UUID playerUUID = entry.getKey();
                    LocalAwardStorage.PlayerMedalData medalData = localAwardStorage.getPlayerMedals(playerUUID);
                    LocalAwardStorage.PlayerAwardData awardData = localAwardStorage.getPlayerAwards(playerUUID);
                    
                    sender.sendMessage(ChatColor.GOLD + "=== Local Stats for " + playerName + " ===");
                    sender.sendMessage(ChatColor.GREEN + "Total Points: " + pointData.getTotalPoints());
                    
                    if (medalData != null) {
                        sender.sendMessage(ChatColor.GOLD + "Gold Medals: " + medalData.getGoldCount());
                        sender.sendMessage(ChatColor.GRAY + "Silver Medals: " + medalData.getSilverCount());
                        sender.sendMessage(ChatColor.YELLOW + "Bronze Medals: " + medalData.getBronzeCount());
                    }
                    
                    if (awardData != null && !awardData.getAwards().isEmpty()) {
                        sender.sendMessage(ChatColor.AQUA + "Individual Awards:");
                        for (LocalAwardStorage.AwardEntry award : awardData.getAwards()) {
                            String medalColor = award.getMedal().equalsIgnoreCase("gold") ? ChatColor.GOLD.toString() :
                                              award.getMedal().equalsIgnoreCase("silver") ? ChatColor.GRAY.toString() :
                                              ChatColor.YELLOW.toString();
                            sender.sendMessage(ChatColor.GRAY + "- " + medalColor + award.getMedal().toUpperCase() + 
                                             " " + award.getAwardName() + " (" + award.getPoints() + " points)");
                        }
                    }
                        return;
                    }
            }
        }
        
        // Fallback to online player check
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            sender.sendMessage(ChatColor.GREEN + "Getting stats for online player: " + playerName);
            if (awardManager != null && awardManager.isEnabled()) {
                awardManager.showPlayerAwards(sender, playerName);
        } else {
                sender.sendMessage(ChatColor.RED + "Award system is not enabled.");
            }
            return;
        }
        
        sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found in local storage or online.");
        sender.sendMessage(ChatColor.YELLOW + "Try running /sqlstats calculate_awards first to calculate awards for all players.");
    }

    private void handlePlayerAwards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.medals")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view player awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards medals <player>");
            return;
        }

        String playerName = args[1];
        
        // First try to get from local storage (for all players, online and offline)
        if (localAwardStorage != null) {
            // Try to find player by name in local storage
            for (Map.Entry<UUID, LocalAwardStorage.PlayerPointData> entry : localAwardStorage.getAllPlayerPoints().entrySet()) {
                LocalAwardStorage.PlayerPointData pointData = entry.getValue();
                if (pointData.getPlayerName().equalsIgnoreCase(playerName)) {
                    UUID playerUUID = entry.getKey();
                    LocalAwardStorage.PlayerMedalData medalData = localAwardStorage.getPlayerMedals(playerUUID);
                    LocalAwardStorage.PlayerAwardData awardData = localAwardStorage.getPlayerAwards(playerUUID);
                    
                    sender.sendMessage(ChatColor.GOLD + "=== Awards for " + playerName + " ===");
                    sender.sendMessage(ChatColor.GREEN + "Total Points: " + pointData.getTotalPoints());
                    
                    if (medalData != null) {
                        sender.sendMessage(ChatColor.GREEN + "Total Medals: " + medalData.getTotalMedals());
                        sender.sendMessage(ChatColor.GOLD + "Gold: " + medalData.getGoldCount() + 
                                         ChatColor.GRAY + " Silver: " + medalData.getSilverCount() + 
                                         ChatColor.YELLOW + " Bronze: " + medalData.getBronzeCount());
                    }
                    
                    if (awardData != null && !awardData.getAwards().isEmpty()) {
                        sender.sendMessage(ChatColor.AQUA + "Individual Medals:");
                        for (LocalAwardStorage.AwardEntry award : awardData.getAwards()) {
                            String medalColor = award.getMedal().equalsIgnoreCase("gold") ? ChatColor.GOLD.toString() :
                                              award.getMedal().equalsIgnoreCase("silver") ? ChatColor.GRAY.toString() :
                                              ChatColor.YELLOW.toString();
                            sender.sendMessage(ChatColor.GRAY + "- " + medalColor + award.getMedal().toUpperCase() + 
                                             " " + award.getAwardName() + " (" + award.getPoints() + " points)");
                        }
                    }
                    return;
                }
            }
        }
        
        // Fallback to online player check
        Player player = Bukkit.getPlayer(playerName);
        if (player != null) {
            sender.sendMessage(ChatColor.GREEN + "Getting awards for online player: " + playerName);
            if (awardManager != null && awardManager.isEnabled()) {
                awardManager.showPlayerAwards(sender, playerName);
            } else {
                sender.sendMessage(ChatColor.RED + "Award system is not enabled.");
            }
            return;
        }
        
        sender.sendMessage(ChatColor.RED + "Player " + playerName + " not found in local storage or online.");
            sender.sendMessage(ChatColor.YELLOW + "Try running /sqlstats calculate_awards first to calculate awards for all players.");
    }

    private void handleListAwards(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.list")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to list awards!");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "=== Available Awards ===");
        List<String> awardNames = awardManager.getAllAwardNames();
        if (awardNames.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No awards found.");
        } else {
            for (String awardName : awardNames) {
                sender.sendMessage(ChatColor.YELLOW + awardName);
            }
        }
    }

    private void handleRecalculate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.recalculate")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to recalculate awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards recalculate <player|all>");
            sender.sendMessage(ChatColor.YELLOW + "Note: 'all' processes ALL players (online and offline)");
            return;
        }
        
        // Add debug command
        if (args[1].equalsIgnoreCase("debug") && args.length >= 3) {
            String playerName = args[2];
            sender.sendMessage(ChatColor.GREEN + "Debugging stats for player: " + playerName);
            awardManager.debugPlayerStats(playerName);
            return;
        }

        String playerName = args[1];
        
        if (playerName.equalsIgnoreCase("all")) {
            // Recalculate for ALL players (online and offline)
            sender.sendMessage(ChatColor.GREEN + "Recalculating awards for ALL players (this may take a while)...");
            sender.sendMessage(ChatColor.YELLOW + "Online players will use their file stats to prevent conflicts.");
            
            CompletableFuture.runAsync(() -> {
                try {
                    // Use the AwardManager's method to calculate for offline players only
                    awardManager.calculateAwardsForAllPlayers();
                    
                    // Save all awards to local storage
                    for (Map.Entry<UUID, AwardManager.PlayerAwards> entry : awardManager.getPlayerAwards().entrySet()) {
                        awardManager.savePlayerAwardsToLocal(entry.getKey());
                    }
                    
                    sender.sendMessage(ChatColor.GREEN + "Awards recalculated for ALL players!");
                    sender.sendMessage(ChatColor.GRAY + "Processed " + awardManager.getPlayerAwards().size() + " total players");
                    sender.sendMessage(ChatColor.YELLOW + "Online players used file stats to prevent conflicts.");
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + "Error recalculating awards for all players: " + e.getMessage());
                    plugin.getLogger().warning("Error recalculating awards for all players: " + e.getMessage());
                }
            });
            return;
        }
        
        Player player = Bukkit.getPlayer(playerName);
        
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online to recalculate awards");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Load player stats from all worlds
                Map<String, Object> playerStats = new HashMap<>();
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    java.io.File statFile = new java.io.File(world.getWorldFolder(), "stats/" + player.getUniqueId() + ".json");
                    if (statFile.exists()) {
                        Map<String, Object> worldStats = awardManager.loadStatsFromFile(player.getUniqueId(), statFile);
                        playerStats.putAll(worldStats);
                    }
                }
                
                // Calculate awards for this player
                Map<Player, Map<String, Object>> allPlayerStats = new HashMap<>();
                allPlayerStats.put(player, playerStats);
                awardManager.calculateAllAwards(allPlayerStats);
                
                // Save to local storage
                awardManager.savePlayerAwardsToLocal(player.getUniqueId());
                
                sender.sendMessage(ChatColor.GREEN + "Awards recalculated for " + playerName);
                sender.sendMessage(ChatColor.GRAY + "Loaded " + playerStats.size() + " stat categories");
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error recalculating awards: " + e.getMessage());
                plugin.getLogger().warning("Error recalculating awards: " + e.getMessage());
            }
        });
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.debug")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to debug awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards debug <player>");
            return;
        }

        String playerName = args[1];
        Player player = Bukkit.getPlayer(playerName);
        
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player must be online to debug stats");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Get player stats from all worlds
                Map<String, Object> playerStats = new HashMap<>();
                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    java.io.File statFile = new java.io.File(world.getWorldFolder(), "stats/" + player.getUniqueId() + ".json");
                    if (statFile.exists()) {
                        Map<String, Object> worldStats = awardManager.loadStatsFromFile(player.getUniqueId(), statFile);
                        playerStats.putAll(worldStats);
                    }
                }
                
                // Show available stats
                sender.sendMessage(ChatColor.GREEN + "=== Available Stats for " + playerName + " ===");
                sender.sendMessage(ChatColor.YELLOW + "Total categories: " + playerStats.size());
                
                for (Map.Entry<String, Object> categoryEntry : playerStats.entrySet()) {
                    String category = categoryEntry.getKey();
                    Object categoryData = categoryEntry.getValue();
                    
                    if (categoryData instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> categoryMap = (Map<String, Object>) categoryData;
                        sender.sendMessage(ChatColor.AQUA + "Category: " + category + " (" + categoryMap.size() + " stats)");
                        
                        // Show first 10 stats in each category
                        int count = 0;
                        for (String statName : categoryMap.keySet()) {
                            if (count < 10) {
                                Object value = categoryMap.get(statName);
                                sender.sendMessage(ChatColor.GRAY + "  " + statName + ": " + value);
                                count++;
                            } else {
                                sender.sendMessage(ChatColor.GRAY + "  ... and " + (categoryMap.size() - 10) + " more");
                                break;
                            }
                        }
                    }
                }
                
                // Test some common stat paths
                sender.sendMessage(ChatColor.GREEN + "=== Testing Common Stat Paths ===");
                String[] testPaths = {
                    "killed.total", "killed_by.total", "custom.walk_one_cm", 
                    "mined.total", "used.total", "crafted.total"
                };
                
                for (String testPath : testPaths) {
                    Long value = awardManager.getStatResolver().resolveStatValue(playerStats, testPath);
                    if (value != null) {
                        sender.sendMessage(ChatColor.GREEN + "✓ " + testPath + ": " + value);
                    } else {
                        sender.sendMessage(ChatColor.RED + "✗ " + testPath + ": NOT FOUND");
                    }
                }
                
                // Test some specific stats that should exist
                sender.sendMessage(ChatColor.GREEN + "=== Testing Specific Stats ===");
                String[] specificPaths = {
                    "killed.skeleton", "killed_by.skeleton", "custom.jump",
                    "crafted.oak_slab", "used.bedrock"
                };
                
                for (String testPath : specificPaths) {
                    Long value = awardManager.getStatResolver().resolveStatValue(playerStats, testPath);
                    if (value != null) {
                        sender.sendMessage(ChatColor.GREEN + "✓ " + testPath + ": " + value);
                    } else {
                        sender.sendMessage(ChatColor.RED + "✗ " + testPath + ": NOT FOUND");
                    }
                }
                
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error debugging stats: " + e.getMessage());
                plugin.getLogger().warning("Error debugging stats: " + e.getMessage());
            }
        });
    }

    private void handleDebugStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards debugstats <player>");
            return;
        }
        String playerName = args[1];
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online: " + playerName);
            return;
        }
        if (awardManager == null || !awardManager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Award system is not enabled.");
            return;
        }
        // Get all available stat keys for this player
        Map<String, Object> playerStats = new HashMap<>();
        for (org.bukkit.World world : player.getServer().getWorlds()) {
            java.io.File statFile = new java.io.File(world.getWorldFolder(), "stats/" + player.getUniqueId() + ".json");
            if (statFile.exists()) {
                Map<String, Object> worldStats = awardManager.loadStatsFromFile(player.getUniqueId(), statFile);
                playerStats.putAll(worldStats);
            }
        }
        List<String> availableStats = awardManager.getStatResolver().getAvailableStats(playerStats);
        sender.sendMessage(ChatColor.GOLD + "=== Available Stat Keys for " + playerName + " ===");
        for (String stat : availableStats) {
            sender.sendMessage(ChatColor.GRAY + stat);
        }
    }

    private void handleTopValues(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.topvalues")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view top values!");
            return;
        }

        if (awardManager == null || !awardManager.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "Award system is not enabled.");
            return;
        }

        awardManager.debugTopValues(sender);
    }

    private void handleSync(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.sync")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to sync awards!");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /awards sync <type>");
            sender.sendMessage(ChatColor.GRAY + "Types: awards, medals, points, leaderboard, all");
            return;
        }

        String syncType = args[1].toLowerCase();
        
        CompletableFuture.runAsync(() -> {
            try {
                switch (syncType) {
                    case "awards":
                        awardManager.syncAllAwardsToSupabase();
                        sender.sendMessage(ChatColor.GREEN + "Syncing all awards to Supabase...");
                        break;
                    case "medals":
                        awardManager.syncAllMedalsToSupabase();
                        sender.sendMessage(ChatColor.GREEN + "Syncing all medals to Supabase...");
                        break;
                    case "points":
                        awardManager.syncAllPointsToSupabase();
                        sender.sendMessage(ChatColor.GREEN + "Syncing all points to Supabase...");
                        break;
                    case "leaderboard":
                        sender.sendMessage(ChatColor.YELLOW + "Leaderboard syncing to Supabase is disabled to prevent timeouts.");
                        sender.sendMessage(ChatColor.GRAY + "Leaderboards are handled locally for Minecraft. Supabase is for website only.");
                        break;
                    case "all":
                        awardManager.syncAllAwardsToSupabase();
                        awardManager.syncAllMedalsToSupabase();
                        awardManager.syncAllPointsToSupabase();
                        // Note: syncAllLeaderboardToSupabase() is disabled to prevent timeouts
                        sender.sendMessage(ChatColor.GREEN + "Syncing all award data to Supabase (except leaderboards)...");
                        break;
                    default:
                        sender.sendMessage(ChatColor.RED + "Unknown sync type: " + syncType);
                        sender.sendMessage(ChatColor.GRAY + "Available types: awards, medals, points, all");
                        sender.sendMessage(ChatColor.YELLOW + "Note: leaderboard syncing is disabled to prevent timeouts");
                        break;
                }
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Error during sync: " + e.getMessage());
                plugin.getLogger().warning("Error during sync: " + e.getMessage());
            }
        });
    }

    private void handleAwardInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.awards.info")) {
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
            try {
                int limit = 10;
                int offset = (page - 1) * limit;
                
                // Get leaderboard data from Supabase
                String leaderboardData = supabaseManager.getLeaderboard(category, limit + offset);
                if (leaderboardData != null && !leaderboardData.equals("[]")) {
                    // Parse JSON response
                    JsonArray jsonArray = JsonParser.parseString(leaderboardData).getAsJsonArray();
                    
                    // Skip to the correct page
                    int startIndex = offset;
                    int endIndex = Math.min(startIndex + limit, jsonArray.size());
                    
                    for (int i = startIndex; i < endIndex; i++) {
                        JsonObject entry = jsonArray.get(i).getAsJsonObject();
                        LeaderboardEntry leaderboardEntry = new LeaderboardEntry();
                        leaderboardEntry.playerName = entry.has("name") ? entry.get("name").getAsString() : "Unknown";
                        leaderboardEntry.totalPoints = entry.has("total_points") ? entry.get("total_points").getAsDouble() : 0.0;
                        leaderboardEntry.goldMedals = entry.has("gold_medals") ? entry.get("gold_medals").getAsInt() : 0;
                        leaderboardEntry.silverMedals = entry.has("silver_medals") ? entry.get("silver_medals").getAsInt() : 0;
                        leaderboardEntry.bronzeMedals = entry.has("bronze_medals") ? entry.get("bronze_medals").getAsInt() : 0;
                        leaderboardEntry.totalMedals = entry.has("total_medals") ? entry.get("total_medals").getAsInt() : 0;
                        entries.add(leaderboardEntry);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get leaderboard from Supabase: " + e.getMessage());
                // Fall back to empty list
            }
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
                        entry.totalPoints = rs.getDouble("total_points");
                        entry.goldMedals = rs.getInt("gold_medals");
                        entry.silverMedals = rs.getInt("silver_medals");
                        entry.bronzeMedals = rs.getInt("bronze_medals");
                        entry.totalMedals = entry.goldMedals + entry.silverMedals + entry.bronzeMedals;
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
            try {
                // First get the player UUID
                UUID playerUUID = null;
                org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                }
                
                if (playerUUID == null) {
                    return null;
                }
                
                // Get player stats from Supabase
                String statsData = supabaseManager.getPlayerStats(playerUUID);
                if (statsData != null && !statsData.equals("[]")) {
                    JsonArray jsonArray = JsonParser.parseString(statsData).getAsJsonArray();
                    if (jsonArray.size() > 0) {
                        JsonObject statsObject = jsonArray.get(0).getAsJsonObject();
                        if (statsObject.has("stats")) {
                            String statsJson = statsObject.get("stats").toString();
                            return PlayerStats.fromJson(statsJson);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get player stats from Supabase: " + e.getMessage());
                // Fall back to null
            }
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
            try {
                // First get the player UUID
                UUID playerUUID = null;
                org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
                if (offlinePlayer.hasPlayedBefore()) {
                    playerUUID = offlinePlayer.getUniqueId();
                }
                
                if (playerUUID == null) {
                    return null;
                }
                
                // Get player awards from Supabase
                String awardsData = supabaseManager.getPlayerAwards(playerUUID);
                if (awardsData != null && !awardsData.equals("[]")) {
                    JsonArray jsonArray = JsonParser.parseString(awardsData).getAsJsonArray();
                    if (jsonArray.size() > 0) {
                        PlayerAwards awards = new PlayerAwards();
                        awards.playerName = playerName;
                        awards.totalPoints = 0.0;
                        awards.goldMedals = 0;
                        awards.silverMedals = 0;
                        awards.bronzeMedals = 0;
                        
                        // Aggregate data from awards
                        for (int i = 0; i < jsonArray.size(); i++) {
                            JsonObject awardObject = jsonArray.get(i).getAsJsonObject();
                            if (awardObject.has("points")) {
                                awards.totalPoints += awardObject.get("points").getAsDouble();
                            }
                            if (awardObject.has("medal")) {
                                String medal = awardObject.get("medal").getAsString();
                                switch (medal.toLowerCase()) {
                                    case "gold":
                                        awards.goldMedals++;
                                        break;
                                    case "silver":
                                        awards.silverMedals++;
                                        break;
                                    case "bronze":
                                        awards.bronzeMedals++;
                                        break;
                                }
                            }
                        }
                        
                        return awards;
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get player awards from Supabase: " + e.getMessage());
                // Fall back to null
            }
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
            completions.addAll(Arrays.asList("leaderboard", "top", "list", "stats", "player", "awards", "medals", "recalculate", "info", "debug", "sync", "topvalues"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "leaderboard":
                case "top":
                    completions.add("total");
                    // Add actual award names from the config
                    if (awardManager != null && awardManager.isEnabled()) {
                        completions.addAll(awardManager.getAllAwardNames());
                    }
                    break;
                case "stats":
                case "player":
                case "medals":
                case "awards":
                case "recalculate":
                case "debug":
                case "debugstats":
                case "topvalues":
                    // Add online players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        completions.add(player.getName());
                    }
                    break;
                case "info":
                    if (awardManager != null && awardManager.isEnabled()) {
                        completions.addAll(awardManager.getAllAwardNames());
                    }
                    break;
            }
        }
        
        return completions;
    }

    // Data classes
    public static class LeaderboardEntry {
        public String playerName;
        public double totalPoints;
        public int goldMedals;
        public int silverMedals;
        public int bronzeMedals;
        public int totalMedals;
        public int stoneTier;
        public int ironTier;
        public int diamondTier;
        public java.sql.Timestamp lastUpdated;
    }

    public static class PlayerAwards {
        public String playerName;
        public double totalPoints;
        public int goldMedals;
        public int silverMedals;
        public int bronzeMedals;
        public int stoneTier;
        public int ironTier;
        public int diamondTier;
        public java.sql.Timestamp lastUpdated;
    }


} 