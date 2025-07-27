package com.swinefeather.progression;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LevelCommands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final LevelManager levelManager;
    private final AchievementManager achievementManager;

    public LevelCommands(Main plugin) {
        this.plugin = plugin;
        this.levelManager = plugin.levelManager;
        this.achievementManager = plugin.achievementManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                handleInfo(sender, args);
                break;
            case "leaderboard":
                handleLeaderboard(sender, args);
                break;
            case "top":
                handleTop(sender, args);
                break;
            case "recalculate":
                handleRecalculate(sender, args);
                break;
            case "addxp":
                handleAddXP(sender, args);
                break;
            case "setlevel":
                handleSetLevel(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "achievements":
                handleAchievements(sender, args);
                break;
            case "claim":
                handleClaim(sender, args);
                break;
            case "reload":
                handleReload(sender, args);
                break;
            case "help":
                showHelp(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown subcommand. Use /level help for available commands.");
                break;
        }

        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.info")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view level information!");
            return;
        }

        String targetName;
        if (args.length > 1) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            targetName = sender.getName();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /level info [player]");
            return;
        }

        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        LevelManager.PlayerLevelData levelData = levelManager.getPlayerLevelData(targetUUID);
        if (levelData == null) {
            sender.sendMessage(ChatColor.YELLOW + targetName + " has no level data yet.");
            return;
        }

        LevelManager.LevelDefinition levelDef = levelManager.getPlayerLevelDefinition(levelData.getLevel());
        int xpToNext = levelManager.getXPToNextLevel(targetUUID);

        sender.sendMessage(ChatColor.GOLD + "=== " + targetName + "'s Level Information ===");
        sender.sendMessage(ChatColor.GREEN + "Level: " + ChatColor.WHITE + levelData.getLevel());
        if (levelDef != null) {
            sender.sendMessage(ChatColor.GREEN + "Title: " + ChatColor.WHITE + levelDef.getTitle());
            sender.sendMessage(ChatColor.GREEN + "Description: " + ChatColor.WHITE + levelDef.getDescription());
        }
        sender.sendMessage(ChatColor.GREEN + "Total XP: " + ChatColor.WHITE + levelData.getTotalXP());
        
        if (xpToNext > 0) {
            sender.sendMessage(ChatColor.GREEN + "XP to Next Level: " + ChatColor.WHITE + xpToNext);
        } else {
            sender.sendMessage(ChatColor.GOLD + "Maximum level reached!");
        }

        // Show achievements
        if (achievementManager != null) {
            AchievementManager.PlayerAchievementData achievementData = achievementManager.getPlayerAchievementData(targetUUID);
            if (achievementData != null) {
                int totalAchievements = achievementData.getUnlockedTiers().values().stream()
                    .mapToInt(List::size).sum();
                sender.sendMessage(ChatColor.GREEN + "Achievements Unlocked: " + ChatColor.WHITE + totalAchievements);
            }
        }
    }

    private void handleLeaderboard(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.leaderboard")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view leaderboards!");
            return;
        }

        String type = args.length > 1 ? args[1].toLowerCase() : "level";
        int limit = args.length > 2 ? Math.min(20, Math.max(1, Integer.parseInt(args[2]))) : 10;

        sender.sendMessage(ChatColor.GOLD + "=== Top " + limit + " Players by " + type + " ===");

        List<LevelManager.PlayerLevelData> sortedPlayers = new ArrayList<>();
        for (LevelManager.PlayerLevelData data : levelManager.getAllPlayerLevelData()) {
            sortedPlayers.add(data);
        }

        if (type.equals("xp")) {
            sortedPlayers.sort((a, b) -> Integer.compare(b.getTotalXP(), a.getTotalXP()));
        } else {
            sortedPlayers.sort((a, b) -> Integer.compare(b.getLevel(), a.getLevel()));
        }

        for (int i = 0; i < Math.min(limit, sortedPlayers.size()); i++) {
            LevelManager.PlayerLevelData data = sortedPlayers.get(i);
            LevelManager.LevelDefinition levelDef = levelManager.getPlayerLevelDefinition(data.getLevel());
            String title = levelDef != null ? levelDef.getTitle() : "Unknown";
            
            if (type.equals("xp")) {
                sender.sendMessage(ChatColor.GOLD + "#" + (i + 1) + " " + ChatColor.WHITE + data.getPlayerName() + 
                    ChatColor.GRAY + " - " + ChatColor.GREEN + data.getTotalXP() + " XP " + 
                    ChatColor.GRAY + "(" + title + ")");
            } else {
                sender.sendMessage(ChatColor.GOLD + "#" + (i + 1) + " " + ChatColor.WHITE + data.getPlayerName() + 
                    ChatColor.GRAY + " - " + ChatColor.GREEN + "Level " + data.getLevel() + 
                    ChatColor.GRAY + " (" + title + ")");
            }
        }
    }

    private void handleTop(CommandSender sender, String[] args) {
        handleLeaderboard(sender, args);
    }

    private void handleRecalculate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.recalculate")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to recalculate levels!");
            return;
        }
        if (args.length > 1) {
            String targetName = args[1];
            UUID targetUUID = getPlayerUUID(targetName);
            if (targetUUID == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
                return;
            }
            recalculatePlayerLevel(targetUUID, targetName);
            sender.sendMessage(ChatColor.GREEN + "Recalculated level and achievements for " + targetName);
        } else {
            recalculateAllPlayerLevels();
            sender.sendMessage(ChatColor.GREEN + "Recalculated levels and achievements for all players.");
        }
    }

    private void handleAddXP(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.addxp")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to add XP!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /level addxp <player> <amount>");
            return;
        }

        String targetName = args[1];
        int xpAmount;
        try {
            xpAmount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid XP amount: " + args[2]);
            return;
        }

        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        levelManager.addPlayerXP(targetUUID, targetName, xpAmount);
        sender.sendMessage(ChatColor.GREEN + "Added " + xpAmount + " XP to " + targetName + "!");
        
        // Play sound for the command sender if they're a player
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.setlevel")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to set levels!");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /level setlevel <player> <level>");
            return;
        }

        String targetName = args[1];
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid level: " + args[2]);
            return;
        }

        if (level < 1 || level > 35) {
            sender.sendMessage(ChatColor.RED + "Level must be between 1 and 35!");
            return;
        }

        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        LevelManager.LevelDefinition levelDef = levelManager.getPlayerLevelDefinition(level);
        if (levelDef == null) {
            sender.sendMessage(ChatColor.RED + "Invalid level: " + level);
            return;
        }

        LevelManager.PlayerLevelData levelData = levelManager.getPlayerLevelData(targetUUID);
        if (levelData == null) {
            levelData = new LevelManager.PlayerLevelData(targetUUID, targetName, level, levelDef.getXpRequired());
        } else {
            levelData.setLevel(level);
            levelData.setTotalXP(levelDef.getXpRequired());
        }

        sender.sendMessage(ChatColor.GREEN + "Set " + targetName + "'s level to " + level + " (" + levelDef.getTitle() + ")!");
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.reset")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to reset levels!");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /level reset <player>");
            return;
        }

        String targetName = args[1];
        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        // Reset level data
        LevelManager.PlayerLevelData levelData = levelManager.getPlayerLevelData(targetUUID);
        if (levelData != null) {
            levelData.setLevel(1);
            levelData.setTotalXP(0);
        }

        // Reset achievement data
        if (achievementManager != null) {
            AchievementManager.PlayerAchievementData achievementData = achievementManager.getPlayerAchievementData(targetUUID);
            if (achievementData != null) {
                achievementData.clearAllUnlockedTiers();
                achievementManager.saveAllData();
            }
        }

        // Sync to database if enabled
        if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
            plugin.levelDatabaseManager.syncPlayerLevel(targetUUID, targetName, 1, 0);
        }

        sender.sendMessage(ChatColor.GREEN + "Reset " + targetName + "'s level to 1 and cleared all achievements!");
        
        // Play sound for the command sender if they're a player
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    private void handleAchievements(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.info")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to view achievement information!");
            return;
        }

        String targetName;
        if (args.length > 1) {
            targetName = args[1];
        } else if (sender instanceof Player) {
            targetName = sender.getName();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /level achievements [player]");
            return;
        }

        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        if (achievementManager == null) {
            sender.sendMessage(ChatColor.RED + "Achievement system is not available!");
            return;
        }

        // Get player stats to calculate progress
        Map<String, Object> playerStats = new HashMap<>();
        for (World world : plugin.getServer().getWorlds()) {
            File statsFolder = new File(world.getWorldFolder(), "stats");
            if (statsFolder.exists() && statsFolder.isDirectory()) {
                File statFile = new File(statsFolder, targetUUID + ".json");
                if (statFile.exists()) {
                    try (FileReader reader = new FileReader(statFile)) {
                        JSONObject root = (JSONObject) new JSONParser().parse(reader);
                        JSONObject stats = (JSONObject) root.get("stats");
                        if (stats != null) {
                            playerStats.putAll(stats);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to read stats for " + targetName + ": " + e.getMessage());
                    }
                }
            }
        }

        // Check and unlock achievements based on current stats
        if (achievementManager != null) {
            achievementManager.checkPlayerAchievements(targetUUID, targetName, playerStats);
        }

        AchievementManager.PlayerAchievementData achievementData = achievementManager.getPlayerAchievementData(targetUUID);
        if (achievementData == null) {
            achievementData = new AchievementManager.PlayerAchievementData(targetUUID, targetName);
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + targetName + "'s Achievement Progress ===");

        List<AchievementManager.AchievementDefinition> achievements = achievementManager.getAchievementDefinitions();
        int totalAchievements = 0;
        int unlockedAchievements = 0;

        for (AchievementManager.AchievementDefinition achievement : achievements) {
            // Only show player achievements
            if (!"player".equals(achievement.getType())) {
                continue;
            }

            totalAchievements++;
            
            // Find the next tier to unlock
            AchievementManager.AchievementTier nextTier = null;
            int currentValue = 0;
            
            // Use StatResolver to get current stat value
            Long statValue = plugin.achievementManager.statResolver.resolveStatValue(playerStats, achievement.getStat());
            if (statValue != null) {
                currentValue = statValue.intValue();
            }

            // Find next tier to unlock
            for (AchievementManager.AchievementTier tier : achievement.getTiers()) {
                if (!achievementData.hasUnlockedTier(achievement.getId(), tier.getTier())) {
                    nextTier = tier;
                    break;
                }
            }

            if (nextTier != null) {
                // Show progress for next tier
                int progress = Math.min(currentValue, nextTier.getThreshold());
                int percentage = (int) ((double) progress / nextTier.getThreshold() * 100);
                
                String progressBar = createProgressBar(percentage);
                
                sender.sendMessage(ChatColor.GOLD + nextTier.getIcon() + " " + ChatColor.WHITE + achievement.getName() + 
                    ChatColor.GRAY + " - " + nextTier.getName());
                sender.sendMessage(ChatColor.GRAY + "  " + nextTier.getDescription());
                sender.sendMessage(ChatColor.GRAY + "  Progress: " + progressBar + " " + 
                    ChatColor.WHITE + progress + "/" + nextTier.getThreshold() + " (" + percentage + "%)");
                
                if (progress >= nextTier.getThreshold()) {
                    // Show clickable claim button
                    sendClaimButton(sender, targetName, achievement.getId(), nextTier);
                } else {
                    sender.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.YELLOW + "In Progress");
                }
                sender.sendMessage("");
            } else {
                // All tiers unlocked
                unlockedAchievements++;
                sender.sendMessage(ChatColor.GREEN + "✅ " + ChatColor.WHITE + achievement.getName() + 
                    ChatColor.GRAY + " - " + ChatColor.GREEN + "COMPLETED!");
                sender.sendMessage(ChatColor.GRAY + "  All tiers unlocked!");
                sender.sendMessage("");
            }
        }

        sender.sendMessage(ChatColor.GOLD + "=== Summary ===");
        sender.sendMessage(ChatColor.GREEN + "Total Achievements: " + ChatColor.WHITE + totalAchievements);
        sender.sendMessage(ChatColor.GREEN + "Completed: " + ChatColor.WHITE + unlockedAchievements);
        sender.sendMessage(ChatColor.GREEN + "Remaining: " + ChatColor.WHITE + (totalAchievements - unlockedAchievements));
    }

    private String createProgressBar(int percentage) {
        int bars = 20; // More granular bar
        int filledBars = (int) Math.round((percentage / 100.0) * bars);
        StringBuilder progressBar = new StringBuilder();
        progressBar.append(ChatColor.GREEN);
        for (int i = 0; i < filledBars; i++) {
            progressBar.append("█");
        }
        progressBar.append(ChatColor.DARK_GRAY);
        for (int i = filledBars; i < bars; i++) {
            progressBar.append("█");
        }
        progressBar.append(ChatColor.RESET);
        return progressBar.toString();
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "==== Progression Level Commands ====");
        sender.sendMessage(ChatColor.YELLOW + "/level info [player]" + ChatColor.WHITE + " - View level information");
        sender.sendMessage(ChatColor.YELLOW + "/level leaderboard [type] [limit]" + ChatColor.WHITE + " - View leaderboard (level/xp)");
        sender.sendMessage(ChatColor.YELLOW + "/level top [type] [limit]" + ChatColor.WHITE + " - Alias for leaderboard");
        sender.sendMessage(ChatColor.YELLOW + "/level recalculate [player]" + ChatColor.WHITE + " - Recalculate levels for all or a player");
        sender.sendMessage(ChatColor.YELLOW + "/level addxp <player> <amount>" + ChatColor.WHITE + " - Add XP to player");
        sender.sendMessage(ChatColor.YELLOW + "/level setlevel <player> <level>" + ChatColor.WHITE + " - Set player level");
        sender.sendMessage(ChatColor.YELLOW + "/level reset <player>" + ChatColor.WHITE + " - Reset player level");
        sender.sendMessage(ChatColor.YELLOW + "/level achievements [player]" + ChatColor.WHITE + " - View achievement progress");
        sender.sendMessage(ChatColor.YELLOW + "/level claim <player> <achievement> <tier>" + ChatColor.WHITE + " - Claim an achievement");
        sender.sendMessage(ChatColor.YELLOW + "/level reload" + ChatColor.WHITE + " - Reload the config file");
        sender.sendMessage(ChatColor.YELLOW + "/level help" + ChatColor.WHITE + " - Show this help");
        sender.sendMessage(ChatColor.GOLD + "====================================");
    }

    private UUID getPlayerUUID(String playerName) {
        // Try online players first
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        // Try offline players
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    private void recalculatePlayerLevel(UUID playerUUID, String playerName) {
        // This would need to be implemented to recalculate XP from stats
        // For now, just trigger a stat sync for the player
        if (plugin.statSyncTask != null) {
            plugin.statSyncTask.syncSinglePlayer(playerUUID, null);
        }
    }

    private void recalculateAllPlayerLevels() {
        // This would need to be implemented to recalculate XP from stats for all players
        // For now, just trigger a full stat sync
        if (plugin.statSyncTask != null) {
            plugin.statSyncTask.syncAllPlayers(null);
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.reload")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload the config.");
            return;
        }
        
        try {
            plugin.reloadConfig();
            plugin.achievementManager.reloadAchievements();
            plugin.levelManager.reloadLevelDefinitions();
            sender.sendMessage(ChatColor.GREEN + "Progression config reloaded and systems re-initialized.");
            
            // Play sound for the command sender if they're a player
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error reloading config: " + e.getMessage());
            plugin.logManager.warning("Error reloading config: " + e.getMessage());
        }
    }

    private void handleClaim(CommandSender sender, String[] args) {
        if (!sender.hasPermission("progression.level.claim")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to claim achievements!");
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /level claim <player> <achievement> <tier>");
            return;
        }

        String targetName = args[1];
        String achievementId = args[2];
        int tier;
        
        try {
            tier = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid tier number: " + args[3]);
            return;
        }

        UUID targetUUID = getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return;
        }

        // Get player stats to verify they can claim
        Map<String, Object> playerStats = new HashMap<>();
        for (World world : plugin.getServer().getWorlds()) {
            File statsFolder = new File(world.getWorldFolder(), "stats");
            if (statsFolder.exists() && statsFolder.isDirectory()) {
                File statFile = new File(statsFolder, targetUUID + ".json");
                if (statFile.exists()) {
                    try (FileReader reader = new FileReader(statFile)) {
                        JSONObject root = (JSONObject) new JSONParser().parse(reader);
                        JSONObject stats = (JSONObject) root.get("stats");
                        if (stats != null) {
                            playerStats.putAll(stats);
                        }
                    } catch (Exception e) {
                        sender.sendMessage(ChatColor.RED + "Failed to read stats for " + targetName + ": " + e.getMessage());
                        return;
                    }
                }
            }
        }

        // Find the achievement and tier
        AchievementManager.AchievementDefinition achievement = null;
        AchievementManager.AchievementTier achievementTier = null;
        
        for (AchievementManager.AchievementDefinition ach : achievementManager.getAchievementDefinitions()) {
            if (ach.getId().equals(achievementId)) {
                achievement = ach;
                for (AchievementManager.AchievementTier t : ach.getTiers()) {
                    if (t.getTier() == tier) {
                        achievementTier = t;
                        break;
                    }
                }
                break;
            }
        }

        if (achievement == null || achievementTier == null) {
            sender.sendMessage(ChatColor.RED + "Achievement or tier not found!");
            return;
        }

        // Check if player has reached the threshold
        Long statValue = plugin.achievementManager.statResolver.resolveStatValue(playerStats, achievement.getStat());
        if (statValue == null || statValue < achievementTier.getThreshold()) {
            sender.sendMessage(ChatColor.RED + "Player has not reached the threshold for this achievement!");
            return;
        }

        // Check if already unlocked
        AchievementManager.PlayerAchievementData achievementData = achievementManager.getPlayerAchievementData(targetUUID);
        if (achievementData == null) {
            achievementData = new AchievementManager.PlayerAchievementData(targetUUID, targetName);
        }

        if (achievementData.hasUnlockedTier(achievementId, tier)) {
            sender.sendMessage(ChatColor.RED + "This achievement tier has already been claimed!");
            return;
        }

        // Unlock the achievement
        achievementData.unlockTier(achievementId, achievementTier, statValue.intValue());
        
        // Award XP
        final int xpGained = achievementTier.getPoints();
        final String tierName = achievementTier.getName();
        final String tierDescription = achievementTier.getDescription();
        plugin.levelManager.addPlayerXP(targetUUID, targetName, xpGained);
        
        // Save the achievement data
        achievementManager.saveAllData();
        
        // Sync to database if enabled
        if (plugin.levelDatabaseManager != null && plugin.levelDatabaseManager.isEnabled()) {
            plugin.levelDatabaseManager.syncUnlockedAchievement(targetUUID, null, achievementId, tier, xpGained);
        }
        
        // Achievement claimed successfully - no auto-resend to prevent spam
        sender.sendMessage(ChatColor.GREEN + "Successfully claimed " + tierName + " achievement for " + targetName + " (+" + xpGained + " XP)");
        
        // Send notification after a brief delay
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player targetPlayer = plugin.getServer().getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                targetPlayer.sendMessage("§a§l🏆 ACHIEVEMENT CLAIMED! §a" + tierName);
                targetPlayer.sendMessage("§7" + tierDescription + " §a(+" + xpGained + " XP)");
                // Play achievement sound
                targetPlayer.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }, 5L);
    }

    private void sendClaimButton(CommandSender sender, String playerName, String achievementId, AchievementManager.AchievementTier tier) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.GRAY + "  Status: " + ChatColor.GREEN + "READY! (Use /level claim <achievement> to claim)");
            return;
        }

        TextComponent claimButton = new TextComponent(ChatColor.GRAY + "  Status: " + ChatColor.GREEN + "READY! ");
        TextComponent clickToClaim = new TextComponent(ChatColor.GOLD + "[CLAIM]");
        
        clickToClaim.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
            "/level claim " + playerName + " " + achievementId + " " + tier.getTier()));
        clickToClaim.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
            new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.GREEN + "Click to claim " + tier.getName() + " achievement!\n" + 
                           ChatColor.YELLOW + "Reward: " + ChatColor.GOLD + "+" + tier.getPoints() + " XP").create()));
        
        claimButton.addExtra(clickToClaim);
        ((Player) sender).spigot().sendMessage(claimButton);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "leaderboard", "top", "recalculate", "addxp", "setlevel", "reset", "achievements", "claim", "reload", "help");
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "info":
                case "addxp":
                case "setlevel":
                case "reset":
                case "recalculate":
                case "achievements":
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(player.getName());
                        }
                    }
                    break;
            }
        }
        return completions;
    }
} 