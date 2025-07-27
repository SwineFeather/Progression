package com.swinefeather.progression;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TownStatsCommands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final TownyManager townyManager;

    public TownStatsCommands(Main plugin) {
        this.plugin = plugin;
        this.townyManager = plugin.townyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!townyManager.isEnabled()) {
            sender.sendMessage("§cTowny integration is disabled!");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /townstats info <town>");
                    return true;
                }
                showTownInfo(sender, args[1]);
                break;
                
            case "level":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /townstats level <town>");
                    return true;
                }
                showTownLevel(sender, args[1]);
                break;
                
            case "achievements":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /townstats achievements <town>");
                    return true;
                }
                showTownAchievements(sender, args[1]);
                break;
                
            case "claim":
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /townstats claim <town> <achievement> <tier>");
                    return true;
                }
                claimTownAchievement(sender, args[1], args[2], Integer.parseInt(args[3]));
                break;
                
            case "leaderboard":
            case "top":
                showTownLeaderboard(sender);
                break;
                
            case "sync":
                if (sender.hasPermission("progression.towny.admin")) {
                    syncTowns(sender);
                } else {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                }
                break;
                
            case "debug":
                if (sender.hasPermission("progression.towny.admin")) {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /townstats debug <town>");
                        return true;
                    }
                    debugTownBalance(sender, args[1]);
                } else {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                }
                break;
                
            case "reset":
                if (sender.hasPermission("progression.towny.admin")) {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /townstats reset <town>");
                        return true;
                    }
                    resetTownStats(sender, args[1]);
                } else {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                }
                break;
                
            case "reload":
                if (sender.hasPermission("progression.towny.admin")) {
                    reloadTownyConfig(sender);
                } else {
                    sender.sendMessage("§cYou don't have permission to use this command!");
                }
                break;
                
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Town Stats Commands ===");
        sender.sendMessage("§e/townstats info <town> §7- Show town information");
        sender.sendMessage("§e/townstats level <town> §7- Show town level");
        sender.sendMessage("§e/townstats achievements <town> §7- Show town achievements");
        sender.sendMessage("§e/townstats claim <town> <achievement> <tier> §7- Claim town achievement");
        sender.sendMessage("§e/townstats leaderboard §7- Show town leaderboard");
        if (sender.hasPermission("progression.towny.admin")) {
            sender.sendMessage("§e/townstats sync §7- Sync all towns");
            sender.sendMessage("§e/townstats debug <town> §7- Debug town balance methods");
            sender.sendMessage("§e/townstats reset <town> §7- Reset town level and achievements");
            sender.sendMessage("§e/townstats reload §7- Reload Towny configuration");
        }
    }

    private void showTownInfo(CommandSender sender, String townName) {
        TownyManager.TownData townData = townyManager.getTownData(townName);
        if (townData == null) {
            sender.sendMessage("§cTown '" + townName + "' not found!");
            return;
        }

        Map<String, Object> stats = townData.getStats();
        
        sender.sendMessage("§6=== Town: " + townName + " ===");
        sender.sendMessage("§ePopulation: §f" + stats.getOrDefault("population", 0));
        sender.sendMessage("§eBalance: §f" + stats.getOrDefault("balance", 0.0));
        sender.sendMessage("§eNation: §f" + stats.getOrDefault("nation", "none"));
        sender.sendMessage("§ePlot Count: §f" + stats.getOrDefault("plot_count", 0));
        sender.sendMessage("§eSize: §f" + stats.getOrDefault("size", 0) + " chunks");
        sender.sendMessage("§eAge: §f" + stats.getOrDefault("age", 0) + " days");
        sender.sendMessage("§eMayor: §f" + stats.getOrDefault("mayor", "none"));
        
        // Show level info
        TownyManager.TownLevelData levelData = townyManager.getTownLevelData(townName);
        if (levelData != null) {
            sender.sendMessage("§eLevel: §f" + levelData.getLevel());
            sender.sendMessage("§eTotal XP: §f" + levelData.getTotalXP());
        }
    }

    private void showTownLevel(CommandSender sender, String townName) {
        TownyManager.TownLevelData levelData = townyManager.getTownLevelData(townName);
        if (levelData == null) {
            sender.sendMessage("§cTown '" + townName + "' not found or has no level data!");
            return;
        }

        int currentLevel = levelData.getLevel();
        int currentXP = levelData.getTotalXP();
        
        // Get level name from config
        String levelName = "Unknown";
        if (plugin.getConfig().contains("towny.leveling.levels." + currentLevel + ".name")) {
            levelName = plugin.getConfig().getString("towny.leveling.levels." + currentLevel + ".name");
        }
        
        sender.sendMessage("§6=== " + townName + " Level Info ===");
        sender.sendMessage("§eCurrent Level: §f" + currentLevel + " - " + levelName);
        sender.sendMessage("§eTotal XP: §f" + currentXP);
        
        // Calculate XP to next level
        int xpToNext = calculateXPToNextLevel(currentLevel, currentXP);
        if (xpToNext > 0) {
            sender.sendMessage("§eXP to Next Level: §f" + xpToNext);
        } else {
            sender.sendMessage("§eStatus: §aMaximum level reached!");
        }
    }

    private void showTownAchievements(CommandSender sender, String townName) {
        TownyManager.TownData townData = townyManager.getTownData(townName);
        if (townData == null) {
            sender.sendMessage("§cTown '" + townName + "' not found!");
            return;
        }

        Map<String, Object> townStats = townData.getStats();
        TownyManager.TownAchievementData achievementData = townyManager.getTownAchievementData(townName);
        if (achievementData == null) {
            achievementData = new TownyManager.TownAchievementData(townName);
        }

        sender.sendMessage("§6=== " + townName + " Achievement Progress ===");

        // Get achievements from config
        if (!plugin.getConfig().contains("towny.achievements")) {
            sender.sendMessage("§cNo town achievements configured!");
            return;
        }

        int totalAchievements = 0;
        int unlockedAchievements = 0;

        for (String achievementKey : plugin.getConfig().getConfigurationSection("towny.achievements").getKeys(false)) {
            if (achievementKey.equals("enabled")) continue;
            
            String achievementName = plugin.getConfig().getString("towny.achievements." + achievementKey + ".name", achievementKey);
            String statName = plugin.getConfig().getString("towny.achievements." + achievementKey + ".stat");
            
            if (statName == null) continue;
            
            totalAchievements++;
            
            // Get current stat value
            Object statValue = townStats.get(statName);
            int currentValue = 0;
            if (statValue instanceof Number) {
                currentValue = ((Number) statValue).intValue();
            }
            
            // Find next tier to unlock
            String nextTierName = null;
            int nextTierThreshold = 0;
            int nextTierPoints = 0;
            int nextTier = 0;
            
            if (plugin.getConfig().contains("towny.achievements." + achievementKey + ".tiers")) {
                for (String tierKey : plugin.getConfig().getConfigurationSection("towny.achievements." + achievementKey + ".tiers").getKeys(false)) {
                    int tier = Integer.parseInt(tierKey);
                    int threshold = plugin.getConfig().getInt("towny.achievements." + achievementKey + ".tiers." + tierKey + ".threshold", 0);
                    
                    if (!achievementData.hasUnlockedTier(achievementKey, tier)) {
                        nextTierName = plugin.getConfig().getString("towny.achievements." + achievementKey + ".tiers." + tierKey + ".name", "Tier " + tier);
                        nextTierThreshold = threshold;
                        nextTierPoints = plugin.getConfig().getInt("towny.achievements." + achievementKey + ".tiers." + tierKey + ".points", 0);
                        nextTier = tier;
                        break;
                    }
                }
            }
            
            if (nextTierName != null) {
                // Show progress for next tier
                int progress = Math.min(currentValue, nextTierThreshold);
                int percentage = (int) ((double) progress / nextTierThreshold * 100);
                
                String progressBar = createProgressBar(percentage);
                
                sender.sendMessage("§6🏆 " + ChatColor.WHITE + achievementName + 
                    ChatColor.GRAY + " - " + nextTierName);
                sender.sendMessage(ChatColor.GRAY + "  Progress: " + progressBar + " " + 
                    ChatColor.WHITE + progress + "/" + nextTierThreshold + " (" + percentage + "%)");
                
                if (progress >= nextTierThreshold) {
                    // Show clickable claim button
                    sendTownClaimButton(sender, townName, achievementKey, nextTier, nextTierName, nextTierPoints);
                } else {
                    sender.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.YELLOW + "In Progress");
                }
                sender.sendMessage("");
            } else {
                // All tiers unlocked
                unlockedAchievements++;
                sender.sendMessage(ChatColor.GREEN + "✅ " + ChatColor.WHITE + achievementName + 
                    ChatColor.GRAY + " - " + ChatColor.GREEN + "COMPLETED!");
                sender.sendMessage(ChatColor.GRAY + "  All tiers unlocked!");
                sender.sendMessage("");
            }
        }

        sender.sendMessage("§6=== Summary ===");
        sender.sendMessage(ChatColor.GREEN + "Total Achievements: " + ChatColor.WHITE + totalAchievements);
        sender.sendMessage(ChatColor.GREEN + "Completed: " + ChatColor.WHITE + unlockedAchievements);
    }

    private void claimTownAchievement(CommandSender sender, String townName, String achievementId, int tier) {
        if (!sender.hasPermission("progression.towny.admin")) {
            sender.sendMessage("§cYou don't have permission to claim town achievements!");
            return;
        }

        TownyManager.TownData townData = townyManager.getTownData(townName);
        if (townData == null) {
            sender.sendMessage("§cTown '" + townName + "' not found!");
            return;
        }

        Map<String, Object> townStats = townData.getStats();
        TownyManager.TownAchievementData achievementData = townyManager.getTownAchievementData(townName);
        if (achievementData == null) {
            achievementData = new TownyManager.TownAchievementData(townName);
        }

        // Check if already unlocked
        if (achievementData.hasUnlockedTier(achievementId, tier)) {
            sender.sendMessage("§cThis achievement tier has already been claimed!");
            return;
        }

        // Get achievement config
        String statName = plugin.getConfig().getString("towny.achievements." + achievementId + ".stat");
        if (statName == null) {
            sender.sendMessage("§cAchievement not found!");
            return;
        }

        int threshold = plugin.getConfig().getInt("towny.achievements." + achievementId + ".tiers." + tier + ".threshold", 0);
        String tierName = plugin.getConfig().getString("towny.achievements." + achievementId + ".tiers." + tier + ".name", "Tier " + tier);
        int points = plugin.getConfig().getInt("towny.achievements." + achievementId + ".tiers." + tier + ".points", 0);

        // Check if town meets threshold
        Object statValue = townStats.get(statName);
        int currentValue = 0;
        if (statValue instanceof Number) {
            currentValue = ((Number) statValue).intValue();
        }

        if (currentValue < threshold) {
            sender.sendMessage("§cTown has not reached the threshold for this achievement!");
            return;
        }

        // Unlock achievement
        achievementData.unlockTier(achievementId, tier, currentValue);
        
        // Award XP to town
        TownyManager.TownLevelData levelData = townyManager.getTownLevelData(townName);
        if (levelData != null) {
            levelData.addXP(points);
        }
        
        // Announce achievement
        if (plugin.getConfig().getBoolean("towny.notifications.achievements", true)) {
            plugin.getServer().broadcastMessage("§6[Towny] §e" + townName + " §ahas claimed achievement: §6" + tierName + "§a! (+" + points + " XP)");
        }
        
        sender.sendMessage("§aSuccessfully claimed " + tierName + " achievement for " + townName + " (+" + points + " XP)");
    }

    private void sendTownClaimButton(CommandSender sender, String townName, String achievementId, int tier, String tierName, int points) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.GREEN + "READY! (Use /townstats claim <town> <achievement> <tier> to claim)");
            return;
        }

        TextComponent claimButton = new TextComponent(ChatColor.GRAY + "  Status: " + ChatColor.GREEN + "READY! ");
        TextComponent clickToClaim = new TextComponent(ChatColor.GOLD + "[CLAIM]");
        
        clickToClaim.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
            "/townstats claim " + townName + " " + achievementId + " " + tier));
        clickToClaim.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.GREEN + "Click to claim " + tierName + " achievement!\n" + 
                           ChatColor.YELLOW + "Reward: " + ChatColor.GOLD + "+" + points + " XP").create()));
        
        claimButton.addExtra(clickToClaim);
        ((Player) sender).spigot().sendMessage(claimButton);
    }

    private String createProgressBar(int percentage) {
        int bars = 10;
        int filledBars = (int) ((percentage / 100.0) * bars);
        
        StringBuilder bar = new StringBuilder();
        bar.append(ChatColor.GREEN);
        for (int i = 0; i < filledBars; i++) {
            bar.append("█");
        }
        bar.append(ChatColor.GRAY);
        for (int i = filledBars; i < bars; i++) {
            bar.append("█");
        }
        return bar.toString();
    }

    private void showTownLeaderboard(CommandSender sender) {
        List<ProgressionAPI.TownLevelInfo> allLevels = townyManager.getAllTownLevels();
        if (allLevels.isEmpty()) {
            sender.sendMessage("§cNo town level data available!");
            return;
        }

        // Sort by total XP
        allLevels.sort((a, b) -> Long.compare(b.getTotalXP(), a.getTotalXP()));

        sender.sendMessage("§6=== Town Leaderboard ===");
        int rank = 1;
        for (ProgressionAPI.TownLevelInfo levelInfo : allLevels) {
            if (rank > 10) break; // Show top 10
            
            String townName = levelInfo.getTownName();
            int level = levelInfo.getLevel();
            long totalXP = levelInfo.getTotalXP();
            String levelTitle = levelInfo.getLevelTitle();
            
            sender.sendMessage("§e" + rank + ". §f" + townName + " §7- Level " + level + " (" + levelTitle + ") §8- " + totalXP + " XP");
            rank++;
        }
    }

    private void syncTowns(CommandSender sender) {
        sender.sendMessage("§aSyncing all towns...");
        townyManager.syncAllTowns();
        sender.sendMessage("§aTown sync completed!");
    }
    
    private void debugTownBalance(CommandSender sender, String townName) {
        sender.sendMessage("§6=== Debug Town Balance: " + townName + " ===");
        
        try {
            // Get Towny API classes
            Class<?> townyUniverseClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            
            // Get town object
            Object townyUniverse = townyUniverseClass.getMethod("getInstance").invoke(null);
            Object town = townyUniverseClass.getMethod("getTown", String.class).invoke(townyUniverse, townName);
            
            if (town == null) {
                sender.sendMessage("§cTown not found: " + townName);
                return;
            }
            
            sender.sendMessage("§eTesting different balance methods:");
            
            // Test getAccount()
            try {
                double balance1 = (Double) townClass.getMethod("getAccount").invoke(town);
                sender.sendMessage("§a✓ getAccount(): " + balance1);
            } catch (Exception e) {
                sender.sendMessage("§c✗ getAccount(): " + e.getMessage());
            }
            
            // Test getAccount().getHoldingBalance()
            try {
                Object account = townClass.getMethod("getAccount").invoke(town);
                if (account != null) {
                    double balance2 = (Double) account.getClass().getMethod("getHoldingBalance").invoke(account);
                    sender.sendMessage("§a✓ getAccount().getHoldingBalance(): " + balance2);
                } else {
                    sender.sendMessage("§c✗ getAccount() returned null");
                }
            } catch (Exception e) {
                sender.sendMessage("§c✗ getAccount().getHoldingBalance(): " + e.getMessage());
            }
            
            // Test getBalance()
            try {
                double balance3 = (Double) townClass.getMethod("getBalance").invoke(town);
                sender.sendMessage("§a✓ getBalance(): " + balance3);
            } catch (Exception e) {
                sender.sendMessage("§c✗ getBalance(): " + e.getMessage());
            }
            
            // Test getTreasury()
            try {
                Object treasury = townClass.getMethod("getTreasury").invoke(town);
                if (treasury != null) {
                    double balance4 = (Double) treasury.getClass().getMethod("getBalance").invoke(treasury);
                    sender.sendMessage("§a✓ getTreasury().getBalance(): " + balance4);
                } else {
                    sender.sendMessage("§c✗ getTreasury() returned null");
                }
            } catch (Exception e) {
                sender.sendMessage("§c✗ getTreasury().getBalance(): " + e.getMessage());
            }
            
            // Show current cached data
            TownyManager.TownData townData = townyManager.getTownData(townName);
            if (townData != null) {
                Map<String, Object> stats = townData.getStats();
                sender.sendMessage("§eCurrent cached balance: " + stats.getOrDefault("balance", "not found"));
            } else {
                sender.sendMessage("§cNo cached data found for this town");
            }
            
        } catch (Exception e) {
            sender.sendMessage("§cError debugging town balance: " + e.getMessage());
        }
    }

    private void resetTownStats(CommandSender sender, String townName) {
        if (!sender.hasPermission("progression.towny.admin")) {
            sender.sendMessage("§cYou don't have permission to reset town stats!");
            return;
        }

        // Check if town exists
        TownyManager.TownData townData = townyManager.getTownData(townName);
        if (townData == null) {
            sender.sendMessage("§cTown '" + townName + "' not found!");
            return;
        }

        // Reset level data
        TownyManager.TownLevelData levelData = townyManager.getTownLevelData(townName);
        if (levelData != null) {
            levelData.setLevel(1);
            levelData.setTotalXP(0);
        }

        // Reset achievement data
        TownyManager.TownAchievementData achievementData = townyManager.getTownAchievementData(townName);
        if (achievementData != null) {
            // Clear all unlocked tiers
            achievementData.clearAllUnlockedTiers();
        }

        // Sync to database if enabled
        if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
            plugin.levelDatabaseManager.syncTownLevel(townName, new TownyManager.TownLevelData(townName, 1, 0));
        }

        sender.sendMessage("§aReset " + townName + "'s level to 1 and cleared all achievements!");
        
        // Play sound for the command sender if they're a player
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void reloadTownyConfig(CommandSender sender) {
        sender.sendMessage("§aReloading Towny configuration...");
        plugin.reloadConfig();
        sender.sendMessage("§aConfiguration reloaded!");
    }

    private int calculateXPToNextLevel(int currentLevel, int currentXP) {
        int nextLevel = currentLevel + 1;
        if (plugin.getConfig().contains("towny.leveling.levels." + nextLevel + ".xp_required")) {
            int nextLevelXP = plugin.getConfig().getInt("towny.leveling.levels." + nextLevel + ".xp_required");
            return Math.max(0, nextLevelXP - currentXP);
        }
        return 0; // Max level reached
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("info");
            completions.add("level");
            completions.add("achievements");
            completions.add("claim");
            completions.add("leaderboard");
            completions.add("top");
            if (sender.hasPermission("progression.towny.admin")) {
                completions.add("sync");
                completions.add("debug");
                completions.add("reset");
                completions.add("reload");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("level") || 
                args[0].equalsIgnoreCase("achievements") || args[0].equalsIgnoreCase("claim") ||
                args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("debug")) {
                // Add town names to completion
                Map<String, TownyManager.TownData> allTowns = townyManager.getAllTownData();
                completions.addAll(allTowns.keySet());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("claim")) {
            // Add achievement IDs to completion
            if (plugin.getConfig().contains("towny.achievements")) {
                for (String achievementKey : plugin.getConfig().getConfigurationSection("towny.achievements").getKeys(false)) {
                    if (!achievementKey.equals("enabled")) {
                        completions.add(achievementKey);
                    }
                }
            }
        }

        // Filter completions based on what the user has typed
        completions.removeIf(s -> !s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()));
        return completions;
    }
} 