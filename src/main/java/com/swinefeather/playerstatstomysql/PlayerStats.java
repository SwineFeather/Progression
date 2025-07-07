package com.swinefeather.playerstatstomysql;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class PlayerStats {
    private CombatStats combat;
    private MovementStats movement;
    private MiningStats mining;
    private TimeStats time;

    public PlayerStats() {
        this.combat = new CombatStats();
        this.movement = new MovementStats();
        this.mining = new MiningStats();
        this.time = new TimeStats();
    }

    public static PlayerStats fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new PlayerStats();
        }
        
        try {
            Gson gson = new Gson();
            return gson.fromJson(json, PlayerStats.class);
        } catch (Exception e) {
            return new PlayerStats();
        }
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    // Getters and setters
    public CombatStats getCombat() { return combat; }
    public void setCombat(CombatStats combat) { this.combat = combat; }

    public MovementStats getMovement() { return movement; }
    public void setMovement(MovementStats movement) { this.movement = movement; }

    public MiningStats getMining() { return mining; }
    public void setMining(MiningStats mining) { this.mining = mining; }

    public TimeStats getTime() { return time; }
    public void setTime(TimeStats time) { this.time = time; }

    // Inner classes for different stat categories
    public static class CombatStats {
        private int kills;
        private int deaths;
        private int damageDealt;
        private int damageTaken;

        public CombatStats() {
            this.kills = 0;
            this.deaths = 0;
            this.damageDealt = 0;
            this.damageTaken = 0;
        }

        public double getKDRatio() {
            return deaths > 0 ? (double) kills / deaths : kills;
        }

        // Getters and setters
        public int getKills() { return kills; }
        public void setKills(int kills) { this.kills = kills; }

        public int getDeaths() { return deaths; }
        public void setDeaths(int deaths) { this.deaths = deaths; }

        public int getDamageDealt() { return damageDealt; }
        public void setDamageDealt(int damageDealt) { this.damageDealt = damageDealt; }

        public int getDamageTaken() { return damageTaken; }
        public void setDamageTaken(int damageTaken) { this.damageTaken = damageTaken; }
    }

    public static class MovementStats {
        private double distanceWalked;
        private double distanceSprinted;
        private double distanceSwum;
        private double distanceFallen;
        private int jumps;

        public MovementStats() {
            this.distanceWalked = 0.0;
            this.distanceSprinted = 0.0;
            this.distanceSwum = 0.0;
            this.distanceFallen = 0.0;
            this.jumps = 0;
        }

        // Getters and setters
        public double getDistanceWalked() { return distanceWalked; }
        public void setDistanceWalked(double distanceWalked) { this.distanceWalked = distanceWalked; }

        public double getDistanceSprinted() { return distanceSprinted; }
        public void setDistanceSprinted(double distanceSprinted) { this.distanceSprinted = distanceSprinted; }

        public double getDistanceSwum() { return distanceSwum; }
        public void setDistanceSwum(double distanceSwum) { this.distanceSwum = distanceSwum; }

        public double getDistanceFallen() { return distanceFallen; }
        public void setDistanceFallen(double distanceFallen) { this.distanceFallen = distanceFallen; }

        public int getJumps() { return jumps; }
        public void setJumps(int jumps) { this.jumps = jumps; }
    }

    public static class MiningStats {
        private int blocksMined;
        private int diamondsMined;
        private int ironMined;
        private int coalMined;
        private int goldMined;
        private int emeraldsMined;
        private int redstoneMined;

        public MiningStats() {
            this.blocksMined = 0;
            this.diamondsMined = 0;
            this.ironMined = 0;
            this.coalMined = 0;
            this.goldMined = 0;
            this.emeraldsMined = 0;
            this.redstoneMined = 0;
        }

        // Getters and setters
        public int getBlocksMined() { return blocksMined; }
        public void setBlocksMined(int blocksMined) { this.blocksMined = blocksMined; }

        public int getDiamondsMined() { return diamondsMined; }
        public void setDiamondsMined(int diamondsMined) { this.diamondsMined = diamondsMined; }

        public int getIronMined() { return ironMined; }
        public void setIronMined(int ironMined) { this.ironMined = ironMined; }

        public int getCoalMined() { return coalMined; }
        public void setCoalMined(int coalMined) { this.coalMined = coalMined; }

        public int getGoldMined() { return goldMined; }
        public void setGoldMined(int goldMined) { this.goldMined = goldMined; }

        public int getEmeraldsMined() { return emeraldsMined; }
        public void setEmeraldsMined(int emeraldsMined) { this.emeraldsMined = emeraldsMined; }

        public int getRedstoneMined() { return redstoneMined; }
        public void setRedstoneMined(int redstoneMined) { this.redstoneMined = redstoneMined; }
    }

    public static class TimeStats {
        private long playtime;
        private int daysPlayed;
        private int sessions;

        public TimeStats() {
            this.playtime = 0;
            this.daysPlayed = 0;
            this.sessions = 0;
        }

        // Getters and setters
        public long getPlaytime() { return playtime; }
        public void setPlaytime(long playtime) { this.playtime = playtime; }

        public int getDaysPlayed() { return daysPlayed; }
        public void setDaysPlayed(int daysPlayed) { this.daysPlayed = daysPlayed; }

        public int getSessions() { return sessions; }
        public void setSessions(int sessions) { this.sessions = sessions; }
    }
} 