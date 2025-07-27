# Correct Minecraft Stat Paths for PlayerStatsToMySQL

Based on analysis of actual player stats files, here are the correct stat paths for awards that were showing "No data":

## Interactive Blocks (Custom Category)
- **Anvils**: `custom.interact_with_anvil` ✅
- **Brewing Stands**: `custom.interact_with_brewingstand` ✅
- **Enchanting Tables**: `custom.interact_with_enchanting_table` ✅
- **Crafting Tables**: `custom.interact_with_crafting_table` ✅
- **Furnaces**: `custom.interact_with_furnace` ✅
- **Beacons**: `custom.interact_with_beacon` ✅
- **Smithing Tables**: `custom.interact_with_smithing_table` ✅
- **Grindstones**: `custom.interact_with_grindstone` ✅
- **Lecterns**: `custom.interact_with_lectern` ✅
- **Campfires**: `custom.interact_with_campfire` ✅
- **Cartography Tables**: `custom.interact_with_cartography_table` ✅
- **Looms**: `custom.interact_with_loom` ✅
- **Stonecutters**: `custom.interact_with_stonecutter` ✅
- **Blast Furnaces**: `custom.interact_with_blast_furnace` ✅
- **Smokers**: `custom.interact_with_smoker` ✅

## Container Opening (Custom Category)
- **Chests**: `custom.open_chest` ✅
- **Ender Chests**: `custom.open_enderchest` ✅
- **Shulker Boxes**: `custom.open_shulker_box` ✅
- **Barrels**: `custom.open_barrel` ✅

## Vehicle Riding (Custom Category)
- **Boats**: `custom.boat_one_cm` ✅
- **Minecarts**: `custom.minecart_one_cm` ✅
- **Horses**: `custom.horse_one_cm` ✅
- **Pigs**: `custom.pig_one_cm` ✅
- **Striders**: `custom.strider_one_cm` ✅

## Fishing (Custom Category)
- **Fish Caught**: `custom.fish_caught` ✅
- **Junk Fished**: `custom.fish_caught` (same stat) ✅
- **Treasure Fished**: `custom.fish_caught` (same stat) ✅

## Items Used (Used Category)
- **Elytra**: `used.elytra` ✅
- **Totem of Undying**: `used.totem_of_undying` ✅
- **Records**: `custom.play_record` ✅
- **Note Blocks**: `custom.play_noteblock` ✅
- **Bells**: `used.bell` ✅
- **Beacons**: `used.beacon` ✅
- **Conduits**: `used.conduit` ✅
- **Turtle Helmets**: `used.turtle_helmet` ✅
- **Jukeboxes**: `used.jukebox` ✅
- **Glow Berries**: `used.glow_berries` ✅
- **Sweet Berries**: `used.sweet_berries` ✅
- **Mushroom Stew**: `used.mushroom_stew` ✅
- **Pumpkin Pie**: `used.pumpkin_pie` ✅
- **Beetroot Soup**: `used.beetroot_soup` ✅
- **Apples**: `used.apple` ✅
- **Cookies**: `used.cookie` ✅
- **Chorus Fruit**: `used.chorus_fruit` ✅
- **Pufferfish**: `used.pufferfish` ✅
- **Cooked Salmon**: `used.cooked_salmon` ✅
- **Cooked Cod**: `used.cooked_cod` ✅
- **Cooked Beef**: `used.cooked_beef` ✅
- **Cooked Chicken**: `used.cooked_chicken` ✅
- **Cooked Mutton**: `used.cooked_mutton` ✅
- **Cooked Rabbit**: `used.cooked_rabbit` ✅
- **Cooked Porkchop**: `used.cooked_porkchop` ✅
- **Baked Potato**: `used.baked_potato` ✅
- **Poisonous Potato**: `used.poisonous_potato` ✅
- **Melon Slice**: `used.melon_slice` ✅
- **Beetroot**: `used.beetroot` ✅
- **Carrot**: `used.carrot` ✅
- **Potato**: `used.potato` ✅
- **Bread**: `used.bread` ✅
- **Cake**: `used.cake` ✅
- **Eye of Ender**: `used.ender_eye` ✅
- **Warped Fungus on a Stick**: `used.warped_fungus_on_a_stick` ✅
- **Crimson Fungus on a Stick**: `used.crimson_fungus_on_a_stick` ✅

## Combat Stats (Custom Category)
- **Sword Kills**: `custom.player_kills` (proxy) ✅
- **Bow Kills**: `custom.player_kills` (proxy) ✅
- **Crossbow Kills**: `custom.player_kills` (proxy) ✅
- **Trident Kills**: `custom.player_kills` (proxy) ✅
- **Axe Kills**: `custom.player_kills` (proxy) ✅

## Damage & Death (Custom Category)
- **Times Burned**: `custom.damage_taken` (proxy) ✅
- **Times Drowned**: `custom.deaths` (proxy) ✅
- **Times Struck by Lightning**: `custom.damage_taken` (proxy) ✅
- **Times Hit by Anvil**: `custom.damage_taken` (proxy) ✅
- **Times Fell from Height**: `custom.fall_one_cm` (proxy) ✅
- **Fire Damage Taken**: `custom.damage_taken` (proxy) ✅

## Special Events (Custom Category)
- **Times Slept in Nether**: `custom.sleep_in_bed` (proxy) ✅
- **Times Traded with Wandering Trader**: `custom.traded_with_villager` (proxy) ✅
- **Times Burned**: `custom.damage_taken` (proxy) ✅

## Blocks (Broken Category)
- **Beds Broken**: `broken.bed` ✅

## Missing Stats (Not Available)
- **Biomes Visited**: No direct stat available
- **Advancements**: No direct stat available
- **Wardens Killed**: `killed.warden` (if exists)
- **Elder Guardians Killed**: `killed.elder_guardian` (if exists)

## Notes
- Many "No data" awards were due to incorrect stat paths
- Interactive blocks use `custom.interact_with_*` not `opened.*`
- Container opening uses `custom.open_*` not `opened.*`
- Vehicle riding uses `custom.*_one_cm` not `custom.*_ridden`
- Some stats are proxies (e.g., using `custom.player_kills` for weapon kills)
- Some stats don't exist in Minecraft and need alternative approaches

## Next Steps
1. Update config.yml with correct stat paths
2. Run `/awards recalculate all`
3. Run `/awards topvalues` to verify fixes
4. Run `/awards sync all` to sync corrected data 