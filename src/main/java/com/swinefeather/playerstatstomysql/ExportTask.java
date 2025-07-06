package com.swinefeather.playerstatstomysql;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ExportTask {
    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;

    public ExportTask(JavaPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    public void exportStats(CommandSender sender, String format) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        if (!format.equalsIgnoreCase("json")) {
            if (sender != null) {
                sender.sendMessage("§cOnly JSON format supported!");
            }
            return;
        }

        try (Connection conn = dbManager.getConnection()) {
            File exportDir = new File(plugin.getDataFolder(), "exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".json";
            File jsonFile = new File(exportDir, fileName);
            try (FileWriter writer = new FileWriter(jsonFile)) {
                writer.write("{\n  \"timestamp\": \"" + LocalDateTime.now() + "\",\n  \"players\": [\n");

                try (PreparedStatement playerStmt = conn.prepareStatement("SELECT * FROM players");
                     ResultSet players = playerStmt.executeQuery()) {
                    boolean firstPlayer = true;
                    while (players.next()) {
                        if (!firstPlayer) {
                            writer.write(",\n");
                        }
                        firstPlayer = false;

                        String uuid = players.getString("player_uuid");
                        String name = players.getString("player_name");
                        String firstJoined = players.getString("first_joined");
                        String lastUpdated = players.getString("last_updated");
                        writer.write("    {\n      \"uuid\": \"" + uuid + "\",\n      \"name\": \"" + name + "\",\n      \"first_joined\": \"" + firstJoined + "\",\n      \"last_updated\": \"" + lastUpdated + "\"");

                        try (ResultSet tables = conn.getMetaData().getTables(null, null, "stats_%", null)) {
                            while (tables.next()) {
                                String table = tables.getString("TABLE_NAME");
                                try (PreparedStatement statStmt = conn.prepareStatement(
                                        "SELECT stat_key, stat_value FROM " + table + " WHERE player_uuid = ?")) {
                                    statStmt.setString(1, uuid);
                                    try (ResultSet stats = statStmt.executeQuery()) {
                                        if (stats.next()) {
                                            writer.write(",\n      \"" + table + "\": {");
                                            boolean firstStat = true;
                                            do {
                                                String statKey = stats.getString("stat_key");
                                                long value = stats.getLong("stat_value");
                                                if (value > 0) {
                                                    if (!firstStat) writer.write(", ");
                                                    writer.write("\"" + statKey + "\": " + value);
                                                    firstStat = false;
                                                }
                                            } while (stats.next());
                                            writer.write("}");
                                        }
                                    }
                                }
                            }
                        }

                        try (PreparedStatement placeholderStmt = conn.prepareStatement(
                                "SELECT * FROM stats_placeholders WHERE player_uuid = ?")) {
                            placeholderStmt.setString(1, uuid);
                            try (ResultSet placeholders = placeholderStmt.executeQuery()) {
                                if (placeholders.next()) {
                                    writer.write(",\n      \"stats_placeholders\": [");
                                    boolean firstPlaceholder = true;
                                    do {
                                        if (!firstPlaceholder) writer.write(", ");
                                        String key = placeholders.getString("placeholder_key");
                                        writer.write("{\"key\": \"" + key +
                                                "\", \"value\": \"" + placeholders.getString("value") + "\"}");
                                        firstPlaceholder = false;
                                    } while (placeholders.next());
                                    writer.write("]");
                                }
                            }
                        }

                        writer.write("\n    }");
                    }
                }

                writer.write("\n  ]\n}");
            }

            if (plugin.getConfig().getBoolean("export.compression")) {
                File zipFile = new File(exportDir, fileName + ".zip");
                try (FileInputStream fis = new FileInputStream(jsonFile);
                     FileOutputStream fos = new FileOutputStream(zipFile);
                     ZipOutputStream zos = new ZipOutputStream(fos)) {
                    ZipEntry zipEntry = new ZipEntry(fileName);
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                }
                jsonFile.delete();
                if (sender != null) {
                    sender.sendMessage(String.format("§aExported stats to %s", zipFile.getName()));
                }
            } else {
                if (sender != null) {
                    sender.sendMessage(String.format("§aExported stats to %s", fileName));
                }
            }

            if (plugin.getConfig().getString("logging.level", "minimal").equalsIgnoreCase("debug")) {
                plugin.getLogger().info(String.format("Export completed: %s", fileName));
            }
        } catch (SQLException | IOException e) {
            if (sender != null) {
                sender.sendMessage(String.format("§cExport failed: %s", e.getMessage()));
            }
            plugin.getLogger().severe(String.format("Export failed: %s", e.getMessage()));
        }
    }

    public void cleanupStats(CommandSender sender) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(plugin)) {
            if (sender != null) {
                sender.sendMessage("§cPlugin is disabled. Use /sqlstats start to enable.");
            }
            return;
        }

        try (Connection conn = dbManager.getConnection()) {
            exportStats(sender, "json");

            int activeDays = plugin.getConfig().getInt("export.cleanup.active_days", 30);
            try (PreparedStatement cleanupStmt = conn.prepareStatement(
                    "DELETE FROM players WHERE last_updated < DATE_SUB(NOW(), INTERVAL ? DAY)")) {
                cleanupStmt.setInt(1, activeDays);
                cleanupStmt.executeUpdate();
            }

            int keepDays = plugin.getConfig().getInt("export.cleanup.keep_files_days", 90);
            File exportDir = new File(plugin.getDataFolder(), "exports");
            if (exportDir.exists()) {
                File[] files = exportDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.lastModified() < System.currentTimeMillis() - keepDays * 24 * 60 * 60 * 1000L) {
                            file.delete();
                            if (plugin.getConfig().getString("logging.level", "minimal").equalsIgnoreCase("debug")) {
                                plugin.getLogger().info(String.format("Deleted old export: %s", file.getName()));
                            }
                        }
                    }
                }
            }

            if (sender != null) {
                sender.sendMessage("§aCleanup completed!");
            }
        } catch (SQLException e) {
            if (sender != null) {
                sender.sendMessage(String.format("§cCleanup failed: %s", e.getMessage()));
            }
            plugin.getLogger().severe(String.format("Cleanup failed: %s", e.getMessage()));
        }
    }
}