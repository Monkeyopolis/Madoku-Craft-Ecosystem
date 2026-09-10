package madoku.craft.java.ecosystem;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;

import java.util.List;

public final class NaturalErosionConfigManager {
	public static final String FIELD_WATER_EROSION = "water-erosion";
	public static final String FIELD_LAVA_EROSION = "lava-erosion";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_EROSION_RADIUS = "erosion-radius";
	public static final String FIELD_BLOCK_EROSION = "block-erosion";
	public static final String FIELD_MUD = "mud";
	public static final String FIELD_RED_SAND = "red-sand";
	public static final String FIELD_SAND = "sand";
	public static final String FIELD_MAGMA_BLOCK = "magma-block";
	public static final String FIELD_SOURCE_BLOCKS = "source-blocks";
	public static final String FIELD_ELIGIBLE_BIOMES = "eligible-biomes";
	public static final String FIELD_EROSION_TIME = "erosion-time";
	public static final String FIELD_MIN_TIME = "min-time";
	public static final String FIELD_MAX_TIME = "max-time";

	private static final List<String> DEFAULT_GROUND_SOURCE_BLOCKS = List.of(
		"minecraft:grass_block",
		"minecraft:dirt",
		"minecraft:rooted_dirt",
		"minecraft:dirt_path",
		"minecraft:podzol",
		"minecraft:mycelium",
		"minecraft:coarse_dirt"
	);
	private static final List<String> DEFAULT_MUD_BIOMES = List.of("minecraft:swamp", "minecraft:mangrove_swamp");
	private static final List<String> DEFAULT_RED_SAND_BIOMES = List.of(
		"minecraft:badlands",
		"minecraft:wooded_badlands",
		"minecraft:eroded_badlands"
	);
	private static final List<String> DEFAULT_MAGMA_SOURCE_BLOCKS = List.of(
		"minecraft:blackstone",
		"minecraft:basalt",
		"minecraft:smooth_basalt",
		"minecraft:netherrack",
		"minecraft:stone",
		"minecraft:andesite",
		"minecraft:diorite",
		"minecraft:granite",
		"minecraft:deepslate",
		"minecraft:tuff",
		"minecraft:cobbled_deepslate",
		"minecraft:cobblestone",
		"minecraft:mossy_cobblestone"
	);

	private NaturalErosionConfigManager() {
	}

	private static volatile Settings cachedRulesSettings;
	private static volatile List<NamedErosionRule> cachedPriorityRules = List.of();

	public static Settings defaults() {
		return new Settings(
			new WaterErosionSettings(
				true,
				3,
				new WaterBlockErosionSettings(
					new ErosionRuleSettings(true, DEFAULT_GROUND_SOURCE_BLOCKS, DEFAULT_MUD_BIOMES, new EcosystemConfigManager.DayRange(7, 14)),
					new ErosionRuleSettings(true, DEFAULT_GROUND_SOURCE_BLOCKS, DEFAULT_RED_SAND_BIOMES, new EcosystemConfigManager.DayRange(7, 14)),
					new ErosionRuleSettings(true, DEFAULT_GROUND_SOURCE_BLOCKS, List.of(), new EcosystemConfigManager.DayRange(7, 14))
				)
			),
			new LavaErosionSettings(
				true,
				3,
				new LavaBlockErosionSettings(
					new ErosionRuleSettings(true, DEFAULT_MAGMA_SOURCE_BLOCKS, List.of(), new EcosystemConfigManager.DayRange(14, 28))
				)
			)
		);
	}

	public static List<NamedErosionRule> erosionRulesInPriority(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		List<NamedErosionRule> cached = cachedPriorityRules;
		if (value == cachedRulesSettings) {
			return cached;
		}
		List<NamedErosionRule> resolved = List.of(
			new NamedErosionRule(FIELD_MUD, value.waterErosion.blockErosion.mud()),
			new NamedErosionRule(FIELD_RED_SAND, value.waterErosion.blockErosion.redSand()),
			new NamedErosionRule(FIELD_SAND, value.waterErosion.blockErosion.sand()),
			new NamedErosionRule(FIELD_MAGMA_BLOCK, value.lavaErosion.blockErosion.magmaBlock())
		);
		cachedRulesSettings = value;
		cachedPriorityRules = resolved;
		return resolved;
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		if (source == null) {
			return fallback;
		}

		JsonObject waterRoot = EcosystemConfigManager.readObject(source, FIELD_WATER_EROSION);
		JsonObject waterBlockRoot = EcosystemConfigManager.readObject(waterRoot, FIELD_BLOCK_EROSION);
		JsonObject lavaRoot = EcosystemConfigManager.readObject(source, FIELD_LAVA_EROSION);
		JsonObject lavaBlockRoot = EcosystemConfigManager.readObject(lavaRoot, FIELD_BLOCK_EROSION);
		return new Settings(
			new WaterErosionSettings(
				EcosystemConfigManager.readBoolean(waterRoot, FIELD_ENABLED, fallback.waterErosion.enabled()),
				EcosystemConfigManager.readInt(waterRoot, FIELD_EROSION_RADIUS, fallback.waterErosion.erosionRadius()),
				new WaterBlockErosionSettings(
					readRule(waterBlockRoot, FIELD_MUD, fallback.waterErosion.blockErosion.mud()),
					readRule(waterBlockRoot, FIELD_RED_SAND, fallback.waterErosion.blockErosion.redSand()),
					readRule(waterBlockRoot, FIELD_SAND, fallback.waterErosion.blockErosion.sand())
				)
			),
			new LavaErosionSettings(
				EcosystemConfigManager.readBoolean(lavaRoot, FIELD_ENABLED, fallback.lavaErosion.enabled()),
				EcosystemConfigManager.readInt(lavaRoot, FIELD_EROSION_RADIUS, fallback.lavaErosion.erosionRadius()),
				new LavaBlockErosionSettings(
					readRule(lavaBlockRoot, FIELD_MAGMA_BLOCK, fallback.lavaErosion.blockErosion.magmaBlock())
				)
			)
		);
	}

	public static JsonObject toJson(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		return JSONFormatAPIManager.object()
			.object(FIELD_WATER_EROSION, waterRoot -> {
				waterRoot.put(FIELD_ENABLED, value.waterErosion.enabled());
				waterRoot.put(FIELD_EROSION_RADIUS, value.waterErosion.erosionRadius());
				waterRoot.object(FIELD_BLOCK_EROSION, blockRoot -> {
					blockRoot.object(FIELD_MUD, mud -> writeRule(mud, value.waterErosion.blockErosion.mud()));
					blockRoot.object(FIELD_RED_SAND, redSand -> writeRule(redSand, value.waterErosion.blockErosion.redSand()));
					blockRoot.object(FIELD_SAND, sand -> writeRule(sand, value.waterErosion.blockErosion.sand()));
				});
			})
			.object(FIELD_LAVA_EROSION, lavaRoot -> {
				lavaRoot.put(FIELD_ENABLED, value.lavaErosion.enabled());
				lavaRoot.put(FIELD_EROSION_RADIUS, value.lavaErosion.erosionRadius());
				lavaRoot.object(FIELD_BLOCK_EROSION, blockRoot -> blockRoot.object(FIELD_MAGMA_BLOCK, magma -> writeRule(magma, value.lavaErosion.blockErosion.magmaBlock())));
			})
			.build();
	}

	private static ErosionRuleSettings readRule(JsonObject source, String key, ErosionRuleSettings fallback) {
		ErosionRuleSettings safeFallback = fallback == null
			? new ErosionRuleSettings(true, List.of(), List.of(), new EcosystemConfigManager.DayRange(1, 1))
			: fallback;
		JsonObject root = EcosystemConfigManager.readObject(source, key);
		return new ErosionRuleSettings(
			EcosystemConfigManager.readBoolean(root, FIELD_ENABLED, safeFallback.enabled()),
			EcosystemConfigManager.readStringArray(root, FIELD_SOURCE_BLOCKS, safeFallback.sourceBlocks()),
			EcosystemConfigManager.readStringArray(root, FIELD_ELIGIBLE_BIOMES, safeFallback.eligibleBiomes()),
			EcosystemConfigManager.readDayRange(root, FIELD_EROSION_TIME, safeFallback.erosionTime(), FIELD_MIN_TIME, FIELD_MAX_TIME)
		);
	}

	private static void writeRule(JSONFormatAPIManager.ObjectBuilder builder, ErosionRuleSettings value) {
		ErosionRuleSettings safe = value == null
			? new ErosionRuleSettings(true, List.of(), List.of(), new EcosystemConfigManager.DayRange(1, 1))
			: value;
		builder.put(FIELD_ENABLED, safe.enabled())
			.put(FIELD_SOURCE_BLOCKS, EcosystemConfigManager.toStringArray(safe.sourceBlocks()))
			.put(FIELD_ELIGIBLE_BIOMES, EcosystemConfigManager.toStringArray(safe.eligibleBiomes()))
			.object(FIELD_EROSION_TIME, range -> range
				.put(FIELD_MIN_TIME, safe.erosionTime().minDays())
				.put(FIELD_MAX_TIME, safe.erosionTime().maxDays()));
	}

	public record Settings(WaterErosionSettings waterErosion, LavaErosionSettings lavaErosion) {
		public Settings {
			waterErosion = waterErosion == null ? defaults().waterErosion() : waterErosion;
			lavaErosion = lavaErosion == null ? defaults().lavaErosion() : lavaErosion;
		}

		public boolean isEnabled() {
			return waterErosion.enabled() || lavaErosion.enabled();
		}

		public int waterErosionRadius() {
			return waterErosion == null ? 0 : waterErosion.erosionRadius();
		}

		public int lavaErosionRadius() {
			return lavaErosion == null ? 0 : lavaErosion.erosionRadius();
		}
	}

	public record WaterErosionSettings(boolean enabled, int erosionRadius, WaterBlockErosionSettings blockErosion) {
		public WaterErosionSettings {
			erosionRadius = Math.max(0, erosionRadius);
			blockErosion = blockErosion == null ? defaults().waterErosion().blockErosion() : blockErosion;
		}
	}

	public record WaterBlockErosionSettings(
		ErosionRuleSettings mud,
		ErosionRuleSettings redSand,
		ErosionRuleSettings sand
	) {
		public WaterBlockErosionSettings {
			mud = mud == null ? defaults().waterErosion().blockErosion().mud() : mud;
			redSand = redSand == null ? defaults().waterErosion().blockErosion().redSand() : redSand;
			sand = sand == null ? defaults().waterErosion().blockErosion().sand() : sand;
		}
	}

	public record LavaErosionSettings(boolean enabled, int erosionRadius, LavaBlockErosionSettings blockErosion) {
		public LavaErosionSettings {
			erosionRadius = Math.max(0, erosionRadius);
			blockErosion = blockErosion == null ? defaults().lavaErosion().blockErosion() : blockErosion;
		}
	}

	public record LavaBlockErosionSettings(ErosionRuleSettings magmaBlock) {
		public LavaBlockErosionSettings {
			magmaBlock = magmaBlock == null ? defaults().lavaErosion().blockErosion().magmaBlock() : magmaBlock;
		}
	}

	public record ErosionRuleSettings(
		boolean enabled,
		List<String> sourceBlocks,
		List<String> eligibleBiomes,
		EcosystemConfigManager.DayRange erosionTime
	) {
		public ErosionRuleSettings {
			sourceBlocks = EcosystemConfigManager.normalizeList(sourceBlocks);
			eligibleBiomes = EcosystemConfigManager.normalizeList(eligibleBiomes);
			erosionTime = erosionTime == null ? new EcosystemConfigManager.DayRange(1, 1) : erosionTime;
		}
	}

	public record NamedErosionRule(String ruleId, ErosionRuleSettings rule) {
		public NamedErosionRule {
			ruleId = EcosystemConfigManager.normalize(ruleId);
			rule = rule == null ? new ErosionRuleSettings(true, List.of(), List.of(), new EcosystemConfigManager.DayRange(1, 1)) : rule;
		}
	}
}


