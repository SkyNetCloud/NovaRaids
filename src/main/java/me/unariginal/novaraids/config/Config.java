package me.unariginal.novaraids.config;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import me.unariginal.novaraids.NovaRaids;
import me.unariginal.novaraids.data.Boss;
import me.unariginal.novaraids.data.BossbarData;
import me.unariginal.novaraids.data.Category;
import me.unariginal.novaraids.data.Location;
import me.unariginal.novaraids.data.rewards.*;
import me.unariginal.novaraids.managers.Messages;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentChanges;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.time.LocalTime;
import java.util.*;

public class Config {
    private final NovaRaids nr = NovaRaids.INSTANCE;
    private Settings settings;
    private Messages messages;
    private List<Category> categories;
    private List<Boss> bosses;
    private List<Location> locations;
    private List<BossbarData> bossbars;
    private List<Reward> rewards;
    private List<RewardPool> reward_pools;

    public Config() {
        try {
            checkFiles();
        } catch (IOException e) {
            nr.logger().error("[RAIDS] Failed to generate default configuration files. Error: {}", e.getMessage());
        }

        loadConfig();
        loadCategories();
        loadBosses();
        loadLocations();
        loadBossBars();
        loadMessages();
        loadRewards();
        loadRewardPools();
    }

    private void checkFiles() throws IOException {
        File rootFolder = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids").toFile();
        if (!rootFolder.exists()) {
            if (!rootFolder.mkdirs()) {
                return;
            }
        }

        File bossesFolder = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/bosses").toFile();
        if (!bossesFolder.exists()) {
            if (!bossesFolder.mkdirs()) {
                return;
            }
        }

        String[] fileNames = {
                "config",
                "locations",
                "messages",
                "bossbars",
                "categories",
                "rewards",
                "reward_pools"
        };

        for (String fileName : fileNames) {
            File file = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/" + fileName + ".json").toFile();
            if (file.createNewFile()) {
                InputStream stream = NovaRaids.class.getResourceAsStream("/nr_config_files/" + fileName + ".json");
                assert stream != null;
                OutputStream out = new FileOutputStream(file);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = stream.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }

                stream.close();
                out.close();
            }
        }

        File boss_example = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/bosses/example_boss.json").toFile();
        if (boss_example.createNewFile()) {
            InputStream stream = NovaRaids.class.getResourceAsStream("/nr_config_files/bosses/example_boss.json");
            assert stream != null;
            OutputStream out = new FileOutputStream(boss_example);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            stream.close();
            out.close();
        }
    }

    private JsonElement getRoot(File file) {
        try {
            return JsonParser.parseReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            nr.logger().error("[RAIDS] Failed to load file. Error: {}", e.getMessage());
            return null;
        }
    }

    private void loadConfig() {
        File configFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/config.json").toFile();

        JsonElement root = getRoot(configFile);
        assert root != null;
        JsonObject config = root.getAsJsonObject();
        nr.logger().info("[RAIDS] Loading config..");

        TimeZone timezone = TimeZone.getTimeZone(config.get("timezone").getAsString());
        TimeZone.setDefault(timezone);

        JsonObject settingsObject = config.getAsJsonObject("raid_settings");
        int raid_radius = settingsObject.get("raid_radius").getAsInt();
        int raid_pushback_radius = settingsObject.get("raid_pushback_radius").getAsInt();
        boolean bosses_glow = settingsObject.get("bosses_glow").getAsBoolean();
        boolean do_health_scaling = settingsObject.get("do_health_scaling").getAsBoolean();
        int health_increase = settingsObject.get("health_increase_per_player").getAsInt();
        boolean do_catch_phase = settingsObject.get("do_catch_phase").getAsBoolean();
        int setup_phase_time = settingsObject.get("setup_phase_time").getAsInt();
        int fight_phase_time = settingsObject.get("fight_phase_time").getAsInt();
        int pre_catch_phase_time = settingsObject.get("pre_catch_phase_time").getAsInt();
        int catch_phase_time = settingsObject.get("catch_phase_time").getAsInt();

        JsonObject banned_section = settingsObject.getAsJsonObject("banned_section");
        JsonArray banned_pokemon = banned_section.getAsJsonArray("banned_pokemon");
        JsonArray banned_moves = banned_section.getAsJsonArray("banned_moves");
        JsonArray banned_abilities = banned_section.getAsJsonArray("banned_abilities");
        JsonArray banned_held_items = banned_section.getAsJsonArray("banned_held_items");
        JsonArray banned_bag_items = banned_section.getAsJsonArray("banned_bag_items");

        List<Species> banned_pokemon_list = new ArrayList<>();
        for (JsonElement element : banned_pokemon) {
            String speciesString = element.getAsString();
            Species species = PokemonSpecies.INSTANCE.getByName(speciesString);
            if (species != null) {
                banned_pokemon_list.add(species);
            }
        }

        List<Move> banned_move_list = new ArrayList<>();
        for (JsonElement element : banned_moves) {
            String moveString = element.getAsString();
            MoveTemplate moveTemplate = Moves.INSTANCE.getByName(moveString);
            if (moveTemplate != null) {
                banned_move_list.add(moveTemplate.create());
            }
        }

        List<Ability> banned_abilities_list = new ArrayList<>();
        for (JsonElement element : banned_abilities) {
            String abilityString = element.getAsString();
            AbilityTemplate ability = Abilities.INSTANCE.get(abilityString);
            if (ability != null) {
                banned_abilities_list.add(ability.create(false, Priority.LOWEST));
            }
        }

        List<Item> banned_held_item_list = new ArrayList<>();
        for (JsonElement element : banned_held_items) {
            String itemString = element.getAsString();
            Item item = Registries.ITEM.get(Identifier.of(itemString));
            banned_held_item_list.add(item);
        }

        List<Item> banned_bag_item_list = new ArrayList<>();
        for (JsonElement element : banned_bag_items) {
            String itemString = element.getAsString();
            Item item = Registries.ITEM.get(Identifier.of(itemString));
            banned_bag_item_list.add(item);
        }

        JsonObject items = settingsObject.getAsJsonObject("items");
        Item voucher_item = Registries.ITEM.get(Identifier.of(items.get("voucher_item").getAsString()));
        ComponentChanges voucher_item_data = null;
        if (items.get("voucher_item_data") != null) {
            DataResult<Pair<ComponentChanges, JsonElement>> data = ComponentChanges.CODEC.decode(JsonOps.INSTANCE, items.get("voucher_item_data"));
            voucher_item_data = data.getOrThrow().getFirst();
        }
        Item pass_item = Registries.ITEM.get(Identifier.of(items.get("pass_item").getAsString()));
        ComponentChanges pass_item_data = null;
        if (items.get("pass_item_data") != null) {
            DataResult<Pair<ComponentChanges, JsonElement>> data = ComponentChanges.CODEC.decode(JsonOps.INSTANCE, items.get("pass_item_data"));
            pass_item_data = data.getOrThrow().getFirst();
        }

        boolean use_raid_pokeballs = items.get("use_raid_pokeballs").getAsBoolean();
        Item raid_pokeball = null;
        ComponentChanges raid_pokeball_data = null;
        if (use_raid_pokeballs) {
            raid_pokeball = Registries.ITEM.get(Identifier.of(items.get("raid_pokeball").getAsString()));
            if (items.get("raid_pokeball_data") != null) {
                DataResult<Pair<ComponentChanges, JsonElement>> data = ComponentChanges.CODEC.decode(JsonOps.INSTANCE, items.get("raid_pokeball_data"));
                raid_pokeball_data = data.getOrThrow().getFirst();
            }
        }

        this.settings = new Settings(
                raid_radius,
                raid_pushback_radius,
                bosses_glow,
                do_health_scaling,
                health_increase,
                do_catch_phase,
                setup_phase_time,
                fight_phase_time,
                pre_catch_phase_time,
                catch_phase_time,
                banned_pokemon_list,
                banned_move_list,
                banned_abilities_list,
                banned_held_item_list,
                banned_bag_item_list,
                voucher_item,
                voucher_item_data,
                pass_item,
                pass_item_data,
                use_raid_pokeballs,
                raid_pokeball,
                raid_pokeball_data
        );
    }

    private void loadCategories() {
        File categoriesFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/categories.json").toFile();

        JsonElement root = getRoot(categoriesFile);
        assert root != null;
        JsonObject categories = root.getAsJsonObject();
        nr.logger().info("[RAIDS] Loading categories...");

        List<Category> categoriesList = new ArrayList<>();
        for (String category : categories.keySet()) {
            JsonObject categoryObject = categories.get(category).getAsJsonObject();

            boolean require_pass = categoryObject.get("require_pass").getAsBoolean();

            JsonObject player_count = categoryObject.getAsJsonObject("player_count");
            int min_players = player_count.get("min").getAsInt();
            int max_players = player_count.get("max").getAsInt();

            JsonObject random_wait_time = categoryObject.getAsJsonObject("random_wait_time");
            int min_wait_time = random_wait_time.get("min").getAsInt();
            int max_wait_time = random_wait_time.get("max").getAsInt();

            JsonArray set_times = categoryObject.getAsJsonArray("set_times");
            List<LocalTime> set_times_list = new ArrayList<>();
            for (JsonElement element : set_times) {
                String time = element.getAsString();
                set_times_list.add(LocalTime.parse(time));
            }

            JsonArray rewards = categoryObject.getAsJsonArray("rewards");
            Map<List<String>, List<String>> rewards_map = new HashMap<>();
            for (JsonElement element : rewards) {
                JsonObject reward = element.getAsJsonObject();
                List<String> places = reward.getAsJsonArray("places").asList().stream().map(JsonElement::getAsString).toList();
                List<String> reward_pools = reward.getAsJsonArray("reward_pools").asList().stream().map(JsonElement::getAsString).toList();
                rewards_map.put(places, reward_pools);
            }

            categoriesList.add(new Category(category, require_pass, min_players, max_players, min_wait_time, max_wait_time, set_times_list, rewards_map));
        }
        this.categories = categoriesList;
    }

    private void loadBosses() {
        File bossesFolder = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/bosses").toFile();
        if (!bossesFolder.exists()) {
            if (!bossesFolder.mkdirs()) {
                return;
            }
        }

        List<Boss> bossesList = new ArrayList<>();
        for (File bossFile : Objects.requireNonNull(bossesFolder.listFiles())) {
            if (bossFile.getName().endsWith(".json")) {
                JsonElement root = getRoot(bossFile);
                assert root != null;
                JsonObject bossObject = root.getAsJsonObject();
                JsonObject pokemon_details = bossObject.getAsJsonObject("pokemon_details");
                Species species = PokemonSpecies.INSTANCE.getByName(pokemon_details.get("species").getAsString());
                if (species != null) {
                    int level = pokemon_details.get("level").getAsInt();
                    AbilityTemplate abilityTemplate = Abilities.INSTANCE.get(pokemon_details.get("ability").getAsString());
                    if (abilityTemplate != null) {
                        Ability ability = abilityTemplate.create(false, Priority.LOWEST);
                        Nature nature = Natures.INSTANCE.getNature(pokemon_details.get("nature").getAsString());
                        PokemonProperties form = PokemonProperties.Companion.parse(pokemon_details.get("form").getAsString());
                        Gender gender = Gender.valueOf(pokemon_details.get("gender").getAsString().toUpperCase());
                        boolean shiny = pokemon_details.get("shiny").getAsBoolean();
                        float scale = pokemon_details.get("scale").getAsFloat();
                        String held_item_string = pokemon_details.get("held_item").getAsString();
                        Item held_item = null;
                        if (held_item_string != null && !held_item_string.isEmpty()) {
                            held_item = Registries.ITEM.get(Identifier.of(pokemon_details.get("held_item").getAsString()));
                        }

                        MoveSet moves = new MoveSet();
                        JsonArray moves_list = pokemon_details.getAsJsonArray("moves");
                        for (int i = 0; (i < moves_list.size() && i < 4); i++) {
                            MoveTemplate moveTemplate = Moves.INSTANCE.getByName(moves_list.get(i).getAsString());
                            if (moveTemplate != null) {
                                moves.setMove(i, moveTemplate.create());
                            }
                        }

                        JsonObject ivObject = pokemon_details.getAsJsonObject("ivs");
                        IVs ivs = new IVs();
                        ivs.set(Stats.HP, ivObject.get("hp").getAsInt());
                        ivs.set(Stats.ATTACK, ivObject.get("atk").getAsInt());
                        ivs.set(Stats.DEFENCE, ivObject.get("def").getAsInt());
                        ivs.set(Stats.SPECIAL_ATTACK, ivObject.get("sp_atk").getAsInt());
                        ivs.set(Stats.SPECIAL_DEFENCE, ivObject.get("sp_def").getAsInt());
                        ivs.set(Stats.SPEED, ivObject.get("spd").getAsInt());

                        JsonObject evObject = pokemon_details.getAsJsonObject("evs");
                        EVs evs = new EVs();
                        evs.set(Stats.HP, evObject.get("hp").getAsInt());
                        evs.set(Stats.ATTACK, evObject.get("atk").getAsInt());
                        evs.set(Stats.DEFENCE, evObject.get("def").getAsInt());
                        evs.set(Stats.SPECIAL_ATTACK, evObject.get("sp_atk").getAsInt());
                        evs.set(Stats.SPECIAL_DEFENCE, evObject.get("sp_def").getAsInt());
                        evs.set(Stats.SPEED, evObject.get("spd").getAsInt());

                        JsonObject boss_details = bossObject.getAsJsonObject("boss_details");
                        int base_health = boss_details.get("base_health").getAsInt();
                        String category = boss_details.get("category").getAsString();

                        Map<String, Double> spawn_locations = new HashMap<>();
                        JsonArray locations = boss_details.getAsJsonArray("locations");
                        for (JsonElement element : locations) {
                            JsonObject locationObject = element.getAsJsonObject();
                            String location = locationObject.get("location").getAsString();
                            double weight = locationObject.get("weight").getAsDouble();
                            spawn_locations.put(location, weight);
                        }


                        bossesList.add(new Boss(bossFile.getName().replace(".json", ""), species, level, ability, nature, form, gender, shiny, scale, held_item, moves, ivs, evs, base_health, category, spawn_locations));
                    } else {
                        nr.logger().error("[RAIDS] Invalid Ability: {}", pokemon_details.get("ability").getAsString());
                    }
                } else {
                    nr.logger().error("[RAIDS] Invalid Boss Species: {}", pokemon_details.get("species").getAsString());
                }
                this.bosses = bossesList;
            }
        }
    }

    private void loadLocations() {
        File locationsFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/locations.json").toFile();

        JsonElement root = getRoot(locationsFile);
        assert root != null;

        JsonObject locationsObject = root.getAsJsonObject();
        List<Location> locationsList = new ArrayList<>();
        for (String location : locationsObject.keySet()) {
            JsonObject locationObject = locationsObject.getAsJsonObject(location);

            double x = locationObject.get("x_pos").getAsDouble();
            double y = locationObject.get("y_pos").getAsDouble();
            double z = locationObject.get("z_pos").getAsDouble();
            Vec3d pos = new Vec3d(x, y, z);

            String world_path = locationObject.get("world").getAsString();
            ServerWorld world = nr.server().getOverworld();
            for (ServerWorld world_loop : nr.server().getWorlds()) {
                if ((world_loop.getRegistryKey().getValue().getNamespace() + ":" + world_loop.getRegistryKey().getValue().getPath()).equalsIgnoreCase(world_path)) {
                    world = world_loop;
                    break;
                }
            }

            Location loc = new Location(location, pos, world);
            locationsList.add(loc);
        }
        this.locations = locationsList;
    }

    private void loadBossBars() {
        File bossbarsFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/bossbars.json").toFile();

        JsonElement root = getRoot(bossbarsFile);
        assert root != null;

        JsonObject bossbarsObject = root.getAsJsonObject();
        List<BossbarData> bossbarDataList = new ArrayList<>();
        for (String bossbar : bossbarsObject.keySet()) {
            JsonObject bossbarObject = bossbarsObject.getAsJsonObject(bossbar);
            String phase = bossbarObject.get("phase").getAsString();
            boolean use_overlay = bossbarObject.get("use_overlay").getAsBoolean();
            String overlay_text = bossbarObject.get("overlay_text").getAsString();
            String bar_color = bossbarObject.get("bar_color").getAsString();
            String bar_style = bossbarObject.get("bar_style").getAsString();
            String bar_text = bossbarObject.get("bar_text").getAsString();
            List<String> bosses = bossbarObject.getAsJsonArray("bosses").asList().stream().map(JsonElement::getAsString).toList();
            List<String> categories = bossbarObject.getAsJsonArray("categories").asList().stream().map(JsonElement::getAsString).toList();
            bossbarDataList.add(new BossbarData(bossbar, phase, use_overlay, overlay_text, bar_color, bar_style, bar_text, bosses, categories));
        }
        this.bossbars = bossbarDataList;
    }

    private void loadMessages() {
        File messagesFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/messages.json").toFile();
        JsonElement root = getRoot(messagesFile);
        assert root != null;
        JsonObject messagesObject = root.getAsJsonObject();
        Map<String, String> messages_map = new HashMap<>();
        String prefix = messagesObject.get("prefix").getAsString();
        JsonObject messages = messagesObject.getAsJsonObject("messages");
        for (String key : messages.keySet()) {
            messages_map.put(key, messages.get(key).getAsString());
        }
        this.messages = new Messages(prefix, messages_map);
    }

    private void loadRewards() {
        File rewardsFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/rewards.json").toFile();
        JsonElement root = getRoot(rewardsFile);
        assert root != null;
        JsonObject rewardsObject = root.getAsJsonObject();
        List<Reward> rewardsList = new ArrayList<>();
        for (String key : rewardsObject.keySet()) {
            JsonObject rewardObject = rewardsObject.getAsJsonObject(key);
            String type = rewardObject.get("type").getAsString();
            Reward reward = null;
            if (type.equalsIgnoreCase("item")) {
                String item = rewardObject.get("item").getAsString();
                JsonElement data = null;
                if (rewardObject.get("data") != null) {
                    data = rewardObject.get("data");
                }
                JsonObject count = rewardObject.getAsJsonObject("count");
                int min_count = count.get("min").getAsInt();
                int max_count = count.get("max").getAsInt();
                reward = new ItemReward(key, item, data, min_count, max_count);
            } else if (type.equalsIgnoreCase("command")) {
                List<String> commands = rewardObject.getAsJsonArray("commands").asList().stream().map(JsonElement::getAsString).toList();
                reward = new CommandReward(key, commands);
            } else if (type.equalsIgnoreCase("pokemon")) {
                JsonObject pokemon_info = rewardObject.getAsJsonObject("pokemon");
                String species = pokemon_info.get("species").getAsString();
                int level = pokemon_info.get("level").getAsInt();
                String ability = pokemon_info.get("ability").getAsString();
                String nature = pokemon_info.get("nature").getAsString();
                String form = pokemon_info.get("form").getAsString();
                String gender = pokemon_info.get("gender").getAsString();
                boolean shiny = pokemon_info.get("shiny").getAsBoolean();
                float scale = pokemon_info.get("scale").getAsFloat();
                String held_item = pokemon_info.get("held_item").getAsString();
                JsonElement held_item_data = null;
                if (pokemon_info.get("held_item_data") != null) {
                    held_item_data = pokemon_info.get("held_item_data");
                }
                List<String> move_set = pokemon_info.getAsJsonArray("moves").asList().stream().map(JsonElement::getAsString).toList();
                JsonObject ivObject = pokemon_info.getAsJsonObject("ivs");
                IVs ivs = new IVs();
                ivs.set(Stats.HP, ivObject.get("hp").getAsInt());
                ivs.set(Stats.ATTACK, ivObject.get("atk").getAsInt());
                ivs.set(Stats.DEFENCE, ivObject.get("def").getAsInt());
                ivs.set(Stats.SPECIAL_ATTACK, ivObject.get("sp_atk").getAsInt());
                ivs.set(Stats.SPECIAL_DEFENCE, ivObject.get("sp_def").getAsInt());
                ivs.set(Stats.SPEED, ivObject.get("spd").getAsInt());
                JsonObject evObject = pokemon_info.getAsJsonObject("evs");
                EVs evs = new EVs();
                evs.set(Stats.HP, evObject.get("hp").getAsInt());
                evs.set(Stats.ATTACK, evObject.get("atk").getAsInt());
                evs.set(Stats.DEFENCE, evObject.get("def").getAsInt());
                evs.set(Stats.SPECIAL_ATTACK, evObject.get("sp_atk").getAsInt());
                evs.set(Stats.SPECIAL_DEFENCE, evObject.get("sp_def").getAsInt());
                evs.set(Stats.SPEED, evObject.get("spd").getAsInt());

                reward = new PokemonReward(key, species, level, ability, nature, form, gender, shiny, scale, held_item, held_item_data, move_set, ivs, evs);
            } else {
                nr.logger().error("Unknown reward type: {}", type);
            }
            rewardsList.add(reward);
        }
        rewards = rewardsList;
    }

    private void loadRewardPools() {
        File rewardPoolsFile = FabricLoader.getInstance().getConfigDir().resolve("NovaRaids/reward_pools.json").toFile();
        JsonElement root = getRoot(rewardPoolsFile);
        assert root != null;
        JsonObject rewardPoolsObject = root.getAsJsonObject();
        List<RewardPool> rewardPools = new ArrayList<>();
        for (String key : rewardPoolsObject.keySet()) {
            JsonObject rewardPoolObject = rewardPoolsObject.getAsJsonObject(key);
            boolean allow_duplicates = rewardPoolObject.get("allow_duplicates").getAsBoolean();
            JsonObject rolls = rewardPoolObject.getAsJsonObject("rolls");
            int min_rolls = rolls.get("min").getAsInt();
            int max_rolls = rolls.get("max").getAsInt();
            JsonArray rewards = rewardPoolObject.getAsJsonArray("rewards");
            Map<String, Double> rewards_map = new HashMap<>();
            for (JsonElement reward : rewards) {
                JsonObject rewardObject = reward.getAsJsonObject();
                String reward_name = rewardObject.get("reward").getAsString();
                double weight = rewardObject.get("weight").getAsDouble();
                rewards_map.put(reward_name, weight);
            }
            rewardPools.add(new RewardPool(key, allow_duplicates, min_rolls, max_rolls, rewards_map));
        }
        reward_pools = rewardPools;
    }

    public Settings getSettings() {
        return settings;
    }

    public Messages getMessages() {
        return messages;
    }

    public List<Category> getCategories() {
        return categories;
    }

    public Category getCategory(String name) {
        for (Category category : categories) {
            if (category.name().equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }

    public List<Boss> getBosses() {
        return bosses;
    }

    public Boss getBoss(String name) {
        for (Boss boss : bosses) {
            if (boss.name().equalsIgnoreCase(name)) {
                return boss;
            }
        }
        return null;
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public Reward getReward(String name) {
        for (Reward reward : rewards) {
            if (reward.name().equalsIgnoreCase(name)) {
                return reward;
            }
        }
        return null;
    }

    public List<RewardPool> getRewardPools() {
        return reward_pools;
    }

    public RewardPool getRewardPool(String name) {
        for (RewardPool rewardPool : reward_pools) {
            if (rewardPool.name().equalsIgnoreCase(name)) {
                return rewardPool;
            }
        }
        return null;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public Location getLocation(String name) {
        for (Location loc : locations) {
            if (loc.name().equalsIgnoreCase(name)) {
                return loc;
            }
        }
        return null;
    }

    public List<BossbarData> getBossbars() {
        return bossbars;
    }

    public BossbarData getBossbar(String name) {
        for (BossbarData boss : bossbars) {
            if (boss.name().equalsIgnoreCase(name)) {
                return boss;
            }
        }
        return null;
    }

    public BossbarData getBossbar(String phase, String category, String boss) {
        for (BossbarData bossbarData : bossbars) {
            if (bossbarData.phase().equalsIgnoreCase(phase)) {
                if (bossbarData.categories().contains(category) || bossbarData.bosses().contains(boss)) {
                    return bossbarData;
                }
            }
        }
        return null;
    }
}
