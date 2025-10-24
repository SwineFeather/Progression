package com.swinefeather.progression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * CacheManager provides in-memory caching for frequently accessed data
 * to improve performance and reduce database load.
 */
public class CacheManager {
    private final Main plugin;
    private final LogManager logManager;
    
    // Cache storage
    private final Map<String, CacheEntry<Object>> playerStatsCache;
    private final Map<String, CacheEntry<Object>> playerLevelsCache;
    private final Map<String, CacheEntry<Object>> townStatsCache;
    private final Map<String, CacheEntry<Object>> townLevelsCache;
    private final Map<String, CacheEntry<Object>> leaderboardCache;
    
    // Cache configuration
    private final long playerStatsTTL;
    private final long playerLevelsTTL;
    private final long townStatsTTL;
    private final long townLevelsTTL;
    private final long leaderboardTTL;
    private final int maxCacheSize;
    
    // Cleanup scheduler
    private final ScheduledExecutorService cleanupScheduler;
    
    public CacheManager(Main plugin) {
        this.plugin = plugin;
        this.logManager = plugin.logManager;
        
        // Initialize caches
        this.playerStatsCache = new ConcurrentHashMap<>();
        this.playerLevelsCache = new ConcurrentHashMap<>();
        this.townStatsCache = new ConcurrentHashMap<>();
        this.townLevelsCache = new ConcurrentHashMap<>();
        this.leaderboardCache = new ConcurrentHashMap<>();
        
        // Load configuration
        this.playerStatsTTL = plugin.getConfig().getLong("cache.player_stats_ttl_seconds", 300) * 1000; // 5 minutes
        this.playerLevelsTTL = plugin.getConfig().getLong("cache.player_levels_ttl_seconds", 600) * 1000; // 10 minutes
        this.townStatsTTL = plugin.getConfig().getLong("cache.town_stats_ttl_seconds", 300) * 1000; // 5 minutes
        this.townLevelsTTL = plugin.getConfig().getLong("cache.town_levels_ttl_seconds", 600) * 1000; // 10 minutes
        this.leaderboardTTL = plugin.getConfig().getLong("cache.leaderboard_ttl_seconds", 60) * 1000; // 1 minute
        this.maxCacheSize = plugin.getConfig().getInt("cache.max_size", 1000);
        
        // Start cleanup scheduler
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        this.cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 60, 60, TimeUnit.SECONDS);
        
        logManager.debug("CacheManager initialized with TTLs: PlayerStats=" + playerStatsTTL + 
                        "ms, PlayerLevels=" + playerLevelsTTL + "ms, TownStats=" + townStatsTTL + 
                        "ms, TownLevels=" + townLevelsTTL + "ms, Leaderboard=" + leaderboardTTL + "ms");
    }
    
    /**
     * Get player stats from cache
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPlayerStats(UUID playerUUID) {
        String key = playerUUID.toString();
        CacheEntry<Object> entry = playerStatsCache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            logManager.debug("Cache hit for player stats: " + playerUUID);
            return (Map<String, Object>) entry.getData();
        }
        
        if (entry != null) {
            playerStatsCache.remove(key);
        }
        
        logManager.debug("Cache miss for player stats: " + playerUUID);
        return null;
    }
    
    /**
     * Store player stats in cache
     */
    public void putPlayerStats(UUID playerUUID, Map<String, Object> stats) {
        String key = playerUUID.toString();
        playerStatsCache.put(key, new CacheEntry<>(stats, playerStatsTTL));
        
        // Check cache size
        if (playerStatsCache.size() > maxCacheSize) {
            cleanupOldestEntries(playerStatsCache);
        }
        
        logManager.debug("Cached player stats for: " + playerUUID);
    }
    
    /**
     * Get player level from cache
     */
    public Object getPlayerLevel(UUID playerUUID) {
        String key = playerUUID.toString();
        CacheEntry<Object> entry = playerLevelsCache.get(key);
        
        if (entry != null && !entry.isExpired()) {
            logManager.debug("Cache hit for player level: " + playerUUID);
            return entry.getData();
        }
        
        if (entry != null) {
            playerLevelsCache.remove(key);
        }
        
        logManager.debug("Cache miss for player level: " + playerUUID);
        return null;
    }
    
    /**
     * Store player level in cache
     */
    public void putPlayerLevel(UUID playerUUID, Object levelData) {
        String key = playerUUID.toString();
        playerLevelsCache.put(key, new CacheEntry<>(levelData, playerLevelsTTL));
        
        if (playerLevelsCache.size() > maxCacheSize) {
            cleanupOldestEntries(playerLevelsCache);
        }
        
        logManager.debug("Cached player level for: " + playerUUID);
    }
    
    /**
     * Get town stats from cache
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTownStats(String townName) {
        CacheEntry<Object> entry = townStatsCache.get(townName);
        
        if (entry != null && !entry.isExpired()) {
            logManager.debug("Cache hit for town stats: " + townName);
            return (Map<String, Object>) entry.getData();
        }
        
        if (entry != null) {
            townStatsCache.remove(townName);
        }
        
        logManager.debug("Cache miss for town stats: " + townName);
        return null;
    }
    
    /**
     * Store town stats in cache
     */
    public void putTownStats(String townName, Map<String, Object> stats) {
        townStatsCache.put(townName, new CacheEntry<>(stats, townStatsTTL));
        
        if (townStatsCache.size() > maxCacheSize) {
            cleanupOldestEntries(townStatsCache);
        }
        
        logManager.debug("Cached town stats for: " + townName);
    }
    
    /**
     * Get town level from cache
     */
    public Object getTownLevel(String townName) {
        CacheEntry<Object> entry = townLevelsCache.get(townName);
        
        if (entry != null && !entry.isExpired()) {
            logManager.debug("Cache hit for town level: " + townName);
            return entry.getData();
        }
        
        if (entry != null) {
            townLevelsCache.remove(townName);
        }
        
        logManager.debug("Cache miss for town level: " + townName);
        return null;
    }
    
    /**
     * Store town level in cache
     */
    public void putTownLevel(String townName, Object levelData) {
        townLevelsCache.put(townName, new CacheEntry<>(levelData, townLevelsTTL));
        
        if (townLevelsCache.size() > maxCacheSize) {
            cleanupOldestEntries(townLevelsCache);
        }
        
        logManager.debug("Cached town level for: " + townName);
    }
    
    /**
     * Get leaderboard from cache
     */
    public Object getLeaderboard(String type) {
        CacheEntry<Object> entry = leaderboardCache.get(type);
        
        if (entry != null && !entry.isExpired()) {
            logManager.debug("Cache hit for leaderboard: " + type);
            return entry.getData();
        }
        
        if (entry != null) {
            leaderboardCache.remove(type);
        }
        
        logManager.debug("Cache miss for leaderboard: " + type);
        return null;
    }
    
    /**
     * Store leaderboard in cache
     */
    public void putLeaderboard(String type, Object data) {
        leaderboardCache.put(type, new CacheEntry<>(data, leaderboardTTL));
        
        if (leaderboardCache.size() > maxCacheSize) {
            cleanupOldestEntries(leaderboardCache);
        }
        
        logManager.debug("Cached leaderboard: " + type);
    }
    
    /**
     * Invalidate cache entries for a specific player
     */
    public void invalidatePlayer(UUID playerUUID) {
        String key = playerUUID.toString();
        playerStatsCache.remove(key);
        playerLevelsCache.remove(key);
        logManager.debug("Invalidated cache for player: " + playerUUID);
    }
    
    /**
     * Invalidate cache entries for a specific town
     */
    public void invalidateTown(String townName) {
        townStatsCache.remove(townName);
        townLevelsCache.remove(townName);
        logManager.debug("Invalidated cache for town: " + townName);
    }
    
    /**
     * Clear all caches
     */
    public void clearAll() {
        playerStatsCache.clear();
        playerLevelsCache.clear();
        townStatsCache.clear();
        townLevelsCache.clear();
        leaderboardCache.clear();
        logManager.debug("All caches cleared");
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("player_stats_cache_size", playerStatsCache.size());
        stats.put("player_levels_cache_size", playerLevelsCache.size());
        stats.put("town_stats_cache_size", townStatsCache.size());
        stats.put("town_levels_cache_size", townLevelsCache.size());
        stats.put("leaderboard_cache_size", leaderboardCache.size());
        stats.put("max_cache_size", maxCacheSize);
        return stats;
    }
    
    /**
     * Cleanup expired entries from all caches
     */
    private void cleanupExpiredEntries() {
        int removed = 0;
        
        removed += cleanupExpiredEntries(playerStatsCache);
        removed += cleanupExpiredEntries(playerLevelsCache);
        removed += cleanupExpiredEntries(townStatsCache);
        removed += cleanupExpiredEntries(townLevelsCache);
        removed += cleanupExpiredEntries(leaderboardCache);
        
        if (removed > 0) {
            logManager.debug("Cleaned up " + removed + " expired cache entries");
        }
    }
    
    /**
     * Cleanup expired entries from a specific cache
     */
    private int cleanupExpiredEntries(Map<String, CacheEntry<Object>> cache) {
        final int[] removed = {0};
        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }
    
    /**
     * Cleanup oldest entries when cache is full
     */
    private void cleanupOldestEntries(Map<String, CacheEntry<Object>> cache) {
        if (cache.size() <= maxCacheSize) return;
        
        // Remove oldest entries
        int toRemove = cache.size() - maxCacheSize + 10; // Remove extra to prevent frequent cleanup
        
        cache.entrySet().stream()
            .sorted((a, b) -> Long.compare(a.getValue().getCreatedAt(), b.getValue().getCreatedAt()))
            .limit(toRemove)
            .forEach(entry -> cache.remove(entry.getKey()));
        
        logManager.debug("Cleaned up " + toRemove + " oldest cache entries");
    }
    
    /**
     * Shutdown the cache manager
     */
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        clearAll();
        logManager.debug("CacheManager shutdown complete");
    }
    
    /**
     * Cache entry wrapper with expiration
     */
    private static class CacheEntry<T> {
        private final T data;
        private final long createdAt;
        private final long ttl;
        
        public CacheEntry(T data, long ttl) {
            this.data = data;
            this.createdAt = System.currentTimeMillis();
            this.ttl = ttl;
        }
        
        public T getData() {
            return data;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ttl;
        }
    }
} 