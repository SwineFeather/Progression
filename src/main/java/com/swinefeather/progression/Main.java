package com.swinefeather.progression;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.server.ServerLoadEvent;

public class Main extends JavaPlugin implements Listener {
    private DatabaseManager dbManager;
    private SupabaseManager supabaseManager;
    private PlaceholderManager placeholderManager;
    public StatSyncTask statSyncTask;
    public AwardManager awardManager;
    private WebhookManager webhookManager;
    public LogManager logManager;
    public LevelManager levelManager;
    public AchievementManager achievementManager;
    public LevelDatabaseManager levelDatabaseManager;
    public TownyManager townyManager;
    private boolean disabled = false;

    @Override
    public void onEnable() {
        // Save default config first
        saveDefaultConfig();
        
        // Initialize LogManager first
        logManager = new LogManager(this);
        
        logManager.debug("Progression v1.0 starting up...");
        
        // Check database configuration
        String dbType = getConfig().getString("database.type", "mysql");
        boolean mysqlEnabled = getConfig().getBoolean("mysql.enabled", true);
        boolean supabaseEnabled = getConfig().getBoolean("supabase.enabled", false);
        
        if (dbType.equals("supabase")) {
            mysqlEnabled = false;
            supabaseEnabled = true;
        }
        
        // Initialize MySQL if enabled
        if (mysqlEnabled) {
            if (!initializeMySQL()) {
                if (dbType.equals("mysql")) {
                    logManager.severe("MySQL initialization failed and it's the only database type configured!");
                    return;
                }
            }
        }
        
        // Initialize Supabase if enabled
        if (supabaseEnabled) {
            if (!initializeSupabase()) {
                if (dbType.equals("supabase")) {
                    logManager.severe("Supabase initialization failed and it's the only database type configured!");
                    return;
                }
            }
        }
        
        // Check if at least one database is working
        if ((dbManager == null || !dbManager.isConnected()) && 
            (supabaseManager == null || !supabaseManager.isEnabled())) {
            logManager.warning("No database connection established! Running in local-only mode.");
            // Do not return; allow plugin to function with local file storage
        }

        // Initialize other components
        placeholderManager = new PlaceholderManager(this, dbManager);
        placeholderManager.loadPlaceholders();

        statSyncTask = new StatSyncTask(this, dbManager, supabaseManager, placeholderManager);

        // Initialize WebhookManager
        webhookManager = new WebhookManager(this);
        webhookManager.initialize(getConfig().getConfigurationSection("webhooks"));

        // Initialize Level and Achievement systems
        levelManager = new LevelManager(this);
        achievementManager = new AchievementManager(this);
        levelDatabaseManager = new LevelDatabaseManager(this, supabaseManager);
        
        // Initialize Towny integration
        townyManager = new TownyManager(this);
        
        // Initialize AwardManager
        awardManager = new AwardManager(this, supabaseManager, webhookManager, logManager);
        awardManager.initialize(getConfig().getConfigurationSection("awards"));
        
        // Initialize API
        ProgressionAPI.initialize(this);

        // Initialize AwardManager with LogManager
        awardManager = new AwardManager(this, supabaseManager, webhookManager, logManager);
        awardManager.initialize(getConfig().getConfigurationSection("awards"));

        // Sync level and achievement definitions to database
        if (levelDatabaseManager.isEnabled()) {
            levelDatabaseManager.syncLevelDefinitions(
                levelManager.getPlayerLevelDefinitions(), 
                levelManager.getTownLevelDefinitions()
            );
            levelDatabaseManager.syncAchievementDefinitions(
                achievementManager.getAchievementDefinitions()
            );
        }

        // Level and Achievement systems already initialized above

        // Register award commands
        AwardCommands awardCommands = new AwardCommands(this, awardManager, dbManager, supabaseManager, awardManager.getLocalStorage());
        getCommand("awards").setExecutor(awardCommands);
        getCommand("awards").setTabCompleter(awardCommands);

        // Register level commands
        LevelCommands levelCommands = new LevelCommands(this);
        getCommand("level").setExecutor(levelCommands);
        getCommand("level").setTabCompleter(levelCommands);
        
        // Register townstats commands
        TownStatsCommands townStatsCommands = new TownStatsCommands(this);
        getCommand("townstats").setExecutor(townStatsCommands);
        getCommand("townstats").setTabCompleter(townStatsCommands);

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);

        // Start scheduled tasks
        long syncInterval = getConfig().getLong("sync-interval-ticks", 12000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!disabled) {
                    logManager.debug("Running scheduled sync for all players...");
                    syncAllAwardsAndStatsForAllPlayers();
                }
            }
        }.runTaskTimerAsynchronously(this, syncInterval, syncInterval);

        long exportInterval = getConfig().getLong("export.interval-ticks", 72000L);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!disabled) {
                    // Only run export if we have a database manager (MySQL mode)
                    if (dbManager != null && dbManager.isConnected()) {
                        new ExportTask(Main.this, dbManager).exportStats(null, "json");
                    } else {
                        logManager.debug("Skipping export task - no MySQL database available");
                    }
                }
            }
        }.runTaskTimerAsynchronously(this, exportInterval, exportInterval);

        // DISABLED: Initial sync on startup to prevent level-up spam
        // The plugin will now only sync when players are online or when manually triggered
        logManager.debug("Skipping initial sync on startup to prevent level-up spam");

        logManager.debug("Progression v1.0 enabled successfully!");
        if (supabaseEnabled && supabaseManager != null && supabaseManager.isEnabled()) {
            logManager.debug("Supabase: Connected");
        }

        // DISABLED: ServerLoadEvent sync to prevent level-up spam
        // The plugin will now only sync when players are online or when manually triggered
        logManager.debug("Skipping ServerLoadEvent sync to prevent level-up spam");
    }
    
    private boolean initializeMySQL() {
        getLogger().info("Initializing MySQL connection...");
        
        // Validate MySQL config
        String dbUrl = getConfig().getString("mysql.url");
        String dbUser = getConfig().getString("mysql.user");
        String dbPassword = getConfig().getString("mysql.password");
        
        if (dbUrl == null || dbUrl.isEmpty() || dbUrl.contains("yourdatabaselink")) {
            getLogger().info("MySQL URL not configured properly!");
            getLogger().info("Please edit plugins/Progression/config.yml and update the MySQL settings:");
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
        logManager.debug("Initializing Supabase connection...");
        
        // Validate Supabase config
        String supabaseUrl = getConfig().getString("supabase.url");
        String supabaseKey = getConfig().getString("supabase.key");
        
        if (supabaseUrl == null || supabaseUrl.isEmpty() || supabaseUrl.contains("your-project")) {
            logManager.warning("Supabase URL not configured properly!");
            return false;
        }
        
        if (supabaseKey == null || supabaseKey.isEmpty() || supabaseKey.contains("your-anon-key")) {
            logManager.warning("Supabase key not configured!");
            return false;
        }
        
        // Initialize Supabase manager
        supabaseManager = new SupabaseManager(this, logManager);
        if (!supabaseManager.initialize(getConfig().getConfigurationSection("supabase"))) {
            logManager.severe("Supabase initialization failed!");
            return false;
        }
        
        logManager.debug("Supabase connection successful!");
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
        if (levelManager != null) {
            levelManager.shutdown();
        }
        if (achievementManager != null) {
            achievementManager.shutdown();
        }
        if (levelDatabaseManager != null) {
            levelDatabaseManager.shutdown();
        }
        getLogger().info("Progression v1.0 disabled!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!disabled) {
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            logManager.debug("Syncing stats for player leaving: " + playerUUID);
            
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
                sender.sendMessage("§cUsage: /sqlstats <sync|sync_online|export|view|reload|status|help|generate_stats|calculate_awards>");
                return true;
            }

            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("generate_stats")) {
                if (!sender.hasPermission("progression.sqlstats.generate")) {
                    sender.sendMessage("§cYou don't have permission to generate stats.");
                    return true;
                }
                try {
                    generatePossibleStats();
                    sender.sendMessage("§aGenerated possible_stats.json with all possible stats.");
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to generate stats: " + e.getMessage());
                }
                return true;
            }

            if (subCommand.equals("calculate_awards")) {
                if (!sender.hasPermission("progression.sqlstats.calculate_awards")) {
                    sender.sendMessage("§cYou don't have permission to calculate awards.");
                    return true;
                }
                try {
                    if (awardManager != null && awardManager.isEnabled()) {
                        sender.sendMessage("§aCalculating awards for all players...");
                        awardManager.calculateAwardsForAllPlayers();
                        sender.sendMessage("§aAward calculation completed!");
                    } else {
                        sender.sendMessage("§cAward system is not enabled.");
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to calculate awards: " + e.getMessage());
                }
                return true;
            }

            if (subCommand.equals("sync") && sender.hasPermission("progression.sqlstats.sync")) {
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
                            // Disabled automatic Supabase syncing - use local storage for Minecraft
                            // awardManager.syncAllAwardsToSupabase();
                            // awardManager.syncAllMedalsToSupabase();
                            // awardManager.syncAllPointsToSupabase();
                        }
                        break;
                    case "stats":
                        sender.sendMessage("§aSyncing only player stats...");
                        statSyncTask.syncAllPlayers(sender);
                        break;
                    case "awards":
                        sender.sendMessage("§cAutomatic award syncing to Supabase is disabled. Use local storage for Minecraft.");
                        break;
                    case "medals":
                        sender.sendMessage("§cAutomatic medal syncing to Supabase is disabled. Use local storage for Minecraft.");
                        break;
                    case "points":
                        sender.sendMessage("§cAutomatic point syncing to Supabase is disabled. Use local storage for Minecraft.");
                        break;
                    default:
                        sender.sendMessage("§cUnknown sync type. Use: all, stats, medals, points, awards");
                        break;
                }
                return true;
            }

            if (subCommand.equals("export") && sender.hasPermission("progression.sqlstats.export")) {
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

            if (subCommand.equals("view") && sender.hasPermission("progression.sqlstats.view")) {
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

            if (subCommand.equals("reload") && sender.hasPermission("progression.sqlstats.reload")) {
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
                        supabaseManager = new SupabaseManager(this, logManager);
                        if (!supabaseManager.initialize(getConfig().getConfigurationSection("supabase"))) {
                            sender.sendMessage("§cSupabase reinitialization failed! Check console for details.");
                            logManager.severe("Supabase reinitialization failed after reload!");
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
                        
                        awardManager = new AwardManager(this, supabaseManager, webhookManager, logManager);
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

            if (subCommand.equals("placeholder") && sender.hasPermission("progression.sqlstats.placeholder")) {
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

            if (subCommand.equals("status") && sender.hasPermission("progression.sqlstats.status")) {
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

            if (subCommand.equals("versioncheck") && sender.hasPermission("progression.sqlstats.versioncheck")) {
                String mcVersion = getServer().getVersion();
                sender.sendMessage(String.format("§aRunning on Minecraft %s", mcVersion));
                if (!mcVersion.contains("1.16") && !mcVersion.contains("1.20") && !mcVersion.contains("1.21")) {
                    sender.sendMessage(String.format("§eWarning: Plugin tested on 1.16.5-1.21.1. May not work on %s", mcVersion));
                }
                return true;
            }

            if (subCommand.equals("cleanup") && sender.hasPermission("progression.sqlstats.cleanup")) {
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

            if (subCommand.equals("stop") && sender.hasPermission("progression.sqlstats.stop")) {
                if (disabled) {
                    sender.sendMessage("§cPlugin is already disabled!");
                } else {
                    disabled = true;
                    sender.sendMessage("§aPlugin disabled. Stat syncing and exports paused.");
                }
                return true;
            }

            if (subCommand.equals("start") && sender.hasPermission("progression.sqlstats.start")) {
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

            if (subCommand.equals("help") && sender.hasPermission("progression.sqlstats.help")) {
                sender.sendMessage("§aProgression Commands:");
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

    private void generatePossibleStats() throws IOException {
        Map<String, Map<String, Object>> stats = new HashMap<>();

        // Mined: all blocks
        Map<String, Object> mined = new HashMap<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                mined.put(m.getKey().getKey(), 0);
            }
        }
        stats.put("mined", mined);

        // Items for used, crafted, broken, picked_up, dropped
        Map<String, Object> items = new HashMap<>();
        for (Material m : Material.values()) {
            if (m.isItem()) {
                items.put(m.getKey().getKey(), 0);
            }
        }
        stats.put("used", new HashMap<>(items));
        stats.put("crafted", new HashMap<>(items));
        stats.put("broken", new HashMap<>(items));
        stats.put("picked_up", new HashMap<>(items));
        stats.put("dropped", new HashMap<>(items));

        // Killed and killed_by: all living entities
        Map<String, Object> entities = new HashMap<>();
        for (EntityType e : EntityType.values()) {
            if (e.isAlive()) {
                entities.put(e.getKey().getKey(), 0);
            }
        }
        stats.put("killed", new HashMap<>(entities));
        stats.put("killed_by", new HashMap<>(entities));

        // Custom stats (expanded list from Minecraft wiki knowledge)
        Map<String, Object> custom = new HashMap<>();
        String[] customStats = {
            "animals_bred", "armor_cleaned", "aviate_one_cm", "banner_cleaned", "beacon_interaction",
            "bell_ring", "boat_one_cm", "brewingstand_interaction", "cake_slices_eaten", "cauldron_filled",
            "cauldron_used", "chest_opened", "clean_armor", "clean_banner", "clean_shulker_box",
            "climb_one_cm", "crouch_one_cm", "damage_absorbed", "damage_blocked_by_shield", "damage_dealt",
            "damage_dealt_absorbed", "damage_dealt_resisted", "damage_resisted", "damage_taken", "deaths",
            "dispenser_inspected", "drop", "dropper_inspected", "eat_cake_slice", "enchant_item",
            "enderchest_opened", "fall_one_cm", "fill_cauldron", "fish_caught", "flower_potted",
            "fly_one_cm", "furnace_interaction", "hopper_inspected", "horse_one_cm", "interact_with_anvil",
            "interact_with_beacon", "interact_with_blast_furnace", "interact_with_brewingstand",
            "interact_with_campfire", "interact_with_cartography_table", "interact_with_crafting_table",
            "interact_with_furnace", "interact_with_grindstone", "interact_with_lectern", "interact_with_loom",
            "interact_with_smithing_table", "interact_with_smoker", "interact_with_stonecutter",
            "item_enchanted", "jump", "leave_game", "minecart_one_cm", "mob_kills", "noteblock_played",
            "noteblock_tuned", "open_barrel", "open_chest", "open_enderchest", "open_shulker_box",
            "pig_one_cm", "play_noteblock", "play_one_minute", "play_record", "play_time",
            "player_kills", "pot_flower", "raid_trigger", "raid_win", "shulker_box_cleaned",
            "shulker_box_opened", "shulker_opened", "sleep_in_bed", "sneak_time", "sprint_one_cm",
            "strider_one_cm", "swim_one_cm", "talked_to_villager", "time_since_death", "time_since_rest",
            "total_world_time", "traded_with_villager", "trapped_chest_triggered", "trigger_trapped_chest",
            "tune_noteblock", "use_cauldron", "walk_on_water_one_cm", "walk_one_cm", "walk_under_water_one_cm",
            "water_one_cm" // Add more if needed
        };
        for (String s : customStats) {
            custom.put(s, 0);
        }
        stats.put("custom", custom);

        // Advancements (expanded list from Minecraft wiki)
        Map<String, Object> advancements = new HashMap<>();
        String[] advList = {
            "adventure/adventuring_time", "adventure/arbalistic", "adventure/bullseye", "adventure/hero_of_the_village",
            "adventure/honey_block_slide", "adventure/kill_a_mob", "adventure/kill_all_mobs", "adventure/lightning_rod_with_villager_no_fire",
            "adventure/ol_betsy", "adventure/play_jukebox_in_meadows", "adventure/root", "adventure/shoot_arrow",
            "adventure/sleep_in_bed", "adventure/sniper_duel", "adventure/spyglass_at_dragon", "adventure/spyglass_at_ghast",
            "adventure/spyglass_at_parrot", "adventure/summon_iron_golem", "adventure/throw_trident", "adventure/totem_of_undying",
            "adventure/trade", "adventure/trade_at_world_height", "adventure/two_birds_one_arrow", "adventure/very_very_frightening",
            "adventure/voluntary_exile", "adventure/walk_on_powder_snow_with_leather_boots", "adventure/whos_the_pillager_now",
            "end/dragon_breath", "end/dragon_egg", "end/elytra", "end/enter_end_gateway", "end/find_end_city",
            "end/kill_dragon", "end/levitate", "end/respawn_dragon", "end/root",
            "husbandry/allay_deliver_item_to_player", "husbandry/allay_deliver_to_note_block", "husbandry/axolotl_in_a_bucket",
            "husbandry/balanced_diet", "husbandry/bred_all_animals", "husbandry/breed_an_animal", "husbandry/complete_catalogue",
            "husbandry/fishy_business", "husbandry/froglights", "husbandry/kill_axolotl_target", "husbandry/leash_all_frog_variants",
            "husbandry/make_a_sign_glow", "husbandry/obtain_netherite_hoe", "husbandry/obtain_sniffer_egg", "husbandry/plant_seed",
            "husbandry/ride_a_boat_with_a_goat", "husbandry/root", "husbandry/safely_harvest_honey", "husbandry/silk_touch_nest",
            "husbandry/tactical_fishing", "husbandry/tadpole_in_a_bucket", "husbandry/tame_an_animal", "husbandry/wax_off",
            "husbandry/wax_on", "nether/all_effects", "nether/all_potions", "nether/brew_potion", "nether/charge_respawn_anchor",
            "nether/create_beacon", "nether/create_full_beacon", "nether/distract_piglin", "nether/explore_nether",
            "nether/fast_travel", "nether/find_bastion", "nether/find_fortress", "nether/get_wither_skull",
            "nether/loot_bastion", "nether/nether_travel", "nether/obtain_ancient_debris", "nether/obtain_blaze_rod",
            "nether/obtain_crying_obsidian", "nether/obtain_netherite_armor", "nether/return_to_sender", "nether/ride_strider",
            "nether/ride_strider_in_overworld_lava", "nether/root", "nether/summon_wither", "nether/uneasy_alliance",
            "nether/use_lodestone", "story/cure_zombie_villager", "story/deflect_arrow", "story/enchant_item",
            "story/enter_the_end", "story/enter_the_nether", "story/follow_ender_eye", "story/form_obsidian",
            "story/iron_tools", "story/lava_bucket", "story/mine_diamond", "story/mine_stone",
            "story/obtain_armor", "story/root", "story/shiny_gear", "story/smelt_iron", "story/upgrade_tools"
        };
        for (String a : advList) {
            advancements.put(a, false);
        }
        stats.put("advancements", advancements);

        // Write to file in plugins folder
        File outputFile = new File(getDataFolder(), "possible_stats.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(outputFile)) {
            gson.toJson(stats, writer);
        }
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

    public static boolean isSyncEnabled(org.bukkit.plugin.Plugin plugin) {
        if (plugin == null) return false;
        org.bukkit.configuration.file.FileConfiguration config = ((JavaPlugin) plugin).getConfig();
        boolean mysqlEnabled = config.getBoolean("mysql.enabled", false);
        boolean supabaseEnabled = config.getBoolean("supabase.enabled", false);
        return mysqlEnabled || supabaseEnabled;
    }
    
    /**
     * Check if initial sync is needed based on existing data
     * @return true if initial sync should be performed
     */
    private boolean checkIfNeedsInitialSync() {
        // Check if we have recent level data
        if (levelManager != null) {
            List<LevelManager.PlayerLevelData> playerData = levelManager.getAllPlayerLevelData();
            if (!playerData.isEmpty()) {
                // Check if any player has recent data (within last 24 hours)
                long currentTime = System.currentTimeMillis();
                long oneDayAgo = currentTime - (24 * 60 * 60 * 1000);
                
                for (LevelManager.PlayerLevelData data : playerData) {
                    if (data.getLastUpdated() > oneDayAgo) {
                        return false; // Recent data exists, skip initial sync
                    }
                }
            }
        }
        
        // Check if we have recent award data
        if (awardManager != null && awardManager.isEnabled()) {
            // If no recent level data but we have award data, still skip to avoid duplicate calculations
            return false;
        }
        
        return true; // No recent data, need initial sync
    }
    
    // ==================== API GETTER METHODS ====================
    
    /**
     * Get the LevelManager instance
     * @return LevelManager instance
     */
    public LevelManager getLevelManager() {
        return levelManager;
    }
    
    /**
     * Get the AwardManager instance
     * @return AwardManager instance
     */
    public AwardManager getAwardManager() {
        return awardManager;
    }
    
    /**
     * Get the TownyManager instance
     * @return TownyManager instance
     */
    public TownyManager getTownyManager() {
        return townyManager;
    }
    
    /**
     * Get the SupabaseManager instance
     * @return SupabaseManager instance
     */
    public SupabaseManager getSupabaseManager() {
        return supabaseManager;
    }
}