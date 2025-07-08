package com.swinefeather.playerstatstomysql;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class Main extends JavaPlugin implements Listener {
    private DatabaseManager dbManager;
    private SupabaseManager supabaseManager;
    private PlaceholderManager placeholderManager;
    private StatSyncTask statSyncTask;
    public AwardManager awardManager;
    private WebhookManager webhookManager;
    private boolean disabled = false;

    @Override
    public void onEnable() {
        // Save default config first
        saveDefaultConfig();
        
        getLogger().info("PlayerStatsToMySQL v1.0 starting up...");
        
        // Check database configuration
        String dbType = getConfig().getString("database.type", "mysql");
        boolean mysqlEnabled = getConfig().getBoolean("mysql.enabled", true);
        boolean supabaseEnabled = getConfig().getBoolean("supabase.enabled", false);
        
        getLogger().info("Initial database configuration:");
        getLogger().info("   Database type: " + dbType);
        getLogger().info("   MySQL enabled: " + mysqlEnabled);
        getLogger().info("   Supabase enabled: " + supabaseEnabled);
        
        if (dbType.equals("supabase")) {
            mysqlEnabled = false;
            supabaseEnabled = true;
            getLogger().info("Database type is 'supabase', enabling Supabase and disabling MySQL");
        } else if (dbType.equals("both")) {
            getLogger().info("Database type is 'both', both databases will be enabled");
        } else {
            getLogger().info("Database type is '" + dbType + "', using individual enabled flags");
        }
        
        // Initialize MySQL if enabled
        if (mysqlEnabled) {
            if (!initializeMySQL()) {
                if (dbType.equals("mysql")) {
                    getLogger().info("MySQL initialization failed and it's the only database type configured!");
                    return;
                }
            }
        }
        
        // Initialize Supabase if enabled
        if (supabaseEnabled) {
            if (!initializeSupabase()) {
                if (dbType.equals("supabase")) {
                    getLogger().info("Supabase initialization failed and it's the only database type configured!");
                    return;
                }
            }
        }
        
        // Check if at least one database is working
        if ((dbManager == null || !dbManager.isConnected()) && 
            (supabaseManager == null || !supabaseManager.isEnabled())) {
            getLogger().info("No database connection established! Plugin cannot function.");
            return;
        }

        // Initialize other components
        placeholderManager = new PlaceholderManager(this, dbManager);
        placeholderManager.loadPlaceholders();

        statSyncTask = new StatSyncTask(this, dbManager, supabaseManager, placeholderManager);

        // Initialize WebhookManager
        webhookManager = new WebhookManager(this);
        webhookManager.initialize(getConfig().getConfigurationSection("webhooks"));

        // Initialize AwardManager
        awardManager = new AwardManager(this, supabaseManager, webhookManager);
        awardManager.initialize(getConfig().getConfigurationSection("awards"));

        // Register award commands
        AwardCommands awardCommands = new AwardCommands(this, awardManager, dbManager, supabaseManager);
        getCommand("awards").setExecutor(awardCommands);
        getCommand("awards").setTabCompleter(awardCommands);

        getServer().getPluginManager().registerEvents(this, this);

        // Start scheduled tasks
        long syncInterval = getConfig().getLong("sync-interval-ticks", 12000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!disabled) {
                    getLogger().info("Running scheduled sync for all players (online and offline)...");
                    syncAllAwardsAndStatsForAllPlayers();
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

        // Immediately trigger full sync for all players on startup
        getLogger().info("Running initial full sync for all players (online and offline)...");
        syncAllAwardsAndStatsForAllPlayers();

        getLogger().info("PlayerStatsToMySQL v1.0 enabled successfully!");
        getLogger().info("Database type: " + dbType);
        if (mysqlEnabled && dbManager != null && dbManager.isConnected()) {
            getLogger().info("MySQL: Connected");
        }
        if (supabaseEnabled && supabaseManager != null && supabaseManager.isEnabled()) {
            getLogger().info("Supabase: Connected");
        }
        getLogger().info("Use /sqlstats help for available commands.");
    }
    
    private boolean initializeMySQL() {
        getLogger().info("Initializing MySQL connection...");
        
        // Validate MySQL config
        String dbUrl = getConfig().getString("mysql.url");
        String dbUser = getConfig().getString("mysql.user");
        String dbPassword = getConfig().getString("mysql.password");
        
        if (dbUrl == null || dbUrl.isEmpty() || dbUrl.contains("yourdatabaselink")) {
            getLogger().info("MySQL URL not configured properly!");
            getLogger().info("Please edit plugins/PlayerStatsToMySQL/config.yml and update the MySQL settings:");
            getLogger().info("mysql:");
            getLogger().info("   url: \"jdbc:mysql://your-server:3306/your-database\"");
            getLogger().info("   user: \"your-username\"");
            getLogger().info("   password: \"your-password\"");
            return false;
        }
        
        if (dbUser == null || dbUser.isEmpty() || dbUser.contains("your_username")) {
            getLogger().info("MySQL username not configured!");
            getLogger().info("Please update the 'mysql.user' setting in config.yml");
            return false;
        }
        
        if (dbPassword == null || dbPassword.isEmpty() || dbPassword.contains("your_password")) {
            getLogger().info("MySQL password not configured!");
            getLogger().info("Please update the 'mysql.password' setting in config.yml");
            return false;
        }
        
        // Initialize database manager
        dbManager = new DatabaseManager(this);
        if (!dbManager.isConnected()) {
            getLogger().info("MySQL connection failed!");
            getLogger().info("Please check your MySQL settings in config.yml:");
            getLogger().info("- Ensure MySQL server is running");
            getLogger().info("- Verify database URL, username, and password");
            getLogger().info("- Check if database exists and user has proper permissions");
            return false;
        }
        
        getLogger().info("MySQL connection successful!");
        
        // Setup database tables
        try {
            dbManager.setupDatabase();
            getLogger().info("MySQL tables created/verified successfully!");
        } catch (Exception e) {
            getLogger().info("Failed to setup MySQL tables: " + e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private boolean initializeSupabase() {
        getLogger().info("Initializing Supabase connection...");
        
        // Debug: Log the entire config section
        getLogger().info("Database type from config: " + getConfig().getString("database.type", "not set"));
        getLogger().info("Supabase enabled from config: " + getConfig().getBoolean("supabase.enabled", false));
        
        // Validate Supabase config
        String supabaseUrl = getConfig().getString("supabase.url");
        String supabaseKey = getConfig().getString("supabase.key");
        
        getLogger().info("Raw Supabase URL from config: " + supabaseUrl);
        getLogger().info("Raw Supabase key from config: " + (supabaseKey != null && supabaseKey.length() > 10 ? supabaseKey.substring(0, 10) + "..." : supabaseKey));
        
        if (supabaseUrl == null || supabaseUrl.isEmpty()) {
            getLogger().info("Supabase URL is null or empty!");
            return false;
        }
        
        if (supabaseUrl.contains("your-project")) {
            getLogger().info("Supabase URL still contains placeholder value!");
            getLogger().info("Please edit plugins/PlayerStatsToMySQL/config.yml and update the Supabase settings:");
            getLogger().info("supabase:");
            getLogger().info("   url: \"https://your-project.supabase.co\"");
            getLogger().info("   key: \"your-anon-key\"");
            return false;
        }
        
        if (supabaseKey == null || supabaseKey.isEmpty() || supabaseKey.contains("your-anon-key")) {
            getLogger().info("Supabase key not configured!");
            getLogger().info("Please update the 'supabase.key' setting in config.yml");
            return false;
        }
        
        // Initialize Supabase manager
        supabaseManager = new SupabaseManager(this);
        if (!supabaseManager.initialize(getConfig().getConfigurationSection("supabase"))) {
            getLogger().info("Supabase initialization failed!");
            getLogger().info("Please check your Supabase settings in config.yml:");
            getLogger().info("- Verify project URL and API key");
            getLogger().info("- Ensure tables 'players' and 'player_stats' exist");
            getLogger().info("- Check if RLS policies allow INSERT/UPDATE operations");
            return false;
        }
        
        getLogger().info("Supabase connection successful!");
        return true;
    }

    @Override
    public void onDisable() {
        if (dbManager != null) {
            dbManager.close();
        }
        if (supabaseManager != null) {
            supabaseManager.shutdown();
        }
        if (webhookManager != null) {
            webhookManager.shutdown();
        }
        getLogger().info("PlayerStatsToMySQL v1.0 disabled!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!disabled) {
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            getLogger().info("Syncing stats for player leaving: " + playerUUID);
            
            // Sync to MySQL
            if (dbManager != null && dbManager.isConnected()) {
                statSyncTask.syncSinglePlayer(playerUUID, null);
            }
            
            // Sync to Supabase
            if (supabaseManager != null && supabaseManager.isEnabled()) {
                statSyncTask.onPlayerQuit(player);
            }
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
                // Parse sync type
                String syncType = args.length > 1 ? args[1].toLowerCase() : "all";
                switch (syncType) {
                    case "all":
                        sender.sendMessage("§aStarting full sync: stats, medals, points, awards...");
                        statSyncTask.syncAllPlayers(sender);
                        if (awardManager != null && awardManager.isEnabled()) {
                            // TODO: Implement granular syncs in AwardManager
                            awardManager.syncAllAwardsToSupabase();
                            awardManager.syncAllMedalsToSupabase();
                            awardManager.syncAllPointsToSupabase();
                        }
                        break;
                    case "stats":
                        sender.sendMessage("§aSyncing only player stats...");
                        statSyncTask.syncAllPlayers(sender);
                        break;
                    case "awards":
                        sender.sendMessage("§aSyncing only awards...");
                        if (awardManager != null && awardManager.isEnabled()) {
                            awardManager.syncAllAwardsToSupabase();
                        }
                        break;
                    case "medals":
                        sender.sendMessage("§aSyncing only medals...");
                        if (awardManager != null && awardManager.isEnabled()) {
                            awardManager.syncAllMedalsToSupabase();
                        }
                        break;
                    case "points":
                        sender.sendMessage("§aSyncing only points...");
                        if (awardManager != null && awardManager.isEnabled()) {
                            awardManager.syncAllPointsToSupabase();
                        }
                        break;
                    default:
                        sender.sendMessage("§cUnknown sync type. Use: all, stats, medals, points, awards");
                        break;
                }
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
                    
                    // Reinitialize Supabase if enabled
                    String dbType = getConfig().getString("database.type", "mysql");
                    boolean supabaseEnabled = getConfig().getBoolean("supabase.enabled", false);
                    
                    if (dbType.equals("supabase")) {
                        supabaseEnabled = true;
                    }
                    
                    if (supabaseEnabled) {
                        // Close existing connection
                        if (supabaseManager != null) {
                            supabaseManager.shutdown();
                        }
                        
                        // Reinitialize
                        supabaseManager = new SupabaseManager(this);
                        if (!supabaseManager.initialize(getConfig().getConfigurationSection("supabase"))) {
                            sender.sendMessage("§cSupabase reinitialization failed! Check console for details.");
                            getLogger().info("Supabase reinitialization failed after reload!");
                        } else {
                            sender.sendMessage("§aSupabase reinitialized successfully!");
                        }
                    }
                    
                    // Reinitialize MySQL if enabled
                    boolean mysqlEnabled = getConfig().getBoolean("mysql.enabled", true);
                    if (dbType.equals("mysql")) {
                        mysqlEnabled = true;
                    }
                    
                    if (mysqlEnabled) {
                        // Close existing connection
                        if (dbManager != null) {
                            dbManager.close();
                        }
                        
                        // Reinitialize
                        if (!initializeMySQL()) {
                            sender.sendMessage("§cMySQL reinitialization failed! Check console for details.");
                        } else {
                            sender.sendMessage("§aMySQL reinitialized successfully!");
                        }
                    }
                    
                    // Reload placeholders
                    if (placeholderManager != null) {
                        placeholderManager.loadPlaceholders();
                        sender.sendMessage("§aPlaceholders reloaded!");
                    }
                    
                    // Reinitialize other components
                    if (supabaseManager != null && supabaseManager.isEnabled()) {
                        webhookManager = new WebhookManager(this);
                        webhookManager.initialize(getConfig().getConfigurationSection("webhooks"));
                        
                        awardManager = new AwardManager(this, supabaseManager, webhookManager);
                        awardManager.initialize(getConfig().getConfigurationSection("awards"));
                    }
                    
                    // Reinitialize StatSyncTask
                    statSyncTask = new StatSyncTask(this, dbManager, supabaseManager, placeholderManager);
                    
                    sender.sendMessage("§aConfig and all components reloaded successfully!");
                    
                } catch (Exception e) {
                    sender.sendMessage("§cReload failed: " + e.getMessage());
                    getLogger().info("Reload failed: " + e.getMessage());
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
                String dbType = getConfig().getString("database.type", "mysql");
                sender.sendMessage("§aStatus:");
                sender.sendMessage("§7Plugin: " + (disabled ? "§cDisabled" : "§aEnabled"));
                sender.sendMessage("§7Database Type: " + dbType);
                sender.sendMessage("§7MySQL: " + (dbManager != null && dbManager.isConnected() ? "§aConnected" : "§cDisconnected"));
                sender.sendMessage("§7Supabase: " + (supabaseManager != null && supabaseManager.isEnabled() ? "§aConnected" : "§cDisconnected"));
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
                } else if ((dbManager == null || !dbManager.isConnected()) && 
                          (supabaseManager == null || !supabaseManager.isEnabled())) {
                    sender.sendMessage("§cCannot start: No database connection available!");
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

    // Sync and recalculate all awards/stats for all players (online and offline)
    public void syncAllAwardsAndStatsForAllPlayers() {
        if (awardManager != null && awardManager.isEnabled()) {
            // Load all stats for all players (offline and online)
            Map<UUID, Map<String, Object>> allStats = awardManager.loadAllPlayerStats();
            Map<Player, Map<String, Object>> onlineStats = new HashMap<>();
            for (Player player : getServer().getOnlinePlayers()) {
                onlineStats.put(player, allStats.getOrDefault(player.getUniqueId(), new HashMap<>()));
            }
            // Calculate and sync for all players (offline and online)
            awardManager.calculateAllAwards(onlineStats);
        }
    }
}