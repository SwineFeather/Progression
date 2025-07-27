package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelDatabaseManager {
    private final Main plugin;
    private final LogManager logManager;
    private final SupabaseManager supabaseManager;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    private String supabaseUrl;
    private String supabaseKey;
    private boolean enabled = false;

    public LevelDatabaseManager(Main plugin, SupabaseManager supabaseManager) {
        this.plugin = plugin;
        this.logManager = plugin.logManager;
        this.supabaseManager = supabaseManager;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        
        initialize();
    }

    private void initialize() {
        ConfigurationSection supabaseConfig = plugin.getConfig().getConfigurationSection("supabase");
        if (supabaseConfig != null && supabaseConfig.getBoolean("enabled", false)) {
            this.supabaseUrl = supabaseConfig.getString("url");
            this.supabaseKey = supabaseConfig.getString("key");
            this.enabled = true;
            logManager.debug("Level database manager initialized with Supabase");
        } else {
            logManager.debug("Level database manager disabled - Supabase not configured");
        }
    }

    public boolean isEnabled() {
        return enabled && supabaseManager != null && supabaseManager.isEnabled();
    }

    // Sync player level data to database
    public CompletableFuture<Boolean> syncPlayerLevel(UUID playerUUID, String playerName, int level, int totalXP) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject playerData = new JsonObject();
                playerData.addProperty("uuid", playerUUID.toString());
                playerData.addProperty("name", playerName);
                playerData.addProperty("level", level);
                playerData.addProperty("total_xp", totalXP);
                playerData.addProperty("last_level_up", System.currentTimeMillis());

                String url = supabaseUrl + "/rest/v1/players";
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
                    if (response.isSuccessful()) {
                        logManager.debug("Synced player level data for " + playerName);
                        return true;
                    } else {
                        logManager.warning("Failed to sync player level data for " + playerName + ": " + response.code());
                        return false;
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error syncing player level data for " + playerName + ": " + e.getMessage());
                return false;
            }
        });
    }

    // Sync level definitions to database
    public CompletableFuture<Boolean> syncLevelDefinitions(List<LevelManager.LevelDefinition> playerLevels, List<LevelManager.LevelDefinition> townLevels) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Sync player level definitions
                for (LevelManager.LevelDefinition levelDef : playerLevels) {
                    JsonObject levelData = new JsonObject();
                    levelData.addProperty("level_type", "player");
                    levelData.addProperty("level", levelDef.getLevel());
                    levelData.addProperty("xp_required", levelDef.getXpRequired());
                    levelData.addProperty("title", levelDef.getTitle());
                    levelData.addProperty("description", levelDef.getDescription());
                    levelData.addProperty("color", levelDef.getColor());

                    String url = supabaseUrl + "/rest/v1/level_definitions";
                    RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), 
                        levelData.toString()
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
                            // 409 Conflict is expected for upserts with duplicate keys
                            if (response.code() != 409) {
                                logManager.warning("Failed to sync player level definition " + levelDef.getLevel() + ": " + response.code());
                            }
                        }
                    }
                }

                // Sync town level definitions
                for (LevelManager.LevelDefinition levelDef : townLevels) {
                    JsonObject levelData = new JsonObject();
                    levelData.addProperty("level_type", "town");
                    levelData.addProperty("level", levelDef.getLevel());
                    levelData.addProperty("xp_required", levelDef.getXpRequired());
                    levelData.addProperty("title", levelDef.getTitle());
                    levelData.addProperty("description", levelDef.getDescription());
                    levelData.addProperty("color", levelDef.getColor());

                    String url = supabaseUrl + "/rest/v1/level_definitions";
                    RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), 
                        levelData.toString()
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
                            // 409 Conflict is expected for upserts with duplicate keys
                            if (response.code() != 409) {
                                logManager.warning("Failed to sync town level definition " + levelDef.getLevel() + ": " + response.code());
                            }
                        }
                    }
                }

                logManager.debug("Synced " + playerLevels.size() + " player level definitions and " + townLevels.size() + " town level definitions");
                return true;
            } catch (Exception e) {
                logManager.severe("Error syncing level definitions: " + e.getMessage());
                return false;
            }
        });
    }

    // Sync achievement definitions to database
    public CompletableFuture<Boolean> syncAchievementDefinitions(List<AchievementManager.AchievementDefinition> achievements) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
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
                    RequestBody body = RequestBody.create(
                        MediaType.parse("application/json"), 
                        achievementData.toString()
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
                            // 409 Conflict is expected for upserts with duplicate keys
                            if (response.code() != 409) {
                                logManager.warning("Failed to sync achievement definition " + achievement.getId() + ": " + response.code());
                            }
                        }
                    }

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
                        RequestBody tierBody = RequestBody.create(
                            MediaType.parse("application/json"), 
                            tierData.toString()
                        );

                        Request tierRequest = new Request.Builder()
                            .url(tierUrl)
                            .addHeader("apikey", supabaseKey)
                            .addHeader("Authorization", "Bearer " + supabaseKey)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Prefer", "resolution=merge-duplicates")
                            .post(tierBody)
                            .build();

                        try (Response tierResponse = httpClient.newCall(tierRequest).execute()) {
                            if (!tierResponse.isSuccessful()) {
                                // 409 Conflict is expected for upserts with duplicate keys
                                if (tierResponse.code() != 409) {
                                    logManager.warning("Failed to sync achievement tier " + achievement.getId() + " tier " + tier.getTier() + ": " + tierResponse.code());
                                }
                            }
                        }
                    }
                }

                logManager.debug("Synced " + achievements.size() + " achievement definitions with their tiers");
                return true;
            } catch (Exception e) {
                logManager.severe("Error syncing achievement definitions: " + e.getMessage());
                return false;
            }
        });
    }

    // Sync unlocked achievement to database
    public CompletableFuture<Boolean> syncUnlockedAchievement(UUID playerUUID, String townName, String achievementId, int tier, int xpAwarded) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject achievementData = new JsonObject();
                if (playerUUID != null) {
                    achievementData.addProperty("player_uuid", playerUUID.toString());
                }
                if (townName != null) {
                    achievementData.addProperty("town_name", townName);
                }
                achievementData.addProperty("achievement_id", achievementId);
                achievementData.addProperty("tier", tier);
                achievementData.addProperty("xp_awarded", xpAwarded);

                String url = supabaseUrl + "/rest/v1/unlocked_achievements";
                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), 
                    achievementData.toString()
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
                    if (response.isSuccessful()) {
                        String entity = playerUUID != null ? playerUUID.toString() : townName;
                        logManager.debug("Synced unlocked achievement " + achievementId + " tier " + tier + " for " + entity);
                        return true;
                    } else {
                        logManager.warning("Failed to sync unlocked achievement " + achievementId + " tier " + tier + ": " + response.code());
                        return false;
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error syncing unlocked achievement " + achievementId + " tier " + tier + ": " + e.getMessage());
                return false;
            }
        });
    }

    // Get player level data from database
    public CompletableFuture<JsonObject> getPlayerLevelData(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = supabaseUrl + "/rest/v1/players?uuid=eq." + playerUUID.toString();
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        if (responseBody.startsWith("[") && responseBody.endsWith("]")) {
                            JsonArray array = gson.fromJson(responseBody, JsonArray.class);
                            if (array.size() > 0) {
                                return array.get(0).getAsJsonObject();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error getting player level data for " + playerUUID + ": " + e.getMessage());
            }
            return null;
        });
    }

    // Get level leaderboard from database
    public CompletableFuture<JsonArray> getLevelLeaderboard(int limit) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = supabaseUrl + "/rest/v1/level_leaderboard?limit=" + limit;
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        return gson.fromJson(responseBody, JsonArray.class);
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error getting level leaderboard: " + e.getMessage());
            }
            return null;
        });
    }

    // Get achievement progress from database
    public CompletableFuture<JsonArray> getAchievementProgress(UUID playerUUID) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = supabaseUrl + "/rest/v1/achievement_progress?player_uuid=eq." + playerUUID.toString();
                Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", supabaseKey)
                    .addHeader("Authorization", "Bearer " + supabaseKey)
                    .get()
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        return gson.fromJson(responseBody, JsonArray.class);
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error getting achievement progress for " + playerUUID + ": " + e.getMessage());
            }
            return null;
        });
    }

    // Sync town data to database
    public CompletableFuture<Boolean> syncTownData(String townName, Map<String, Object> townStats) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Convert town stats to JSON
                JsonObject townData = new JsonObject();
                townData.addProperty("town_name", townName);
                
                // Add all town stats as properties
                for (Map.Entry<String, Object> entry : townStats.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        townData.addProperty(entry.getKey(), (String) value);
                    } else if (value instanceof Number) {
                        townData.addProperty(entry.getKey(), (Number) value);
                    } else if (value instanceof Boolean) {
                        townData.addProperty(entry.getKey(), (Boolean) value);
                    }
                }

                String url = supabaseUrl + "/rest/v1/town_stats";
                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), 
                    townData.toString()
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
                    if (response.isSuccessful()) {
                        logManager.debug("Synced town data for " + townName);
                        return true;
                    } else {
                        logManager.warning("Failed to sync town data for " + townName + ": " + response.code());
                        return false;
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error syncing town data for " + townName + ": " + e.getMessage());
                return false;
            }
        });
    }

    // Sync town level data to database
    public CompletableFuture<Boolean> syncTownLevel(String townName, TownyManager.TownLevelData levelData) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject townLevelData = new JsonObject();
                townLevelData.addProperty("town_name", townName);
                townLevelData.addProperty("level", levelData.getLevel());
                townLevelData.addProperty("total_xp", levelData.getTotalXP());
                townLevelData.addProperty("last_updated", levelData.getLastUpdated());

                String url = supabaseUrl + "/rest/v1/town_levels";
                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), 
                    townLevelData.toString()
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
                    if (response.isSuccessful()) {
                        logManager.debug("Synced town level data for " + townName);
                        return true;
                    } else {
                        logManager.warning("Failed to sync town level data for " + townName + ": " + response.code());
                        return false;
                    }
                }
            } catch (Exception e) {
                logManager.severe("Error syncing town level data for " + townName + ": " + e.getMessage());
                return false;
            }
        });
    }

    public void shutdown() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }
} 