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
import com.google.gson.stream.JsonReader;
import java.io.StringReader;

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
                logger.log(Level.SEVERE, "Error syncing player stats for " + player.getName(), e);
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
            
            logger.info("Successfully synced stats for player: " + player.getName() + " (" + player.getUniqueId() + ")");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to sync player stats for " + player.getName() + " (" + player.getUniqueId() + ")", e);
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
            logger.severe("ERROR: Exception occurred while connecting to: " + url);
            logger.severe("ERROR: Exception message: " + e.getMessage());
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
            logger.severe("ERROR: Exception occurred while connecting to: " + url);
            logger.severe("ERROR: Exception message: " + e.getMessage());
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
                logger.warning("Supabase queue processing interrupted");
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
                logger.info("Completed batch " + batchNum + " of " + ((playerList.size() - 1) / batchSize + 1));
            }, (i / batchSize) * (batchDelayMs / 50)); // Convert ms to ticks
        }
    }
    
    public void onPlayerQuit(Player player, Map<String, Object> stats) {
        if (!enabled || !syncOnPlayerQuit) return;
        
        logger.info("Syncing final stats for player: " + player.getName());
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
                logger.log(Level.SEVERE, "Error syncing player stats for " + name + " (" + uuid + ")", e);
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

            logger.info("Successfully synced stats for player: " + name + " (" + uuid + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to sync player stats for " + name + " (" + uuid + ")", e);
        }
    }

    // --- Leaderboard/stat query methods ---
    public String getLeaderboard(String stat, int limit) {
        if (!enabled) return "Supabase not enabled";
        String url = supabaseUrl + "/rest/v1/player_stats?select=player_uuid,stats->>" + stat + "&order=stats->>" + stat + ".desc.nullslast&limit=" + limit;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Failed to fetch leaderboard: " + response.code() + " " + response.message();
            }
            return response.body() != null ? response.body().string() : "No data";
        } catch (Exception e) {
            return "Error fetching leaderboard: " + e.getMessage();
        }
    }
    public String getPlayerStats(UUID uuid) {
        if (!enabled) return "Supabase not enabled";
        String url = supabaseUrl + "/rest/v1/player_stats?player_uuid=eq." + uuid + "&select=*";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Failed to fetch player stats: " + response.code() + " " + response.message();
            }
            return response.body() != null ? response.body().string() : "No data";
        } catch (Exception e) {
            return "Error fetching player stats: " + e.getMessage();
        }
    }
    public String getAwardLeaderboard(String awardId, int limit) {
        if (!enabled) return "Supabase not enabled";
        String url = supabaseUrl + "/rest/v1/player_awards?award_id=eq." + awardId + "&order=points.desc.nullslast&limit=" + limit;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer " + supabaseKey)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "Failed to fetch award leaderboard: " + response.code() + " " + response.message();
            }
            return response.body() != null ? response.body().string() : "No data";
        } catch (Exception e) {
            return "Error fetching award leaderboard: " + e.getMessage();
        }
    }

    // --- New upsert methods for awards, medals, and points ---
    public void upsertPlayerAward(UUID uuid, String name, String awardId, String awardName, double points, long awardedAt) {
        if (!enabled) return;
        try {
            // Determine tier and medal from awardId and points
            String tier = "stone"; // default
            String medal = "bronze"; // default
            
            // This is a simplified logic - in practice, the AwardManager should pass these values
            if (points >= 6.0) {
                tier = "diamond";
                medal = "gold";
            } else if (points >= 4.0) {
                tier = "diamond";
                medal = "silver";
            } else if (points >= 3.0) {
                tier = "diamond";
                medal = "bronze";
            } else if (points >= 2.0) {
                tier = "iron";
                medal = "gold";
            } else if (points >= 1.0) {
                tier = "iron";
                medal = "silver";
            } else if (points >= 0.5) {
                tier = "stone";
                medal = "gold";
            }
            
            JsonObject awardData = new JsonObject();
            awardData.addProperty("player_uuid", uuid.toString());
            awardData.addProperty("award_id", awardId);
            awardData.addProperty("award_name", awardName);
            awardData.addProperty("tier", tier);
            awardData.addProperty("medal", medal);
            awardData.addProperty("points", points);
            awardData.addProperty("achieved_at", new java.sql.Timestamp(awardedAt).toString());
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
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.warning("Failed to upsert player_awards: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error upserting player_awards for " + name, e);
        }
    }

    public void upsertPlayerMedal(UUID uuid, String name, String awardId, String medalType, double points, int rank, long statValue, long awardedAt) {
        if (!enabled) return;
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
                        // Parse existing counts
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
            totalMedals++;
            
            // Upsert updated medal counts
            JsonObject medalData = new JsonObject();
            medalData.addProperty("player_uuid", uuid.toString());
            medalData.addProperty("bronze_count", bronzeCount);
            medalData.addProperty("silver_count", silverCount);
            medalData.addProperty("gold_count", goldCount);
            medalData.addProperty("total_medals", totalMedals);
            medalData.addProperty("last_updated", new java.sql.Timestamp(System.currentTimeMillis()).toString());
            
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
                    logger.warning("Failed to upsert player_medals: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error upserting player_medals for " + name, e);
        }
    }

    public void upsertPlayerPoint(UUID uuid, String name, double totalPoints) {
        if (!enabled) return;
        try {
            JsonObject pointData = new JsonObject();
            pointData.addProperty("player_uuid", uuid.toString());
            pointData.addProperty("total_points", totalPoints);
            pointData.addProperty("last_updated", new java.sql.Timestamp(System.currentTimeMillis()).toString());
            String url = supabaseUrl + "/rest/v1/player_points";
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
                    logger.warning("Failed to upsert player_points: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error upserting player_points for " + name, e);
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
            logger.log(Level.SEVERE, "Error in rawGet for Supabase: " + urlPath, e);
            return "[]";
        }
    }
} 