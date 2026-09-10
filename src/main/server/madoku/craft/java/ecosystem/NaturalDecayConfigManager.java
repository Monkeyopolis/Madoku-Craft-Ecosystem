package madoku.craft.java.ecosystem;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;

import java.util.List;

public final class NaturalDecayConfigManager {
	public static final String FIELD_LEAF_LITTER = "leaf-litter";
	public static final String FIELD_ENABLED = "enabled";
	public static final String FIELD_DECAY_TIME = "decay-time";
	public static final String FIELD_DECAY_MULTIPLIER = "decay-multiplier";
	public static final String FIELD_MIN_TIME = "min-time";
	public static final String FIELD_MAX_TIME = "max-time";
	public static final String FIELD_SEASON_SPRING = EcosystemConfigManager.FIELD_SEASON_SPRING;
	public static final String FIELD_SEASON_SUMMER = EcosystemConfigManager.FIELD_SEASON_SUMMER;
	public static final String FIELD_SEASON_FALL = EcosystemConfigManager.FIELD_SEASON_FALL;
	public static final String FIELD_SEASON_WINTER = EcosystemConfigManager.FIELD_SEASON_WINTER;

	private NaturalDecayConfigManager() {
	}

	public static Settings defaults() {
		return new Settings(
			new LeafLitterSettings(
				true,
				new EcosystemConfigManager.DayRange(5, 7),
				new EcosystemConfigManager.SeasonGrowthMultiplier(1.0d, 0.0d, 3.0d, 0.0d)
			)
		);
	}

	public static List<NamedLeafLitterDecayRule> decayRulesInPriority(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		return value.leafLitter == null
			? List.of()
			: List.of(new NamedLeafLitterDecayRule(FIELD_LEAF_LITTER, value.leafLitter));
	}

	public static JsonObject buildDefaultsJson() {
		return toJson(defaults());
	}

	public static Settings fromJson(JsonObject source) {
		Settings fallback = defaults();
		if (source == null) {
			return fallback;
		}

		JsonObject leafRoot = EcosystemConfigManager.readObject(source, FIELD_LEAF_LITTER);
		JsonObject multiplierRoot = EcosystemConfigManager.readObject(leafRoot, FIELD_DECAY_MULTIPLIER);
		return new Settings(
			new LeafLitterSettings(
				EcosystemConfigManager.readBoolean(leafRoot, FIELD_ENABLED, fallback.leafLitter.enabled()),
				EcosystemConfigManager.readDayRange(leafRoot, FIELD_DECAY_TIME, fallback.leafLitter.decayTime(), FIELD_MIN_TIME, FIELD_MAX_TIME),
				EcosystemConfigManager.readSeasonMultiplier(multiplierRoot, fallback.leafLitter.decayMultiplier())
			)
		);
	}

	public static JsonObject toJson(Settings settings) {
		Settings value = settings == null ? defaults() : settings;
		return JSONFormatAPIManager.object()
			.object(FIELD_LEAF_LITTER, leaf -> leaf
				.put(FIELD_ENABLED, value.leafLitter.enabled())
				.object(FIELD_DECAY_TIME, time -> time
					.put(FIELD_MIN_TIME, value.leafLitter.decayTime().minDays())
					.put(FIELD_MAX_TIME, value.leafLitter.decayTime().maxDays()))
				.object(FIELD_DECAY_MULTIPLIER, multiplier -> {
					multiplier.put(FIELD_ENABLED, value.leafLitter.decayMultiplier().enabled());
					multiplier.put(FIELD_SEASON_SPRING, value.leafLitter.decayMultiplier().spring());
					multiplier.put(FIELD_SEASON_SUMMER, value.leafLitter.decayMultiplier().summer());
					multiplier.put(FIELD_SEASON_FALL, value.leafLitter.decayMultiplier().fall());
					multiplier.put(FIELD_SEASON_WINTER, value.leafLitter.decayMultiplier().winter());
				}))
			.build();
	}

	public record Settings(LeafLitterSettings leafLitter) {
		public Settings {
			leafLitter = leafLitter == null ? defaults().leafLitter() : leafLitter;
		}

		public boolean isEnabled() {
			return leafLitter != null && leafLitter.enabled();
		}

		public EcosystemConfigManager.DayRange treeDecayForSeason(String seasonId) {
			return leafLitter == null ? null : leafLitter.decayForSeason(seasonId);
		}
	}

	public record LeafLitterSettings(
		boolean enabled,
		EcosystemConfigManager.DayRange decayTime,
		EcosystemConfigManager.SeasonGrowthMultiplier decayMultiplier
	) {
		public LeafLitterSettings {
			decayTime = decayTime == null ? defaults().leafLitter().decayTime() : decayTime;
			decayMultiplier = decayMultiplier == null ? defaults().leafLitter().decayMultiplier() : decayMultiplier;
		}

		public EcosystemConfigManager.DayRange decayForSeason(String seasonId) {
			return enabled ? seasonGrowthRange(decayTime, decayMultiplier, seasonId) : null;
		}
	}

	public record NamedLeafLitterDecayRule(String ruleId, LeafLitterSettings rule) {
		public NamedLeafLitterDecayRule {
			ruleId = EcosystemConfigManager.normalize(ruleId);
			rule = rule == null ? defaults().leafLitter() : rule;
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



