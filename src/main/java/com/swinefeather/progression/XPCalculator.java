package com.swinefeather.progression;

import java.util.Map;

public class XPCalculator {
    
    /**
     * Calculate XP gain from player stats
     * @param stats Player statistics
     * @return Total XP gained
     */
    public static int calculatePlayerXP(Map<String, Object> stats) {
        int totalXP = 0;
        
        // Debug: Log some key stats (disabled for production)
        // System.out.println("XPCalculator: Processing " + stats.size() + " stats");
        // for (String key : stats.keySet()) {
        //     if (key.contains("play_time") || key.contains("mine") || key.contains("kill") || key.contains("walk")) {
        //         System.out.println("XPCalculator: " + key + " = " + stats.get(key));
        //     }
        // }
        
        // Playtime XP (1 hour = 50 XP) - Much more generous!
        Object playTime = getStatValue(stats, "custom_minecraft_play_time");
        if (playTime != null) {
            int playTicks = ((Number) playTime).intValue();
            int hours = playTicks / 72000; // 72000 ticks = 1 hour
            totalXP += hours * 50;
        }
        
        // Block placement XP (1 block = 1 XP) - Much more generous!
        Object blocksPlaced = getStatValue(stats, "use_dirt");
        if (blocksPlaced != null) {
            int blocks = ((Number) blocksPlaced).intValue();
            totalXP += blocks;
        }
        
        // Block mining XP (1 block = 0.5 XP) - Much more generous!
        Object blocksMined = getStatValue(stats, "mine_ground");
        if (blocksMined != null) {
            int blocks = ((Number) blocksMined).intValue();
            totalXP += (int)(blocks * 0.5);
        }
        
        // Mob kills XP (1 kill = 5 XP) - Much more generous!
        Object mobKills = getStatValue(stats, "kill_any");
        if (mobKills != null) {
            int kills = ((Number) mobKills).intValue();
            totalXP += kills * 5;
        }
        
        // Diamond mining XP (1 diamond ore = 100 XP) - Much more generous!
        Object diamondOre = getStatValue(stats, "mine_diamond_ore");
        if (diamondOre != null) {
            int diamonds = ((Number) diamondOre).intValue();
            totalXP += diamonds * 100;
        }
        
        // Distance traveled XP (1km = 10 XP) - Much more generous!
        Object distanceWalked = getStatValue(stats, "walk_one_cm");
        if (distanceWalked != null) {
            int cm = ((Number) distanceWalked).intValue();
            int km = cm / 100000; // 100,000 cm = 1 km
            totalXP += km * 10;
        }
        
        // Swimming XP (1km = 15 XP) - Much more generous!
        Object distanceSwam = getStatValue(stats, "swim_one_cm");
        if (distanceSwam != null) {
            int cm = ((Number) distanceSwam).intValue();
            int km = cm / 100000;
            totalXP += km * 15;
        }
        
        // Boat travel XP (1km = 20 XP) - Much more generous!
        Object boatDistance = getStatValue(stats, "ride_boat");
        if (boatDistance != null) {
            int cm = ((Number) boatDistance).intValue();
            int km = cm / 100000;
            totalXP += km * 20;
        }
        
        // Horse riding XP (1km = 15 XP) - Much more generous!
        Object horseDistance = getStatValue(stats, "ride_horse");
        if (horseDistance != null) {
            int cm = ((Number) horseDistance).intValue();
            int km = cm / 100000;
            totalXP += km * 15;
        }
        
        // Elytra flying XP (1km = 25 XP) - Much more generous!
        Object elytraDistance = getStatValue(stats, "aviate_one_cm");
        if (elytraDistance != null) {
            int cm = ((Number) elytraDistance).intValue();
            int km = cm / 100000;
            totalXP += km * 25;
        }
        
        // Wood harvesting XP (1 log = 2 XP) - Much more generous!
        Object woodHarvested = getStatValue(stats, "mine_wood");
        if (woodHarvested != null) {
            int logs = ((Number) woodHarvested).intValue();
            totalXP += logs * 2;
        }
        
        // Farming XP (1 crop = 1 XP) - Much more generous!
        Object wheatHarvested = getStatValue(stats, "mined_wheat");
        if (wheatHarvested != null) {
            int wheat = ((Number) wheatHarvested).intValue();
            totalXP += wheat;
        }
        
        // Crafting XP (1 item = 5 XP) - Much more generous!
        Object itemsCrafted = getStatValue(stats, "craft_tools");
        if (itemsCrafted != null) {
            int crafted = ((Number) itemsCrafted).intValue();
            totalXP += crafted * 5;
        }
        
        // Enchanting XP (1 enchant = 25 XP) - Much more generous!
        Object itemsEnchanted = getStatValue(stats, "enchant");
        if (itemsEnchanted != null) {
            int enchanted = ((Number) itemsEnchanted).intValue();
            totalXP += enchanted * 25;
        }
        
        // Trading XP (1 trade = 10 XP) - Much more generous!
        Object trades = getStatValue(stats, "trade");
        if (trades != null) {
            int tradeCount = ((Number) trades).intValue();
            totalXP += tradeCount * 10;
        }
        
        // Raid victories XP (1 raid = 500 XP) - Much more generous!
        Object raidsWon = getStatValue(stats, "win_raid");
        if (raidsWon != null) {
            int raids = ((Number) raidsWon).intValue();
            totalXP += raids * 500;
        }
        
        // Death penalty (-5 XP per death, but minimum 0) - Reduced penalty
        Object deaths = getStatValue(stats, "death");
        if (deaths != null) {
            int deathCount = ((Number) deaths).intValue();
            int deathPenalty = deathCount * 5;
            totalXP = Math.max(0, totalXP - deathPenalty);
        }
        
        return totalXP;
    }
    
    /**
     * Calculate XP gain from town stats (Towny integration)
     * @param townStats Town statistics
     * @return Total XP gained
     */
    public static int calculateTownXP(Map<String, Object> townStats) {
        int totalXP = 0;
        
        // Population XP (1 resident = 10 XP)
        Object population = getStatValue(townStats, "population");
        if (population != null) {
            int residents = ((Number) population).intValue();
            totalXP += residents * 10;
        }
        
        // Nation membership XP (50 XP for being in a nation)
        Object nationMember = getStatValue(townStats, "nation_member");
        if (nationMember != null) {
            boolean isNationMember = ((Number) nationMember).intValue() > 0;
            if (isNationMember) {
                totalXP += 50;
            }
        }
        
        // Capital status XP (100 XP for being a capital)
        Object capital = getStatValue(townStats, "capital");
        if (capital != null) {
            boolean isCapital = ((Number) capital).intValue() > 0;
            if (isCapital) {
                totalXP += 100;
            }
        }
        
        // Independent status XP (75 XP for being independent)
        Object independent = getStatValue(townStats, "independent");
        if (independent != null) {
            boolean isIndependent = ((Number) independent).intValue() > 0;
            if (isIndependent) {
                totalXP += 75;
            }
        }
        
        return totalXP;
    }
    
    /**
     * Get stat value from stats map, handling both direct values and nested objects
     */
    private static Object getStatValue(Map<String, Object> stats, String statName) {
        Object value = stats.get(statName);
        if (value == null) return null;
        
        // If it's a map with a "value" field, extract the value
        if (value instanceof Map) {
            Map<String, Object> statMap = (Map<String, Object>) value;
            return statMap.get("value");
        }
        
        return value;
    }
    
    /**
     * Calculate XP from specific stat value
     * @param statName The stat name
     * @param value The stat value
     * @return XP gained from this stat
     */
    public static int calculateStatXP(String statName, int value) {
        switch (statName) {
            case "custom_minecraft_play_time":
                return (value / 72000) * 50; // 1 hour = 50 XP
            case "use_dirt":
                return value; // 1 block = 1 XP
            case "mine_ground":
                return (int)(value * 0.5); // 1 block = 0.5 XP
            case "kill_any":
                return value * 5; // 1 kill = 5 XP
            case "mine_diamond_ore":
                return value * 100; // 1 diamond ore = 100 XP
            case "walk_one_cm":
                return (int)((value / 100000.0) * 10); // 1km = 10 XP
            case "swim_one_cm":
                return (int)((value / 100000.0) * 15); // 1km = 15 XP
            case "ride_boat":
                return (int)((value / 100000.0) * 20); // 1km = 20 XP
            case "ride_horse":
                return (int)((value / 100000.0) * 15); // 1km = 15 XP
            case "aviate_one_cm":
                return (int)((value / 100000.0) * 25); // 1km = 25 XP
            case "mine_wood":
                return value * 2; // 1 log = 2 XP
            case "mined_wheat":
                return value; // 1 crop = 1 XP
            case "craft_tools":
                return value * 5; // 1 item = 5 XP
            case "enchant":
                return value * 25; // 1 enchant = 25 XP
            case "trade":
                return value * 10; // 1 trade = 10 XP
            case "win_raid":
                return value * 500; // 1 raid = 500 XP
            case "death":
                return -value * 5; // 1 death = -5 XP
            case "population":
                return value * 10; // 1 resident = 10 XP
            case "nation_member":
                return value > 0 ? 50 : 0; // Nation member = 50 XP
            case "capital":
                return value > 0 ? 100 : 0; // Capital = 100 XP
            case "independent":
                return value > 0 ? 75 : 0; // Independent = 75 XP
            default:
                return 0;
        }
    }
} 