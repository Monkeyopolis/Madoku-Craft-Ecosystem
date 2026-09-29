package madoku.craft.java.ecosystem;

import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class EcosystemNaturalErosionManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalErosionManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "madoku-natural-erosion";

	private static volatile NaturalErosionConfigManager.Settings settings = NaturalErosionConfigManager.defaults();
	private static final Map<String, Identifier> BIOME_IDENTIFIERS = new ConcurrentHashMap<>();
	private static final Map<String, ResourceKey<Biome>> BIOME_KEYS = new ConcurrentHashMap<>();
	private static final Map<String, TagKey<Biome>> BIOME_TAG_KEYS = new ConcurrentHashMap<>();
	private static final Map<Holder<Biome>, Map<String, Boolean>> BIOME_RULE_MATCHES = new ConcurrentHashMap<>();
	private static final Map<Holder<Biome>, Map<ErosionRuleCacheKey, Optional<NaturalErosionConfigManager.NamedErosionRule>>> EROSION_RULE_RESOLUTION_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, SpreadTickState> SPREAD_STATES_BY_LEVEL = new LinkedHashMap<>();
	private static final int RULE_RESOLUTION_GENERAL = 0;
	private static final int RULE_RESOLUTION_WATER = 1;
	private static final int RULE_RESOLUTION_LAVA = 2;

	private EcosystemNaturalErosionManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		BIOME_IDENTIFIERS.clear();
		BIOME_KEYS.clear();
		BIOME_TAG_KEYS.clear();
		BIOME_RULE_MATCHES.clear();
		EROSION_RULE_RESOLUTION_CACHE.clear();
		SPREAD_STATES_BY_LEVEL.clear();
	}

	private enum WetEligibility {
		ELIGIBLE,
		NOT_TRACKABLE,
		NOT_SURFACE,
		SUBMERGED,
		NO_RULE
	}

	private record ErosionRuleCacheKey(String blockId, String preferredRuleId, int resolutionMode) {
	}

	private record AdjacentSeedMatch(
		NaturalErosionConfigManager.NamedErosionRule rule,
		BlockPos fluidSourcePosition
	) {
	}

	private record SeedSpreadKey(long sourcePosition, String ruleId) {
	}

	private record SpreadCellKey(long position, String ruleId) {
	}

	private static final class SpreadTickState {
		private long gameTime = Long.MIN_VALUE;
		private final Set<SeedSpreadKey> processedSeeds = new LinkedHashSet<>();
		private final Set<SpreadCellKey> scannedCells = new LinkedHashSet<>();

		private void prepare(long currentGameTime) {
			if (gameTime == currentGameTime) {
				return;
			}
			gameTime = currentGameTime;
			processedSeeds.clear();
			scannedCells.clear();
		}

		private boolean markSeed(long sourcePosition, String ruleId) {
			return processedSeeds.add(new SeedSpreadKey(sourcePosition, ruleId));
		}

		private boolean markCell(long position, String ruleId) {
			return scannedCells.add(new SpreadCellKey(position, ruleId));
		}
	}



	public static NaturalErosionConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return EcosystemAPIManager.isEnabled() && settings.isEnabled();
	}

	private static boolean isNaturalErosionEnabled() {
		return isEnabled();
	}

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		if (event == null) {
			return;
		}
		ServerLevel world = event.level();
		BlockPos sampledPosition = event.surfaceGroundPosition();
		if (world == null || sampledPosition == null || !isEnabled()) {
			return;
		}
		BlockPos position = sampledPosition;
		BlockState groundState = world.getBlockState(position);
		if (position == null || groundState == null) {
			return;
		}
		boolean sampledSurfaceStillMatches = event.surfaceGroundState() != null
			&& event.surfaceGroundState().equals(groundState);
		AdjacentSeedMatch seedMatch =
			resolveAdjacentSeedRule(world, event.chunk(), position, groundState, sampledSurfaceStillMatches);
		if (seedMatch == null) {
			return;
		}

		String seedKey = EcosystemAPIManager.levelId(world) + "|" + position.asLong();
		EcosystemAPIManager.DirtState trackedSeed = EcosystemAPIManager.dirtBlocksByKey.get(seedKey);
		if (trackedSeed != null && "wet".equals(trackedSeed.mode)) {
			return;
		}

		long currentGameTime = world.getGameTime();
		SpreadTickState spreadState = SPREAD_STATES_BY_LEVEL.computeIfAbsent(
			EcosystemAPIManager.levelId(world),
			ignored -> new SpreadTickState()
		);
		spreadState.prepare(currentGameTime);
		if (!spreadState.markSeed(seedMatch.fluidSourcePosition().asLong(), seedMatch.rule().ruleId())) {
			return;
		}

		spreadWetTrackingFromSeed(world, position, seedMatch.rule(), spreadState);
	}

	/**
	 * Finds a seed only when this one surface block is horizontally next to a
	 * still, same-height surface fluid. The configured radius is intentionally
	 * not used here; it belongs to the one-time spread from the discovered seed.
	 */
	private static AdjacentSeedMatch resolveAdjacentSeedRule(
		ServerLevel world,
		LevelChunk chunk,
		BlockPos blockPos,
		BlockState state,
		boolean sampledSurfaceStillMatches
	) {
		if (world == null || blockPos == null || state == null) {
			return null;
		}
		if (!isTrackableGroundBlock(state)) {
			return null;
		}
		if (isSubmergedInErosionFluid(world, blockPos)) {
			return null;
		}
		if (!sampledSurfaceStillMatches && !isSurfaceGroundBlock(world, chunk, blockPos)) {
			return null;
		}

		BlockPos waterSourcePosition = null;
		BlockPos lavaSourcePosition = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos sourcePosition = blockPos.relative(direction);
			var fluidState = world.getFluidState(sourcePosition);
			if (!fluidState.isSource()) {
				continue;
			}
			if (isWaterErosionEnabled()
				&& fluidState.is(FluidTags.WATER)
				&& isSurfaceFluidSource(world, chunk, sourcePosition, FluidTags.WATER)) {
				if (waterSourcePosition == null) {
					waterSourcePosition = sourcePosition;
				}
			}
			if (isLavaErosionEnabled()
				&& fluidState.is(FluidTags.LAVA)
				&& isSurfaceFluidSource(world, chunk, sourcePosition, FluidTags.LAVA)) {
				if (lavaSourcePosition == null) {
					lavaSourcePosition = sourcePosition;
				}
			}
		}
		if (waterSourcePosition == null && lavaSourcePosition == null) {
			return null;
		}

		NaturalErosionConfigManager.NamedErosionRule rule = waterSourcePosition != null
			? resolveErosionRuleForFluid(world, blockPos, state, true)
			: null;
		BlockPos sourcePosition = waterSourcePosition;
		if (rule == null && lavaSourcePosition != null) {
			rule = resolveErosionRuleForFluid(world, blockPos, state, false);
			sourcePosition = lavaSourcePosition;
		}
		if (rule == null || sourcePosition == null) {
			return null;
		}
		return new AdjacentSeedMatch(rule, sourcePosition);
	}

	/** Spreads one discovered seed across the configured same-height surface radius. */
	private static int spreadWetTrackingFromSeed(
		ServerLevel world,
		BlockPos seedPosition,
		NaturalErosionConfigManager.NamedErosionRule seedRule,
		SpreadTickState spreadState
	) {
		if (world == null || seedPosition == null || seedRule == null || seedRule.rule() == null || spreadState == null) {
			return 0;
		}
		boolean waterSeed = isRuleForFluid(seedRule, true);
		int radius = waterSeed
			? (isWaterErosionEnabled() ? Math.max(0, currentSettings().waterErosionRadius()) : -1)
			: (isLavaErosionEnabled() ? Math.max(0, currentSettings().lavaErosionRadius()) : -1);
		if (radius < 0) {
			return 0;
		}

		int trackedCandidates = 0;
		for (int offsetX = -radius; offsetX <= radius; offsetX++) {
			for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
				if (Math.abs(offsetX) + Math.abs(offsetZ) > radius) {
					continue;
				}
				BlockPos candidatePosition = seedPosition.offset(offsetX, 0, offsetZ);
				if (!spreadState.markCell(candidatePosition.asLong(), seedRule.ruleId())) {
					continue;
				}
				BlockState candidateState = world.getBlockState(candidatePosition);
				WetEligibility candidateEligibility = evaluateWetEligibility(
					world,
					candidatePosition,
					candidateState,
					seedRule.ruleId()
				);
				if (candidateEligibility != WetEligibility.ELIGIBLE) {
					continue;
				}
				if (EcosystemAPIManager.trackWetCandidate(
					world,
					candidatePosition,
					candidateState,
					seedRule
				)) {
					trackedCandidates++;
				}
			}
		}
		return trackedCandidates;
	}

	private static NaturalErosionConfigManager.NamedErosionRule resolveErosionRuleForFluid(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		boolean water
	) {
		if (world == null || pos == null || state == null) {
			return null;
		}
		String blockId = EcosystemConfigManager.blockId(state.getBlock());
		if (blockId.isBlank()) {
			return null;
		}

		Holder<Biome> biomeHolder = world.getBiome(pos);
		Map<ErosionRuleCacheKey, Optional<NaturalErosionConfigManager.NamedErosionRule>> cachedByKey =
			EROSION_RULE_RESOLUTION_CACHE.computeIfAbsent(biomeHolder, ignored -> new ConcurrentHashMap<>());
		ErosionRuleCacheKey cacheKey = new ErosionRuleCacheKey(
			blockId,
			"",
			water ? RULE_RESOLUTION_WATER : RULE_RESOLUTION_LAVA
		);
		Optional<NaturalErosionConfigManager.NamedErosionRule> cached = cachedByKey.get(cacheKey);
		if (cached == null) {
			cached = Optional.ofNullable(resolveErosionRuleForFluidUncached(world, pos, blockId, water, biomeHolder));
			cachedByKey.put(cacheKey, cached);
		}
		return cached.orElse(null);
	}

	private static NaturalErosionConfigManager.NamedErosionRule resolveErosionRuleForFluidUncached(
		ServerLevel world,
		BlockPos pos,
		String blockId,
		boolean water,
		Holder<Biome> biomeHolder
	) {
		for (NaturalErosionConfigManager.NamedErosionRule candidate : EcosystemAPIManager.cachedErosionRules) {
			if (candidate == null || candidate.rule() == null || !isRuleForFluid(candidate, water)
				|| !isErosionRuleEnabled(candidate.ruleId())) {
				continue;
			}
			if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule(), biomeHolder)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean isRuleForFluid(NaturalErosionConfigManager.NamedErosionRule rule, boolean water) {
		if (rule == null) {
			return false;
		}
		boolean magmaRule = NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(rule.ruleId());
		return water != magmaRule;
	}

	static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		return isWetTrackedCandidate(world, blockPos, state, "");
	}

	static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state, String preferredRuleId) {
		WetEligibility result = evaluateWetEligibility(world, blockPos, state, preferredRuleId == null ? "" : preferredRuleId);
		return result == WetEligibility.ELIGIBLE;
	}

	static String wetEligibilityOutcome(ServerLevel world, BlockPos blockPos, BlockState state, String preferredRuleId) {
		return evaluateWetEligibility(world, blockPos, state, preferredRuleId == null ? "" : preferredRuleId)
			.name()
			.toLowerCase(java.util.Locale.ROOT);
	}

	private static WetEligibility evaluateWetEligibility(ServerLevel world, BlockPos blockPos, BlockState state, String preferredRuleId) {
		if (world == null || blockPos == null || state == null
			|| (!isWaterErosionEnabled() && !isLavaErosionEnabled())
			|| !isTrackableGroundBlock(state)) {
			return WetEligibility.NOT_TRACKABLE;
		}
		if (isSubmergedInErosionFluid(world, blockPos)) {
			return WetEligibility.SUBMERGED;
		}
		if (!isSurfaceGroundBlock(world, resolveChunk(world, blockPos), blockPos)) {
			return WetEligibility.NOT_SURFACE;
		}
		NaturalErosionConfigManager.NamedErosionRule rule = EcosystemAPIManager.resolveErosionRule(world, blockPos, state, preferredRuleId);
		if (rule == null || (!preferredRuleId.isBlank() && !preferredRuleId.equals(rule.ruleId()))) {
			return WetEligibility.NO_RULE;
		}
		return WetEligibility.ELIGIBLE;
	}

	private static boolean isSurfaceGroundBlock(ServerLevel world, LevelChunk chunk, BlockPos blockPos) {
		if (world == null || chunk == null || blockPos == null) {
			return false;
		}
		int surfaceY = chunk.getHeight(
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			blockPos.getX() & 15,
			blockPos.getZ() & 15
		);
		return blockPos.getY() == surfaceY;
	}

	private static LevelChunk resolveChunk(ServerLevel world, BlockPos position) {
		if (world == null || position == null) {
			return null;
		}
		return world.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
	}

	static boolean isLavaMagmaSourceBlockId(String blockId) {
		if (blockId == null || blockId.isBlank()) {
			return false;
		}
		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(NaturalErosionConfigManager.FIELD_MAGMA_BLOCK);
		return magmaRule != null
			&& magmaRule.rule() != null
			&& magmaRule.rule().enabled()
			&& magmaRule.rule().sourceBlocks().contains(blockId);
	}

	static NaturalErosionConfigManager.NamedErosionRule resolveErosionRule(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String preferredRuleId
	) {
		if (world == null || pos == null || state == null || !isNaturalErosionEnabled()) {
			return null;
		}
		String blockId = EcosystemConfigManager.blockId(state.getBlock());
		if (blockId.isBlank()) {
			return null;
		}

		String normalizedPreferredRuleId = preferredRuleId == null ? "" : EcosystemConfigManager.normalize(preferredRuleId);
		Holder<Biome> biomeHolder = world.getBiome(pos);
		Map<ErosionRuleCacheKey, Optional<NaturalErosionConfigManager.NamedErosionRule>> cachedByKey =
			EROSION_RULE_RESOLUTION_CACHE.computeIfAbsent(biomeHolder, ignored -> new ConcurrentHashMap<>());
		ErosionRuleCacheKey cacheKey = new ErosionRuleCacheKey(
			blockId,
			normalizedPreferredRuleId,
			RULE_RESOLUTION_GENERAL
		);
		Optional<NaturalErosionConfigManager.NamedErosionRule> cached = cachedByKey.get(cacheKey);
		if (cached == null) {
			cached = Optional.ofNullable(resolveErosionRuleUncached(
				world,
				pos,
				blockId,
				normalizedPreferredRuleId,
				biomeHolder
			));
			cachedByKey.put(cacheKey, cached);
		}
		return cached.orElse(null);
	}

	private static NaturalErosionConfigManager.NamedErosionRule resolveErosionRuleUncached(
		ServerLevel world,
		BlockPos pos,
		String blockId,
		String preferredRuleId,
		Holder<Biome> biomeHolder
	) {
		NaturalErosionConfigManager.NamedErosionRule magmaRule = findErosionRuleById(NaturalErosionConfigManager.FIELD_MAGMA_BLOCK);
		if (magmaRule != null && isLavaErosionEnabled()
			&& matchesErosionRule(world, pos, blockId, magmaRule.ruleId(), magmaRule.rule(), biomeHolder)) {
			return magmaRule;
		}

		if (preferredRuleId != null && !preferredRuleId.isBlank()) {
			for (NaturalErosionConfigManager.NamedErosionRule candidate : EcosystemAPIManager.cachedErosionRules) {
				if (candidate == null || !preferredRuleId.equals(candidate.ruleId())) {
					continue;
				}
				if (!isErosionRuleEnabled(candidate.ruleId())
					|| NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(candidate.ruleId())) {
					break;
				}
				if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule(), biomeHolder)) {
					return candidate;
				}
				break;
			}
		}

		for (NaturalErosionConfigManager.NamedErosionRule candidate : EcosystemAPIManager.cachedErosionRules) {
			if (candidate == null || !isErosionRuleEnabled(candidate.ruleId())
				|| NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(candidate.ruleId())) {
				continue;
			}
			if (matchesErosionRule(world, pos, blockId, candidate.ruleId(), candidate.rule(), biomeHolder)) {
				return candidate;
			}
		}
		return null;
	}

	private static NaturalErosionConfigManager.NamedErosionRule findErosionRuleById(String ruleId) {
		if (ruleId == null || ruleId.isBlank()) {
			return null;
		}
		for (NaturalErosionConfigManager.NamedErosionRule candidate : EcosystemAPIManager.cachedErosionRules) {
			if (candidate == null || candidate.rule() == null) {
				continue;
			}
			if (ruleId.equals(candidate.ruleId())) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean matchesErosionRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		String ruleId,
		NaturalErosionConfigManager.ErosionRuleSettings rule,
		Holder<Biome> biomeHolder
	) {
		if (world == null || pos == null || sourceBlockId == null || sourceBlockId.isBlank() || rule == null || !rule.enabled()) {
			return false;
		}
		if (biomeHolder == null) {
			return false;
		}
		if (!rule.sourceBlocks().contains(sourceBlockId)) {
			return false;
		}
		Block targetBlock = resolveErosionTargetBlock(ruleId);
		if (targetBlock == null) {
			return false;
		}

		List<String> eligibleBiomes = rule.eligibleBiomes();
		if (eligibleBiomes == null || eligibleBiomes.isEmpty()) {
			return true;
		}

		Map<String, Boolean> cachedRules = BIOME_RULE_MATCHES.computeIfAbsent(biomeHolder, ignored -> new ConcurrentHashMap<>());
		return cachedRules.computeIfAbsent(ruleId, ignored -> matchesEligibleBiome(biomeHolder, eligibleBiomes));
	}

	private static boolean matchesEligibleBiome(Holder<Biome> biomeHolder, List<String> eligibleBiomes) {
		for (String biomeEntry : eligibleBiomes) {
			String normalized = biomeEntry == null ? "" : biomeEntry.trim();
			if (normalized.isEmpty()) {
				continue;
			}
			if (normalized.startsWith("#")) {
				normalized = normalized.substring(1);
			}
			String normalizedBiomeId = JSONAPIManager.normalizeRegistryIdentifierForLookup(normalized);
			Identifier id = BIOME_IDENTIFIERS.computeIfAbsent(normalizedBiomeId, Identifier::tryParse);
			if (id == null) {
				continue;
			}
			ResourceKey<Biome> biomeKey = BIOME_KEYS.computeIfAbsent(
				normalizedBiomeId,
				key -> ResourceKey.create(Registries.BIOME, id)
			);
			TagKey<Biome> biomeTagKey = BIOME_TAG_KEYS.computeIfAbsent(
				normalizedBiomeId,
				key -> TagKey.create(Registries.BIOME, id)
			);
			if (biomeHolder.is(biomeKey)) {
				return true;
			}
			if (biomeHolder.is(biomeTagKey)) {
				return true;
			}
		}
		return false;
	}

	private static Block resolveErosionTargetBlock(String ruleId) {
		String normalizedRuleId = EcosystemConfigManager.normalize(ruleId);
		String targetBlockId = switch (normalizedRuleId) {
			case NaturalErosionConfigManager.FIELD_MUD -> "minecraft:mud";
			case NaturalErosionConfigManager.FIELD_RED_SAND -> "minecraft:red_sand";
			case NaturalErosionConfigManager.FIELD_SAND -> "minecraft:sand";
			case NaturalErosionConfigManager.FIELD_MAGMA_BLOCK -> "minecraft:magma_block";
			default -> "";
		};
		return EcosystemConfigManager.resolveBlock(targetBlockId);
	}

	static boolean isTrackableGroundBlock(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		if (block == Blocks.DIRT) {
			return true;
		}
		if (EcosystemAPIManager.TRACKABLE_WET_GROUND_BLOCKS.contains(block) && isWaterErosionEnabled()) {
			return true;
		}
		String blockId = EcosystemConfigManager.blockId(block);
		if (isLavaMagmaSourceBlockId(blockId) && isLavaErosionEnabled()) {
			return true;
		}
		if (!isWaterErosionEnabled()) {
			return false;
		}
		for (NaturalErosionConfigManager.NamedErosionRule rule : EcosystemAPIManager.cachedErosionRules) {
			if (rule == null || rule.rule() == null || !rule.rule().enabled()) {
				continue;
			}
			if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(rule.ruleId())) {
				continue;
			}
			if (rule.rule().sourceBlocks().contains(blockId)) {
				return true;
			}
		}
		return false;
	}

	static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos, BlockState state, String preferredRuleId) {
		NaturalErosionConfigManager.NamedErosionRule rule = resolveErosionRule(world, pos, state, preferredRuleId);
		if (rule == null || rule.rule() == null) {
			return null;
		}
		return resolveErosionTargetBlock(rule.ruleId());
	}

	static boolean isSubmerged(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return world.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)
			|| world.getFluidState(pos.above()).is(net.minecraft.tags.FluidTags.WATER);
	}

	private static boolean isSurfaceFluidSource(
		ServerLevel world,
		LevelChunk activeChunk,
		BlockPos fluidPosition,
		net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> fluidTag
	) {
		if (world == null || fluidPosition == null || fluidTag == null) {
			return false;
		}
		LevelChunk chunk = activeChunk != null
			&& activeChunk.getPos().x() == (fluidPosition.getX() >> 4)
			&& activeChunk.getPos().z() == (fluidPosition.getZ() >> 4)
			? activeChunk
			: resolveChunk(world, fluidPosition);
		if (chunk == null) {
			return false;
		}
		int surfaceY = chunk.getHeight(
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			fluidPosition.getX() & 15,
			fluidPosition.getZ() & 15
		);
		return fluidPosition.getY() == surfaceY;
	}

	private static boolean isSubmergedInErosionFluid(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return isErosionFluid(world.getFluidState(pos));
	}

	private static boolean isErosionFluid(net.minecraft.world.level.material.FluidState fluidState) {
		return fluidState != null
			&& (fluidState.is(FluidTags.WATER) || fluidState.is(FluidTags.LAVA));
	}

	private static NaturalErosionConfigManager.Settings currentSettings() {
		return settings == null ? NaturalErosionConfigManager.defaults() : settings;
	}

	static boolean isWaterErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.waterErosion() != null && current.waterErosion().enabled();
	}

	static boolean isLavaErosionEnabled() {
		NaturalErosionConfigManager.Settings current = currentSettings();
		return isEnabled() && current.lavaErosion() != null && current.lavaErosion().enabled();
	}

	private static boolean isErosionRuleEnabled(String ruleId) {
		if (NaturalErosionConfigManager.FIELD_MAGMA_BLOCK.equals(EcosystemConfigManager.normalize(ruleId))) {
			return isLavaErosionEnabled();
		}
		return isWaterErosionEnabled();
	}

	private static void loadConfig() {
		NaturalErosionConfigManager.Settings fallback = NaturalErosionConfigManager.defaults();
		BIOME_RULE_MATCHES.clear();
		EROSION_RULE_RESOLUTION_CACHE.clear();
		SPREAD_STATES_BY_LEVEL.clear();
		JsonObject defaults = NaturalErosionConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults);
			settings = NaturalErosionConfigManager.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, NaturalErosionConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalErosionManager config; using defaults.", exception);
		}
	}

}
