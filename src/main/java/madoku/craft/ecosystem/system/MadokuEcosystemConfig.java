package madoku.craft.ecosystem.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MadokuEcosystemConfig {
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_NATURAL_GROWTH_ENABLED = "natural-growth-enabled";
	public static final String FIELD_NATURAL_EROSION_ENABLED = "natural-erosion-enabled";

	public static final String FIELD_NATURAL_DIRT_GROWTH = "natural-dirt-growth";
	public static final String FIELD_NATURAL_GRASS_GROWTH = "natural-grass-growth";
	public static final String FIELD_NATURAL_FOLIAGE_GROWTH = "natural-foliage-growth";
	public static final String FIELD_NATURAL_TREE_GROWTH = "natural-tree-growth";
	public static final String FIELD_MIN_GROWTH_TIME = "min-growth-time";
	public static final String FIELD_MAX_GROWTH_TIME = "max-growth-time";
	public static final String FIELD_GROWTH_TIME = "growth-time";
	public static final String FIELD_GROWTH_SPEED_MULTIPLIER = "growth-speed-multiplier";
	public static final String FIELD_MIN_EROSION_TIME = "min-erosion-time";
	public static final String FIELD_MAX_EROSION_TIME = "max-erosion-time";
	public static final String FIELD_MIN_DECAY_TIME = "min-decay-time";
	public static final String FIELD_MAX_DECAY_TIME = "max-decay-time";
	public static final String FIELD_DECAY_TIME = "decay-time";
	public static final String FIELD_DECAY_SPEED_MULTIPLIER = "decay-speed-multiplier";

	public static final String FIELD_SEASON_SPRING = "spring";
	public static final String FIELD_SEASON_SUMMER = "summer";
	public static final String FIELD_SEASON_FALL = "fall";
	public static final String FIELD_SEASON_WINTER = "winter";

	public static final String FIELD_TREE_OAK = "oak";
	public static final String FIELD_TREE_SPRUCE = "spruce";
	public static final String FIELD_TREE_BIRCH = "birch";
	public static final String FIELD_TREE_JUNGLE = "jungle";
	public static final String FIELD_TREE_MANGROVE = "mangrove";
	public static final String FIELD_TREE_ACACIA = "acacia";
	public static final String FIELD_TREE_DARK_OAK = "dark_oak";
	public static final String FIELD_TREE_PALE_OAK = "pale_oak";
	public static final String FIELD_TREE_CHERRY = "cherry";
	public static final String FIELD_FOLIAGE_WILDFLOWERS = "wildflowers";
	public static final String FIELD_FOLIAGE_PINK_PETALS = "pink_petals";

	public static final String FIELD_WATER_EROSION_RADIUS = "water-erosion-radius";
	public static final String FIELD_BLOCK_EROSION_MUD = "block-erosion-mud";
	public static final String FIELD_BLOCK_EROSION_RED_SAND = "block-erosion-red-sand";
	public static final String FIELD_BLOCK_EROSION_SAND = "block-erosion-sand";
	public static final String FIELD_NATURAL_TREE_DECAY = "natural-tree-decay";

	private static final String FIELD_SOURCE_BLOCKS = "source-blocks";
	private static final String FIELD_TARGET_BLOCK = "target-block";
	private static final String FIELD_REQUIRED_BIOME_IDS = "required-biome-ids";
	private static final String FIELD_REQUIRED_BIOME_TAGS = "required-biome-tags";
	private static final String FIELD_EROSION_TIME = "erosion-time";

	private MadokuEcosystemConfig() {
	}

	public static Settings defaults() {
		return new Settings(
			new SystemSettings(true, true),
			new NaturalGrowthSettings(
				new GrowthProfile(new DayRange(1, 5), new SeasonGrowthMultiplier(1.25d, 0.75d, 1.25d, 0.75d)),
				new GrowthProfile(new DayRange(1, 5), new SeasonGrowthMultiplier(1.25d, 0.75d, 1.25d, 0.75d)),
				new GrowthProfile(new DayRange(1, 5), new SeasonGrowthMultiplier(3.5d, 0.0d, 0.5d, 0.0d)),
				new GrowthProfile(new DayRange(1, 5), new SeasonGrowthMultiplier(3.0d, 0.0d, 1.0d, 0.0d)),
				new GrowthProfile(new DayRange(7, 15), new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)),
				new GrowthProfile(new DayRange(7, 15), new SeasonGrowthMultiplier(1.0d, 0.25d, 1.25d, 1.5d)),
				new GrowthProfile(new DayRange(7, 15), new SeasonGrowthMultiplier(1.25d, 0.75d, 1.25d, 0.75d)),
				new GrowthProfile(new DayRange(7, 15), new SeasonGrowthMultiplier(1.0d, 2.0d, 0.75d, 0.25d)),
				new GrowthProfile(new DayRange(11, 19), new SeasonGrowthMultiplier(0.5d, 3.0d, 0.5d, 0.0d)),
				new GrowthProfile(new DayRange(7, 15), new SeasonGrowthMultiplier(1.25d, 1.25d, 0.75d, 0.75d)),
				new GrowthProfile(new DayRange(11, 19), new SeasonGrowthMultiplier(1.25d, 1.75d, 0.75d, 0.25d)),
				new GrowthProfile(new DayRange(11, 19), new SeasonGrowthMultiplier(1.5d, 0.5d, 1.5d, 0.5d)),
				new GrowthProfile(new DayRange(11, 19), new SeasonGrowthMultiplier(2.0d, 0.5d, 1.0d, 0.5d))
			),
			new NaturalErosionSettings(
				2,
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:mud",
					List.of("minecraft:swamp", "minecraft:mangrove_swamp"),
					List.of("minecraft:is_jungle"),
					new DayRange(7, 11)
				),
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:red_sand",
					List.of(),
					List.of("minecraft:is_badlands"),
					new DayRange(7, 11)
				),
				new ErosionRule(
					true,
					List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:rooted_dirt", "minecraft:dirt_path", "minecraft:podzol", "minecraft:mycelium", "minecraft:coarse_dirt"),
					"minecraft:sand",
					List.of(),
					List.of(),
					new DayRange(7, 11)
				),
				new DecayProfile(new DayRange(3, 7), new SeasonGrowthMultiplier(1.0d, 0.0d, 3.0d, 0.0d))
			)
		);
	}

	public static JsonObject buildSystemDefaultsJson() {
		return toSystemJson(defaults().system());
	}

	public static JsonObject buildNaturalGrowthDefaultsJson() {
		return toNaturalGrowthJson(defaults().naturalGrowth());
	}

	public static JsonObject buildNaturalErosionDefaultsJson() {
		return toNaturalErosionJson(defaults().naturalErosion());
	}

	public static SystemSettings systemFromJson(JsonObject source) {
		SystemSettings fallback = defaults().system();
		if (source == null) {
			return fallback;
		}
		return new SystemSettings(
			readBoolean(source, FIELD_NATURAL_GROWTH_ENABLED, fallback.naturalGrowthEnabled()),
			readBoolean(source, FIELD_NATURAL_EROSION_ENABLED, fallback.naturalErosionEnabled())
		);
	}

	public static NaturalGrowthSettings naturalGrowthFromJson(JsonObject source) {
		NaturalGrowthSettings fallback = defaults().naturalGrowth();
		if (source == null) {
			return fallback;
		}

		JsonObject dirtRoot = readObject(source, FIELD_NATURAL_DIRT_GROWTH);
		JsonObject grassRoot = readObject(source, FIELD_NATURAL_GRASS_GROWTH);
		JsonObject foliageRoot = readObject(source, FIELD_NATURAL_FOLIAGE_GROWTH);
		JsonObject wildflowerFoliageRoot = readObject(foliageRoot, FIELD_FOLIAGE_WILDFLOWERS);
		JsonObject pinkPetalFoliageRoot = readObject(foliageRoot, FIELD_FOLIAGE_PINK_PETALS);
		JsonObject treeRoot = readObject(source, FIELD_NATURAL_TREE_GROWTH);
		return new NaturalGrowthSettings(
			readGrowthProfile(dirtRoot, fallback.dirtGrowth()),
			readGrowthProfile(grassRoot, fallback.grassGrowth()),
			readGrowthProfile(wildflowerFoliageRoot, fallback.foliageWildflowersGrowth()),
			readGrowthProfile(pinkPetalFoliageRoot, fallback.foliagePinkPetalsGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_OAK, fallback.treeOakGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_SPRUCE, fallback.treeSpruceGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_BIRCH, fallback.treeBirchGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_JUNGLE, fallback.treeJungleGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_MANGROVE, fallback.treeMangroveGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_ACACIA, fallback.treeAcaciaGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_DARK_OAK, fallback.treeDarkOakGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_PALE_OAK, fallback.treePaleOakGrowth()),
			readTreeGrowthProfile(treeRoot, FIELD_TREE_CHERRY, fallback.treeCherryGrowth())
		);
	}

	public static NaturalErosionSettings naturalErosionFromJson(JsonObject source) {
		NaturalErosionSettings fallback = defaults().naturalErosion();
		if (source == null) {
			return fallback;
		}
		return new NaturalErosionSettings(
			Math.max(0, readInt(source, FIELD_WATER_EROSION_RADIUS, fallback.waterErosionRadius())),
			readRule(source, FIELD_BLOCK_EROSION_MUD, fallback.blockErosionMud()),
			readRule(source, FIELD_BLOCK_EROSION_RED_SAND, fallback.blockErosionRedSand()),
			readRule(source, FIELD_BLOCK_EROSION_SAND, fallback.blockErosionSand()),
			readDecayProfile(readObject(source, FIELD_NATURAL_TREE_DECAY), fallback.naturalTreeDecay())
		);
	}

	public static JsonObject toSystemJson(SystemSettings settings) {
		SystemSettings value = settings == null ? defaults().system() : settings;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_NATURAL_GROWTH_ENABLED, value.naturalGrowthEnabled());
		root.addProperty(FIELD_NATURAL_EROSION_ENABLED, value.naturalErosionEnabled());
		return root;
	}

	public static JsonObject toNaturalGrowthJson(NaturalGrowthSettings settings) {
		NaturalGrowthSettings value = settings == null ? defaults().naturalGrowth() : settings;
		JsonObject root = new JsonObject();

		root.add(FIELD_NATURAL_DIRT_GROWTH, toGrowthProfileJson(value.dirtGrowth()));
		root.add(FIELD_NATURAL_GRASS_GROWTH, toGrowthProfileJson(value.grassGrowth()));
		JsonObject foliageRoot = new JsonObject();
		foliageRoot.add(FIELD_FOLIAGE_WILDFLOWERS, toGrowthProfileJson(value.foliageWildflowersGrowth()));
		foliageRoot.add(FIELD_FOLIAGE_PINK_PETALS, toGrowthProfileJson(value.foliagePinkPetalsGrowth()));
		root.add(FIELD_NATURAL_FOLIAGE_GROWTH, foliageRoot);

		JsonObject treeRoot = new JsonObject();
		treeRoot.add(FIELD_TREE_OAK, toGrowthProfileJson(value.treeOakGrowth()));
		treeRoot.add(FIELD_TREE_SPRUCE, toGrowthProfileJson(value.treeSpruceGrowth()));
		treeRoot.add(FIELD_TREE_BIRCH, toGrowthProfileJson(value.treeBirchGrowth()));
		treeRoot.add(FIELD_TREE_JUNGLE, toGrowthProfileJson(value.treeJungleGrowth()));
		treeRoot.add(FIELD_TREE_MANGROVE, toGrowthProfileJson(value.treeMangroveGrowth()));
		treeRoot.add(FIELD_TREE_ACACIA, toGrowthProfileJson(value.treeAcaciaGrowth()));
		treeRoot.add(FIELD_TREE_DARK_OAK, toGrowthProfileJson(value.treeDarkOakGrowth()));
		treeRoot.add(FIELD_TREE_PALE_OAK, toGrowthProfileJson(value.treePaleOakGrowth()));
		treeRoot.add(FIELD_TREE_CHERRY, toGrowthProfileJson(value.treeCherryGrowth()));
		root.add(FIELD_NATURAL_TREE_GROWTH, treeRoot);
		return root;
	}

	public static JsonObject toNaturalErosionJson(NaturalErosionSettings settings) {
		NaturalErosionSettings value = settings == null ? defaults().naturalErosion() : settings;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_WATER_EROSION_RADIUS, value.waterErosionRadius());
		root.add(FIELD_BLOCK_EROSION_MUD, toRuleJson(value.blockErosionMud()));
		root.add(FIELD_BLOCK_EROSION_RED_SAND, toRuleJson(value.blockErosionRedSand()));
		root.add(FIELD_BLOCK_EROSION_SAND, toRuleJson(value.blockErosionSand()));
		root.add(FIELD_NATURAL_TREE_DECAY, toDecayProfileJson(value.naturalTreeDecay()));
		return root;
	}

	public static List<NamedErosionRule> erosionRulesInPriority(NaturalErosionSettings settings) {
		NaturalErosionSettings value = settings == null ? defaults().naturalErosion() : settings;
		List<NamedErosionRule> rules = new ArrayList<>();
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_MUD, value.blockErosionMud()));
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_RED_SAND, value.blockErosionRedSand()));
		rules.add(new NamedErosionRule(FIELD_BLOCK_EROSION_SAND, value.blockErosionSand()));
		return rules;
	}

	private static GrowthProfile readTreeGrowthProfile(JsonObject root, String treeKey, GrowthProfile fallback) {
		JsonObject treeRoot = readObject(root, treeKey);
		return readGrowthProfile(treeRoot, fallback);
	}

	private static GrowthProfile readGrowthProfile(JsonObject root, GrowthProfile fallback) {
		GrowthProfile value = fallback == null ? new GrowthProfile(new DayRange(1, 1), new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)) : fallback;
		if (root == null || (!root.has(FIELD_GROWTH_TIME) && !root.has(FIELD_GROWTH_SPEED_MULTIPLIER))) {
			return value;
		}
		JsonObject multiplierRoot = readObject(root, FIELD_GROWTH_SPEED_MULTIPLIER);
		return new GrowthProfile(
			readRange(root, FIELD_GROWTH_TIME, value.growthTime()),
			readSeasonGrowthMultiplier(multiplierRoot, value.growthSpeedMultiplier())
		);
	}

	private static JsonObject toGrowthProfileJson(GrowthProfile profile) {
		GrowthProfile value = profile == null
			? new GrowthProfile(new DayRange(1, 1), new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d))
			: profile;
		JsonObject root = new JsonObject();
		root.add(FIELD_GROWTH_TIME, toRangeJson(value.growthTime()));
		root.add(FIELD_GROWTH_SPEED_MULTIPLIER, toSeasonGrowthMultiplierJson(value.growthSpeedMultiplier()));
		return root;
	}

	private static SeasonGrowthMultiplier readSeasonGrowthMultiplier(JsonObject root, SeasonGrowthMultiplier fallback) {
		SeasonGrowthMultiplier value = fallback == null
			? new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)
			: fallback;
		return new SeasonGrowthMultiplier(
			readDouble(root, FIELD_SEASON_SPRING, value.spring()),
			readDouble(root, FIELD_SEASON_SUMMER, value.summer()),
			readDouble(root, FIELD_SEASON_FALL, value.fall()),
			readDouble(root, FIELD_SEASON_WINTER, value.winter())
		);
	}

	private static JsonObject toSeasonGrowthMultiplierJson(SeasonGrowthMultiplier multiplier) {
		SeasonGrowthMultiplier value = multiplier == null
			? new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)
			: multiplier;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_SEASON_SPRING, value.spring());
		root.addProperty(FIELD_SEASON_SUMMER, value.summer());
		root.addProperty(FIELD_SEASON_FALL, value.fall());
		root.addProperty(FIELD_SEASON_WINTER, value.winter());
		return root;
	}

	private static ErosionRule readRule(JsonObject root, String key, ErosionRule fallback) {
		JsonObject source = readObject(root, key);
		return new ErosionRule(
			readBoolean(source, FIELD_ENABLED, fallback.enabled()),
			readStringArray(source, FIELD_SOURCE_BLOCKS, fallback.sourceBlocks()),
			readString(source, FIELD_TARGET_BLOCK, fallback.targetBlock()),
			readStringArray(source, FIELD_REQUIRED_BIOME_IDS, fallback.requiredBiomeIds()),
			readStringArray(source, FIELD_REQUIRED_BIOME_TAGS, fallback.requiredBiomeTags()),
			readErosionRange(source, fallback.erosionTime())
		);
	}

	private static JsonObject toRuleJson(ErosionRule rule) {
		ErosionRule value = rule == null ? defaults().naturalErosion().blockErosionSand() : rule;
		JsonObject root = new JsonObject();
		root.addProperty(FIELD_ENABLED, value.enabled());
		root.add(FIELD_SOURCE_BLOCKS, toStringArray(value.sourceBlocks()));
		root.addProperty(FIELD_TARGET_BLOCK, value.targetBlock());
		root.add(FIELD_REQUIRED_BIOME_IDS, toStringArray(value.requiredBiomeIds()));
		root.add(FIELD_REQUIRED_BIOME_TAGS, toStringArray(value.requiredBiomeTags()));
		root.add(FIELD_EROSION_TIME, toRangeJson(value.erosionTime(), FIELD_MIN_EROSION_TIME, FIELD_MAX_EROSION_TIME));
		return root;
	}

	private static DecayProfile readDecayProfile(JsonObject root, DecayProfile fallback) {
		DecayProfile value = fallback == null
			? new DecayProfile(new DayRange(3, 7), new SeasonGrowthMultiplier(1.0d, 0.0d, 3.0d, 0.0d))
			: fallback;
		if (root == null || (!root.has(FIELD_DECAY_TIME) && !root.has(FIELD_DECAY_SPEED_MULTIPLIER))) {
			return value;
		}
		JsonObject multiplierRoot = readObject(root, FIELD_DECAY_SPEED_MULTIPLIER);
		return new DecayProfile(
			readRange(root, FIELD_DECAY_TIME, value.decayTime(), FIELD_MIN_DECAY_TIME, FIELD_MAX_DECAY_TIME),
			readSeasonGrowthMultiplier(multiplierRoot, value.decaySpeedMultiplier())
		);
	}

	private static JsonObject toDecayProfileJson(DecayProfile profile) {
		DecayProfile value = profile == null
			? new DecayProfile(new DayRange(3, 7), new SeasonGrowthMultiplier(1.0d, 0.0d, 3.0d, 0.0d))
			: profile;
		JsonObject root = new JsonObject();
		root.add(FIELD_DECAY_TIME, toRangeJson(value.decayTime(), FIELD_MIN_DECAY_TIME, FIELD_MAX_DECAY_TIME));
		root.add(FIELD_DECAY_SPEED_MULTIPLIER, toSeasonGrowthMultiplierJson(value.decaySpeedMultiplier()));
		return root;
	}

	private static DayRange readRange(JsonObject object, String key, DayRange fallback) {
		JsonObject rangeRoot = readObject(object, key);
		int min = readInt(rangeRoot, FIELD_MIN_GROWTH_TIME, fallback.minDays());
		int max = readInt(rangeRoot, FIELD_MAX_GROWTH_TIME, fallback.maxDays());
		return new DayRange(min, max);
	}

	private static DayRange readRange(JsonObject object, String key, DayRange fallback, String minKey, String maxKey) {
		JsonObject rangeRoot = readObject(object, key);
		int min = readInt(rangeRoot, minKey, fallback.minDays());
		int max = readInt(rangeRoot, maxKey, fallback.maxDays());
		return new DayRange(min, max);
	}

	private static JsonObject toRangeJson(DayRange range) {
		return toRangeJson(range, FIELD_MIN_GROWTH_TIME, FIELD_MAX_GROWTH_TIME);
	}

	private static DayRange readErosionRange(JsonObject object, DayRange fallback) {
		JsonObject erosionRangeRoot = readObject(object, FIELD_EROSION_TIME);
		int min = readInt(
			erosionRangeRoot,
			FIELD_MIN_EROSION_TIME,
			fallback.minDays()
		);
		int max = readInt(
			erosionRangeRoot,
			FIELD_MAX_EROSION_TIME,
			fallback.maxDays()
		);
		return new DayRange(min, max);
	}

	private static JsonObject toRangeJson(DayRange range, String minKey, String maxKey) {
		DayRange safe = range == null ? new DayRange(1, 1) : range;
		JsonObject root = new JsonObject();
		root.addProperty(minKey, safe.minDays());
		root.addProperty(maxKey, safe.maxDays());
		return root;
	}

	private static List<String> readStringArray(JsonObject object, String key, List<String> fallback) {
		JsonElement element = object == null ? null : object.get(key);
		if (!(element instanceof JsonArray array)) {
			return normalizeList(fallback);
		}
		List<String> values = new ArrayList<>();
		for (JsonElement entry : array) {
			if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				continue;
			}
			values.add(entry.getAsString());
		}
		List<String> normalized = normalizeList(values);
		return normalized.isEmpty() ? normalizeList(fallback) : normalized;
	}

	private static JsonArray toStringArray(List<String> values) {
		JsonArray array = new JsonArray();
		for (String value : normalizeList(values)) {
			array.add(value);
		}
		return array;
	}

	private static List<String> normalizeList(List<String> source) {
		Set<String> values = new LinkedHashSet<>();
		if (source != null) {
			for (String value : source) {
				String normalized = normalize(value);
				if (!normalized.isBlank()) {
					values.add(normalized);
				}
			}
		}
		return List.copyOf(values);
	}

	private static String readString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return normalize(fallback);
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return normalize(fallback);
		}
		return normalize(element.getAsString());
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase();
	}

	private static JsonObject readObject(JsonObject object, String key) {
		if (object == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonObject()) {
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static int readInt(JsonObject object, String key, int fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public record DayRange(int minDays, int maxDays) {
		public DayRange {
			int safeMin = Math.max(1, minDays);
			int safeMax = Math.max(1, maxDays);
			if (safeMax < safeMin) {
				safeMax = safeMin;
			}
			minDays = safeMin;
			maxDays = safeMax;
		}
	}

	public record SeasonGrowthMultiplier(double spring, double summer, double fall, double winter) {
		public SeasonGrowthMultiplier {
			spring = Math.max(0.0d, spring);
			summer = Math.max(0.0d, summer);
			fall = Math.max(0.0d, fall);
			winter = Math.max(0.0d, winter);
		}

		public double forSeason(String seasonId) {
			if (FIELD_SEASON_SPRING.equals(seasonId)) {
				return spring;
			}
			if (FIELD_SEASON_SUMMER.equals(seasonId)) {
				return summer;
			}
			if (FIELD_SEASON_FALL.equals(seasonId)) {
				return fall;
			}
			if (FIELD_SEASON_WINTER.equals(seasonId)) {
				return winter;
			}
			return summer;
		}
	}

	public record GrowthProfile(DayRange growthTime, SeasonGrowthMultiplier growthSpeedMultiplier) {
		public GrowthProfile {
			growthTime = growthTime == null ? new DayRange(1, 1) : growthTime;
			growthSpeedMultiplier = growthSpeedMultiplier == null
				? new SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)
				: growthSpeedMultiplier;
		}

		public DayRange growthForSeason(String seasonId) {
			return scaleRangeBySpeed(growthTime, growthSpeedMultiplier.forSeason(seasonId));
		}
	}

	public record ErosionRule(
		boolean enabled,
		List<String> sourceBlocks,
		String targetBlock,
		List<String> requiredBiomeIds,
		List<String> requiredBiomeTags,
		DayRange erosionTime
	) {
		public ErosionRule {
			sourceBlocks = normalizeList(sourceBlocks);
			targetBlock = normalize(targetBlock);
			requiredBiomeIds = normalizeList(requiredBiomeIds);
			requiredBiomeTags = normalizeList(requiredBiomeTags);
			erosionTime = erosionTime == null ? new DayRange(7, 11) : erosionTime;
		}
	}

	public record DecayProfile(DayRange decayTime, SeasonGrowthMultiplier decaySpeedMultiplier) {
		public DecayProfile {
			decayTime = decayTime == null ? new DayRange(3, 7) : decayTime;
			decaySpeedMultiplier = decaySpeedMultiplier == null
				? new SeasonGrowthMultiplier(1.0d, 0.0d, 3.0d, 0.0d)
				: decaySpeedMultiplier;
		}

		public DayRange decayForSeason(String seasonId) {
			return scaleRangeBySpeed(decayTime, decaySpeedMultiplier.forSeason(seasonId));
		}
	}

	public record NamedErosionRule(String ruleId, ErosionRule rule) {
	}

	public record SystemSettings(boolean naturalGrowthEnabled, boolean naturalErosionEnabled) {
	}

	public record NaturalGrowthSettings(
		GrowthProfile dirtGrowth,
		GrowthProfile grassGrowth,
		GrowthProfile foliageWildflowersGrowth,
		GrowthProfile foliagePinkPetalsGrowth,
		GrowthProfile treeOakGrowth,
		GrowthProfile treeSpruceGrowth,
		GrowthProfile treeBirchGrowth,
		GrowthProfile treeJungleGrowth,
		GrowthProfile treeMangroveGrowth,
		GrowthProfile treeAcaciaGrowth,
		GrowthProfile treeDarkOakGrowth,
		GrowthProfile treePaleOakGrowth,
		GrowthProfile treeCherryGrowth
	) {
		public NaturalGrowthSettings {
			dirtGrowth = dirtGrowth == null ? defaults().naturalGrowth().dirtGrowth() : dirtGrowth;
			grassGrowth = grassGrowth == null ? defaults().naturalGrowth().grassGrowth() : grassGrowth;
			foliageWildflowersGrowth = foliageWildflowersGrowth == null ? defaults().naturalGrowth().foliageWildflowersGrowth() : foliageWildflowersGrowth;
			foliagePinkPetalsGrowth = foliagePinkPetalsGrowth == null ? defaults().naturalGrowth().foliagePinkPetalsGrowth() : foliagePinkPetalsGrowth;
			treeOakGrowth = treeOakGrowth == null ? defaults().naturalGrowth().treeOakGrowth() : treeOakGrowth;
			treeSpruceGrowth = treeSpruceGrowth == null ? defaults().naturalGrowth().treeSpruceGrowth() : treeSpruceGrowth;
			treeBirchGrowth = treeBirchGrowth == null ? defaults().naturalGrowth().treeBirchGrowth() : treeBirchGrowth;
			treeJungleGrowth = treeJungleGrowth == null ? defaults().naturalGrowth().treeJungleGrowth() : treeJungleGrowth;
			treeMangroveGrowth = treeMangroveGrowth == null ? defaults().naturalGrowth().treeMangroveGrowth() : treeMangroveGrowth;
			treeAcaciaGrowth = treeAcaciaGrowth == null ? defaults().naturalGrowth().treeAcaciaGrowth() : treeAcaciaGrowth;
			treeDarkOakGrowth = treeDarkOakGrowth == null ? defaults().naturalGrowth().treeDarkOakGrowth() : treeDarkOakGrowth;
			treePaleOakGrowth = treePaleOakGrowth == null ? defaults().naturalGrowth().treePaleOakGrowth() : treePaleOakGrowth;
			treeCherryGrowth = treeCherryGrowth == null ? defaults().naturalGrowth().treeCherryGrowth() : treeCherryGrowth;
		}

		public DayRange dirtGrowthForSeason(String seasonId) {
			return dirtGrowth == null ? null : dirtGrowth.growthForSeason(seasonId);
		}

		public DayRange treeGrowthForSeason(String treeType, String seasonId) {
			GrowthProfile profile = switch (treeType) {
				case FIELD_TREE_OAK -> treeOakGrowth;
				case FIELD_TREE_SPRUCE -> treeSpruceGrowth;
				case FIELD_TREE_BIRCH -> treeBirchGrowth;
				case FIELD_TREE_JUNGLE -> treeJungleGrowth;
				case FIELD_TREE_MANGROVE -> treeMangroveGrowth;
				case FIELD_TREE_ACACIA -> treeAcaciaGrowth;
				case FIELD_TREE_DARK_OAK -> treeDarkOakGrowth;
				case FIELD_TREE_PALE_OAK -> treePaleOakGrowth;
				case FIELD_TREE_CHERRY -> treeCherryGrowth;
				default -> null;
			};
			return profile == null ? null : profile.growthForSeason(seasonId);
		}

		public DayRange grassGrowthForSeason(String seasonId) {
			return grassGrowth == null ? null : grassGrowth.growthForSeason(seasonId);
		}

		public DayRange foliageGrowthForSeason(String foliageType, String seasonId) {
			GrowthProfile profile = switch (normalize(foliageType)) {
				case FIELD_FOLIAGE_PINK_PETALS -> foliagePinkPetalsGrowth;
				case FIELD_FOLIAGE_WILDFLOWERS -> foliageWildflowersGrowth;
				default -> null;
			};
			return profile == null ? null : profile.growthForSeason(seasonId);
		}
	}

	public record NaturalErosionSettings(
		int waterErosionRadius,
		ErosionRule blockErosionMud,
		ErosionRule blockErosionRedSand,
		ErosionRule blockErosionSand,
		DecayProfile naturalTreeDecay
	) {
		public NaturalErosionSettings {
			waterErosionRadius = Math.max(0, waterErosionRadius);
			blockErosionMud = blockErosionMud == null ? defaults().naturalErosion().blockErosionMud() : blockErosionMud;
			blockErosionRedSand = blockErosionRedSand == null ? defaults().naturalErosion().blockErosionRedSand() : blockErosionRedSand;
			blockErosionSand = blockErosionSand == null ? defaults().naturalErosion().blockErosionSand() : blockErosionSand;
			naturalTreeDecay = naturalTreeDecay == null ? defaults().naturalErosion().naturalTreeDecay() : naturalTreeDecay;
		}

		public DayRange treeDecayForSeason(String seasonId) {
			return naturalTreeDecay == null ? null : naturalTreeDecay.decayForSeason(seasonId);
		}
	}

	public record Settings(SystemSettings system, NaturalGrowthSettings naturalGrowth, NaturalErosionSettings naturalErosion) {
		public Settings {
			system = system == null ? new SystemSettings(true, true) : system;
			naturalGrowth = naturalGrowth == null ? defaults().naturalGrowth() : naturalGrowth;
			naturalErosion = naturalErosion == null ? defaults().naturalErosion() : naturalErosion;
		}
	}

	private static DayRange scaleRangeBySpeed(DayRange baseRange, double growthSpeedMultiplier) {
		if (baseRange == null) {
			return null;
		}
		if (growthSpeedMultiplier <= 0.0d) {
			return null;
		}
		int adjustedMin = (int) Math.ceil(baseRange.minDays() / growthSpeedMultiplier);
		int adjustedMax = (int) Math.ceil(baseRange.maxDays() / growthSpeedMultiplier);
		return new DayRange(adjustedMin, adjustedMax);
	}
}
