package com.swinefeather.progression;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class StatResolver {
    private final LogManager logger;
    
    // Common stat mappings for different Minecraft versions
    private static final Map<String, String[]> STAT_ALIASES = new HashMap<>();
    
    static {
        // Distance stats
        STAT_ALIASES.put("custom.boat_one_cm", new String[]{"custom.boat_one_cm", "custom.boat", "minecraft:custom.boat_one_cm"});
        STAT_ALIASES.put("custom.horse_one_cm", new String[]{"custom.horse_one_cm", "custom.horse", "minecraft:custom.horse_one_cm"});
        STAT_ALIASES.put("custom.minecart_one_cm", new String[]{"custom.minecart_one_cm", "custom.minecart", "minecraft:custom.minecart_one_cm"});
        STAT_ALIASES.put("custom.strider_one_cm", new String[]{"custom.strider_one_cm", "custom.strider", "minecraft:custom.strider_one_cm"});
        STAT_ALIASES.put("custom.walk_one_cm", new String[]{"custom.walk_one_cm", "custom.walk", "minecraft:custom.walk_one_cm"});
        STAT_ALIASES.put("custom.sprint_one_cm", new String[]{"custom.sprint_one_cm", "custom.sprint", "minecraft:custom.sprint_one_cm"});
        STAT_ALIASES.put("custom.swim_one_cm", new String[]{"custom.swim_one_cm", "custom.swim", "minecraft:custom.swim_one_cm"});
        STAT_ALIASES.put("custom.fly_one_cm", new String[]{"custom.fly_one_cm", "custom.fly", "minecraft:custom.fly_one_cm"});
        STAT_ALIASES.put("custom.fall_one_cm", new String[]{"custom.fall_one_cm", "custom.fall", "minecraft:custom.fall_one_cm"});
        STAT_ALIASES.put("custom.ride_one_cm", new String[]{"custom.ride_one_cm", "custom.ride", "minecraft:custom.ride_one_cm"});
        
        // Mining stats
        STAT_ALIASES.put("mined.total", new String[]{"mined.total", "minecraft:mined.total"});
        STAT_ALIASES.put("mined.diamond_ore", new String[]{"mined.diamond_ore", "minecraft:mined.diamond_ore"});
        STAT_ALIASES.put("mined.iron_ore", new String[]{"mined.iron_ore", "minecraft:mined.iron_ore"});
        STAT_ALIASES.put("mined.coal_ore", new String[]{"mined.coal_ore", "minecraft:mined.coal_ore"});
        STAT_ALIASES.put("mined.gold_ore", new String[]{"mined.gold_ore", "minecraft:mined.gold_ore"});
        STAT_ALIASES.put("mined.emerald_ore", new String[]{"mined.emerald_ore", "minecraft:mined.emerald_ore"});
        STAT_ALIASES.put("mined.redstone_ore", new String[]{"mined.redstone_ore", "minecraft:mined.redstone_ore"});
        STAT_ALIASES.put("mined.lapis_ore", new String[]{"mined.lapis_ore", "minecraft:mined.lapis_ore"});
        STAT_ALIASES.put("mined.quartz_ore", new String[]{"mined.quartz_ore", "minecraft:mined.quartz_ore"});
        STAT_ALIASES.put("mined.ancient_debris", new String[]{"mined.ancient_debris", "minecraft:mined.ancient_debris"});
        STAT_ALIASES.put("mined.deepslate", new String[]{"mined.deepslate", "minecraft:mined.deepslate"});
        STAT_ALIASES.put("mined.stone", new String[]{"mined.stone", "minecraft:mined.stone"});
        STAT_ALIASES.put("mined.copper_ore", new String[]{"mined.copper_ore", "minecraft:mined.copper_ore"});
        STAT_ALIASES.put("mined.amethyst_cluster", new String[]{"mined.amethyst_cluster", "minecraft:mined.amethyst_cluster"});
        
        // Killed stats
        STAT_ALIASES.put("killed.total", new String[]{"killed.total", "minecraft:killed.total"});
        STAT_ALIASES.put("killed.zombie", new String[]{"killed.zombie", "minecraft:killed.zombie"});
        STAT_ALIASES.put("killed.skeleton", new String[]{"killed.skeleton", "minecraft:killed.skeleton"});
        STAT_ALIASES.put("killed.creeper", new String[]{"killed.creeper", "minecraft:killed.creeper"});
        STAT_ALIASES.put("killed.spider", new String[]{"killed.spider", "minecraft:killed.spider"});
        STAT_ALIASES.put("killed.enderman", new String[]{"killed.enderman", "minecraft:killed.enderman"});
        STAT_ALIASES.put("killed.blaze", new String[]{"killed.blaze", "minecraft:killed.blaze"});
        STAT_ALIASES.put("killed.ghast", new String[]{"killed.ghast", "minecraft:killed.ghast"});
        STAT_ALIASES.put("killed.shulker", new String[]{"killed.shulker", "minecraft:killed.shulker"});
        STAT_ALIASES.put("killed.warden", new String[]{"killed.warden", "minecraft:killed.warden"});
        STAT_ALIASES.put("killed.evoker", new String[]{"killed.evoker", "minecraft:killed.evoker"});
        STAT_ALIASES.put("killed.piglin", new String[]{"killed.piglin", "minecraft:killed.piglin"});
        STAT_ALIASES.put("killed.hoglin", new String[]{"killed.hoglin", "minecraft:killed.hoglin"});
        STAT_ALIASES.put("killed.ravager", new String[]{"killed.ravager", "minecraft:killed.ravager"});
        STAT_ALIASES.put("killed.elder_guardian", new String[]{"killed.elder_guardian", "minecraft:killed.elder_guardian"});
        STAT_ALIASES.put("killed.wither_skeleton", new String[]{"killed.wither_skeleton", "minecraft:killed.wither_skeleton"});
        
        // Killed by stats
        STAT_ALIASES.put("killed_by.total", new String[]{"killed_by.total", "minecraft:killed_by.total"});
        STAT_ALIASES.put("killed_by.sword", new String[]{"killed_by.sword", "minecraft:killed_by.sword"});
        STAT_ALIASES.put("killed_by.bow", new String[]{"killed_by.bow", "minecraft:killed_by.bow"});
        STAT_ALIASES.put("killed_by.axe", new String[]{"killed_by.axe", "minecraft:killed_by.axe"});
        STAT_ALIASES.put("killed_by.trident", new String[]{"killed_by.trident", "minecraft:killed_by.trident"});
        STAT_ALIASES.put("killed_by.crossbow", new String[]{"killed_by.crossbow", "minecraft:killed_by.crossbow"});
        
        // Used stats
        STAT_ALIASES.put("used.total", new String[]{"used.total", "minecraft:used.total"});
        STAT_ALIASES.put("used.chest", new String[]{"used.chest", "minecraft:used.chest"});
        STAT_ALIASES.put("used.furnace", new String[]{"used.furnace", "minecraft:used.furnace"});
        STAT_ALIASES.put("used.crafting_table", new String[]{"used.crafting_table", "minecraft:used.crafting_table"});
        STAT_ALIASES.put("used.ender_chest", new String[]{"used.ender_chest", "minecraft:used.ender_chest"});
        STAT_ALIASES.put("used.torch", new String[]{"used.torch", "minecraft:used.torch"});
        STAT_ALIASES.put("used.stone", new String[]{"used.stone", "minecraft:used.stone"});
        STAT_ALIASES.put("used.dirt", new String[]{"used.dirt", "minecraft:used.dirt"});
        STAT_ALIASES.put("used.oak_planks", new String[]{"used.oak_planks", "minecraft:used.oak_planks"});
        STAT_ALIASES.put("used.glass", new String[]{"used.glass", "minecraft:used.glass"});
        
        // Crafted stats
        STAT_ALIASES.put("crafted.total", new String[]{"crafted.total", "minecraft:crafted.total"});
        STAT_ALIASES.put("crafted.wooden_pickaxe", new String[]{"crafted.wooden_pickaxe", "minecraft:crafted.wooden_pickaxe"});
        STAT_ALIASES.put("crafted.stone_pickaxe", new String[]{"crafted.stone_pickaxe", "minecraft:crafted.stone_pickaxe"});
        STAT_ALIASES.put("crafted.iron_pickaxe", new String[]{"crafted.iron_pickaxe", "minecraft:crafted.iron_pickaxe"});
        STAT_ALIASES.put("crafted.diamond_pickaxe", new String[]{"crafted.diamond_pickaxe", "minecraft:crafted.diamond_pickaxe"});
        STAT_ALIASES.put("crafted.netherite_pickaxe", new String[]{"crafted.netherite_pickaxe", "minecraft:crafted.netherite_pickaxe"});
        STAT_ALIASES.put("crafted.chest", new String[]{"crafted.chest", "minecraft:crafted.chest"});
        STAT_ALIASES.put("crafted.furnace", new String[]{"crafted.furnace", "minecraft:crafted.furnace"});
        STAT_ALIASES.put("crafted.crafting_table", new String[]{"crafted.crafting_table", "minecraft:crafted.crafting_table"});
        STAT_ALIASES.put("crafted.torch", new String[]{"crafted.torch", "minecraft:crafted.torch"});
        STAT_ALIASES.put("crafted.stick", new String[]{"crafted.stick", "minecraft:crafted.stick"});
        
        // Picked up stats
        STAT_ALIASES.put("picked_up.wheat", new String[]{"picked_up.wheat", "minecraft:picked_up.wheat"});
        STAT_ALIASES.put("picked_up.carrot", new String[]{"picked_up.carrot", "minecraft:picked_up.carrot"});
        STAT_ALIASES.put("picked_up.potato", new String[]{"picked_up.potato", "minecraft:picked_up.potato"});
        STAT_ALIASES.put("picked_up.beetroot", new String[]{"picked_up.beetroot", "minecraft:picked_up.beetroot"});
        STAT_ALIASES.put("picked_up.pumpkin", new String[]{"picked_up.pumpkin", "minecraft:picked_up.pumpkin"});
        STAT_ALIASES.put("picked_up.melon_slice", new String[]{"picked_up.melon_slice", "minecraft:picked_up.melon_slice"});
        STAT_ALIASES.put("picked_up.sugar_cane", new String[]{"picked_up.sugar_cane", "minecraft:picked_up.sugar_cane"});
        STAT_ALIASES.put("picked_up.sweet_berries", new String[]{"picked_up.sweet_berries", "minecraft:picked_up.sweet_berries"});
        STAT_ALIASES.put("picked_up.glow_berries", new String[]{"picked_up.glow_berries", "minecraft:picked_up.glow_berries"});
        STAT_ALIASES.put("picked_up.chorus_fruit", new String[]{"picked_up.chorus_fruit", "minecraft:picked_up.chorus_fruit"});
        STAT_ALIASES.put("picked_up.nether_wart", new String[]{"picked_up.nether_wart", "minecraft:picked_up.nether_wart"});
        
        // Fished stats
        STAT_ALIASES.put("fished.fish_caught", new String[]{"fished.fish_caught", "minecraft:fished.fish_caught"});
        STAT_ALIASES.put("fished.junk_fished", new String[]{"fished.junk_fished", "minecraft:fished.junk_fished"});
        STAT_ALIASES.put("fished.treasure_fished", new String[]{"fished.treasure_fished", "minecraft:fished.treasure_fished"});
        
        // Opened stats
        STAT_ALIASES.put("opened.chest", new String[]{"opened.chest", "minecraft:opened.chest"});
        STAT_ALIASES.put("opened.ender_chest", new String[]{"opened.ender_chest", "minecraft:opened.ender_chest"});
        STAT_ALIASES.put("opened.furnace", new String[]{"opened.furnace", "minecraft:opened.furnace"});
        STAT_ALIASES.put("opened.crafting_table", new String[]{"opened.crafting_table", "minecraft:opened.crafting_table"});
        STAT_ALIASES.put("opened.enchanting_table", new String[]{"opened.enchanting_table", "minecraft:opened.enchanting_table"});
        STAT_ALIASES.put("opened.anvil", new String[]{"opened.anvil", "minecraft:opened.anvil"});
        STAT_ALIASES.put("opened.shulker_box", new String[]{"opened.shulker_box", "minecraft:opened.shulker_box"});
        STAT_ALIASES.put("opened.brewing_stand", new String[]{"opened.brewing_stand", "minecraft:opened.brewing_stand"});
        
        // Broken stats
        STAT_ALIASES.put("broken.bed", new String[]{"broken.bed", "minecraft:broken.bed"});
        
        // Custom stats
        STAT_ALIASES.put("custom.damage_dealt", new String[]{"custom.damage_dealt", "minecraft:custom.damage_dealt"});
        STAT_ALIASES.put("custom.damage_taken", new String[]{"custom.damage_taken", "minecraft:custom.damage_taken"});
        STAT_ALIASES.put("custom.damage_blocked_by_shield", new String[]{"custom.damage_blocked_by_shield", "minecraft:custom.damage_blocked_by_shield"});
        STAT_ALIASES.put("custom.time_since_death", new String[]{"custom.time_since_death", "minecraft:custom.time_since_death"});
        STAT_ALIASES.put("custom.play_time", new String[]{"custom.play_time", "minecraft:custom.play_time"});
        STAT_ALIASES.put("custom.sleep_in_bed", new String[]{"custom.sleep_in_bed", "minecraft:custom.sleep_in_bed"});
        STAT_ALIASES.put("custom.jump", new String[]{"custom.jump", "minecraft:custom.jump"});
        STAT_ALIASES.put("custom.talked_to_villager", new String[]{"custom.talked_to_villager", "minecraft:custom.talked_to_villager"});
        STAT_ALIASES.put("custom.traded_with_wandering_trader", new String[]{"custom.traded_with_wandering_trader", "minecraft:custom.traded_with_wandering_trader"});
        STAT_ALIASES.put("custom.totem_of_undying_used_in_end", new String[]{"custom.totem_of_undying_used_in_end", "minecraft:custom.totem_of_undying_used_in_end"});
        STAT_ALIASES.put("custom.totem_of_undying_used_in_nether", new String[]{"custom.totem_of_undying_used_in_nether", "minecraft:custom.totem_of_undying_used_in_nether"});
        STAT_ALIASES.put("custom.totem_of_undying_used_in_overworld", new String[]{"custom.totem_of_undying_used_in_overworld", "minecraft:custom.totem_of_undying_used_in_overworld"});
        STAT_ALIASES.put("custom.times_burned", new String[]{"custom.times_burned", "minecraft:custom.times_burned"});
        STAT_ALIASES.put("custom.times_hit_by_anvil", new String[]{"custom.times_hit_by_anvil", "minecraft:custom.times_hit_by_anvil"});
        STAT_ALIASES.put("custom.times_struck_by_lightning", new String[]{"custom.times_struck_by_lightning", "minecraft:custom.times_struck_by_lightning"});
        STAT_ALIASES.put("custom.times_drowned", new String[]{"custom.times_drowned", "minecraft:custom.times_drowned"});
        STAT_ALIASES.put("custom.times_fell_from_height", new String[]{"custom.times_fell_from_height", "minecraft:custom.times_fell_from_height"});
        STAT_ALIASES.put("custom.slept_in_nether", new String[]{"custom.slept_in_nether", "minecraft:custom.slept_in_nether"});
        STAT_ALIASES.put("custom.elytra_used", new String[]{"custom.elytra_used", "minecraft:custom.elytra_used"});
        STAT_ALIASES.put("custom.elytra_used_in_overworld", new String[]{"custom.elytra_used_in_overworld", "minecraft:custom.elytra_used_in_overworld"});
        STAT_ALIASES.put("custom.elytra_used_in_nether", new String[]{"custom.elytra_used_in_nether", "minecraft:custom.elytra_used_in_nether"});
        STAT_ALIASES.put("custom.elytra_used_in_end", new String[]{"custom.elytra_used_in_end", "minecraft:custom.elytra_used_in_end"});
        STAT_ALIASES.put("custom.boat_ridden", new String[]{"custom.boat_ridden", "minecraft:custom.boat_ridden"});
        STAT_ALIASES.put("custom.horse_ridden", new String[]{"custom.horse_ridden", "minecraft:custom.horse_ridden"});
        STAT_ALIASES.put("custom.minecart_ridden", new String[]{"custom.minecart_ridden", "minecraft:custom.minecart_ridden"});
        STAT_ALIASES.put("custom.strider_ridden", new String[]{"custom.strider_ridden", "minecraft:custom.strider_ridden"});
        STAT_ALIASES.put("custom.pig_ridden", new String[]{"custom.pig_ridden", "minecraft:custom.pig_ridden"});
        STAT_ALIASES.put("custom.donkey_ridden", new String[]{"custom.donkey_ridden", "minecraft:custom.donkey_ridden"});
        STAT_ALIASES.put("custom.mule_ridden", new String[]{"custom.mule_ridden", "minecraft:custom.mule_ridden"});
        STAT_ALIASES.put("custom.llama_ridden", new String[]{"custom.llama_ridden", "minecraft:custom.llama_ridden"});
        STAT_ALIASES.put("custom.skeleton_horse_ridden", new String[]{"custom.skeleton_horse_ridden", "minecraft:custom.skeleton_horse_ridden"});
        STAT_ALIASES.put("custom.zombie_horse_ridden", new String[]{"custom.zombie_horse_ridden", "minecraft:custom.zombie_horse_ridden"});
        
        // Used items
        STAT_ALIASES.put("used.cooked_beef", new String[]{"used.cooked_beef", "minecraft:used.cooked_beef"});
        STAT_ALIASES.put("used.cooked_chicken", new String[]{"used.cooked_chicken", "minecraft:used.cooked_chicken"});
        STAT_ALIASES.put("used.cooked_porkchop", new String[]{"used.cooked_porkchop", "minecraft:used.cooked_porkchop"});
        STAT_ALIASES.put("used.cooked_rabbit", new String[]{"used.cooked_rabbit", "minecraft:used.cooked_rabbit"});
        STAT_ALIASES.put("used.cooked_mutton", new String[]{"used.cooked_mutton", "minecraft:used.cooked_mutton"});
        STAT_ALIASES.put("used.cooked_salmon", new String[]{"used.cooked_salmon", "minecraft:used.cooked_salmon"});
        STAT_ALIASES.put("used.cooked_cod", new String[]{"used.cooked_cod", "minecraft:used.cooked_cod"});
        STAT_ALIASES.put("used.bread", new String[]{"used.bread", "minecraft:used.bread"});
        STAT_ALIASES.put("used.apple", new String[]{"used.apple", "minecraft:used.apple"});
        STAT_ALIASES.put("used.carrot", new String[]{"used.carrot", "minecraft:used.carrot"});
        STAT_ALIASES.put("used.potato", new String[]{"used.potato", "minecraft:used.potato"});
        STAT_ALIASES.put("used.beetroot", new String[]{"used.beetroot", "minecraft:used.beetroot"});
        STAT_ALIASES.put("used.cookie", new String[]{"used.cookie", "minecraft:used.cookie"});
        STAT_ALIASES.put("used.melon_slice", new String[]{"used.melon_slice", "minecraft:used.melon_slice"});
        STAT_ALIASES.put("used.sweet_berries", new String[]{"used.sweet_berries", "minecraft:used.sweet_berries"});
        STAT_ALIASES.put("used.glow_berries", new String[]{"used.glow_berries", "minecraft:used.glow_berries"});
        STAT_ALIASES.put("used.golden_apple", new String[]{"used.golden_apple", "minecraft:used.golden_apple"});
        STAT_ALIASES.put("used.enchanted_golden_apple", new String[]{"used.enchanted_golden_apple", "minecraft:used.enchanted_golden_apple"});
        STAT_ALIASES.put("used.golden_carrot", new String[]{"used.golden_carrot", "minecraft:used.golden_carrot"});
        STAT_ALIASES.put("used.pumpkin_pie", new String[]{"used.pumpkin_pie", "minecraft:used.pumpkin_pie"});
        STAT_ALIASES.put("used.cake", new String[]{"used.cake", "minecraft:used.cake"});
        STAT_ALIASES.put("used.mushroom_stew", new String[]{"used.mushroom_stew", "minecraft:used.mushroom_stew"});
        STAT_ALIASES.put("used.rabbit_stew", new String[]{"used.rabbit_stew", "minecraft:used.rabbit_stew"});
        STAT_ALIASES.put("used.beetroot_soup", new String[]{"used.beetroot_soup", "minecraft:used.beetroot_soup"});
        STAT_ALIASES.put("used.suspicious_stew", new String[]{"used.suspicious_stew", "minecraft:used.suspicious_stew"});
        STAT_ALIASES.put("used.milk_bucket", new String[]{"used.milk_bucket", "minecraft:used.milk_bucket"});
        STAT_ALIASES.put("used.honey_bottle", new String[]{"used.honey_bottle", "minecraft:used.honey_bottle"});
        STAT_ALIASES.put("used.potion", new String[]{"used.potion", "minecraft:used.potion"});
        STAT_ALIASES.put("used.experience_bottle", new String[]{"used.experience_bottle", "minecraft:used.experience_bottle"});
        STAT_ALIASES.put("used.totem_of_undying", new String[]{"used.totem_of_undying", "minecraft:used.totem_of_undying"});
        STAT_ALIASES.put("used.ender_pearl", new String[]{"used.ender_pearl", "minecraft:used.ender_pearl"});
        STAT_ALIASES.put("used.ender_eye", new String[]{"used.ender_eye", "minecraft:used.ender_eye"});
        STAT_ALIASES.put("used.firework_rocket", new String[]{"used.firework_rocket", "minecraft:used.firework_rocket"});
        STAT_ALIASES.put("used.bow", new String[]{"used.bow", "minecraft:used.bow"});
        STAT_ALIASES.put("used.crossbow", new String[]{"used.crossbow", "minecraft:used.crossbow"});
        STAT_ALIASES.put("used.trident", new String[]{"used.trident", "minecraft:used.trident"});
        STAT_ALIASES.put("used.shield", new String[]{"used.shield", "minecraft:used.shield"});
        STAT_ALIASES.put("used.shears", new String[]{"used.shears", "minecraft:used.shears"});
        STAT_ALIASES.put("used.flint_and_steel", new String[]{"used.flint_and_steel", "minecraft:used.flint_and_steel"});
        STAT_ALIASES.put("used.bucket", new String[]{"used.bucket", "minecraft:used.bucket"});
        STAT_ALIASES.put("used.lava_bucket", new String[]{"used.lava_bucket", "minecraft:used.lava_bucket"});
        STAT_ALIASES.put("used.bone_meal", new String[]{"used.bone_meal", "minecraft:used.bone_meal"});
        STAT_ALIASES.put("used.name_tag", new String[]{"used.name_tag", "minecraft:used.name_tag"});
        STAT_ALIASES.put("used.fire_charge", new String[]{"used.fire_charge", "minecraft:used.fire_charge"});
        STAT_ALIASES.put("used.spyglass", new String[]{"used.spyglass", "minecraft:used.spyglass"});
        STAT_ALIASES.put("used.clock", new String[]{"used.clock", "minecraft:used.clock"});
        STAT_ALIASES.put("used.compass", new String[]{"used.compass", "minecraft:used.compass"});
        STAT_ALIASES.put("used.map", new String[]{"used.map", "minecraft:used.map"});
        STAT_ALIASES.put("used.carrot_on_a_stick", new String[]{"used.carrot_on_a_stick", "minecraft:used.carrot_on_a_stick"});
        STAT_ALIASES.put("used.warped_fungus_on_a_stick", new String[]{"used.warped_fungus_on_a_stick", "minecraft:used.warped_fungus_on_a_stick"});
        STAT_ALIASES.put("used.saddle", new String[]{"used.saddle", "minecraft:used.saddle"});
        STAT_ALIASES.put("used.tnt", new String[]{"used.tnt", "minecraft:used.tnt"});
        STAT_ALIASES.put("used.beacon", new String[]{"used.beacon", "minecraft:used.beacon"});
        STAT_ALIASES.put("used.conduit", new String[]{"used.conduit", "minecraft:used.conduit"});
        STAT_ALIASES.put("used.turtle_helmet", new String[]{"used.turtle_helmet", "minecraft:used.turtle_helmet"});
        STAT_ALIASES.put("used.phantom_membrane", new String[]{"used.phantom_membrane", "minecraft:used.phantom_membrane"});
        STAT_ALIASES.put("used.banner", new String[]{"used.banner", "minecraft:used.banner"});
        STAT_ALIASES.put("used.lead", new String[]{"used.lead", "minecraft:used.lead"});
        STAT_ALIASES.put("used.bell", new String[]{"used.bell", "minecraft:used.bell"});
        STAT_ALIASES.put("used.jukebox", new String[]{"used.jukebox", "minecraft:used.jukebox"});
        STAT_ALIASES.put("used.note_block", new String[]{"used.note_block", "minecraft:used.note_block"});
        STAT_ALIASES.put("used.music_disc", new String[]{"used.music_disc", "minecraft:used.music_disc"});
        STAT_ALIASES.put("used.snowball", new String[]{"used.snowball", "minecraft:used.snowball"});
        STAT_ALIASES.put("used.sugar", new String[]{"used.sugar", "minecraft:used.sugar"});
        STAT_ALIASES.put("used.dragon_breath", new String[]{"used.dragon_breath", "minecraft:used.dragon_breath"});
        STAT_ALIASES.put("used.nether_star", new String[]{"used.nether_star", "minecraft:used.nether_star"});
        STAT_ALIASES.put("used.pufferfish", new String[]{"used.pufferfish", "minecraft:used.pufferfish"});
        STAT_ALIASES.put("used.poisonous_potato", new String[]{"used.poisonous_potato", "minecraft:used.poisonous_potato"});
        STAT_ALIASES.put("used.baked_potato", new String[]{"used.baked_potato", "minecraft:used.baked_potato"});
        STAT_ALIASES.put("used.beetroot_soup", new String[]{"used.beetroot_soup", "minecraft:used.beetroot_soup"});
        STAT_ALIASES.put("used.rabbit_stew", new String[]{"used.rabbit_stew", "minecraft:used.rabbit_stew"});
        STAT_ALIASES.put("used.mushroom_stew", new String[]{"used.mushroom_stew", "minecraft:used.mushroom_stew"});
        STAT_ALIASES.put("used.suspicious_stew", new String[]{"used.suspicious_stew", "minecraft:used.suspicious_stew"});
        
        // Advancements
        STAT_ALIASES.put("advancements.completed", new String[]{"advancements.completed", "minecraft:advancements.completed"});
        STAT_ALIASES.put("advancements.biomes", new String[]{"advancements.biomes", "minecraft:advancements.biomes"});
    }
    
    public StatResolver(LogManager logger) {
        this.logger = logger;
    }
    
    public Long resolveStatValue(Map<String, Object> stats, String statPath) {
        String[] pathParts = statPath.split("\\.");
        if (pathParts.length < 2 || pathParts.length > 3) {
            logger.warning("Invalid stat path format: " + statPath + " (expected format: category.stat or category.subcategory.stat)");
            return null;
        }
        
        String category = pathParts[0];
        String statName;
        
        if (pathParts.length == 2) {
            statName = pathParts[1];
        } else {
            // Handle 3-part paths like custom.damage_taken.fire
            statName = pathParts[1] + "." + pathParts[2];
        }
        
        logger.statPath("Looking for stat: " + category + "." + statName);
        logger.availableStats("Available categories: " + stats.keySet());
        
        // Try the original path first
        Long value = getStatValueFromPath(stats, category, statName);
        if (value != null) {
            logger.statDetail("Found stat value: " + value + " for " + statPath);
            return value;
        }
        
        // Special handling for "total" stats - aggregate all values in the category
        if (statName.equals("total")) {
            value = getTotalForCategory(stats, category);
            if (value != null) {
                logger.statDetail("Calculated total for " + category + ": " + value);
                return value;
            }
        }
        
        // Try alternative paths from aliases
        String[] aliases = STAT_ALIASES.get(statPath);
        if (aliases != null) {
            for (String alias : aliases) {
                String[] aliasParts = alias.split("\\.");
                if (aliasParts.length >= 2) {
                    String aliasCategory = aliasParts[0];
                    String aliasStatName;
                    
                    if (aliasParts.length == 2) {
                        aliasStatName = aliasParts[1];
                    } else {
                        // Handle 3-part aliases
                        aliasStatName = aliasParts[1] + "." + aliasParts[2];
                    }
                    
                    logger.statPath("Trying alias: " + alias);
                    value = getStatValueFromPath(stats, aliasCategory, aliasStatName);
                    if (value != null) {
                        logger.statDetail("Found stat value: " + value + " for alias " + alias);
                        return value;
                    }
                }
            }
        }
        
        // Try with minecraft: prefix
        String minecraftPath = "minecraft:" + statPath;
        String[] minecraftParts = minecraftPath.split("\\.");
        if (minecraftParts.length >= 2) {
            String minecraftCategory = minecraftParts[0];
            String minecraftStatName;
            
            if (minecraftParts.length == 2) {
                minecraftStatName = minecraftParts[1];
            } else {
                // Handle 3-part minecraft paths
                minecraftStatName = minecraftParts[1] + "." + minecraftParts[2];
            }
            
            logger.statPath("Trying minecraft prefix: " + minecraftPath);
            value = getStatValueFromPath(stats, minecraftCategory, minecraftStatName);
            if (value != null) {
                logger.statDetail("Found stat value: " + value + " for minecraft path " + minecraftPath);
                return value;
            }
        }
        
        logger.statDetail("Stat not found: " + statPath + " (tried all aliases)");
        return null;
    }
    
    private Long getStatValueFromPath(Map<String, Object> stats, String category, String statName) {
        Object categoryData = stats.get(category);
        if (categoryData == null) {
            return null;
        }
        
        if (categoryData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categoryMap = (Map<String, Object>) categoryData;
            logger.availableStats("Available stats in category " + category + ": " + categoryMap.keySet());
            
            Object value = categoryMap.get(statName);
            if (value == null) {
                return null;
            }
            
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof Integer) {
                return ((Integer) value).longValue();
            } else {
                logger.warning("Unexpected value type for stat " + statName + ": " + value.getClass().getSimpleName());
                return null;
            }
        }
        
        return null;
    }
    
    private Long getTotalForCategory(Map<String, Object> stats, String category) {
        Object categoryData = stats.get(category);
        if (categoryData == null) {
            return null;
        }
        
        if (categoryData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> categoryMap = (Map<String, Object>) categoryData;
            
            long total = 0;
            int count = 0;
            
            for (Map.Entry<String, Object> entry : categoryMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Long) {
                    total += (Long) value;
                    count++;
                } else if (value instanceof Integer) {
                    total += ((Integer) value).longValue();
                    count++;
                }
            }
            
            logger.statDetail("Aggregated " + count + " stats in category " + category + " for total: " + total);
            return total > 0 ? total : null;
        }
        
        return null;
    }
    
    public List<String> getAvailableStats(Map<String, Object> stats) {
        List<String> availableStats = new ArrayList<>();
        
        for (Map.Entry<String, Object> categoryEntry : stats.entrySet()) {
            String category = categoryEntry.getKey();
            Object categoryData = categoryEntry.getValue();
            
            if (categoryData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> categoryMap = (Map<String, Object>) categoryData;
                
                for (String statName : categoryMap.keySet()) {
                    availableStats.add(category + "." + statName);
                }
            }
        }
        
        return availableStats;
    }
} 