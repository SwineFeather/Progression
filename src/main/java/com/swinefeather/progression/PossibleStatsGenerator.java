package com.swinefeather.progression;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PossibleStatsGenerator {
    public static void main(String[] args) throws IOException {
        Map<String, Map<String, Object>> stats = new HashMap<>();

        // Mined: all blocks
        Map<String, Object> mined = new HashMap<>();
        for (Material m : Material.values()) {
            if (m.isBlock()) {
                mined.put(m.getKey().getKey(), 0);
            }
        }
        stats.put("mined", mined);

        // Items for used, crafted, broken, picked_up, dropped
        Map<String, Object> items = new HashMap<>();
        for (Material m : Material.values()) {
            if (m.isItem()) {
                items.put(m.getKey().getKey(), 0);
            }
        }
        stats.put("used", new HashMap<>(items));
        stats.put("crafted", new HashMap<>(items));
        stats.put("broken", new HashMap<>(items));
        stats.put("picked_up", new HashMap<>(items));
        stats.put("dropped", new HashMap<>(items));

        // Killed and killed_by: all living entities
        Map<String, Object> entities = new HashMap<>();
        for (EntityType e : EntityType.values()) {
            if (e.isAlive()) {
                entities.put(e.getKey().getKey(), 0);
            }
        }
        stats.put("killed", new HashMap<>(entities));
        stats.put("killed_by", new HashMap<>(entities));

        // Custom stats
        Map<String, Object> custom = new HashMap<>();
        String[] customStats = {
            "damage_dealt", "damage_dealt_absorbed", "damage_dealt_resisted", "damage_taken", "damage_blocked_by_shield",
            "damage_absorbed", "damage_resisted", "deaths", "mob_kills", "animals_bred", "player_kills", "fish_caught",
            "talked_to_villager", "traded_with_villager", "eat_cake_slice", "fill_cauldron", "use_cauldron",
            "clean_armor", "clean_banner", "clean_shulker_box", "interact_with_brewingstand", "interact_with_beacon",
            "inspect_dropper", "inspect_hopper", "inspect_dispenser", "play_noteblock", "tune_noteblock",
            "pot_flower", "trigger_trapped_chest", "open_enderchest", "enchant_item", "play_record",
            "interact_with_furnace", "interact_with_crafting_table", "open_chest", "sleep_in_bed",
            "open_shulker_box", "open_barrel", "interact_with_blast_furnace", "interact_with_smoker",
            "interact_with_lectern", "interact_with_campfire", "interact_with_cartography_table",
            "interact_with_loom", "interact_with_stonecutter", "bell_ring", "raid_trigger", "raid_win",
            "stuck_in_raid", "interact_with_anvil", "interact_with_grindstone", "interact_with_smithing_table",
            "drop", "damage_dealt", "sneak_time", "jump", "leave_game", "play_time", "total_world_time",
            "time_since_death", "time_since_rest", "walk_one_cm", "crouch_one_cm", "sprint_one_cm",
            "swim_one_cm", "fall_one_cm", "climb_one_cm", "fly_one_cm", "minecart_one_cm",
            "boat_one_cm", "pig_one_cm", "horse_one_cm", "aviate_one_cm", "strider_one_cm",
            "walk_on_water_one_cm", "walk_under_water_one_cm"
        };
        for (String s : customStats) {
            custom.put(s, 0);
        }
        stats.put("custom", custom);

        // Advancements: known list (not exhaustive, as no enum)
        Map<String, Object> advancements = new HashMap<>();
        String[] advList = {
            "story/root", "story/mine_stone", "story/upgrade_tools", "story/smelt_iron",
            "story/obtain_armor", "story/lava_bucket", "story/iron_tools", "story/deflect_arrow",
            "story/form_obsidian", "story/mine_diamond", "story/enter_the_nether", "story/shiny_gear",
            "story/enchant_item", "story/cure_zombie_villager", "story/follow_ender_eye", "story/enter_the_end",
            "nether/root", "nether/return_to_sender", "nether/find_bastion", "nether/find_fortress",
            "nether/fast_travel", "nether/get_wither_skull", "nether/obtain_blaze_rod", "nether/summon_wither",
            "nether/brew_potion", "nether/create_beacon", "nether/all_potions", "nether/create_full_beacon",
            "nether/all_effects", "nether/explore_nether", "nether/nether_travel", "nether/distract_piglin",
            "nether/ride_strider", "nether/uneasy_alliance", "nether/loot_bastion", "nether/use_lodestone",
            "nether/obtain_crying_obsidian", "nether/charge_respawn_anchor", "nether/ride_strider_in_overworld_lava",
            "nether/obtain_ancient_debris", "nether/obtain_netherite_armor", "end/root", "end/kill_dragon",
            "end/dragon_egg", "end/enter_end_gateway", "end/respawn_dragon", "end/dragon_breath",
            "end/find_end_city", "end/elytra", "end/levitate",
            // Add more if needed, e.g., adventure, husbandry ones
            "adventure/root", "adventure/voluntary_exile", "adventure/spyglass_at_parrot", "adventure/kill_a_mob",
            "adventure/trade", "adventure/honey_block_slide", "adventure/ol_betsy", "adventure/sleep_in_bed",
            "adventure/hero_of_the_village", "adventure/spyglass_at_ghast", "adventure/throw_trident",
            "adventure/shoot_arrow", "adventure/kill_all_mobs", "adventure/totem_of_undying", "adventure/summon_iron_golem",
            "adventure/trade_at_world_height", "adventure/two_birds_one_arrow", "adventure/whos_the_pillager_now",
            "adventure/arbalistic", "adventure/adventuring_time", "adventure/spyglass_at_dragon", "adventure/very_very_frightening",
            "adventure/sniper_duel", "adventure/bullseye", "husbandry/root", "husbandry/bred_all_animals",
            "husbandry/plant_seed", "husbandry/tame_an_animal", "husbandry/fishy_business", "husbandry/silk_touch_nest",
            "husbandry/ride_a_boat_with_a_goat", "husbandry/make_a_sign_glow", "husbandry/tactical_fishing",
            "husbandry/balanced_diet", "husbandry/obtain_netherite_hoe", "husbandry/bred_all_animals" // duplicate? no
        };
        for (String a : advList) {
            advancements.put(a, false);
        }
        stats.put("advancements", advancements);

        // Write to file
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter("possible_stats.json")) {
            gson.toJson(stats, writer);
        }
        System.out.println("Generated possible_stats.json with all possible stats in nested format.");
    }
} 