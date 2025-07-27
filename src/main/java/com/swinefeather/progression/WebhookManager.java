package com.swinefeather.progression;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebhookManager {
    private final Plugin plugin;
    private final Logger logger;
    private final Gson gson;
    private final HttpClient httpClient;
    
    private String discordWebhookUrl;
    private boolean enabled = false;
    private boolean awardAnnouncements = true;
    private boolean medalChangeNotifications = true;
    private boolean milestoneNotifications = true;
    
    public WebhookManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new Gson();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public boolean initialize(ConfigurationSection config) {
        if (config == null || !config.getBoolean("enabled", false)) {
            logger.fine("Webhook notifications are disabled");
            return false;
        }
        
        this.enabled = true;
        this.discordWebhookUrl = config.getString("discord_webhook_url", "");
        this.awardAnnouncements = config.getBoolean("award_announcements", true);
        this.medalChangeNotifications = config.getBoolean("medal_change_notifications", true);
        this.milestoneNotifications = config.getBoolean("milestone_notifications", true);
        
        if (discordWebhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL not configured - webhook notifications disabled");
            this.enabled = false;
            return false;
        }
        
        logger.fine("Webhook notifications initialized");
        return true;
    }
    
    public void sendAwardAnnouncement(String playerName, String awardName, String medalType, int rank) {
        if (!enabled || !awardAnnouncements || discordWebhookUrl.isEmpty()) {
            return;
        }
        
        String medalEmoji = getMedalEmoji(medalType);
        int color = getMedalColor(medalType);
        
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🏆 New Award Earned!");
        embed.addProperty("description", String.format("%s **%s** has earned the **%s** award!", 
            medalEmoji, playerName, awardName));
        embed.addProperty("color", color);
        
        JsonObject fields = new JsonObject();
        fields.addProperty("name", "Player");
        fields.addProperty("value", playerName);
        fields.addProperty("inline", true);
        
        JsonObject fields2 = new JsonObject();
        fields2.addProperty("name", "Award");
        fields2.addProperty("value", awardName);
        fields2.addProperty("inline", true);
        
        JsonObject fields3 = new JsonObject();
        fields3.addProperty("name", "Rank");
        fields3.addProperty("value", "#" + rank);
        fields3.addProperty("inline", true);
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Progression Award System");
        
        embed.add("fields", gson.toJsonTree(new JsonObject[]{fields, fields2, fields3}));
        embed.add("footer", footer);
        
        sendDiscordWebhook(embed);
    }
    
    public void sendMedalChangeNotification(String playerName, String oldMedal, String newMedal, String awardName) {
        if (!enabled || !medalChangeNotifications || discordWebhookUrl.isEmpty()) {
            return;
        }
        
        String oldEmoji = getMedalEmoji(oldMedal);
        String newEmoji = getMedalEmoji(newMedal);
        
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🔄 Medal Upgrade!");
        embed.addProperty("description", String.format("%s **%s** upgraded from %s to %s in **%s**!", 
            newEmoji, playerName, oldEmoji, newEmoji, awardName));
        embed.addProperty("color", 0x00ff00); // Green
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Progression Award System");
        embed.add("footer", footer);
        
        sendDiscordWebhook(embed);
    }
    
    public void sendMilestoneNotification(String playerName, String milestone, int value) {
        if (!enabled || !milestoneNotifications || discordWebhookUrl.isEmpty()) {
            return;
        }
        
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🎯 Milestone Achieved!");
        embed.addProperty("description", String.format("🎉 **%s** has reached **%s** (%d)!", 
            playerName, milestone, value));
        embed.addProperty("color", 0xffd700); // Gold
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Progression Award System");
        embed.add("footer", footer);
        
        sendDiscordWebhook(embed);
    }
    
    public void sendLeaderboardUpdate(String leaderboardType, String topPlayer, int value) {
        if (!enabled || discordWebhookUrl.isEmpty()) {
            return;
        }
        
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "📊 Leaderboard Update");
        embed.addProperty("description", String.format("🏆 **%s** is now leading in **%s** with **%d**!", 
            topPlayer, leaderboardType, value));
        embed.addProperty("color", 0x4169e1); // Royal Blue
        
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Progression Award System");
        embed.add("footer", footer);
        
        sendDiscordWebhook(embed);
    }
    
    private void sendDiscordWebhook(JsonObject embed) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject webhookData = new JsonObject();
                webhookData.addProperty("username", "Progression Awards");
                webhookData.addProperty("avatar_url", "https://i.imgur.com/example.png");
                webhookData.add("embeds", gson.toJsonTree(new JsonObject[]{embed}));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discordWebhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(webhookData)))
                    .timeout(Duration.ofSeconds(10))
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() != 204) {
                    logger.warning("Discord webhook failed with status: " + response.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                logger.log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        });
    }
    
    private String getMedalEmoji(String medalType) {
        switch (medalType.toLowerCase()) {
            case "gold": return "🥇";
            case "silver": return "🥈";
            case "bronze": return "🥉";
            default: return "🏅";
        }
    }
    
    private int getMedalColor(String medalType) {
        switch (medalType.toLowerCase()) {
            case "gold": return 0xffd700; // Gold
            case "silver": return 0xc0c0c0; // Silver
            case "bronze": return 0xcd7f32; // Bronze
            default: return 0xffffff; // White
        }
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void shutdown() {
        // Nothing to clean up for HttpClient
    }
} 