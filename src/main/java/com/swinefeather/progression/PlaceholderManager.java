package com.swinefeather.progression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class PlaceholderManager {
    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;
    private List<String> placeholders;
    private List<String> placeholderBlacklist;
    public static final List<String> TOWNY_PLACEHOLDERS = Arrays.asList(
            "townyadvanced_town", "townyadvanced_nation", "townyadvanced_has_town",
            "townyadvanced_has_nation", "townyadvanced_is_mayor", "townyadvanced_is_king",
            "townyadvanced_town_balance", "townyadvanced_nation_balance",
            "townyadvanced_resident_tax", "townyadvanced_town_plot_count",
            "townyadvanced_town_resident_count", "townyadvanced_nation_resident_count"
    );

    public PlaceholderManager(JavaPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        placeholders = new ArrayList<>(plugin.getConfig().getStringList("placeholderapi.placeholders"));
        placeholderBlacklist = new ArrayList<>(plugin.getConfig().getStringList("placeholderapi.blacklist"));
    }

    public void loadPlaceholders() {
        placeholders = new ArrayList<>(plugin.getConfig().getStringList("placeholderapi.placeholders"));
        placeholderBlacklist = new ArrayList<>(plugin.getConfig().getStringList("placeholderapi.blacklist"));
        if (plugin.getConfig().getBoolean("towny.enabled") && plugin.getServer().getPluginManager().getPlugin("Towny") != null) {
            for (String townyPlaceholder : TOWNY_PLACEHOLDERS) {
                if (!placeholders.contains(townyPlaceholder) && !placeholderBlacklist.contains(townyPlaceholder)) {
                    placeholders.add(townyPlaceholder);
                }
            }
            plugin.getConfig().set("placeholderapi.placeholders", placeholders);
            plugin.saveConfig();
            plugin.getLogger().info("Loaded Towny placeholders: " + String.join(", ", TOWNY_PLACEHOLDERS));
        } else {
            plugin.getLogger().info("Towny placeholders not loaded: Towny plugin " +
                    (plugin.getServer().getPluginManager().getPlugin("Towny") == null ? "not found" : "disabled in config"));
        }
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI not found, placeholders disabled");
        } else {
            plugin.getLogger().info("Loaded " + placeholders.size() + " placeholders: " + String.join(", ", placeholders));
        }
    }

    public void syncPlayerPlaceholders(UUID playerUUID) {
        if (!plugin.getConfig().getBoolean("placeholderapi.enabled") || 
            placeholders.isEmpty() || 
            !plugin.getServer().getPluginManager().isPluginEnabled(plugin) || 
            plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("Skipping placeholders for " + playerUUID + ": PlaceholderAPI disabled or not found");
            return;
        }

        try {
            Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUUID);
            String playerName = player.getName() != null ? player.getName() : "Unknown";
            plugin.getLogger().info("Processing placeholders for UUID: " + playerUUID + ", Name: " + playerName);

            Map<String, String> placeholderValues = new HashMap<>();
            int failedPlaceholders = 0;
            for (String placeholder : placeholders) {
                if (placeholderBlacklist.contains(placeholder)) {
                    plugin.getLogger().info("Skipping blacklisted placeholder: " + placeholder);
                    continue;
                }
                try {
                    String value = (String) placeholderAPIClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                            .invoke(null, player, "%" + placeholder + "%");
                    plugin.getLogger().info("Raw placeholder value for " + placeholder + ": " + value);
                    if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("none")) {
                        placeholderValues.put(placeholder, value);
                    } else {
                        plugin.getLogger().info("No valid value for placeholder " + placeholder + " for " + playerUUID);
                    }
                } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | NoSuchMethodException e) {
                    failedPlaceholders++;
                    plugin.getLogger().warning("Failed to process placeholder " + placeholder + " for " + playerUUID + ": " + e.getMessage());
                }
            }
            if (failedPlaceholders > 0) {
                plugin.getLogger().warning("Skipped " + failedPlaceholders + " failed placeholders for " + playerUUID);
            }

            if (!placeholderValues.isEmpty()) {
                dbManager.savePlaceholderStats(playerUUID, placeholderValues);
                plugin.getLogger().info("Saved " + placeholderValues.size() + " placeholders for " + playerUUID);
            } else {
                plugin.getLogger().info("No valid placeholder values for " + playerUUID);
            }

            // Sync Towny data if enabled
            if (plugin.getConfig().getBoolean("towny.enabled")) {
                syncTownyData(playerUUID, player);
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("PlaceholderAPI class not found, skipping placeholders for " + playerUUID);
        }
    }

    private void syncTownyData(UUID playerUUID, OfflinePlayer player) {
        try {
            // Try to get Towny API
            Class<?> townyUniverseClass = Class.forName("com.palmergames.bukkit.towny.TownyUniverse");
            Class<?> residentClass = Class.forName("com.palmergames.bukkit.towny.object.Resident");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            Class<?> nationClass = Class.forName("com.palmergames.bukkit.towny.object.Nation");

            Object townyUniverse = townyUniverseClass.getMethod("getInstance").invoke(null);
            Object resident = townyUniverseClass.getMethod("getResident", String.class).invoke(townyUniverse, player.getName());

            if (resident != null) {
                Map<String, String> townyStats = new HashMap<>();
                
                // Get town information
                Object town = residentClass.getMethod("getTownOrNull").invoke(resident);
                if (town != null) {
                    String townName = (String) townClass.getMethod("getName").invoke(town);
                    townyStats.put("town", townName);
                    
                    // Check if player is mayor
                    Object mayor = townClass.getMethod("getMayor").invoke(town);
                    boolean isMayor = mayor != null && mayor.equals(resident);
                    townyStats.put("is_mayor", String.valueOf(isMayor));
                    
                    // Get town balance
                    try {
                        double balance = (Double) townClass.getMethod("getAccount").invoke(town);
                        townyStats.put("town_balance", String.valueOf(balance));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not get town balance: " + e.getMessage());
                    }
                    
                    // Get nation information
                    Object nation = townClass.getMethod("getNationOrNull").invoke(town);
                    if (nation != null) {
                        String nationName = (String) nationClass.getMethod("getName").invoke(nation);
                        townyStats.put("nation", nationName);
                        
                        // Check if player is king
                        Object king = nationClass.getMethod("getKing").invoke(nation);
                        boolean isKing = king != null && king.equals(resident);
                        townyStats.put("is_king", String.valueOf(isKing));
                        
                        // Get nation balance
                        try {
                            double nationBalance = (Double) nationClass.getMethod("getAccount").invoke(nation);
                            townyStats.put("nation_balance", String.valueOf(nationBalance));
                        } catch (Exception e) {
                            plugin.getLogger().warning("Could not get nation balance: " + e.getMessage());
                        }
                    } else {
                        townyStats.put("nation", "none");
                        townyStats.put("is_king", "false");
                    }
                } else {
                    townyStats.put("town", "none");
                    townyStats.put("nation", "none");
                    townyStats.put("is_mayor", "false");
                    townyStats.put("is_king", "false");
                }
                
                if (!townyStats.isEmpty()) {
                    dbManager.saveTownyStats(playerUUID, townyStats);
                    plugin.getLogger().info("Saved Towny data for " + playerUUID + ": " + townyStats);
                }
            } else {
                plugin.getLogger().info("No Towny resident found for " + playerUUID);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync Towny data for " + playerUUID + ": " + e.getMessage());
        }
    }

    public List<String> getAvailablePlaceholders() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return Collections.emptyList();
        }
        List<String> available = new ArrayList<>(placeholders);
        return available;
    }

    public void addPlaceholder(String placeholder) {
        if (!placeholders.contains(placeholder) && !placeholderBlacklist.contains(placeholder)) {
            placeholders.add(placeholder);
            plugin.getConfig().set("placeholderapi.placeholders", placeholders);
            plugin.saveConfig();
        }
    }
}