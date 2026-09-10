package madoku.craft.java.ecosystem;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;

import java.util.List;

public final class NaturalGrowthConfigManager {
	public static final String FIELD_BLOCK_GROWTH = "block-growth";
	public static final String FIELD_FOLIAGE_GROWTH = "folliage-growth";
	public static final String FIELD_DESERT_FOLIAGE_GROWTH = "desert-folliage-growth";
	public static final String FIELD_VEGETATION_GROWTH = "vegetation-growth";
	public static final String FIELD_CACTUS_GROWTH = "cactus-growth";
	public static final String FIELD_TREE_GROWTH = "tree-growth";

	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_GROWTH_TIME = "growth-time";
	public static final String FIELD_GROWTH_MULTIPLIER = "growth-multiplier";
	public static final String FIELD_TARGET_BLOCKS = "target-blocks";
	public static final String FIELD_ELIGIBLE_BIOMES = "eligible-biomes";
	public static final String FIELD_WEIGHT = "weight";
	public static final String FIELD_MIN_TIME = "min-time";
	public static final String FIELD_MAX_TIME = "max-time";

	public static final String FIELD_SHORTGRASS = "shortgrass";
	public static final String FIELD_TALLGRASS = "tallgrass";
	public static final String FIELD_BUSH = "bush";
	public static final String FIELD_SHORT_DRY_GRASS = "shortdrygrass";
	public static final String FIELD_TALL_DRY_GRASS = "drytallgrass";
	public static final String FIELD_DEAD_BUSH = "deadbush";
	public static final String FIELD_WILDFLOWERS = "wildflowers";
	public static final String FIELD_PINK_PETALS = "pink-petals";
	public static final String FIELD_OAK_TREE = "oak-tree";
	public static final String FIELD_BIRCH_TREE = "birch-tree";
	public static final String FIELD_SPRUCE_TREE = "spruce-tree";
	public static final String FIELD_JUNGLE_TREE = "jungle-tree";
	public static final String FIELD_ACACIA_TREE = "acacia-tree";
	public static final String FIELD_DARK_OAK_TREE = "dark-oak-tree";
	public static final String FIELD_PALE_OAK_TREE = "pale-oak-tree";
	public static final String FIELD_MANGROVE_TREE = "mangrove-tree";
	public static final String FIELD_CHERRY_TREE = "cherry-tree";

	private static final List<String> DEFAULT_DIRT_TARGET_BLOCKS = List.of("minecraft:grass_block");
	private static final List<String> DEFAULT_WILDFLOWER_BIOMES = List.of(
		"minecraft:forest",
		"minecraft:birch_forest",
		"minecraft:old_growth_birch_forest",
		"minecraft:meadow"
	);
	private static final List<String> DEFAULT_PINK_PETAL_BIOMES = List.of("minecraft:cherry_grove", "minecraft:meadow");

	private NaturalGrowthConfigManager() {
	}

	public static Settings defaults() {
		return new Settings(
			new BlockGrowthSettings(true, new DirtGrowthSettings(
				true,
				DEFAULT_DIRT_TARGET_BLOCKS,
				new EcosystemConfigManager.DayRange(1, 3),
				new EcosystemConfigManager.SeasonGrowthMultiplier(1.5d, 0.75d, 1.25d, 0.5d)
			)),
			new FoliageGrowthSettings(
				true,
				new EcosystemConfigManager.DayRange(3, 5),
				new EcosystemConfigManager.SeasonGrowthMultiplier(1.5d, 0.75d, 1.25d, 0.5d),
				new FoliageTargetSettings(50),
				new FoliageTargetSettings(10),
				new FoliageTargetSettings(30)
			),
			new FoliageGrowthSettings(
				true,
				new EcosystemConfigManager.DayRange(5, 7),
				new EcosystemConfigManager.SeasonGrowthMultiplier(1.25d, 1.5d, 0.75d, 0.5d),
				new FoliageTargetSettings(30),
				new FoliageTargetSettings(10),
				new FoliageTargetSettings(50)
			),
			new VegetationGrowthSettings(
				true,
				new SpeciesGrowthSettings(true, DEFAULT_WILDFLOWER_BIOMES, new EcosystemConfigManager.DayRange(3, 5), new EcosystemConfigManager.SeasonGrowthMultiplier(2.0d, 0.0d, 2.0d, 0.0d)),
				new SpeciesGrowthSettings(true, DEFAULT_PINK_PETAL_BIOMES, new EcosystemConfigManager.DayRange(3, 5), new EcosystemConfigManager.SeasonGrowthMultiplier(2.0d, 0.0d, 2.0d, 0.0d))
			),
			new CactusGrowthSettings(
				true,
				new EcosystemConfigManager.DayRange(11, 15),
				new EcosystemConfigManager.SeasonGrowthMultiplier(0.75d, 2.25d, 0.75d, 0.25d)
			),
			new TreeGrowthSettings(
				true,
				new SpeciesGrowthSettings(true, List.of("minecraft:bamboo_jungle", "minecraft:dark_forest", "minecraft:forest", "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:plains", "minecraft:river", "minecraft:savanna", "minecraft:swamp", "minecraft:wooded_badlands", "minecraft:windswept_forest", "minecraft:meadow"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:dark_forest", "minecraft:forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:meadow"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(1.25d, 0.75d, 1.25d, 1.25d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:old_growth_spruce_taiga", "minecraft:old_growth_pine_taiga", "minecraft:snowy_taiga", "minecraft:snowy_plains", "minecraft:taiga", "minecraft:windswept_forest", "minecraft:grove"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(1.25d, 0.0d, 1.25d, 1.5d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.5d, 1.0d, 0.5d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(0.75d, 2.5d, 0.75d, 0.0d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:dark_forest"), new EcosystemConfigManager.DayRange(21, 28), new EcosystemConfigManager.SeasonGrowthMultiplier(0.5d, 1.5d, 0.5d, 1.5d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:pale_garden"), new EcosystemConfigManager.DayRange(21, 28), new EcosystemConfigManager.SeasonGrowthMultiplier(1.5d, 0.5d, 1.5d, 0.5d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:swamp"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(1.25d, 1.25d, 1.25d, 0.25d)),
				new SpeciesGrowthSettings(true, List.of("minecraft:cherry_grove", "minecraft:grove"), new EcosystemConfigManager.DayRange(14, 21), new EcosystemConfigManager.SeasonGrowthMultiplier(2.0d, 0.0d, 2.0d, 0.0d))
			)
		);
	}

	static String normalizeFoliageType(String foliageType) {
		String normalized = foliageType == null ? "" : foliageType.trim().toLowerCase();
		if (FIELD_PINK_PETALS.equals(normalized)) {
			return FIELD_PINK_PETALS;
		}
		if (FIELD_WILDFLOWERS.equals(normalized)) {
			return FIELD_WILDFLOWERS;
		}
		return "";
	}

	static boolean propertyNameLooksLikeAmount(String propertyName) {
		String normalized = EcosystemConfigManager.normalize(propertyName);
		return normalized.contains("amount") || normalized.contains("segments");
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		if (source == null) {
			return fallback;
		}

		JsonObject blockGrowthRoot = EcosystemConfigManager.readObject(source, FIELD_BLOCK_GROWTH);
		JsonObject dirtRoot = EcosystemConfigManager.readObject(blockGrowthRoot, "dirt");
		JsonObject foliageRoot = EcosystemConfigManager.readObject(source, FIELD_FOLIAGE_GROWTH);
		JsonObject desertFoliageRoot = EcosystemConfigManager.readObject(source, FIELD_DESERT_FOLIAGE_GROWTH);
		JsonObject vegetationRoot = EcosystemConfigManager.readObject(source, FIELD_VEGETATION_GROWTH);
		JsonObject cactusRoot = EcosystemConfigManager.readObject(source, FIELD_CACTUS_GROWTH);
		JsonObject treeRoot = EcosystemConfigManager.readObject(source, FIELD_TREE_GROWTH);
		return new Settings(
			new BlockGrowthSettings(
				EcosystemConfigManager.readBoolean(blockGrowthRoot, FIELD_ENABLED, fallback.blockGrowth().enabled()),
				new DirtGrowthSettings(
					EcosystemConfigManager.readBoolean(dirtRoot, FIELD_ENABLED, fallback.blockGrowth().dirt().enabled()),
					EcosystemConfigManager.readStringArray(dirtRoot, FIELD_TARGET_BLOCKS, fallback.blockGrowth().dirt().targetBlocks()),
					EcosystemConfigManager.readDayRange(dirtRoot, FIELD_GROWTH_TIME, fallback.blockGrowth().dirt().growthTime(), FIELD_MIN_TIME, FIELD_MAX_TIME),
					EcosystemConfigManager.readSeasonMultiplier(EcosystemConfigManager.readObject(dirtRoot, FIELD_GROWTH_MULTIPLIER), fallback.blockGrowth().dirt().growthMultiplier())
				)
			),
			readFoliageGrowth(foliageRoot, fallback.foliageGrowth()),
			readFoliageGrowth(desertFoliageRoot, fallback.desertFoliageGrowth()),
			new VegetationGrowthSettings(
				EcosystemConfigManager.readBoolean(vegetationRoot, FIELD_ENABLED, fallback.vegetationGrowth().enabled()),
				readSpeciesGrowth(vegetationRoot, FIELD_WILDFLOWERS, fallback.vegetationGrowth().wildflowers()),
				readSpeciesGrowth(vegetationRoot, FIELD_PINK_PETALS, fallback.vegetationGrowth().pinkPetals())
			),
			new CactusGrowthSettings(
				EcosystemConfigManager.readBoolean(cactusRoot, FIELD_ENABLED, fallback.cactusGrowth().enabled()),
				EcosystemConfigManager.readDayRange(cactusRoot, FIELD_GROWTH_TIME, fallback.cactusGrowth().growthTime(), FIELD_MIN_TIME, FIELD_MAX_TIME),
				EcosystemConfigManager.readSeasonMultiplier(EcosystemConfigManager.readObject(cactusRoot, FIELD_GROWTH_MULTIPLIER), fallback.cactusGrowth().growthMultiplier())
			),
			new TreeGrowthSettings(
				EcosystemConfigManager.readBoolean(treeRoot, FIELD_ENABLED, fallback.treeGrowth().enabled()),
				readSpeciesGrowth(treeRoot, FIELD_OAK_TREE, fallback.treeGrowth().oakTree()),
				readSpeciesGrowth(treeRoot, FIELD_BIRCH_TREE, fallback.treeGrowth().birchTree()),
				readSpeciesGrowth(treeRoot, FIELD_SPRUCE_TREE, fallback.treeGrowth().spruceTree()),
				readSpeciesGrowth(treeRoot, FIELD_JUNGLE_TREE, fallback.treeGrowth().jungleTree()),
				readSpeciesGrowth(treeRoot, FIELD_ACACIA_TREE, fallback.treeGrowth().acaciaTree()),
				readSpeciesGrowth(treeRoot, FIELD_DARK_OAK_TREE, fallback.treeGrowth().darkOakTree()),
				readSpeciesGrowth(treeRoot, FIELD_PALE_OAK_TREE, fallback.treeGrowth().paleOakTree()),
				readSpeciesGrowth(treeRoot, FIELD_MANGROVE_TREE, fallback.treeGrowth().mangroveTree()),
				readSpeciesGrowth(treeRoot, FIELD_CHERRY_TREE, fallback.treeGrowth().cherryTree())
			)
		);
	}

	public static JsonObject toJson(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		return JSONFormatAPIManager.object()
			.object(FIELD_BLOCK_GROWTH, block -> {
			block.put(FIELD_ENABLED, value.blockGrowth().enabled());
				block.object("dirt", dirt -> writeDirtGrowth(dirt, value.blockGrowth().dirt()));
			})
			.object(FIELD_FOLIAGE_GROWTH, foliage -> writeFoliageGrowth(foliage, value.foliageGrowth()))
			.object(FIELD_DESERT_FOLIAGE_GROWTH, foliage -> writeFoliageGrowth(foliage, value.desertFoliageGrowth()))
			.object(FIELD_VEGETATION_GROWTH, vegetation -> {
				vegetation.put(FIELD_ENABLED, value.vegetationGrowth().enabled());
				vegetation.object(FIELD_WILDFLOWERS, wildflowers -> writeSpeciesLike(wildflowers, value.vegetationGrowth().wildflowers()));
				vegetation.object(FIELD_PINK_PETALS, pinkPetals -> writeSpeciesLike(pinkPetals, value.vegetationGrowth().pinkPetals()));
			})
			.object(FIELD_CACTUS_GROWTH, cactus -> {
				cactus.put(FIELD_ENABLED, value.cactusGrowth().enabled());
				cactus.object(FIELD_GROWTH_TIME, time -> time
					.put(FIELD_MIN_TIME, value.cactusGrowth().growthTime().minDays())
					.put(FIELD_MAX_TIME, value.cactusGrowth().growthTime().maxDays()));
				cactus.object(FIELD_GROWTH_MULTIPLIER, multiplier -> writeMultiplier(multiplier, value.cactusGrowth().growthMultiplier()));
			})
			.object(FIELD_TREE_GROWTH, tree -> {
				tree.put(FIELD_ENABLED, value.treeGrowth().enabled());
				tree.object(FIELD_OAK_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().oakTree()));
				tree.object(FIELD_BIRCH_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().birchTree()));
				tree.object(FIELD_SPRUCE_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().spruceTree()));
				tree.object(FIELD_JUNGLE_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().jungleTree()));
				tree.object(FIELD_ACACIA_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().acaciaTree()));
				tree.object(FIELD_DARK_OAK_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().darkOakTree()));
				tree.object(FIELD_PALE_OAK_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().paleOakTree()));
				tree.object(FIELD_MANGROVE_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().mangroveTree()));
				tree.object(FIELD_CHERRY_TREE, entry -> writeSpeciesLike(entry, value.treeGrowth().cherryTree()));
			})
			.build();
	}

	private static FoliageGrowthSettings readFoliageGrowth(JsonObject root, FoliageGrowthSettings fallback) {
		FoliageGrowthSettings safeFallback = fallback == null ? defaults().foliageGrowth() : fallback;
		if (root == null) {
			return safeFallback;
		}
		return new FoliageGrowthSettings(
			EcosystemConfigManager.readBoolean(root, FIELD_ENABLED, safeFallback.enabled()),
			EcosystemConfigManager.readDayRange(root, FIELD_GROWTH_TIME, safeFallback.growthTime(), FIELD_MIN_TIME, FIELD_MAX_TIME),
			EcosystemConfigManager.readSeasonMultiplier(EcosystemConfigManager.readObject(root, FIELD_GROWTH_MULTIPLIER), safeFallback.growthMultiplier()),
			readWeight(root, FIELD_SHORTGRASS, safeFallback.shortGrass()),
			readWeight(root, FIELD_TALLGRASS, safeFallback.tallGrass()),
			readWeight(root, FIELD_BUSH, safeFallback.bush())
		);
	}

	private static void writeFoliageGrowth(JSONFormatAPIManager.ObjectBuilder builder, FoliageGrowthSettings value) {
		FoliageGrowthSettings safe = value == null ? defaults().foliageGrowth : value;
		builder.put(FIELD_ENABLED, safe.enabled())
			.object(FIELD_GROWTH_TIME, time -> time
				.put(FIELD_MIN_TIME, safe.growthTime().minDays())
				.put(FIELD_MAX_TIME, safe.growthTime().maxDays()))
			.object(FIELD_GROWTH_MULTIPLIER, multiplier -> writeMultiplier(multiplier, safe.growthMultiplier()))
			.object(FIELD_TARGET_BLOCKS, targets -> {
				targets.object(FIELD_SHORTGRASS, entry -> entry.put(FIELD_WEIGHT, safe.shortGrass().weight()));
				targets.object(FIELD_TALLGRASS, entry -> entry.put(FIELD_WEIGHT, safe.tallGrass().weight()));
				targets.object(FIELD_BUSH, entry -> entry.put(FIELD_WEIGHT, safe.bush().weight()));
			});
	}

	private static SpeciesGrowthSettings readSpeciesGrowth(JsonObject root, String key, SpeciesGrowthSettings fallback) {
		SpeciesGrowthSettings safeFallback = fallback == null ? defaults().vegetationGrowth().wildflowers() : fallback;
		JsonObject entryRoot = EcosystemConfigManager.readObject(root, key);
		return new SpeciesGrowthSettings(
			EcosystemConfigManager.readBoolean(entryRoot, FIELD_ENABLED, safeFallback.enabled()),
			EcosystemConfigManager.readStringArray(entryRoot, FIELD_ELIGIBLE_BIOMES, safeFallback.eligibleBiomes()),
			EcosystemConfigManager.readDayRange(entryRoot, FIELD_GROWTH_TIME, safeFallback.growthTime(), FIELD_MIN_TIME, FIELD_MAX_TIME),
			EcosystemConfigManager.readSeasonMultiplier(EcosystemConfigManager.readObject(entryRoot, FIELD_GROWTH_MULTIPLIER), safeFallback.growthMultiplier())
		);
	}

	private static void writeSpeciesLike(JSONFormatAPIManager.ObjectBuilder builder, SpeciesGrowthSettings value) {
		SpeciesGrowthSettings safe = value == null ? defaults().vegetationGrowth().wildflowers() : value;
		builder.put(FIELD_ENABLED, safe.enabled())
			.put(FIELD_ELIGIBLE_BIOMES, EcosystemConfigManager.toStringArray(safe.eligibleBiomes()))
			.object(FIELD_GROWTH_TIME, time -> time
				.put(FIELD_MIN_TIME, safe.growthTime().minDays())
				.put(FIELD_MAX_TIME, safe.growthTime().maxDays()))
			.object(FIELD_GROWTH_MULTIPLIER, multiplier -> writeMultiplier(multiplier, safe.growthMultiplier()));
	}

	private static FoliageTargetSettings readWeight(JsonObject root, String key, FoliageTargetSettings fallback) {
		FoliageTargetSettings safeFallback = fallback == null ? new FoliageTargetSettings(0) : fallback;
		JsonObject entryRoot = EcosystemConfigManager.readObject(root, key);
		return new FoliageTargetSettings(EcosystemConfigManager.readInt(entryRoot, FIELD_WEIGHT, safeFallback.weight()));
	}

	private static void writeDirtGrowth(JSONFormatAPIManager.ObjectBuilder builder, DirtGrowthSettings value) {
		DirtGrowthSettings safe = value == null ? defaults().blockGrowth().dirt() : value;
		builder.put(FIELD_ENABLED, safe.enabled())
			.put(FIELD_TARGET_BLOCKS, EcosystemConfigManager.toStringArray(safe.targetBlocks()))
			.object(FIELD_GROWTH_TIME, time -> time
				.put(FIELD_MIN_TIME, safe.growthTime().minDays())
				.put(FIELD_MAX_TIME, safe.growthTime().maxDays()))
			.object(FIELD_GROWTH_MULTIPLIER, multiplier -> writeMultiplier(multiplier, safe.growthMultiplier()));
	}

	private static void writeMultiplier(JSONFormatAPIManager.ObjectBuilder builder, EcosystemConfigManager.SeasonGrowthMultiplier value) {
		EcosystemConfigManager.SeasonGrowthMultiplier safe = value == null
			? new EcosystemConfigManager.SeasonGrowthMultiplier(true, 1.0d, 1.0d, 1.0d, 1.0d)
			: value;
		builder.put(FIELD_ENABLED, safe.enabled())
			.put(EcosystemConfigManager.FIELD_SEASON_SPRING, safe.spring())
			.put(EcosystemConfigManager.FIELD_SEASON_SUMMER, safe.summer())
			.put(EcosystemConfigManager.FIELD_SEASON_FALL, safe.fall())
			.put(EcosystemConfigManager.FIELD_SEASON_WINTER, safe.winter());
	}

	public record Settings(
		BlockGrowthSettings blockGrowth,
		FoliageGrowthSettings foliageGrowth,
		FoliageGrowthSettings desertFoliageGrowth,
		VegetationGrowthSettings vegetationGrowth,
		CactusGrowthSettings cactusGrowth,
		TreeGrowthSettings treeGrowth
	) {
		public Settings {
			blockGrowth = blockGrowth == null ? defaults().blockGrowth() : blockGrowth;
			foliageGrowth = foliageGrowth == null ? defaults().foliageGrowth() : foliageGrowth;
			desertFoliageGrowth = desertFoliageGrowth == null ? defaults().desertFoliageGrowth() : desertFoliageGrowth;
			vegetationGrowth = vegetationGrowth == null ? defaults().vegetationGrowth() : vegetationGrowth;
			cactusGrowth = cactusGrowth == null ? defaults().cactusGrowth() : cactusGrowth;
			treeGrowth = treeGrowth == null ? defaults().treeGrowth() : treeGrowth;
		}

		public boolean isEnabled() {
			return blockGrowth.enabled()
				|| foliageGrowth.enabled()
				|| desertFoliageGrowth.enabled()
				|| vegetationGrowth.enabled()
				|| cactusGrowth.enabled()
				|| treeGrowth.enabled();
		}

		public EcosystemConfigManager.DayRange dirtGrowthForSeason(String seasonId) {
			return blockGrowth == null ? null : blockGrowth.dirtGrowthForSeason(seasonId);
		}

		public EcosystemConfigManager.DayRange grassGrowthForSeason(String seasonId) {
			return foliageGrowth == null ? null : foliageGrowth.growthForSeason(seasonId);
		}

		public EcosystemConfigManager.DayRange desertFoliageGrowthForSeason(String seasonId) {
			return desertFoliageGrowth == null ? null : desertFoliageGrowth.growthForSeason(seasonId);
		}

		public EcosystemConfigManager.DayRange cactusGrowthForSeason(String seasonId) {
			return cactusGrowth == null ? null : cactusGrowth.growthForSeason(seasonId);
		}

		public EcosystemConfigManager.DayRange foliageGrowthForSeason(String foliageType, String seasonId) {
			return vegetationGrowth == null ? null : vegetationGrowth.growthForSeason(foliageType, seasonId);
		}

		public EcosystemConfigManager.DayRange treeGrowthForSeason(String treeType, String seasonId) {
			return treeGrowth == null ? null : treeGrowth.growthForSeason(treeType, seasonId);
		}
	}

	public record BlockGrowthSettings(boolean enabled, DirtGrowthSettings dirt) {
		public BlockGrowthSettings {
			dirt = dirt == null ? defaults().blockGrowth().dirt() : dirt;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public EcosystemConfigManager.DayRange dirtGrowthForSeason(String seasonId) {
			return !enabled || dirt == null ? null : dirt.growthForSeason(seasonId);
		}
	}

	public record DirtGrowthSettings(
		boolean enabled,
		List<String> targetBlocks,
		EcosystemConfigManager.DayRange growthTime,
		EcosystemConfigManager.SeasonGrowthMultiplier growthMultiplier
	) {
		public DirtGrowthSettings {
			targetBlocks = EcosystemConfigManager.normalizeList(targetBlocks);
			growthTime = growthTime == null ? new EcosystemConfigManager.DayRange(1, 3) : growthTime;
			growthMultiplier = growthMultiplier == null ? new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d) : growthMultiplier;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public EcosystemConfigManager.DayRange growthForSeason(String seasonId) {
			return enabled ? seasonGrowthRange(growthTime, growthMultiplier, seasonId) : null;
		}
	}

	public record FoliageGrowthSettings(
		boolean enabled,
		EcosystemConfigManager.DayRange growthTime,
		EcosystemConfigManager.SeasonGrowthMultiplier growthMultiplier,
		FoliageTargetSettings shortGrass,
		FoliageTargetSettings tallGrass,
		FoliageTargetSettings bush
	) {
		public FoliageGrowthSettings {
			growthTime = growthTime == null ? new EcosystemConfigManager.DayRange(3, 5) : growthTime;
			growthMultiplier = growthMultiplier == null ? new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d) : growthMultiplier;
			shortGrass = shortGrass == null ? new FoliageTargetSettings(0) : shortGrass;
			tallGrass = tallGrass == null ? new FoliageTargetSettings(0) : tallGrass;
			bush = bush == null ? new FoliageTargetSettings(0) : bush;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public EcosystemConfigManager.DayRange growthForSeason(String seasonId) {
			return enabled ? seasonGrowthRange(growthTime, growthMultiplier, seasonId) : null;
		}
	}

	public record FoliageTargetSettings(int weight) {
		public FoliageTargetSettings {
			weight = Math.max(0, weight);
		}
	}

	public record SpeciesGrowthSettings(
		boolean enabled,
		List<String> eligibleBiomes,
		EcosystemConfigManager.DayRange growthTime,
		EcosystemConfigManager.SeasonGrowthMultiplier growthMultiplier
	) {
		public SpeciesGrowthSettings {
			eligibleBiomes = EcosystemConfigManager.normalizeList(eligibleBiomes);
			growthTime = growthTime == null ? new EcosystemConfigManager.DayRange(1, 1) : growthTime;
			growthMultiplier = growthMultiplier == null ? new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d) : growthMultiplier;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public EcosystemConfigManager.DayRange growthForSeason(String seasonId) {
			return enabled ? seasonGrowthRange(growthTime, growthMultiplier, seasonId) : null;
		}
	}

	public record VegetationGrowthSettings(
		boolean enabled,
		SpeciesGrowthSettings wildflowers,
		SpeciesGrowthSettings pinkPetals
	) {
		public VegetationGrowthSettings {
			wildflowers = wildflowers == null ? defaults().vegetationGrowth().wildflowers() : wildflowers;
			pinkPetals = pinkPetals == null ? defaults().vegetationGrowth().pinkPetals() : pinkPetals;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public boolean isEnabled(String foliageType) {
			if (!enabled) {
				return false;
			}
			return switch (EcosystemConfigManager.normalize(foliageType)) {
				case FIELD_WILDFLOWERS -> wildflowers != null && wildflowers.isEnabled();
				case FIELD_PINK_PETALS -> pinkPetals != null && pinkPetals.isEnabled();
				default -> false;
			};
		}

		public EcosystemConfigManager.DayRange growthForSeason(String foliageType, String seasonId) {
			if (!enabled) {
				return null;
			}
			String normalized = EcosystemConfigManager.normalize(foliageType);
			return switch (normalized) {
				case FIELD_WILDFLOWERS -> wildflowers == null ? null : wildflowers.growthForSeason(seasonId);
				case FIELD_PINK_PETALS -> pinkPetals == null ? null : pinkPetals.growthForSeason(seasonId);
				default -> null;
			};
		}
	}

	public record CactusGrowthSettings(
		boolean enabled,
		EcosystemConfigManager.DayRange growthTime,
		EcosystemConfigManager.SeasonGrowthMultiplier growthMultiplier
	) {
		public CactusGrowthSettings {
			growthTime = growthTime == null ? new EcosystemConfigManager.DayRange(11, 15) : growthTime;
			growthMultiplier = growthMultiplier == null ? new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 1.0d, 1.0d, 1.0d) : growthMultiplier;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public EcosystemConfigManager.DayRange growthForSeason(String seasonId) {
			return enabled ? seasonGrowthRange(growthTime, growthMultiplier, seasonId) : null;
		}
	}

	public record TreeGrowthSettings(
		boolean enabled,
		SpeciesGrowthSettings oakTree,
		SpeciesGrowthSettings birchTree,
		SpeciesGrowthSettings spruceTree,
		SpeciesGrowthSettings jungleTree,
		SpeciesGrowthSettings acaciaTree,
		SpeciesGrowthSettings darkOakTree,
		SpeciesGrowthSettings paleOakTree,
		SpeciesGrowthSettings mangroveTree,
		SpeciesGrowthSettings cherryTree
	) {
		public TreeGrowthSettings {
			oakTree = oakTree == null ? defaults().treeGrowth().oakTree() : oakTree;
			birchTree = birchTree == null ? defaults().treeGrowth().birchTree() : birchTree;
			spruceTree = spruceTree == null ? defaults().treeGrowth().spruceTree() : spruceTree;
			jungleTree = jungleTree == null ? defaults().treeGrowth().jungleTree() : jungleTree;
			acaciaTree = acaciaTree == null ? defaults().treeGrowth().acaciaTree() : acaciaTree;
			darkOakTree = darkOakTree == null ? defaults().treeGrowth().darkOakTree() : darkOakTree;
			paleOakTree = paleOakTree == null ? defaults().treeGrowth().paleOakTree() : paleOakTree;
			mangroveTree = mangroveTree == null ? defaults().treeGrowth().mangroveTree() : mangroveTree;
			cherryTree = cherryTree == null ? defaults().treeGrowth().cherryTree() : cherryTree;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public boolean isEnabled(String treeType) {
			if (!enabled) {
				return false;
			}
			return switch (EcosystemConfigManager.normalize(treeType)) {
				case FIELD_OAK_TREE, "oak" -> oakTree != null && oakTree.isEnabled();
				case FIELD_BIRCH_TREE, "birch" -> birchTree != null && birchTree.isEnabled();
				case FIELD_SPRUCE_TREE, "spruce" -> spruceTree != null && spruceTree.isEnabled();
				case FIELD_JUNGLE_TREE, "jungle" -> jungleTree != null && jungleTree.isEnabled();
				case FIELD_ACACIA_TREE, "acacia" -> acaciaTree != null && acaciaTree.isEnabled();
				case FIELD_DARK_OAK_TREE, "dark_oak" -> darkOakTree != null && darkOakTree.isEnabled();
				case FIELD_PALE_OAK_TREE, "pale_oak" -> paleOakTree != null && paleOakTree.isEnabled();
				case FIELD_MANGROVE_TREE, "mangrove" -> mangroveTree != null && mangroveTree.isEnabled();
				case FIELD_CHERRY_TREE, "cherry" -> cherryTree != null && cherryTree.isEnabled();
				default -> false;
			};
		}

		public EcosystemConfigManager.DayRange growthForSeason(String treeType, String seasonId) {
			if (!enabled) {
				return null;
			}
			return switch (EcosystemConfigManager.normalize(treeType)) {
				case FIELD_OAK_TREE, "oak" -> oakTree == null ? null : oakTree.growthForSeason(seasonId);
				case FIELD_BIRCH_TREE, "birch" -> birchTree == null ? null : birchTree.growthForSeason(seasonId);
				case FIELD_SPRUCE_TREE, "spruce" -> spruceTree == null ? null : spruceTree.growthForSeason(seasonId);
				case FIELD_JUNGLE_TREE, "jungle" -> jungleTree == null ? null : jungleTree.growthForSeason(seasonId);
				case FIELD_ACACIA_TREE, "acacia" -> acaciaTree == null ? null : acaciaTree.growthForSeason(seasonId);
				case FIELD_DARK_OAK_TREE, "dark_oak" -> darkOakTree == null ? null : darkOakTree.growthForSeason(seasonId);
				case FIELD_PALE_OAK_TREE, "pale_oak" -> paleOakTree == null ? null : paleOakTree.growthForSeason(seasonId);
				case FIELD_MANGROVE_TREE, "mangrove" -> mangroveTree == null ? null : mangroveTree.growthForSeason(seasonId);
				case FIELD_CHERRY_TREE, "cherry" -> cherryTree == null ? null : cherryTree.growthForSeason(seasonId);
				default -> null;
			};
		}
	}

	private static EcosystemConfigManager.DayRange seasonGrowthRange(
		EcosystemConfigManager.DayRange baseRange,
		EcosystemConfigManager.SeasonGrowthMultiplier multiplier,
		String seasonId
	) {
		if (baseRange == null || multiplier == null) {
			return null;
		}
		if (!multiplier.enabled() || !SeasonAPIManager.isEnabled()) {
			return baseRange;
		}
		double speed = multiplier.forSeason(seasonId);
		if (speed <= 0.0d) {
			return null;
		}
		int adjustedMin = (int) Math.ceil(baseRange.minDays() / speed);
		int adjustedMax = (int) Math.ceil(baseRange.maxDays() / speed);
		return new EcosystemConfigManager.DayRange(adjustedMin, adjustedMax);
	}
}



