package com.swinefeather.progression;

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
import com.google.gson.stream.JsonReader;
import java.io.StringReader;
import okhttp3.Response;

public class SupabaseManager {
    private final Plugin plugin;
    private final LogManager logger;
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
    
    public SupabaseManager(Plugin plugin, LogManager logManager) {
        this.plugin = plugin;
        this.logger = logManager;
        this.gson = new Gson();
        
        // Configure HTTP client with robust error handling
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(2, 2, TimeUnit.MINUTES)) // Reduced connections
                .retryOnConnectionFailure(false) // Disable automatic retry
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    try {
                        Response response = chain.proceed(request);
                        if (!response.isSuccessful()) {
                            logger.warning("HTTP " + response.code() + " for " + request.url() + ": " + response.message());
                        }
                        return response;
                    } catch (Exception e) {
                        logger.warning("Network error for " + request.url() + ": " + e.getMessage());
                        throw e;
                    }
                })
                .build();
    }
    
    public boolean initialize(ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            logger.debug("Supabase is disabled in configuration");
            return false;
        }
        
        this.enabled = true;
        this.supabaseUrl = config.getString("url", "");
        this.supabaseKey = config.getString("key", "");
        
        // Debug logging
        logger.debug("Supabase configuration loaded");
        
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
        
        logger.debug("Supabase connection established successfully");
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
            logger.severe("Failed to test Supabase connection", e);
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
                logger.severe("Error syncing player stats for " + player.getName(), e);
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
            
            logger.debug("Successfully synced stats for player: " + player.getName() + " (" + player.getUniqueId() + ")");
            
        } catch (Exception e) {
            logger.severe("Failed to sync player stats for " + player.getName() + " (" + player.getUniqueId() + ")", e);
        }
    }
    
    // Utility method for upserts with retry and throttling
    private void performUpsertWithRetry(Request request, String context, int maxRetries) {
        int attempt = 0;
        long delay = batchDelayMs;
        while (attempt <= maxRetries) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return;
                } else {
                    // Special handling for 409 Conflict (duplicate key): this is expected for upserts
                    if (response.code() == 409) {
                        // Do not log anything for 409 upsert conflicts
                        return;
                    }
                    String responseBody = response.body() != null ? response.body().string() : "";
                    logger.warning("Failed to upsert " + context + ": " + response.code() + " " + response.message());
                    if (!responseBody.isEmpty()) {
                        logger.warning("Response body: " + responseBody);
                    }
                    // Retry only on network/server errors (5xx or connection issues)
                    if (response.code() >= 500 || response.code() == 0) {
                        attempt++;
                        try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                        delay *= 2; // Exponential backoff
                        continue;
                    } else {
                        // Don't retry on 4xx errors except 429 (Too Many Requests)
                        if (response.code() == 429) {
                            attempt++;
                            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                            delay *= 2;
                            continue;
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                logger.warning("IOException during upsert " + context + ": " + e.getMessage());
                attempt++;
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                delay *= 2;
            }
        }
    }
    
    private void upsertPlayerData(JsonObject playerData) throws IOException {
        String url = supabaseUrl + "/rest/v1/players";
                    logger.supabaseRequest("Connecting to Supabase");
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), playerData.toString());
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();
        performUpsertWithRetry(request, "player data", 3);
    }
    
    private void upsertStatsData(JsonObject statsData) throws IOException {
        String url = supabaseUrl + "/rest/v1/player_stats";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), statsData.toString());
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(body)
                .build();
        performUpsertWithRetry(request, "stats data", 3);
    }
    
    private void processQueue() {
        if (isProcessing) return;
        
        isProcessing = true;
        new Thread(() -> {
            while (!requestQueue.isEmpty()) {
                CompletableFuture<Void> future = requestQueue.poll();
                if (future != null) {
                    future.complete(null);
                }
                
                try {
                    Thread.sleep(batchDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            isProcessing = false;
        }).start();
    }
    
    public void syncAllPlayers(Map<Player, Map<String, Object>> allPlayerStats) {
        if (!enabled) return;
        
        logger.debug("Syncing all players to Supabase...");
        int syncedCount = 0;
        
        for (Map.Entry<Player, Map<String, Object>> entry : allPlayerStats.entrySet()) {
            Player player = entry.getKey();
            Map<String, Object> stats = entry.getValue();
            
            syncPlayerStats(player, stats);
            syncedCount++;
            
            // Rate limiting
            try {
                Thread.sleep(batchDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        logger.debug("Synced " + syncedCount + " players to Supabase");
    }
    
    public void onPlayerQuit(Player player, Map<String, Object> stats) {
        if (enabled && syncOnPlayerQuit) {
            syncPlayerStats(player, stats);
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void shutdown() {
        enabled = false;
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.severe("Error closing Supabase connection", e);
            }
        }
    }
    
    public void syncPlayerStats(UUID uuid, String name, Map<String, Object> stats) {
        if (!enabled) return;
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        requestQueue.offer(future);
        
        future.thenRunAsync(() -> {
            try {
                performPlayerSync(uuid, name, stats);
            } catch (Exception e) {
                logger.severe("Error syncing player stats for " + name + " (" + uuid + ")", e);
            }
        });
        
        processQueue();
    }
    
    private void performPlayerSync(UUID uuid, String name, Map<String, Object> stats) {
        try {
            // Prepare player data
            JsonObject playerData = new JsonObject();
            playerData.addProperty("uuid", uuid.toString());
            playerData.addProperty("name", name);
            // Send as BIGINT (milliseconds since epoch) as expected by the schema
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
            statsRecord.addProperty("player_uuid", uuid.toString());
            statsRecord.add("stats", statsData);
            // Send as BIGINT (milliseconds since epoch) as expected by the schema
            statsRecord.addProperty("last_updated", System.currentTimeMillis());
            
            upsertStatsData(statsRecord);
            
            logger.debug("Successfully synced stats for offline player: " + name);
            
        } catch (Exception e) {
            logger.severe("Failed to sync player stats for " + name + " (" + uuid + ")", e);
        }
    }
    
    public String getLeaderboard(String stat, int limit) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            // Use the player_leaderboard view for better performance
            String url = supabaseUrl + "/rest/v1/player_leaderboard?order=total_points.desc.nullslast&limit=" + limit;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .addHeader("Prefer", "count=exact")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch leaderboard: " + response.code() + " " + response.message());
                    return "Failed to fetch leaderboard: " + response.code() + " " + response.message();
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching leaderboard: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getPlayerStats(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/player_stats?player_uuid=eq." + uuid;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch player stats: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching player stats: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getPlayerAwards(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/player_awards?player_uuid=eq." + uuid + "&order=achieved_at.desc";
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch player awards: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching player awards: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getPlayerMedals(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/player_medals?player_uuid=eq." + uuid;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch player medals: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching player medals: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getPlayerPoints(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/player_points?player_uuid=eq." + uuid;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch player points: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching player points: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getAwardLeaderboard(String awardId, int limit) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            // Use the award_leaderboard view for better performance
            String url = supabaseUrl + "/rest/v1/award_leaderboard?award_id=eq." + awardId + "&order=points.desc.nullslast&limit=" + limit;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch award leaderboard: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching award leaderboard: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getLevelLeaderboard(int limit) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            // Use the level_leaderboard view for better performance
            String url = supabaseUrl + "/rest/v1/level_leaderboard?order=level.desc,total_xp.desc.nullslast&limit=" + limit;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch level leaderboard: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching level leaderboard: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getAchievementProgress(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/achievement_progress?player_uuid=eq." + uuid + "&order=achievement_id,tier";
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch achievement progress: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching achievement progress: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getTopPlayersByStat(String statPath, int limit) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            // Query player_stats table with JSONB path
            String url = supabaseUrl + "/rest/v1/player_stats?select=player_uuid,stats->" + statPath + "&order=stats->" + statPath + ".desc.nullslast&limit=" + limit;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch top players by stat: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching top players by stat: " + e.getMessage());
            return "[]";
        }
    }

    // --- New upsert methods for awards, medals, and points ---
    public void upsertPlayerAward(UUID uuid, String name, String awardId, String awardName, String awardDescription, double points, long awardedAt, long statValue, String statPath) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String tier = "stone"; String medal = "bronze";
                if (points >= 6.0) { tier = "diamond"; medal = "gold"; }
                else if (points >= 4.0) { tier = "diamond"; medal = "silver"; }
                else if (points >= 3.0) { tier = "diamond"; medal = "bronze"; }
                else if (points >= 2.0) { tier = "iron"; medal = "gold"; }
                else if (points >= 1.0) { tier = "iron"; medal = "silver"; }
                else if (points >= 0.5) { tier = "stone"; medal = "gold"; }
                // Do NOT include 'id' in the upsert body!
                JsonObject awardData = new JsonObject();
                awardData.addProperty("player_uuid", uuid.toString());
                awardData.addProperty("award_id", awardId);
                awardData.addProperty("award_name", awardName);
                if (awardDescription != null && !awardDescription.isEmpty()) {
                    awardData.addProperty("award_description", awardDescription);
                }
                awardData.addProperty("tier", tier);
                awardData.addProperty("medal", medal);
                awardData.addProperty("points", points);
                if (statValue > 0) awardData.addProperty("stat_value", statValue);
                if (statPath != null && !statPath.isEmpty()) awardData.addProperty("stat_path", statPath);
                awardData.addProperty("achieved_at", awardedAt);
                String url = supabaseUrl + "/rest/v1/player_awards";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), awardData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                performUpsertWithRetry(request, "player_awards for " + name, 3);
                updatePlayerMedalsManually(uuid, name, medal);
                updatePlayerPointsManually(uuid, name, points);
            } catch (Exception e) {
                logger.severe("Error upserting player_awards for " + name, e);
            }
        });
    }

    public void upsertPlayerMedal(UUID uuid, String name, String awardId, String medalType, double points, int rank, long statValue, long awardedAt) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Get current medal counts for this player
                String getUrl = supabaseUrl + "/rest/v1/player_medals?player_uuid=eq." + uuid;
                Request getRequest = new Request.Builder()
                        .url(getUrl)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .build();
                
                int bronzeCount = 0, silverCount = 0, goldCount = 0, totalMedals = 0;
                try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                    if (getResponse.isSuccessful() && getResponse.body() != null) {
                        String responseBody = getResponse.body().string();
                        if (!responseBody.equals("[]")) {
                            JsonReader reader = new JsonReader(new StringReader(responseBody));
                            reader.setLenient(true);
                            com.google.gson.JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                            if (arr.size() > 0) {
                                com.google.gson.JsonObject existing = arr.get(0).getAsJsonObject();
                                bronzeCount = existing.has("bronze_count") ? existing.get("bronze_count").getAsInt() : 0;
                                silverCount = existing.has("silver_count") ? existing.get("silver_count").getAsInt() : 0;
                                goldCount = existing.has("gold_count") ? existing.get("gold_count").getAsInt() : 0;
                                totalMedals = existing.has("total_medals") ? existing.get("total_medals").getAsInt() : 0;
                            }
                        }
                    }
                }
                
                // Update counts based on medal type
                switch (medalType.toLowerCase()) {
                    case "bronze":
                        bronzeCount++;
                        break;
                    case "silver":
                        silverCount++;
                        break;
                    case "gold":
                        goldCount++;
                        break;
                }
                totalMedals = bronzeCount + silverCount + goldCount;
                
                // Upsert the updated medal counts
                JsonObject medalData = new JsonObject();
                medalData.addProperty("player_uuid", uuid.toString());
                medalData.addProperty("bronze_count", bronzeCount);
                medalData.addProperty("silver_count", silverCount);
                medalData.addProperty("gold_count", goldCount);
                medalData.addProperty("total_medals", totalMedals);
                
                String url = supabaseUrl + "/rest/v1/player_medals";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), medalData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                performUpsertWithRetry(request, "player_medals for " + name, 3);
            } catch (Exception e) {
                logger.severe("Error upserting player_medals for " + name, e);
            }
        });
    }

    public void upsertPlayerPoint(UUID uuid, String name, double totalPoints, int bronzeCount, int silverCount, int goldCount) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject pointData = new JsonObject();
                pointData.addProperty("player_uuid", uuid.toString());
                String safeNameForPoints = resolvePlayerName(uuid, name);
                pointData.addProperty("player_name", safeNameForPoints);
                pointData.addProperty("total_points", totalPoints);
                pointData.addProperty("last_updated", System.currentTimeMillis());
                String url = supabaseUrl + "/rest/v1/player_points?on_conflict=player_uuid";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), pointData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                performUpsertWithRetry(request, "player_points for " + name, 3);
            } catch (Exception e) {
                logger.severe("Error upserting player_points for " + name, e);
            }
        });
    }

    public void upsertPlayerMedalCounts(UUID uuid, String name, int bronzeCount, int silverCount, int goldCount, int totalMedals) {
        if (!enabled) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject medalData = new JsonObject();
                medalData.addProperty("player_uuid", uuid.toString());
                String safeNameForMedals = resolvePlayerName(uuid, name);
                medalData.addProperty("player_name", safeNameForMedals);
                medalData.addProperty("bronze_count", bronzeCount);
                medalData.addProperty("silver_count", silverCount);
                medalData.addProperty("gold_count", goldCount);
                medalData.addProperty("total_medals", totalMedals);
                medalData.addProperty("last_updated", System.currentTimeMillis());
                
                String url = supabaseUrl + "/rest/v1/player_medals";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), medalData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                performUpsertWithRetry(request, "player_medals for " + name, 3);
            } catch (Exception e) {
                logger.severe("Error upserting player_medals for " + name, e);
            }
        });
    }

    public void upsertPlayerLeaderboard(UUID uuid, String name, String awardId, String awardName, String medalType, 
                                       double points, long statValue, String statPath, int rank) {
        // This method is disabled because player_leaderboard is a view, not a table
        // Views cannot be inserted into directly
        if (!enabled) return;
        
        // Instead, we'll update the underlying tables (player_awards, player_medals, player_points)
        // which will automatically update the view
        logger.debug("Skipping leaderboard upsert - using view instead of table");
    }
    
    // Manual update methods since triggers are disabled
    /**
     * Resolve a non-null player name for Supabase writes.
     * Order of precedence:
     * - Provided name (if non-blank)
     * - Bukkit OfflinePlayer name
     * - Supabase players table lookup
     * - "Unknown"
     */
    private String resolvePlayerName(UUID uuid, String providedName) {
        if (providedName != null && !providedName.trim().isEmpty()) {
            return providedName;
        }
        try {
            String bukkitName = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (bukkitName != null && !bukkitName.trim().isEmpty()) {
                return bukkitName;
            }
        } catch (Exception ignored) { }

        try {
            String body = rawGet("/rest/v1/players?uuid=eq." + uuid);
            if (body != null && !body.equals("[]")) {
                com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(body));
                reader.setLenient(true);
                com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
                if (arr.size() > 0) {
                    com.google.gson.JsonObject obj = arr.get(0).getAsJsonObject();
                    if (obj.has("name")) {
                        String supabaseName = obj.get("name").getAsString();
                        if (supabaseName != null && !supabaseName.trim().isEmpty()) {
                            return supabaseName;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }

        return "Unknown";
    }

    private void updatePlayerMedalsManually(UUID uuid, String name, String medalType) {
        try {
            // First, get current medal counts
            String getUrl = supabaseUrl + "/rest/v1/player_medals?player_uuid=eq." + uuid;
            Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            int bronzeCount = 0, silverCount = 0, goldCount = 0, totalMedals = 0;
            
            try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                if (getResponse.isSuccessful() && getResponse.body() != null) {
                    String responseBody = getResponse.body().string();
                    if (!responseBody.equals("[]")) {
                        // Parse existing medal counts
                        JsonReader reader = new JsonReader(new StringReader(responseBody));
                        reader.setLenient(true);
                        com.google.gson.JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                        if (arr.size() > 0) {
                            com.google.gson.JsonObject existing = arr.get(0).getAsJsonObject();
                            bronzeCount = existing.has("bronze_count") ? existing.get("bronze_count").getAsInt() : 0;
                            silverCount = existing.has("silver_count") ? existing.get("silver_count").getAsInt() : 0;
                            goldCount = existing.has("gold_count") ? existing.get("gold_count").getAsInt() : 0;
                            totalMedals = existing.has("total_medals") ? existing.get("total_medals").getAsInt() : 0;
                        }
                    }
                }
            }
            
            // Update counts based on medal type
            switch (medalType.toLowerCase()) {
                case "bronze":
                    bronzeCount++;
                    break;
                case "silver":
                    silverCount++;
                    break;
                case "gold":
                    goldCount++;
                    break;
            }
            totalMedals = bronzeCount + silverCount + goldCount;
            
            // Upsert updated medal counts
            JsonObject medalData = new JsonObject();
            medalData.addProperty("player_uuid", uuid.toString());
            String safeNameForMedals = resolvePlayerName(uuid, name);
            medalData.addProperty("player_name", safeNameForMedals);
            medalData.addProperty("bronze_count", bronzeCount);
            medalData.addProperty("silver_count", silverCount);
            medalData.addProperty("gold_count", goldCount);
            medalData.addProperty("total_medals", totalMedals);
            medalData.addProperty("last_updated", System.currentTimeMillis());
            
            String url = supabaseUrl + "/rest/v1/player_medals";
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), medalData.toString());
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
                    // 409 Conflict is expected for upserts with duplicate keys
                    if (response.code() != 409) {
                        logger.warning("Failed to update player_medals manually: " + response.code() + " " + response.message());
                        if (response.body() != null) {
                            logger.warning("Response body: " + response.body().string());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Error updating player_medals manually for " + name, e);
        }
    }
    
    private void updatePlayerPointsManually(UUID uuid, String name, double points) {
        try {
            // First, get current points
            String getUrl = supabaseUrl + "/rest/v1/player_points?player_uuid=eq." + uuid;
            Request getRequest = new Request.Builder()
                    .url(getUrl)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            double currentPoints = 0.0;
            
            try (Response getResponse = httpClient.newCall(getRequest).execute()) {
                if (getResponse.isSuccessful() && getResponse.body() != null) {
                    String responseBody = getResponse.body().string();
                    if (!responseBody.equals("[]")) {
                        // Parse existing points
                        JsonReader reader = new JsonReader(new StringReader(responseBody));
                        reader.setLenient(true);
                        com.google.gson.JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                        if (arr.size() > 0) {
                            com.google.gson.JsonObject existing = arr.get(0).getAsJsonObject();
                            currentPoints = existing.has("total_points") ? existing.get("total_points").getAsDouble() : 0.0;
                        }
                    }
                }
            }
            
            // Add new points
            currentPoints += points;
            
            // Upsert updated points
            JsonObject pointData = new JsonObject();
            pointData.addProperty("player_uuid", uuid.toString());
            String safeNameForPoints = resolvePlayerName(uuid, name);
            pointData.addProperty("player_name", safeNameForPoints);
            pointData.addProperty("total_points", currentPoints);
            pointData.addProperty("last_updated", System.currentTimeMillis());
            
            String url = supabaseUrl + "/rest/v1/player_points?on_conflict=player_uuid";
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), pointData.toString());
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
                    // 409 Conflict is expected for upserts with duplicate keys
                    if (response.code() != 409) {
                        logger.warning("Failed to update player_points manually: " + response.code() + " " + response.message());
                        if (response.body() != null) {
                            logger.warning("Response body: " + response.body().string());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Error updating player_points manually for " + name, e);
        }
    }
    
    // Utility method to perform a raw GET request to Supabase REST API
    public String rawGet(String urlPath) {
        if (!enabled) return "[]";
        try {
            String url = supabaseUrl + urlPath;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed raw GET: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.severe("Error in rawGet for Supabase: " + urlPath, e);
            return "[]";
        }
    }

    /**
     * Force refresh player data from database for API
     * @param playerUUID The player's UUID
     */
    public void refreshPlayerData(UUID playerUUID) {
        // This would trigger a refresh of player data from the database
        // Implementation depends on your database structure
        logger.debug("Refreshing player data for: " + playerUUID);
    }

    public void syncLevelDefinitions(List<LevelManager.LevelDefinition> playerLevels, List<LevelManager.LevelDefinition> townLevels) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Sync player levels
                for (LevelManager.LevelDefinition level : playerLevels) {
                    JsonObject levelData = new JsonObject();
                    levelData.addProperty("level_type", "player");
                    levelData.addProperty("level", level.getLevel());
                    levelData.addProperty("xp_required", level.getXpRequired());
                    levelData.addProperty("title", level.getTitle());
                    levelData.addProperty("description", level.getDescription());
                    levelData.addProperty("color", level.getColor());
                    
                    String url = supabaseUrl + "/rest/v1/level_definitions";
                    RequestBody body = RequestBody.create(MediaType.parse("application/json"), levelData.toString());
                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(body)
                            .build();
                    
                    performUpsertWithRetry(request, "level definition sync", 3);
                }
                
                // Sync town levels
                for (LevelManager.LevelDefinition level : townLevels) {
                    JsonObject levelData = new JsonObject();
                    levelData.addProperty("level_type", "town");
                    levelData.addProperty("level", level.getLevel());
                    levelData.addProperty("xp_required", level.getXpRequired());
                    levelData.addProperty("title", level.getTitle());
                    levelData.addProperty("description", level.getDescription());
                    levelData.addProperty("color", level.getColor());
                    
                    String url = supabaseUrl + "/rest/v1/level_definitions";
                    RequestBody body = RequestBody.create(MediaType.parse("application/json"), levelData.toString());
                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(body)
                            .build();
                    
                    performUpsertWithRetry(request, "level definition sync", 3);
                }
                
                logger.debug("Successfully synced " + (playerLevels.size() + townLevels.size()) + " level definitions to Supabase");
                
            } catch (Exception e) {
                logger.severe("Failed to sync level definitions to Supabase", e);
            }
        });
    }
    
    public void syncAchievementDefinitions(List<AchievementManager.AchievementDefinition> achievements) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (AchievementManager.AchievementDefinition achievement : achievements) {
                    // Sync achievement definition
                    JsonObject achievementData = new JsonObject();
                    achievementData.addProperty("achievement_id", achievement.getId());
                    achievementData.addProperty("name", achievement.getName());
                    achievementData.addProperty("description", achievement.getDescription());
                    achievementData.addProperty("stat", achievement.getStat());
                    achievementData.addProperty("color", achievement.getColor());
                    achievementData.addProperty("achievement_type", achievement.getType());
                    
                    String url = supabaseUrl + "/rest/v1/achievement_definitions";
                    RequestBody body = RequestBody.create(MediaType.parse("application/json"), achievementData.toString());
                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(body)
                            .build();
                    
                    performUpsertWithRetry(request, "achievement definition sync", 3);
                    
                    // Sync achievement tiers
                    for (AchievementManager.AchievementTier tier : achievement.getTiers()) {
                        JsonObject tierData = new JsonObject();
                        tierData.addProperty("achievement_id", achievement.getId());
                        tierData.addProperty("tier", tier.getTier());
                        tierData.addProperty("name", tier.getName());
                        tierData.addProperty("description", tier.getDescription());
                        tierData.addProperty("threshold", tier.getThreshold());
                        tierData.addProperty("icon", tier.getIcon());
                        tierData.addProperty("points", tier.getPoints());
                        
                        String tierUrl = supabaseUrl + "/rest/v1/achievement_tiers";
                        RequestBody tierBody = RequestBody.create(MediaType.parse("application/json"), tierData.toString());
                        Request tierRequest = new Request.Builder()
                                .url(tierUrl)
                                .addHeader("apikey", supabaseKey)
                                .addHeader("Authorization", "Bearer " + supabaseKey)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "resolution=merge-duplicates")
                                .post(tierBody)
                                .build();
                        
                        performUpsertWithRetry(tierRequest, "achievement tier sync", 3);
                    }
                }
                
                logger.debug("Successfully synced " + achievements.size() + " achievement definitions to Supabase");
                
            } catch (Exception e) {
                logger.severe("Failed to sync achievement definitions to Supabase", e);
            }
        });
    }
    
    public void syncPlayerLevel(UUID uuid, String name, int level, int totalXP) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject playerData = new JsonObject();
                playerData.addProperty("uuid", uuid.toString());
                playerData.addProperty("name", name);
                playerData.addProperty("level", level);
                playerData.addProperty("total_xp", totalXP);
                playerData.addProperty("last_seen", System.currentTimeMillis());
                if (level > 1) {
                    playerData.addProperty("last_level_up", System.currentTimeMillis());
                }
                
                String url = supabaseUrl + "/rest/v1/players";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), playerData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                
                performUpsertWithRetry(request, "player level sync", 3);
                logger.debug("Successfully synced player level for " + name + " (Level " + level + ", XP: " + totalXP + ")");
                
            } catch (Exception e) {
                logger.severe("Failed to sync player level for " + name, e);
            }
        });
    }
    
    public void syncUnlockedAchievement(UUID uuid, String achievementId, int tier, int xpAwarded) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject achievementData = new JsonObject();
                achievementData.addProperty("player_uuid", uuid.toString());
                achievementData.addProperty("achievement_id", achievementId);
                achievementData.addProperty("tier", tier);
                achievementData.addProperty("xp_awarded", xpAwarded);
                achievementData.addProperty("unlocked_at", new java.sql.Timestamp(System.currentTimeMillis()).toString());
                
                String url = supabaseUrl + "/rest/v1/unlocked_achievements";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), achievementData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                
                performUpsertWithRetry(request, "unlocked achievement sync", 3);
                logger.debug("Successfully synced unlocked achievement: " + achievementId + " tier " + tier + " for player " + uuid);
                
            } catch (Exception e) {
                logger.severe("Failed to sync unlocked achievement for " + uuid, e);
            }
        });
    }

    public void syncTownLevel(String townName, int level, int totalXP) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject townData = new JsonObject();
                townData.addProperty("town_name", townName);
                townData.addProperty("level", level);
                townData.addProperty("total_xp", totalXP);
                townData.addProperty("last_updated", System.currentTimeMillis());
                
                String url = supabaseUrl + "/rest/v1/town_levels";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), townData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                
                performUpsertWithRetry(request, "town level sync", 3);
                logger.debug("Successfully synced town level for " + townName + " (Level " + level + ", XP: " + totalXP + ")");
                
            } catch (Exception e) {
                logger.severe("Failed to sync town level for " + townName, e);
            }
        });
    }
    
    public void syncTownStats(String townName, Map<String, Object> stats) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject townData = new JsonObject();
                townData.addProperty("town_name", townName);
                townData.addProperty("stats", gson.toJson(stats));
                townData.addProperty("last_updated", System.currentTimeMillis());
                
                String url = supabaseUrl + "/rest/v1/town_stats";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), townData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                
                performUpsertWithRetry(request, "town stats sync", 3);
                logger.debug("Successfully synced town stats for " + townName);
                
            } catch (Exception e) {
                logger.severe("Failed to sync town stats for " + townName, e);
            }
        });
    }
    
    public void syncTownAchievement(String townName, String achievementId, int tier, int xpAwarded) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject achievementData = new JsonObject();
                achievementData.addProperty("town_name", townName);
                achievementData.addProperty("achievement_id", achievementId);
                achievementData.addProperty("tier", tier);
                achievementData.addProperty("xp_awarded", xpAwarded);
                achievementData.addProperty("unlocked_at", new java.sql.Timestamp(System.currentTimeMillis()).toString());
                
                String url = supabaseUrl + "/rest/v1/unlocked_achievements";
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), achievementData.toString());
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", supabaseKey)
                        .addHeader("Authorization", "Bearer " + supabaseKey)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .post(body)
                        .build();
                
                performUpsertWithRetry(request, "town achievement sync", 3);
                logger.debug("Successfully synced town achievement: " + achievementId + " tier " + tier + " for town " + townName);
                
            } catch (Exception e) {
                logger.severe("Failed to sync town achievement for " + townName, e);
            }
        });
    }
    
    public String getTownLevels(int limit) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/town_levels?order=level.desc,total_xp.desc.nullslast&limit=" + limit;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch town levels: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching town levels: " + e.getMessage());
            return "[]";
        }
    }
    
    public String getTownStats(String townName) {
        if (!enabled) return "Supabase not enabled";
        
        try {
            String url = supabaseUrl + "/rest/v1/town_stats?town_name=eq." + townName;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to fetch town stats: " + response.code() + " " + response.message());
                    return "[]";
                }
                return response.body() != null ? response.body().string() : "[]";
            }
        } catch (Exception e) {
            logger.warning("Error fetching town stats: " + e.getMessage());
            return "[]";
        }
    }
} 