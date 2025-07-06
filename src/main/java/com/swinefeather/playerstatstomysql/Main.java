package com.swinefeather.playerstatstomysql;

import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements Listener {
    private DatabaseManager dbManager;
    private PlaceholderManager placeholderManager;
    private StatSyncTask statSyncTask;
    private boolean disabled = false;

    @Override
    public void onEnable() {
        // Save default config first
        saveDefaultConfig();
        
        getLogger().info("PlayerStatsToMySQL v1.0 starting up...");
        
        // Check if MySQL is enabled
        if (!getConfig().getBoolean("mysql.enabled", true)) {
            getLogger().warning("MySQL is disabled in config. Plugin will not function properly.");
            getLogger().warning("Set 'mysql.enabled: true' in config.yml to enable the plugin.");
            return;
        }
        
        // Validate config
        String dbUrl = getConfig().getString("mysql.url");
        String dbUser = getConfig().getString("mysql.user");
        String dbPassword = getConfig().getString("mysql.password");
        
        if (dbUrl == null || dbUrl.isEmpty() || dbUrl.contains("yourdatabaselink")) {
            getLogger().severe("Database URL not configured properly!");
            getLogger().severe("Please edit plugins/PlayerStatsToMySQL/config.yml and update the MySQL settings:");
            getLogger().severe("mysql:");
            getLogger().severe("  url: \"jdbc:mysql://your-server:3306/your-database\"");
            getLogger().severe("  user: \"your-username\"");
            getLogger().severe("  password: \"your-password\"");
            return;
        }
        
        if (dbUser == null || dbUser.isEmpty() || dbUser.contains("your_username")) {
            getLogger().severe("Database username not configured!");
            getLogger().severe("Please update the 'mysql.user' setting in config.yml");
            return;
        }
        
        if (dbPassword == null || dbPassword.isEmpty() || dbPassword.contains("your_password")) {
            getLogger().severe("Database password not configured!");
            getLogger().severe("Please update the 'mysql.password' setting in config.yml");
            return;
        }
        
        getLogger().info("Attempting to connect to database...");
        
        // Initialize database manager
        dbManager = new DatabaseManager(this);
        if (!dbManager.isConnected()) {
            getLogger().severe("Database connection failed!");
            getLogger().severe("Please check your MySQL settings in config.yml:");
            getLogger().severe("- Ensure MySQL server is running");
            getLogger().severe("- Verify database URL, username, and password");
            getLogger().severe("- Check if database exists and user has proper permissions");
            getLogger().severe("Plugin will be disabled until database connection is established.");
            return;
        }
        
        getLogger().info("Database connection successful!");
        
        // Setup database tables
        try {
            dbManager.setupDatabase();
            getLogger().info("Database tables created/verified successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to setup database tables: " + e.getMessage());
            return;
        }

        // Initialize other components
        placeholderManager = new PlaceholderManager(this, dbManager);
        placeholderManager.loadPlaceholders();

        statSyncTask = new StatSyncTask(this, dbManager, placeholderManager);

        getServer().getPluginManager().registerEvents(this, this);

        // Start scheduled tasks
        long syncInterval = getConfig().getLong("sync-interval-ticks", 12000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!disabled) {
                    getLogger().info("Running scheduled sync for online players...");
                    statSyncTask.syncOnlinePlayers(null);
                }
            }
        }.runTaskTimerAsynchronously(this, syncInterval, syncInterval);

        long exportInterval = getConfig().getLong("export.interval-ticks", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!disabled) {
                    new ExportTask(Main.this, dbManager).exportStats(null, "json");
                }
            }
        }.runTaskTimerAsynchronously(this, exportInterval, exportInterval);

        getLogger().info("PlayerStatsToMySQL v1.0 enabled successfully!");
        getLogger().info("Use /sqlstats help for available commands.");
    }

    @Override
    public void onDisable() {
        dbManager.close();
        getLogger().info("PlayerStatsToMySQL v1.0 disabled!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!disabled) {
            UUID playerUUID = event.getPlayer().getUniqueId();
            getLogger().info(String.format("Syncing stats for player leaving: %s", playerUUID));
            statSyncTask.syncSinglePlayer(playerUUID, null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sqlstats")) {
            if (args.length == 0) {
                sender.sendMessage("§a/sqlstats <sync|export|view|reload|placeholder|status|versioncheck|cleanup|stop|start|help>");
                return true;
            }

            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("sync") && sender.hasPermission("playerstatstomysql.sqlstats.sync")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
                    return true;
                }
                if (statSyncTask == null) {
                    sender.sendMessage("§cStatSyncTask not available. Plugin may not be fully initialized.");
                    return true;
                }
                sender.sendMessage("§aStarting full stat sync...");
                statSyncTask.syncAllPlayers(sender);
                return true;
            }

            if (subCommand.equals("export") && sender.hasPermission("playerstatstomysql.sqlstats.export")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
                    return true;
                }
                if (dbManager == null) {
                    sender.sendMessage("§cDatabaseManager not available. Plugin may not be fully initialized.");
                    return true;
                }
                String format = args.length > 1 ? args[1] : "json";
                new ExportTask(this, dbManager).exportStats(sender, format);
                return true;
            }

            if (subCommand.equals("view") && sender.hasPermission("playerstatstomysql.sqlstats.view")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
                    return true;
                }
                if (statSyncTask == null) {
                    sender.sendMessage("§cStatSyncTask not available. Plugin may not be fully initialized.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /sqlstats view <player> <category>");
                    return true;
                }
                statSyncTask.viewStats(sender, args[1], args[2]);
                return true;
            }

            if (subCommand.equals("reload") && sender.hasPermission("playerstatstomysql.sqlstats.reload")) {
                try {
                    reloadConfig();
                    if (placeholderManager != null) {
                        placeholderManager.loadPlaceholders();
                        sender.sendMessage("§aConfig reloaded!");
                    } else {
                        sender.sendMessage("§cCannot reload: Plugin not fully initialized. Check console for errors.");
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cReload failed: " + e.getMessage());
                    getLogger().severe("Reload failed: " + e.getMessage());
                }
                return true;
            }

            if (subCommand.equals("placeholder") && sender.hasPermission("playerstatstomysql.sqlstats.placeholder")) {
                if (placeholderManager == null) {
                    sender.sendMessage("§cPlaceholderManager not available. Plugin may not be fully initialized.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /sqlstats placeholder <list|add|blacklist> [placeholder]");
                    return true;
                }
                if (args[1].equalsIgnoreCase("list")) {
                    java.util.List<String> placeholders = placeholderManager.getAvailablePlaceholders();
                    sender.sendMessage("§aAvailable placeholders: " + String.join(", ", placeholders));
                } else if (args[1].equalsIgnoreCase("add") && args.length > 2) {
                    placeholderManager.addPlaceholder(args[2]);
                    sender.sendMessage(String.format("§aAdded placeholder: %s", args[2]));
                } else if (args[1].equalsIgnoreCase("blacklist") && args.length > 2) {
                    placeholderManager.addPlaceholder(args[2]);
                    sender.sendMessage(String.format("§aBlacklisted placeholder: %s", args[2]));
                }
                return true;
            }

            if (subCommand.equals("status") && sender.hasPermission("playerstatstomysql.sqlstats.status")) {
                sender.sendMessage("§aStatus:");
                sender.sendMessage("§7Plugin: " + (disabled ? "§cDisabled" : "§aEnabled"));
                sender.sendMessage("§7MySQL: " + (dbManager != null && dbManager.isConnected() ? "§aConnected" : "§cDisconnected"));
                sender.sendMessage("§7PlaceholderManager: " + (placeholderManager != null ? "§aInitialized" : "§cNot Initialized"));
                sender.sendMessage("§7StatSyncTask: " + (statSyncTask != null ? "§aInitialized" : "§cNot Initialized"));
                sender.sendMessage("§7PlaceholderAPI: " + (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null ? "§aActive" : "§cInactive"));
                sender.sendMessage("§7Towny: " + (getServer().getPluginManager().getPlugin("Towny") != null ? "§aActive" : "§cInactive"));
                return true;
            }

            if (subCommand.equals("versioncheck") && sender.hasPermission("playerstatstomysql.sqlstats.versioncheck")) {
                String mcVersion = getServer().getVersion();
                sender.sendMessage(String.format("§aRunning on Minecraft %s", mcVersion));
                if (!mcVersion.contains("1.16") && !mcVersion.contains("1.20") && !mcVersion.contains("1.21")) {
                    sender.sendMessage(String.format("§eWarning: Plugin tested on 1.16.5-1.21.1. May not work on %s", mcVersion));
                }
                return true;
            }

            if (subCommand.equals("cleanup") && sender.hasPermission("playerstatstomysql.sqlstats.cleanup")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
                    return true;
                }
                if (dbManager == null) {
                    sender.sendMessage("§cDatabaseManager not available. Plugin may not be fully initialized.");
                    return true;
                }
                if (!getConfig().getBoolean("export.cleanup.enabled")) {
                    sender.sendMessage("§cCleanup is disabled in config!");
                    return true;
                }
                new ExportTask(this, dbManager).cleanupStats(sender);
                return true;
            }

            if (subCommand.equals("stop") && sender.hasPermission("playerstatstomysql.sqlstats.stop")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is already disabled!");
                } else {
                    disabled = true;
                    sender.sendMessage("§aPlugin disabled. Stat syncing and exports paused.");
                }
                return true;
            }

            if (subCommand.equals("start") && sender.hasPermission("playerstatstomysql.sqlstats.start")) {
                if (!disabled) {
                    sender.sendMessage("§cPlugin is already enabled!");
                } else if (dbManager == null || !dbManager.isConnected()) {
                    sender.sendMessage("§cCannot start: Database connection failed!");
                } else {
                    disabled = false;
                    sender.sendMessage("§aPlugin enabled. Stat syncing and exports resumed.");
                }
                return true;
            }

            if (subCommand.equals("help") && sender.hasPermission("playerstatstomysql.sqlstats.help")) {
                sender.sendMessage("§aPlayerStatsToMySQL Commands:");
                sender.sendMessage("§7/sqlstats sync - Sync all player stats to database (initial sync)");
                sender.sendMessage("§7/sqlstats export [json] - Export stats to JSON file");
                sender.sendMessage("§7/sqlstats view <player> <category> - View player stats for a category");
                sender.sendMessage("§7/sqlstats reload - Reload configuration");
                sender.sendMessage("§7/sqlstats placeholder <list|add|blacklist> [placeholder] - Manage placeholders");
                sender.sendMessage("§7/sqlstats status - Check plugin status");
                sender.sendMessage("§7/sqlstats versioncheck - Check server version compatibility");
                sender.sendMessage("§7/sqlstats cleanup - Clean up old data (if enabled)");
                sender.sendMessage("§7/sqlstats stop - Disable plugin");
                sender.sendMessage("§7/sqlstats start - Enable plugin");
                sender.sendMessage("§7/sqlstats help - Show this help message");
                return true;
            }

            sender.sendMessage("§cNo permission or invalid subcommand!");
            return true;
        }
        return false;
    }
}