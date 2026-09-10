package madoku.craft.java.ecosystem;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EcosystemConfigManager {
	public static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	public static final String CONFIG_FILE_NAME = "madoku-ecosystem";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_SEASON_SPRING = "spring";
	public static final String FIELD_SEASON_SUMMER = "summer";
	public static final String FIELD_SEASON_FALL = "fall";
	public static final String FIELD_SEASON_WINTER = "winter";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemConfigManager.class);
	private static volatile Settings settings = defaults();
	private static final Map<String, Block> RESOLVED_BLOCKS = new ConcurrentHashMap<>();

	private EcosystemConfigManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		RESOLVED_BLOCKS.clear();
	}

	public static Settings getSettings() {
		return settings;
	}

	public static Settings defaults() {
		return new Settings(true);
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		if (source == null) {
			return fallback;
		}
		return new Settings(EcosystemConfigManager.readBoolean(source, FIELD_ENABLED, fallback.enabled()));
	}

	public static JsonObject toJson(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, value.enabled())
			.build();
	}

	public static JsonObject readObject(JsonObject source, String key) {
		if (source == null || key == null || key.isBlank()) {
			return new JsonObject();
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonObject()) {
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	public static boolean readBoolean(JsonObject source, String key, boolean fallback) {
		if (source == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public static int readInt(JsonObject source, String key, int fallback) {
		if (source == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public static double readDouble(JsonObject source, String key, double fallback) {
		if (source == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	public static String readString(JsonObject source, String key, String fallback) {
		if (source == null || key == null || key.isBlank()) {
			return normalize(fallback);
		}
		JsonElement element = source.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return normalize(fallback);
		}
		return normalize(element.getAsString());
	}

	public static List<String> readStringArray(JsonObject source, String key, List<String> fallback) {
		JsonElement element = source == null ? null : source.get(key);
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

	public static JsonArray toStringArray(List<String> values) {
		JSONFormatAPIManager.ArrayBuilder array = JSONFormatAPIManager.array();
		for (String value : normalizeList(values)) {
			array.add(value);
		}
		return array.build();
	}

	public static List<String> normalizeList(List<String> source) {
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

	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase();
	}

	public static Block resolveBlock(String blockId) {
		String normalized = JSONAPIManager.normalizeRegistryIdentifierForLookup(blockId);
		if (normalized == null || normalized.isBlank()) {
			return null;
		}
		return RESOLVED_BLOCKS.computeIfAbsent(normalized, key -> {
			Identifier id = Identifier.tryParse(key);
			return id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
		});
	}

	public static String blockId(Block block) {
		if (block == null) {
			return "";
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null ? "" : id.toString();
	}

	public static DayRange readDayRange(
		JsonObject source,
		String key,
		DayRange fallback,
		String minKey,
		String maxKey
	) {
		DayRange safeFallback = fallback == null
			? new DayRange(1, 1)
			: fallback;
		JsonObject rangeRoot = readObject(source, key);
		int min = readInt(rangeRoot, minKey, safeFallback.minDays());
		int max = readInt(rangeRoot, maxKey, safeFallback.maxDays());
		return new DayRange(min, max);
	}

	public static JsonObject toDayRangeJson(
		DayRange range,
		String minKey,
		String maxKey
	) {
		DayRange safe = range == null
			? new DayRange(1, 1)
			: range;
		return JSONFormatAPIManager.object()
			.put(minKey, safe.minDays())
			.put(maxKey, safe.maxDays())
			.build();
	}

	public static SeasonGrowthMultiplier readSeasonMultiplier(JsonObject source, SeasonGrowthMultiplier fallback) {
		SeasonGrowthMultiplier safeFallback = fallback == null
			? new SeasonGrowthMultiplier(true, 1.0d, 1.0d, 1.0d, 1.0d)
			: fallback;
		return new SeasonGrowthMultiplier(
			readBoolean(source, FIELD_ENABLED, safeFallback.enabled()),
			readDouble(source, FIELD_SEASON_SPRING, safeFallback.spring()),
			readDouble(source, FIELD_SEASON_SUMMER, safeFallback.summer()),
			readDouble(source, FIELD_SEASON_FALL, safeFallback.fall()),
			readDouble(source, FIELD_SEASON_WINTER, safeFallback.winter())
		);
	}

	public static JsonObject toSeasonMultiplierJson(SeasonGrowthMultiplier multiplier) {
		SeasonGrowthMultiplier safe = multiplier == null
			? new SeasonGrowthMultiplier(true, 1.0d, 1.0d, 1.0d, 1.0d)
			: multiplier;
		return JSONFormatAPIManager.object()
			.put(FIELD_ENABLED, safe.enabled())
			.put(FIELD_SEASON_SPRING, safe.spring())
			.put(FIELD_SEASON_SUMMER, safe.summer())
			.put(FIELD_SEASON_FALL, safe.fall())
			.put(FIELD_SEASON_WINTER, safe.winter())
			.build();
	}

	public static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static void loadConfig() {
		Settings fallback = defaults();
		JsonObject defaults = buildDefaultsJson();
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults);
			settings = fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemConfigManager config; using defaults.", exception);
		}
	}

	public record Settings(boolean enabled) {
	}

	public record DayRange(int minDays, int maxDays) {
		public DayRange {
			minDays = Math.max(1, minDays);
			maxDays = Math.max(minDays, maxDays);
		}
	}

	public record SeasonGrowthMultiplier(boolean enabled, double spring, double summer, double fall, double winter) {
		public SeasonGrowthMultiplier(double spring, double summer, double fall, double winter) {
			this(true, spring, summer, fall, winter);
		}

		public double forSeason(String seasonId) {
			String normalized = normalize(seasonId);
			return switch (normalized) {
				case FIELD_SEASON_SUMMER -> summer;
				case FIELD_SEASON_FALL -> fall;
				case FIELD_SEASON_WINTER -> winter;
				default -> spring;
			};
		}
	}
}

