# Progression Plugin API Usage Guide

## Overview

The Progression plugin provides a comprehensive API that allows other plugins to access player progression data, town information, awards, levels, and achievements. This guide explains how to integrate with the Progression plugin API.

## Getting Started

### 1. Getting the API Instance

First, you need to get the API instance from the Progression plugin:

```java
import com.swinefeather.progression.ProgressionAPI;

public class YourPlugin extends JavaPlugin {
    private ProgressionAPI progressionAPI;
    
    @Override
    public void onEnable() {
        // Get the Progression API instance
        progressionAPI = ProgressionAPI.getInstance();
        
        if (progressionAPI == null) {
            getLogger().warning("Progression plugin not found! API features will be disabled.");
            return;
        }
        
        getLogger().info("Successfully connected to Progression API!");
    }
}
```

### 2. Checking API Availability

Always check if the API is available before using it:

```java
if (progressionAPI != null && progressionAPI.isAvailable()) {
    // Use the API
} else {
    // Handle case where API is not available
}
```

## Player Level Information

### Get Player Level by UUID

```java
import java.util.UUID;
import com.swinefeather.progression.ProgressionAPI.PlayerLevelInfo;

UUID playerUUID = UUID.fromString("player-uuid-here");
PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevelInfo(playerUUID);

if (levelInfo != null) {
    int level = levelInfo.getLevel();
    long totalXP = levelInfo.getTotalXP();
    String levelTitle = levelInfo.getLevelTitle();
    String levelDescription = levelInfo.getLevelDescription();
    String levelColor = levelInfo.getLevelColor();
    
    getLogger().info("Player is level " + level + " with " + totalXP + " XP");
    getLogger().info("Level title: " + levelTitle);
}
```

### Get Player Level by Name

```java
String playerName = "PlayerName";
PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevelInfoByName(playerName);

if (levelInfo != null) {
    // Same usage as above
}
```

### Get All Player Levels

```java
List<PlayerLevelInfo> allLevels = progressionAPI.getAllPlayerLevels();

for (PlayerLevelInfo levelInfo : allLevels) {
    String playerName = levelInfo.getPlayerName();
    int level = levelInfo.getLevel();
    long totalXP = levelInfo.getTotalXP();
    
    getLogger().info(playerName + " is level " + level + " with " + totalXP + " XP");
}
```

### Get Top Players by Level

```java
List<PlayerLevelInfo> topPlayers = progressionAPI.getTopPlayersByLevel(10); // Top 10

for (int i = 0; i < topPlayers.size(); i++) {
    PlayerLevelInfo levelInfo = topPlayers.get(i);
    getLogger().info((i + 1) + ". " + levelInfo.getPlayerName() + 
                    " - Level " + levelInfo.getLevel() + 
                    " (" + levelInfo.getTotalXP() + " XP)");
}
```

### Get Top Players by XP

```java
List<PlayerLevelInfo> topXPPlayers = progressionAPI.getTopPlayersByXP(5); // Top 5

for (PlayerLevelInfo levelInfo : topXPPlayers) {
    getLogger().info(levelInfo.getPlayerName() + " has " + levelInfo.getTotalXP() + " XP");
}
```

## Player Awards Information

### Get Player Awards by UUID

```java
import com.swinefeather.progression.ProgressionAPI.PlayerAwardsInfo;

UUID playerUUID = UUID.fromString("player-uuid-here");
PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwardsInfo(playerUUID);

if (awardsInfo != null) {
    String playerName = awardsInfo.getPlayerName();
    double totalPoints = awardsInfo.getTotalPoints();
    int totalMedals = awardsInfo.getTotalMedals();
    int goldMedals = awardsInfo.getGoldMedals();
    int silverMedals = awardsInfo.getSilverMedals();
    int bronzeMedals = awardsInfo.getBronzeMedals();
    
    getLogger().info(playerName + " has " + totalPoints + " points and " + totalMedals + " medals");
    getLogger().info("Medals: " + goldMedals + " gold, " + silverMedals + " silver, " + bronzeMedals + " bronze");
}
```

### Get Player Awards by Name

```java
String playerName = "PlayerName";
PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwardsInfoByName(playerName);

if (awardsInfo != null) {
    // Same usage as above
}
```

### Get All Player Awards

```java
List<PlayerAwardsInfo> allAwards = progressionAPI.getAllPlayerAwardsInfo();

for (PlayerAwardsInfo awardsInfo : allAwards) {
    getLogger().info(awardsInfo.getPlayerName() + " has " + awardsInfo.getTotalPoints() + " points");
}
```

### Get Top Players by Points

```java
List<PlayerAwardsInfo> topPoints = progressionAPI.getTopPlayersByPoints(10);

for (int i = 0; i < topPoints.size(); i++) {
    PlayerAwardsInfo awardsInfo = topPoints.get(i);
    getLogger().info((i + 1) + ". " + awardsInfo.getPlayerName() + 
                    " - " + awardsInfo.getTotalPoints() + " points");
}
```

### Get Top Players by Medals

```java
List<PlayerAwardsInfo> topMedals = progressionAPI.getTopPlayersByMedals(10);

for (PlayerAwardsInfo awardsInfo : topMedals) {
    getLogger().info(awardsInfo.getPlayerName() + " has " + awardsInfo.getTotalMedals() + " medals");
}
```

### Get Specific Award Information

```java
UUID playerUUID = UUID.fromString("player-uuid-here");
String awardId = "mined_diamond_ore";

AwardInfo awardInfo = progressionAPI.getPlayerAwardInfo(playerUUID, awardId);

if (awardInfo != null) {
    String awardName = awardInfo.getName();
    String description = awardInfo.getDescription();
    String category = awardInfo.getCategory();
    int points = awardInfo.getPoints();
    String medal = awardInfo.getMedal();
    String tier = awardInfo.getTier();
    String requirement = awardInfo.getRequirement();
    
    getLogger().info("Award: " + awardName + " (" + description + ")");
    getLogger().info("Points: " + points + ", Medal: " + medal + ", Tier: " + tier);
}
```

## Town Information

### Get Town Level Information

```java
import com.swinefeather.progression.ProgressionAPI.TownLevelInfo;

String townName = "TownName";
TownLevelInfo townLevel = progressionAPI.getTownLevelInfo(townName);

if (townLevel != null) {
    int level = townLevel.getLevel();
    long totalXP = townLevel.getTotalXP();
    String levelTitle = townLevel.getLevelTitle();
    String levelDescription = townLevel.getLevelDescription();
    String levelColor = townLevel.getLevelColor();
    
    getLogger().info(townName + " is level " + level + " with " + totalXP + " XP");
    getLogger().info("Level title: " + levelTitle);
}
```

### Get All Town Levels

```java
List<TownLevelInfo> allTownLevels = progressionAPI.getAllTownLevels();

for (TownLevelInfo townLevel : allTownLevels) {
    getLogger().info(townLevel.getTownName() + " is level " + townLevel.getLevel());
}
```

### Get Top Towns by Level

```java
List<TownLevelInfo> topTownsByLevel = progressionAPI.getTopTownsByLevel(10);

for (int i = 0; i < topTownsByLevel.size(); i++) {
    TownLevelInfo townLevel = topTownsByLevel.get(i);
    getLogger().info((i + 1) + ". " + townLevel.getTownName() + 
                    " - Level " + townLevel.getLevel());
}
```

### Get Top Towns by XP

```java
List<TownLevelInfo> topTownsByXP = progressionAPI.getTopTownsByXP(10);

for (TownLevelInfo townLevel : topTownsByXP) {
    getLogger().info(townLevel.getTownName() + " has " + townLevel.getTotalXP() + " XP");
}
```

### Get Town Statistics

```java
import com.swinefeather.progression.ProgressionAPI.TownStatsInfo;

String townName = "TownName";
TownStatsInfo townStats = progressionAPI.getTownStatsInfo(townName);

if (townStats != null) {
    int population = townStats.getPopulation();
    double balance = townStats.getBalance();
    String nation = townStats.getNation();
    int plotCount = townStats.getPlotCount();
    int size = townStats.getSize();
    int age = townStats.getAge();
    String mayor = townStats.getMayor();
    boolean isCapital = townStats.isCapital();
    boolean isIndependent = townStats.isIndependent();
    
    getLogger().info(townName + " stats:");
    getLogger().info("Population: " + population + ", Balance: " + balance);
    getLogger().info("Nation: " + nation + ", Mayor: " + mayor);
    getLogger().info("Size: " + size + ", Age: " + age + " days");
}
```

### Get All Town Statistics

```java
List<TownStatsInfo> allTownStats = progressionAPI.getAllTownStats();

for (TownStatsInfo townStats : allTownStats) {
    getLogger().info(townStats.getTownName() + " has " + townStats.getPopulation() + " residents");
}
```

## Award Definitions

### Get All Award Definitions

```java
import com.swinefeather.progression.ProgressionAPI.AwardDefinitionInfo;

List<AwardDefinitionInfo> allAwards = progressionAPI.getAllAwardDefinitions();

for (AwardDefinitionInfo award : allAwards) {
    String id = award.getId();
    String name = award.getName();
    String description = award.getDescription();
    String stat = award.getStat();
    String color = award.getColor();
    String type = award.getType();
    
    getLogger().info("Award: " + name + " (" + id + ")");
    getLogger().info("Description: " + description);
    getLogger().info("Stat: " + stat + ", Type: " + type);
}
```

### Get Specific Award Definition

```java
String awardId = "mined_diamond_ore";
AwardDefinitionInfo award = progressionAPI.getAwardDefinitionInfo(awardId);

if (award != null) {
    getLogger().info("Award: " + award.getName());
    getLogger().info("Description: " + award.getDescription());
}
```

### Get Award Leaderboard

```java
import com.swinefeather.progression.ProgressionAPI.AwardLeaderboardEntry;

String awardId = "mined_diamond_ore";
List<AwardLeaderboardEntry> leaderboard = progressionAPI.getAwardLeaderboard(awardId, 10);

for (int i = 0; i < leaderboard.size(); i++) {
    AwardLeaderboardEntry entry = leaderboard.get(i);
    getLogger().info((i + 1) + ". " + entry.getPlayerName() + 
                    " - " + entry.getPoints() + " points (" + entry.getMedal() + ")");
}
```

## Level Definitions

### Get Level Definitions

```java
import com.swinefeather.progression.ProgressionAPI.LevelDefinitionInfo;

List<LevelDefinitionInfo> playerLevels = progressionAPI.getLevelDefinitions("player");
List<LevelDefinitionInfo> townLevels = progressionAPI.getLevelDefinitions("town");

for (LevelDefinitionInfo level : playerLevels) {
    int levelNum = level.getLevel();
    String title = level.getTitle();
    String description = level.getDescription();
    int xpRequired = level.getXpRequired();
    String color = level.getColor();
    
    getLogger().info("Level " + levelNum + ": " + title + " (" + xpRequired + " XP)");
}
```

### Get Specific Level Definition

```java
LevelDefinitionInfo levelDef = progressionAPI.getLevelDefinition("player", 5);

if (levelDef != null) {
    getLogger().info("Level 5: " + levelDef.getTitle());
    getLogger().info("Requires: " + levelDef.getXpRequired() + " XP");
}
```

## Utility Methods

### Check if API is Available

```java
if (progressionAPI.isAvailable()) {
    getLogger().info("Progression API is available");
} else {
    getLogger().warning("Progression API is not available");
}
```

### Refresh Player Data

```java
UUID playerUUID = UUID.fromString("player-uuid-here");
progressionAPI.refreshPlayerData(playerUUID);
```

### Refresh Town Data

```java
String townName = "TownName";
progressionAPI.refreshTownData(townName);
```

## Data Classes Reference

### PlayerLevelInfo
- `getPlayerName()` - Returns the player's name
- `getPlayerUUID()` - Returns the player's UUID
- `getLevel()` - Returns the current level
- `getTotalXP()` - Returns total XP
- `getLevelTitle()` - Returns the level title
- `getLevelDescription()` - Returns the level description
- `getLevelColor()` - Returns the level color

### PlayerAwardsInfo
- `getPlayerName()` - Returns the player's name
- `getPlayerUUID()` - Returns the player's UUID
- `getTotalPoints()` - Returns total points
- `getTotalMedals()` - Returns total medals
- `getGoldMedals()` - Returns gold medal count
- `getSilverMedals()` - Returns silver medal count
- `getBronzeMedals()` - Returns bronze medal count

### TownLevelInfo
- `getTownName()` - Returns the town name
- `getLevel()` - Returns the current level
- `getTotalXP()` - Returns total XP
- `getLevelTitle()` - Returns the level title
- `getLevelDescription()` - Returns the level description
- `getLevelColor()` - Returns the level color

### TownStatsInfo
- `getTownName()` - Returns the town name
- `getPopulation()` - Returns population count
- `getBalance()` - Returns town balance
- `getNation()` - Returns nation name
- `getPlotCount()` - Returns plot count
- `getSize()` - Returns town size
- `getAge()` - Returns town age in days
- `getMayor()` - Returns mayor name
- `isCapital()` - Returns if town is capital
- `isIndependent()` - Returns if town is independent

### AwardInfo
- `getName()` - Returns award name
- `getDescription()` - Returns award description
- `getCategory()` - Returns award category
- `getPoints()` - Returns points awarded
- `getMedal()` - Returns medal type
- `getTier()` - Returns tier level
- `getRequirement()` - Returns requirement description

### AwardDefinitionInfo
- `getId()` - Returns award ID
- `getName()` - Returns award name
- `getDescription()` - Returns award description
- `getStat()` - Returns stat path
- `getColor()` - Returns award color
- `getType()` - Returns award type (player/town)

### LevelDefinitionInfo
- `getLevel()` - Returns level number
- `getTitle()` - Returns level title
- `getDescription()` - Returns level description
- `getXpRequired()` - Returns XP required
- `getColor()` - Returns level color

### AwardLeaderboardEntry
- `getPlayerName()` - Returns player name
- `getPlayerUUID()` - Returns player UUID
- `getPoints()` - Returns points
- `getMedal()` - Returns medal type
- `getTier()` - Returns tier level
- `getRank()` - Returns rank position

## Example Integration

Here's a complete example of how to integrate with the Progression API:

```java
import com.swinefeather.progression.ProgressionAPI;
import com.swinefeather.progression.ProgressionAPI.PlayerLevelInfo;
import com.swinefeather.progression.ProgressionAPI.PlayerAwardsInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class MyPlugin extends JavaPlugin {
    private ProgressionAPI progressionAPI;
    
    @Override
    public void onEnable() {
        // Get the Progression API
        progressionAPI = ProgressionAPI.getInstance();
        
        if (progressionAPI == null || !progressionAPI.isAvailable()) {
            getLogger().warning("Progression plugin not found! Some features will be disabled.");
            return;
        }
        
        getLogger().info("Successfully connected to Progression API!");
        
        // Register commands that use the API
        getCommand("mystats").setExecutor(new MyStatsCommand(this));
        getCommand("top10").setExecutor(new Top10Command(this));
    }
    
    public class MyStatsCommand implements CommandExecutor {
        private final MyPlugin plugin;
        
        public MyStatsCommand(MyPlugin plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by players!");
                return true;
            }
            
            Player player = (Player) sender;
            UUID playerUUID = player.getUniqueId();
            
            // Get player level info
            PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevelInfo(playerUUID);
            if (levelInfo != null) {
                player.sendMessage("§6=== Your Stats ===");
                player.sendMessage("§eLevel: §f" + levelInfo.getLevel() + " (" + levelInfo.getLevelTitle() + ")");
                player.sendMessage("§eTotal XP: §f" + levelInfo.getTotalXP());
            }
            
            // Get player awards info
            PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwardsInfo(playerUUID);
            if (awardsInfo != null) {
                player.sendMessage("§eTotal Points: §f" + awardsInfo.getTotalPoints());
                player.sendMessage("§eMedals: §6" + awardsInfo.getGoldMedals() + " gold, §7" + 
                                 awardsInfo.getSilverMedals() + " silver, §c" + 
                                 awardsInfo.getBronzeMedals() + " bronze");
            }
            
            return true;
        }
    }
    
    public class Top10Command implements CommandExecutor {
        private final MyPlugin plugin;
        
        public Top10Command(MyPlugin plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            List<PlayerLevelInfo> topPlayers = progressionAPI.getTopPlayersByLevel(10);
            
            sender.sendMessage("§6=== Top 10 Players by Level ===");
            for (int i = 0; i < topPlayers.size(); i++) {
                PlayerLevelInfo levelInfo = topPlayers.get(i);
                sender.sendMessage("§e" + (i + 1) + ". §f" + levelInfo.getPlayerName() + 
                                 " §7- Level " + levelInfo.getLevel() + 
                                 " (" + levelInfo.getTotalXP() + " XP)");
            }
            
            return true;
        }
    }
}
```

## Best Practices

1. **Always check API availability** before using it
2. **Handle null returns** gracefully - not all players/towns may have data
3. **Use async operations** for heavy operations to avoid blocking the main thread
4. **Cache data** when appropriate to reduce API calls
5. **Handle exceptions** that might occur during API calls
6. **Use the appropriate data classes** for type safety

## Troubleshooting

### API Not Available
If `ProgressionAPI.getInstance()` returns null:
- Make sure the Progression plugin is installed and enabled
- Check that your plugin loads after the Progression plugin
- Verify the Progression plugin is compatible with your server version

### No Data Returned
If API calls return null or empty lists:
- Check if the player/town exists in the Progression database
- Verify that the Progression plugin has collected data for the target
- Ensure the database connection is working properly

### Performance Issues
- Use caching for frequently accessed data
- Limit the number of API calls in loops
- Consider using async operations for heavy operations

## Support

For issues with the Progression plugin API:
1. Check the plugin's main documentation
2. Review the server logs for error messages
3. Ensure you're using the latest version of the Progression plugin
4. Test with a minimal example to isolate the issue 