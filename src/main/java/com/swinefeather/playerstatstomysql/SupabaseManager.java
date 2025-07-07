package com.swinefeather.playerstatstomysql;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SupabaseManager {
    private final Plugin plugin;
    private final Logger logger;
    private final Gson gson;
    private final OkHttpClient httpClient;
    
    // Configuration
    private String supabaseUrl;
    private String supabaseKey;
    private boolean enabled;
    
    // Performance settings
    private int batchSize;
    private long batchDelayMs;
    private int maxConcurrentRequests;
    private int timeoutSeconds;
    
    // Sync settings
    private boolean syncOnPlayerQuit;
    private long batchSyncInterval;
    private boolean realTimeUpdates;
    
    // Connection pool
    private Connection connection;
    private final Object connectionLock = new Object();
    
    // Rate limiting
    private final Queue<CompletableFuture<Void>> requestQueue = new LinkedList<>();
    private final Map<String, Long> lastRequestTime = new ConcurrentHashMap<>();
    private volatile boolean isProcessing = false;
    
    public SupabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        
        // Configure HTTP client with timeout
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }
    
    public boolean initialize(ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            logger.info("Supabase is disabled in configuration");
            return false;
        }
        
        this.enabled = true;
        this.supabaseUrl = config.getString("url", "");
        this.supabaseKey = config.getString("key", "");
        
        // Debug logging
        logger.info("Supabase configuration loaded:");
        logger.info("URL: " + supabaseUrl);
        logger.info("Key: " + (supabaseKey.length() > 10 ? supabaseKey.substring(0, 10) + "..." : supabaseKey));
        
        // Performance settings
        ConfigurationSection perfSection = config.getConfigurationSection("performance");
        if (perfSection != null) {
            this.batchSize = perfSection.getInt("batch_size", 1);
            this.batchDelayMs = perfSection.getLong("batch_delay_ms", 1000);
            this.maxConcurrentRequests = perfSection.getInt("max_concurrent_requests", 1);
            this.timeoutSeconds = perfSection.getInt("timeout_seconds", 30);
        }
        
        // Sync settings
        ConfigurationSection syncSection = config.getConfigurationSection("sync");
        if (syncSection != null) {
            this.syncOnPlayerQuit = syncSection.getBoolean("on_player_quit", true);
            this.batchSyncInterval = syncSection.getLong("batch_sync_interval", 300000);
            this.realTimeUpdates = syncSection.getBoolean("real_time_updates", false);
        }
        
        if (supabaseUrl.isEmpty() || supabaseKey.isEmpty()) {
            logger.severe("Supabase URL or key is missing in configuration!");
            return false;
        }
        
        // Test connection
        if (!testConnection()) {
            logger.severe("Failed to connect to Supabase! Check your URL and key.");
            return false;
        }
        
        logger.info("Supabase connection established successfully");
        return true;
    }
    
    private boolean testConnection() {
        try {
            // Test REST API connection
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/")
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to test Supabase connection", e);
            return false;
        }
    }
    
    public void syncPlayerStats(Player player, Map<String, Object> stats) {
        if (!enabled) return;
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        requestQueue.offer(future);
        
        future.thenRunAsync(() -> {
            try {
                performPlayerSync(player, stats);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[PlayerStatsToMySQL] Error syncing player stats for " + player.getName(), e);
            }
        });
        
        processQueue();
    }
    
    private void performPlayerSync(Player player, Map<String, Object> stats) {
        try {
            // Prepare player data
            JsonObject playerData = new JsonObject();
            playerData.addProperty("uuid", player.getUniqueId().toString());
            playerData.addProperty("name", player.getName());
            playerData.addProperty("last_seen", System.currentTimeMillis());
            
            // Prepare stats data
            JsonObject statsData = new JsonObject();
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                if (entry.getValue() != null) {
                    statsData.addProperty(entry.getKey(), entry.getValue().toString());
                }
            }
            
            // UPSERT player data
            upsertPlayerData(playerData);
            
            // UPSERT stats data
            JsonObject statsRecord = new JsonObject();
            statsRecord.addProperty("player_uuid", player.getUniqueId().toString());
            statsRecord.add("stats", statsData);
            statsRecord.addProperty("last_updated", System.currentTimeMillis());
            
            upsertStatsData(statsRecord);
            
            logger.info("[PlayerStatsToMySQL] Successfully synced stats for player: " + player.getName() + " (" + player.getUniqueId() + ")");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PlayerStatsToMySQL] Failed to sync player stats for " + player.getName() + " (" + player.getUniqueId() + ")", e);
        }
    }
    
    private void upsertPlayerData(JsonObject playerData) throws IOException {
        String url = supabaseUrl + "/rest/v1/players";
        
        // Debug: Log the exact URL being used
        logger.info("DEBUG: Attempting to connect to Supabase URL: " + url);
        
        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"),
            playerData.toString()
        );
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = "";
                try {
                    responseBody = response.body() != null ? response.body().string() : "";
                } catch (Exception e) {
                    responseBody = "Could not read response body";
                }
                throw new IOException("Failed to upsert player data: " + response.code() + " " + response.message() + " - Response: " + responseBody);
            }
        } catch (Exception e) {
            logger.severe("[PlayerStatsToMySQL] ERROR: Exception occurred while connecting to: " + url);
            logger.severe("[PlayerStatsToMySQL] ERROR: Exception message: " + e.getMessage());
            throw e;
        }
    }
    
    private void upsertStatsData(JsonObject statsData) throws IOException {
        String url = supabaseUrl + "/rest/v1/player_stats";
        
        // Debug: Log the exact URL being used
        logger.info("DEBUG: Attempting to connect to Supabase URL: " + url);
        
        RequestBody body = RequestBody.create(
            MediaType.parse("application/json"),
            statsData.toString()
        );
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = "";
                try {
                    responseBody = response.body() != null ? response.body().string() : "";
                } catch (Exception e) {
                    responseBody = "Could not read response body";
                }
                throw new IOException("Failed to upsert stats data: " + response.code() + " " + response.message() + " - Response: " + responseBody);
            }
        } catch (Exception e) {
            logger.severe("[PlayerStatsToMySQL] ERROR: Exception occurred while connecting to: " + url);
            logger.severe("[PlayerStatsToMySQL] ERROR: Exception message: " + e.getMessage());
            throw e;
        }
    }
    
    private void processQueue() {
        if (isProcessing) return;
        
        isProcessing = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                while (!requestQueue.isEmpty()) {
                    CompletableFuture<Void> future = requestQueue.poll();
                    if (future != null) {
                        future.complete(null);
                        
                        // Rate limiting delay
                        if (batchDelayMs > 0) {
                            Thread.sleep(batchDelayMs);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("[PlayerStatsToMySQL] Supabase queue processing interrupted");
            } finally {
                isProcessing = false;
            }
        });
    }
    
    public void syncAllPlayers(Map<Player, Map<String, Object>> allPlayerStats) {
        if (!enabled) return;
        
        logger.info("Starting batch sync for " + allPlayerStats.size() + " players");
        
        List<Map.Entry<Player, Map<String, Object>>> playerList = 
            new ArrayList<>(allPlayerStats.entrySet());
        
        // Process in batches
        for (int i = 0; i < playerList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, playerList.size());
            List<Map.Entry<Player, Map<String, Object>>> batch = 
                playerList.subList(i, endIndex);
            
            final int batchNum = (i / batchSize) + 1;
            
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                for (Map.Entry<Player, Map<String, Object>> entry : batch) {
                    Player player = entry.getKey();
                    Map<String, Object> stats = entry.getValue();
                    
                    if (player != null && player.isOnline()) {
                        syncPlayerStats(player, stats);
                    }
                }
                logger.info("[PlayerStatsToMySQL] Completed batch " + batchNum + " of " + ((playerList.size() - 1) / batchSize + 1));
            }, (i / batchSize) * (batchDelayMs / 50)); // Convert ms to ticks
        }
    }
    
    public void onPlayerQuit(Player player, Map<String, Object> stats) {
        if (!enabled || !syncOnPlayerQuit) return;
        
        logger.info("[PlayerStatsToMySQL] Syncing final stats for player: " + player.getName());
        syncPlayerStats(player, stats);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
        
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing Supabase connection", e);
            }
        }
    }
    
    // Add new method for offline player sync
    public void syncPlayerStats(UUID uuid, String name, Map<String, Object> stats) {
        if (!enabled) return;
        CompletableFuture<Void> future = new CompletableFuture<>();
        requestQueue.offer(future);
        future.thenRunAsync(() -> {
            try {
                performPlayerSync(uuid, name, stats);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[PlayerStatsToMySQL] Error syncing player stats for " + name + " (" + uuid + ")", e);
            }
        });
        processQueue();
    }

    // Add new performPlayerSync for offline players
    private void performPlayerSync(UUID uuid, String name, Map<String, Object> stats) {
        try {
            JsonObject playerData = new JsonObject();
            playerData.addProperty("uuid", uuid.toString());
            playerData.addProperty("name", name);
            playerData.addProperty("last_seen", System.currentTimeMillis());

            JsonObject statsData = new JsonObject();
            for (Map.Entry<String, Object> entry : stats.entrySet()) {
                if (entry.getValue() != null) {
                    statsData.addProperty(entry.getKey(), entry.getValue().toString());
                }
            }

            upsertPlayerData(playerData);

            JsonObject statsRecord = new JsonObject();
            statsRecord.addProperty("player_uuid", uuid.toString());
            statsRecord.add("stats", statsData);
            statsRecord.addProperty("last_updated", System.currentTimeMillis());

            upsertStatsData(statsRecord);

            logger.info("[PlayerStatsToMySQL] Successfully synced stats for player: " + name + " (" + uuid + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PlayerStatsToMySQL] Failed to sync player stats for " + name + " (" + uuid + ")", e);
        }
    }
} 