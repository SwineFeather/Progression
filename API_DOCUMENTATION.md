# Progression Plugin API Documentation

## Overview
The Progression plugin provides a comprehensive API that allows other plugins to access player and town progression data, including levels, XP, awards, medals, and statistics.

## Getting Started

### 1. Add Dependency
Make sure your plugin depends on the Progression plugin in your `plugin.yml`:

```yaml
depend: [Progression]
```

### 2. Get API Instance
```java
import com.swinefeather.progression.ProgressionAPI;

public class YourPlugin extends JavaPlugin {
    private ProgressionAPI progressionAPI;
    
    @Override
    public void onEnable() {
        // Get the API instance
        progressionAPI = ProgressionAPI.getInstance();
        
        // Check if API is available
        if (!progressionAPI.isAvailable()) {
            getLogger().severe("Progression API not available!");
            return;
        }
        
        getLogger().info("Progression API loaded successfully!");
    }
}
```

## Player Level API

### Get Player Level Information
```java
// Get level info for online player
Player player = Bukkit.getPlayer("PlayerName");
ProgressionAPI.PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevel(player);

// Get level info by UUID
UUID playerUUID = player.getUniqueId();
ProgressionAPI.PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevel(playerUUID);

// Get level info by name (for offline players)
Optional<ProgressionAPI.PlayerLevelInfo> levelInfo = progressionAPI.getPlayerLevelByName("PlayerName");
if (levelInfo.isPresent()) {
    ProgressionAPI.PlayerLevelInfo info = levelInfo.get();
    int level = info.getLevel();
    long currentXP = info.getCurrentXP();
    long totalXP = info.getTotalXP();
    String levelTitle = info.getLevelTitle();
    String levelDescription = info.getLevelDescription();
    long lastLevelUp = info.getLastLevelUp();
}
```

### Get All Players' Levels
```java
// Get all online players' levels
List<ProgressionAPI.PlayerLevelInfo> allLevels = progressionAPI.getAllPlayerLevels();

// Get top players by level
List<ProgressionAPI.PlayerLevelInfo> topByLevel = progressionAPI.getTopPlayersByLevel(10);

// Get top players by XP
List<ProgressionAPI.PlayerLevelInfo> topByXP = progressionAPI.getTopPlayersByXP(10);
```

## Player Awards API

### Get Player Awards Information
```java
// Get awards for online player
Player player = Bukkit.getPlayer("PlayerName");
ProgressionAPI.PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwards(player);

// Get awards by UUID
UUID playerUUID = player.getUniqueId();
ProgressionAPI.PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwards(playerUUID);

// Access award data
double totalPoints = awardsInfo.getTotalPoints();
int totalMedals = awardsInfo.getTotalMedals();
int goldMedals = awardsInfo.getGoldMedals();
int silverMedals = awardsInfo.getSilverMedals();
int bronzeMedals = awardsInfo.getBronzeMedals();

// Get individual awards
List<ProgressionAPI.AwardInfo> awards = awardsInfo.getAwards();
for (ProgressionAPI.AwardInfo award : awards) {
    String awardId = award.getAwardId();
    String awardName = award.getAwardName();
    String medal = award.getMedal(); // "gold", "silver", "bronze"
    double points = award.getPoints();
    long statValue = award.getStatValue();
    long achievedAt = award.getAchievedAt();
}
```

### Get All Players' Awards
```java
// Get all players' awards
List<ProgressionAPI.PlayerAwardsInfo> allAwards = progressionAPI.getAllPlayerAwards();

// Get top players by total points
List<ProgressionAPI.PlayerAwardsInfo> topByPoints = progressionAPI.getTopPlayersByPoints(10);

// Get top players by total medals
List<ProgressionAPI.PlayerAwardsInfo> topByMedals = progressionAPI.getTopPlayersByMedals(10);
```

### Get Specific Award Information
```java
// Check if player has a specific award
UUID playerUUID = player.getUniqueId();
String awardId = "mining_master";
Optional<ProgressionAPI.AwardInfo> award = progressionAPI.getPlayerAward(playerUUID, awardId);
if (award.isPresent()) {
    ProgressionAPI.AwardInfo awardInfo = award.get();
    // Process award info
}
```

## Town API

### Get Town Level Information
```java
// Get town level info
String townName = "MyTown";
Optional<ProgressionAPI.TownLevelInfo> townLevel = progressionAPI.getTownLevel(townName);
if (townLevel.isPresent()) {
    ProgressionAPI.TownLevelInfo levelInfo = townLevel.get();
    int level = levelInfo.getLevel();
    long currentXP = levelInfo.getCurrentXP();
    long totalXP = levelInfo.getTotalXP();
    String levelTitle = levelInfo.getLevelTitle();
    String levelDescription = levelInfo.getLevelDescription();
    long lastLevelUp = levelInfo.getLastLevelUp();
}
```

### Get Town Statistics
```java
// Get town statistics
String townName = "MyTown";
Optional<ProgressionAPI.TownStatsInfo> townStats = progressionAPI.getTownStats(townName);
if (townStats.isPresent()) {
    ProgressionAPI.TownStatsInfo stats = townStats.get();
    int population = stats.getPopulation();
    double balance = stats.getBalance();
    String nation = stats.getNation();
    int plotCount = stats.getPlotCount();
    int size = stats.getSize();
    int age = stats.getAge();
    String mayor = stats.getMayor();
    boolean isCapital = stats.isCapital();
    boolean isIndependent = stats.isIndependent();
    long lastUpdated = stats.getLastUpdated();
}
```

### Get All Towns
```java
// Get all towns' levels
List<ProgressionAPI.TownLevelInfo> allTownLevels = progressionAPI.getAllTownLevels();

// Get all towns' statistics
List<ProgressionAPI.TownStatsInfo> allTownStats = progressionAPI.getAllTownStats();

// Get top towns by level
List<ProgressionAPI.TownLevelInfo> topTownsByLevel = progressionAPI.getTopTownsByLevel(10);

// Get top towns by XP
List<ProgressionAPI.TownLevelInfo> topTownsByXP = progressionAPI.getTopTownsByXP(10);
```

## Award Definitions API

### Get Award Definitions
```java
// Get all available award definitions
List<ProgressionAPI.AwardDefinitionInfo> allAwards = progressionAPI.getAllAwardDefinitions();
for (ProgressionAPI.AwardDefinitionInfo award : allAwards) {
    String awardId = award.getAwardId();
    String name = award.getName();
    String description = award.getDescription();
    String statPath = award.getStatPath();
    String color = award.getColor();
    boolean enabled = award.isEnabled();
}

// Get specific award definition
String awardId = "mining_master";
Optional<ProgressionAPI.AwardDefinitionInfo> award = progressionAPI.getAwardDefinition(awardId);
```

### Get Award Leaderboards
```java
// Get leaderboard for specific award
String awardId = "mining_master";
List<ProgressionAPI.AwardLeaderboardEntry> leaderboard = progressionAPI.getAwardLeaderboard(awardId, 10);
for (ProgressionAPI.AwardLeaderboardEntry entry : leaderboard) {
    UUID playerUUID = entry.getPlayerUUID();
    String playerName = entry.getPlayerName();
    String awardName = entry.getAwardName();
    double points = entry.getPoints();
    String medal = entry.getMedal();
    String tier = entry.getTier();
    long statValue = entry.getStatValue();
    long achievedAt = entry.getAchievedAt();
    int rank = entry.getRank();
}
```

## Level Definitions API

### Get Level Definitions
```java
// Get player level definitions
List<ProgressionAPI.LevelDefinitionInfo> playerLevels = progressionAPI.getLevelDefinitions("player");
for (ProgressionAPI.LevelDefinitionInfo level : playerLevels) {
    String levelType = level.getLevelType();
    int levelNumber = level.getLevel();
    int xpRequired = level.getXpRequired();
    String title = level.getTitle();
    String description = level.getDescription();
    String color = level.getColor();
}

// Get town level definitions
List<ProgressionAPI.LevelDefinitionInfo> townLevels = progressionAPI.getLevelDefinitions("town");

// Get specific level definition
Optional<ProgressionAPI.LevelDefinitionInfo> level = progressionAPI.getLevelDefinition("player", 5);
```

## Utility Methods

### Check API Availability
```java
if (progressionAPI.isAvailable()) {
    // API is ready to use
} else {
    // API is not available
}
```

### Refresh Data
```java
// Force refresh player data from database
UUID playerUUID = player.getUniqueId();
progressionAPI.refreshPlayerData(playerUUID);

// Force refresh town data from database
String townName = "MyTown";
progressionAPI.refreshTownData(townName);
```

## Complete Example

Here's a complete example of how to use the API:

```java
import com.swinefeather.progression.ProgressionAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

public class MyPlugin extends JavaPlugin {
    private ProgressionAPI progressionAPI;
    
    @Override
    public void onEnable() {
        // Get API instance
        progressionAPI = ProgressionAPI.getInstance();
        
        if (!progressionAPI.isAvailable()) {
            getLogger().severe("Progression API not available!");
            return;
        }
        
        getLogger().info("Progression API loaded successfully!");
        
        // Register commands or events that use the API
    }
    
    public void showPlayerInfo(Player player) {
        // Get player level
        ProgressionAPI.PlayerLevelInfo levelInfo = progressionAPI.getPlayerLevel(player);
        player.sendMessage("§aLevel: " + levelInfo.getLevel() + " (" + levelInfo.getLevelTitle() + ")");
        player.sendMessage("§aXP: " + levelInfo.getCurrentXP() + "/" + levelInfo.getTotalXP());
        
        // Get player awards
        ProgressionAPI.PlayerAwardsInfo awardsInfo = progressionAPI.getPlayerAwards(player);
        player.sendMessage("§6Total Points: " + awardsInfo.getTotalPoints());
        player.sendMessage("§6Medals: " + awardsInfo.getGoldMedals() + " Gold, " + 
                          awardsInfo.getSilverMedals() + " Silver, " + 
                          awardsInfo.getBronzeMedals() + " Bronze");
        
        // Show top players
        List<ProgressionAPI.PlayerLevelInfo> topPlayers = progressionAPI.getTopPlayersByLevel(5);
        player.sendMessage("§e=== Top 5 Players ===");
        for (int i = 0; i < topPlayers.size(); i++) {
            ProgressionAPI.PlayerLevelInfo topPlayer = topPlayers.get(i);
            player.sendMessage("§e" + (i + 1) + ". " + topPlayer.getPlayerName() + 
                              " - Level " + topPlayer.getLevel());
        }
    }
    
    public void showTownInfo(String townName) {
        Optional<ProgressionAPI.TownLevelInfo> townLevel = progressionAPI.getTownLevel(townName);
        if (townLevel.isPresent()) {
            ProgressionAPI.TownLevelInfo levelInfo = townLevel.get();
            getLogger().info("Town: " + townName + " - Level " + levelInfo.getLevel());
        }
        
        Optional<ProgressionAPI.TownStatsInfo> townStats = progressionAPI.getTownStats(townName);
        if (townStats.isPresent()) {
            ProgressionAPI.TownStatsInfo stats = townStats.get();
            getLogger().info("Population: " + stats.getPopulation() + 
                           ", Balance: " + stats.getBalance() + 
                           ", Mayor: " + stats.getMayor());
        }
    }
}
```

## Data Classes Reference

### PlayerLevelInfo
- `getPlayerUUID()` - Player's UUID
- `getPlayerName()` - Player's name (may be null)
- `getLevel()` - Current level
- `getCurrentXP()` - XP in current level
- `getTotalXP()` - Total XP earned
- `getLevelTitle()` - Level title (e.g., "Veteran")
- `getLevelDescription()` - Level description
- `getLastLevelUp()` - Timestamp of last level up

### PlayerAwardsInfo
- `getPlayerUUID()` - Player's UUID
- `getPlayerName()` - Player's name (may be null)
- `getAwards()` - List of individual awards
- `getTotalPoints()` - Total points earned
- `getTotalMedals()` - Total number of medals
- `getGoldMedals()` - Number of gold medals
- `getSilverMedals()` - Number of silver medals
- `getBronzeMedals()` - Number of bronze medals

### AwardInfo
- `getAwardId()` - Award identifier
- `getAwardName()` - Award display name
- `getAwardDescription()` - Award description
- `getTier()` - Award tier
- `getMedal()` - Medal type ("gold", "silver", "bronze")
- `getPoints()` - Points earned for this award
- `getStatValue()` - Stat value that earned the award
- `getStatPath()` - Stat path used for the award
- `getAchievedAt()` - Timestamp when award was earned

### TownLevelInfo
- `getTownName()` - Town name
- `getLevel()` - Current town level
- `getCurrentXP()` - XP in current level
- `getTotalXP()` - Total XP earned
- `getLevelTitle()` - Level title (e.g., "City")
- `getLevelDescription()` - Level description
- `getLastLevelUp()` - Timestamp of last level up

### TownStatsInfo
- `getTownName()` - Town name
- `getPopulation()` - Number of residents
- `getBalance()` - Town balance
- `getNation()` - Nation name (null if independent)
- `getPlotCount()` - Number of town plots
- `getSize()` - Town size
- `getAge()` - Town age in days
- `getMayor()` - Mayor's name
- `isCapital()` - Whether town is a capital
- `isIndependent()` - Whether town is independent
- `getLastUpdated()` - Last update timestamp

## Notes

1. **Thread Safety**: The API methods are thread-safe and can be called from async tasks.
2. **Null Safety**: Methods return `Optional` when the requested data might not exist.
3. **Performance**: Data is cached where possible for better performance.
4. **Dependencies**: Make sure your plugin depends on Progression in `plugin.yml`.
5. **Error Handling**: Always check if the API is available before using it.

## Support

If you encounter any issues with the API or need additional features, please check the plugin's documentation or contact the plugin author. 