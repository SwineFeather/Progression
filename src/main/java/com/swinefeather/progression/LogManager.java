package com.swinefeather.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

public class LogManager {
    private final Plugin plugin;
    private final Logger logger;
    private String logLevel;
    private boolean showStatDetails;
    private boolean showPlayerProcessing;
    private boolean showAwardCalculations;
    private boolean showSupabaseRequests;
    private boolean showStatPaths;
    private boolean showAvailableStats;
    
    public LogManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();
    }
    
    private void loadConfig() {
        ConfigurationSection logConfig = plugin.getConfig().getConfigurationSection("logging");
        if (logConfig != null) {
            this.logLevel = logConfig.getString("level", "minimal");
            
            ConfigurationSection debugConfig = logConfig.getConfigurationSection("debug");
            if (debugConfig != null) {
                this.showStatDetails = debugConfig.getBoolean("show_stat_details", false);
                this.showPlayerProcessing = debugConfig.getBoolean("show_player_processing", false);
                this.showAwardCalculations = debugConfig.getBoolean("show_award_calculations", false);
                this.showSupabaseRequests = debugConfig.getBoolean("show_supabase_requests", false);
                this.showStatPaths = debugConfig.getBoolean("show_stat_paths", false);
                this.showAvailableStats = debugConfig.getBoolean("show_available_stats", false);
            }
        }
    }
    
    public void info(String message) {
        logger.info("[Progression] " + message);
    }
    
    public void warning(String message) {
        logger.warning("[Progression] " + message);
    }
    
    public void severe(String message) {
        logger.severe("[Progression] " + message);
    }
    
    public void severe(String message, Throwable throwable) {
        logger.severe("[Progression] " + message + ": " + throwable.getMessage());
        if (isDebugEnabled()) {
            throwable.printStackTrace();
        }
    }
    
    public void debug(String message) {
        if (isDebugEnabled()) {
            logger.info("[Progression] [DEBUG] " + message);
        }
    }
    
    public void statDetail(String message) {
        if (showStatDetails && isDebugEnabled()) {
            logger.info("[Progression] [STAT] " + message);
        }
    }
    
    public void playerProcessing(String message) {
        if (showPlayerProcessing && isVerboseEnabled()) {
            logger.info("[Progression] [PLAYER] " + message);
        }
    }
    
    public void awardCalculation(String message) {
        if (showAwardCalculations && isDebugEnabled()) {
            logger.info("[Progression] [AWARD] " + message);
        }
    }
    
    public void supabaseRequest(String message) {
        if (showSupabaseRequests && isDebugEnabled()) {
            logger.info("[Progression] [SUPABASE] " + message);
        }
    }
    
    public void statPath(String message) {
        if (showStatPaths && isDebugEnabled()) {
            logger.info("[Progression] [PATH] " + message);
        }
    }
    
    public void availableStats(String message) {
        if (showAvailableStats && isMaxEnabled()) {
            logger.info("[Progression] [STATS] " + message);
        }
    }
    
    public boolean isMinimalEnabled() {
        return "minimal".equals(logLevel) || isVerboseEnabled();
    }
    
    public boolean isVerboseEnabled() {
        return "verbose".equals(logLevel) || isDebugEnabled();
    }
    
    public boolean isDebugEnabled() {
        return "debug".equals(logLevel) || isMaxEnabled();
    }
    
    public boolean isMaxEnabled() {
        return "max".equals(logLevel);
    }

    public void verbose(String message) {
        if (isVerboseEnabled()) {
            logger.info("[Progression] [VERBOSE] " + message);
        }
    }
    
    public void reloadConfig() {
        loadConfig();
    }
} 