package com.example.playerstatstomysql;

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
            plugin.getLogger().info(String.format("Loaded Towny placeholders: %s", String.join(", ", TOWNY_PLACEHOLDERS)));
        } else {
            plugin.getLogger().info(String.format("Towny placeholders not loaded: Towny plugin %s",
                    plugin.getServer().getPluginManager().getPlugin("Towny") == null ? "not found" : "disabled in config"));
        }
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().warning("PlaceholderAPI not found, placeholders disabled");
        } else {
            plugin.getLogger().info(String.format("Loaded %d placeholders: %s", placeholders.size(), String.join(", ", placeholders)));
        }
    }

    public void syncPlayerPlaceholders(UUID playerUUID) {
        if (!plugin.getConfig().getBoolean("placeholderapi.enabled") || 
            placeholders.isEmpty() || 
            !plugin.getServer().getPluginManager().isPluginEnabled(plugin) || 
            plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info(String.format("Skipping placeholders for %s: PlaceholderAPI disabled or not found", playerUUID));
            return;
        }

        try {
            Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerUUID);
            String playerName = player.getName() != null ? player.getName() : "Unknown";
            plugin.getLogger().info(String.format("Processing placeholders for UUID: %s, Name: %s", playerUUID, playerName));

            Map<String, String> placeholderValues = new HashMap<>();
            int failedPlaceholders = 0;
            for (String placeholder : placeholders) {
                if (placeholderBlacklist.contains(placeholder)) {
                    plugin.getLogger().info(String.format("Skipping blacklisted placeholder: %s", placeholder));
                    continue;
                }
                try {
                    String value = (String) placeholderAPIClass.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                            .invoke(null, player, "%" + placeholder + "%");
                    plugin.getLogger().info(String.format("Raw placeholder value for %s: %s", placeholder, value));
                    if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("none")) {
                        placeholderValues.put(placeholder, value);
                    } else {
                        plugin.getLogger().info(String.format("No valid value for placeholder %s for %s", placeholder, playerUUID));
                    }
                } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException | NoSuchMethodException e) {
                    failedPlaceholders++;
                    plugin.getLogger().warning(String.format("Failed to process placeholder %s for %s: %s", placeholder, playerUUID, e.getMessage()));
                }
            }
            if (failedPlaceholders > 0) {
                plugin.getLogger().warning(String.format("Skipped %d failed placeholders for %s", failedPlaceholders, playerUUID));
            }

            if (!placeholderValues.isEmpty()) {
                dbManager.savePlaceholderStats(playerUUID, placeholderValues);
                plugin.getLogger().info(String.format("Saved %d placeholders for %s", placeholderValues.size(), playerUUID));
            } else {
                plugin.getLogger().info(String.format("No valid placeholder values for %s", playerUUID));
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(String.format("PlaceholderAPI class not found, skipping placeholders for %s", playerUUID));
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