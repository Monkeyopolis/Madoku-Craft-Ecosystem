package madoku.craft.ecosystem.system;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import madoku.craft.chunk.ChunkManagerSystem;
import madoku.craft.clock.MadokuTicks;
import madoku.craft.config.JsonManagerSystem;
import madoku.craft.config.JsonStaticSystem;
import madoku.craft.data.DataManagerSystem;
import madoku.craft.debug.MadokuDebug;
import madoku.craft.scheduler.SchedulerManagerSystem;
import madoku.craft.season.MadokuSeason;
import madoku.craft.time.MadokuTime;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class MadokuEcosystem {
	private static final Logger LOGGER = LoggerFactory.getLogger(MadokuEcosystem.class);
	private static final String DATA_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String DATA_FILE_NAME = "madoku-ecosystem";
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "madoku-ecosystem";
	private static final String CONFIG_CATEGORY_FOLDER_NAME = "madoku-ecosystem";
	private static final String CONFIG_FILE_NATURAL_GROWTH = "natural-growth";
	private static final String CONFIG_FILE_NATURAL_EROSION = "natural-erosion";
	private static final String ECOSYSTEM_GROWTH_PROCESS_SCHEDULER_OWNER_ID = "ecosystem_growth_process_gameplay";
	private static final String ECOSYSTEM_EROSION_PROCESS_SCHEDULER_OWNER_ID = "ecosystem_erosion_process_gameplay";
	private static final String TASK_TYPE_ECOSYSTEM_GROWTH_PROCESS_TICK = "ecosystem_growth_process_gameplay_tick";
	private static final String TASK_TYPE_ECOSYSTEM_EROSION_PROCESS_TICK = "ecosystem_erosion_process_gameplay_tick";
	private static final long ECOSYSTEM_SCHEDULER_INTERVAL_TICKS = 5L;
	private static final String CHUNK_PROCESSOR_GROWTH_ID = "ecosystem_natural_growth";
	private static final String CHUNK_PROCESSOR_EROSION_ID = "ecosystem_natural_erosion";

	private static final String FIELD_GROUND_BLOCKS = "ground-blocks";
	private static final String FIELD_LEVEL_ID = "level-id";
	private static final String FIELD_BLOCK_POS = "block-pos";
	private static final String FIELD_MODE = "mode";
	private static final String FIELD_REQUIRED_GROWTH_TICKS = "required-growth-ticks";
	private static final String FIELD_PROGRESS_GROWTH_TICKS = "progress-growth-ticks";
	private static final String FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME = "last-processed-absolute-day-time";
	private static final String FIELD_TREE_CANDIDATES = "tree-candidates";
	private static final String FIELD_GRASS_CANDIDATES = "grass-candidates";
	private static final String FIELD_FOLIAGE_CANDIDATES = "foliage-candidates";
	private static final String FIELD_TREE_DECAY_CANDIDATES = "tree-decay-candidates";
	private static final String FIELD_CHUNK_X = "chunk-x";
	private static final String FIELD_CHUNK_Z = "chunk-z";
	private static final String FIELD_TREE_GROUND_POS = "tree-ground-pos";
	private static final String FIELD_GRASS_GROUND_POS = "grass-ground-pos";
	private static final String FIELD_FOLIAGE_GROUND_POS = "foliage-ground-pos";
	private static final String FIELD_TREE_DECAY_TARGET_POS = "tree-decay-target-pos";
	private static final String FIELD_TREE_TYPE = "tree-type";
	private static final String FIELD_FOLIAGE_TYPE = "foliage-type";
	private static final String FIELD_INITIAL_SEASON_ID = "initial-season-id";
	private static final String FIELD_EROSION_RULE_ID = "erosion-rule-id";
	private static final String BLOCK_ID_SHORT_GRASS = "minecraft:short_grass";
	private static final String BLOCK_ID_TALL_GRASS = "minecraft:tall_grass";
	private static final String BLOCK_ID_BUSH = "minecraft:bush";
	private static final String BLOCK_ID_WILDFLOWERS = "minecraft:wildflowers";
	private static final String BLOCK_ID_PINK_PETALS = "minecraft:pink_petals";
	private static final String BLOCK_ID_LEAF_LITTER = "minecraft:leaf_litter";
	private static final String FOLIAGE_TYPE_WILDFLOWERS = MadokuEcosystemConfig.FIELD_FOLIAGE_WILDFLOWERS;
	private static final String FOLIAGE_TYPE_PINK_PETALS = MadokuEcosystemConfig.FIELD_FOLIAGE_PINK_PETALS;
	private static final int MAX_GRASS_CANDIDATES_PER_CHUNK = 3;
	private static final int MAX_FOLIAGE_CANDIDATES_PER_CHUNK = 5;
	private static final int TREE_DECAY_MAX_DROP_DISTANCE = 16;
	private static final double GRASS_BUSH_GROWTH_CHANCE = 0.10d;
	private static final double GRASS_TALL_GROWTH_CHANCE = 0.20d;

	private static final String MODE_WET = "wet";
	private static final String MODE_SURFACE_DIRT = "surface_dirt";
	private static final long ABSOLUTE_TIME_ROLLBACK_RESET_TICKS = 20L;
	private static final Direction[] HORIZONTAL_DIRECTIONS = new Direction[] {
		Direction.NORTH,
		Direction.SOUTH,
		Direction.EAST,
		Direction.WEST
	};
	private static final Set<Block> TRACKABLE_WET_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.ROOTED_DIRT,
		Blocks.DIRT_PATH,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.COARSE_DIRT
	);
	private static final Set<Block> TRACKABLE_TREE_GROUND_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.MUD
	);
	private static final Set<Block> LEAF_LITTER_SUPPORT_BLOCKS = Set.of(
		Blocks.GRASS_BLOCK,
		Blocks.DIRT,
		Blocks.PODZOL,
		Blocks.MYCELIUM,
		Blocks.COARSE_DIRT,
		Blocks.ROOTED_DIRT
	);
	private static final String TREE_TYPE_OAK = "oak";
	private static final String TREE_TYPE_SPRUCE = "spruce";
	private static final String TREE_TYPE_BIRCH = "birch";
	private static final String TREE_TYPE_JUNGLE = "jungle";
	private static final String TREE_TYPE_MANGROVE = "mangrove";
	private static final String TREE_TYPE_ACACIA = "acacia";
	private static final String TREE_TYPE_DARK_OAK = "dark_oak";
	private static final String TREE_TYPE_PALE_OAK = "pale_oak";
	private static final String TREE_TYPE_CHERRY = "cherry";
	private static final String BIOME_TAG_PREFIX = "#";

	private static volatile String ecosystemGrowthProcessSchedulerId = "";
	private static volatile String ecosystemErosionProcessSchedulerId = "";
	private static volatile boolean ecosystemGrowthProcessTaskScheduled = false;
	private static volatile boolean ecosystemErosionProcessTaskScheduled = false;
	private static volatile long lastAutosaveBucket = Long.MIN_VALUE;
	private static volatile boolean dirty = false;
	private static volatile boolean ecosystemEnabled = true;
	private static volatile MadokuEcosystemConfig.Settings settings = MadokuEcosystemConfig.defaults();
	private static volatile List<MadokuEcosystemConfig.NamedErosionRule> cachedErosionRules = List.of();
	private static volatile long lastUnifiedDiscoveryTick = Long.MIN_VALUE;
	private static volatile String lastUnifiedDiscoveryLevelId = "";
	private static volatile int lastUnifiedDiscoveryChunkX = Integer.MIN_VALUE;
	private static volatile int lastUnifiedDiscoveryChunkZ = Integer.MIN_VALUE;

	private static final Map<String, DirtState> dirtBlocksByKey = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, Set<String>> dirtKeysByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, TreeCandidateState> treeCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, List<GrassCandidateState>> grassCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, List<FoliageCandidateState>> foliageCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<ChunkRefKey, List<TreeDecayCandidateState>> treeDecayCandidatesByChunk = new LinkedHashMap<>();

	private static final ChunkManagerSystem.ChunkProcessor NATURAL_GROWTH_CHUNK_PROCESSOR = new ChunkManagerSystem.ChunkProcessor() {
		@Override
		public boolean requiresSurfaceColumns() {
			return false;
		}

		@Override
		public void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, ChunkManagerSystem.ChunkDiscoverySnapshot snapshot) {
			runUnifiedDiscoveryForChunk(level, chunkX, chunkZ, snapshot);
		}

		@Override
		public void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
			if (level == null || !ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ)) {
				return;
			}
			long currentAbsoluteDayTime = resolveAbsoluteDayTime(level);
			processNaturalGrowthInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		}
	};

	private static final ChunkManagerSystem.ChunkProcessor NATURAL_EROSION_CHUNK_PROCESSOR = new ChunkManagerSystem.ChunkProcessor() {
		@Override
		public boolean requiresSurfaceColumns() {
			return true;
		}

		@Override
		public void discoverLoadedChunk(ServerLevel level, int chunkX, int chunkZ, ChunkManagerSystem.ChunkDiscoverySnapshot snapshot) {
			runUnifiedDiscoveryForChunk(level, chunkX, chunkZ, snapshot);
		}

		@Override
		public void processTrackedChunk(ServerLevel level, int chunkX, int chunkZ) {
			if (level == null || !ChunkManagerSystem.isChunkLoaded(level, chunkX, chunkZ)) {
				return;
			}
			long currentAbsoluteDayTime = resolveAbsoluteDayTime(level);
			processNaturalErosionInChunk(level, chunkX, chunkZ, currentAbsoluteDayTime);
		}
	};

	private enum EcosystemTaskSlot {
		GROWTH_PROCESS(ECOSYSTEM_GROWTH_PROCESS_SCHEDULER_OWNER_ID, TASK_TYPE_ECOSYSTEM_GROWTH_PROCESS_TICK),
		EROSION_PROCESS(ECOSYSTEM_EROSION_PROCESS_SCHEDULER_OWNER_ID, TASK_TYPE_ECOSYSTEM_EROSION_PROCESS_TICK);

		private final String schedulerOwnerId;
		private final String taskType;

		EcosystemTaskSlot(String schedulerOwnerId, String taskType) {
			this.schedulerOwnerId = schedulerOwnerId;
			this.taskType = taskType;
		}
	}

	private MadokuEcosystem() {
	}

	public static void initialize() {
		loadConfig();
		ChunkManagerSystem.registerChunkProcessor(CHUNK_PROCESSOR_GROWTH_ID, NATURAL_GROWTH_CHUNK_PROCESSOR);
		ChunkManagerSystem.registerChunkProcessor(CHUNK_PROCESSOR_EROSION_ID, NATURAL_EROSION_CHUNK_PROCESSOR);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_ECOSYSTEM_GROWTH_PROCESS_TICK, MadokuEcosystem::runNaturalGrowthProcessTask);
		SchedulerManagerSystem.registerTaskHandler(TASK_TYPE_ECOSYSTEM_EROSION_PROCESS_TICK, MadokuEcosystem::runNaturalErosionProcessTask);
	}

	public static void reset() {
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		treeCandidatesByChunk.clear();
		grassCandidatesByChunk.clear();
		foliageCandidatesByChunk.clear();
		treeDecayCandidatesByChunk.clear();
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_GROWTH_ID);
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_EROSION_ID);
		resetEcosystemSchedulerState();
		lastAutosaveBucket = Long.MIN_VALUE;
		dirty = false;
		resetUnifiedDiscoveryState();
	}

	public static boolean isEnabled() {
		return ecosystemEnabled;
	}

	private static boolean isNaturalGrowthEnabled() {
		return isEnabled() && settings.system().naturalGrowthEnabled();
	}

	private static boolean isNaturalErosionEnabled() {
		return isEnabled() && settings.system().naturalErosionEnabled();
	}

	private static void loadConfig() {
		MadokuEcosystemConfig.Settings fallback = MadokuEcosystemConfig.defaults();
		JsonObject naturalGrowthDefaults = MadokuEcosystemConfig.buildNaturalGrowthDefaultsJson();
		JsonObject naturalErosionDefaults = MadokuEcosystemConfig.buildNaturalErosionDefaultsJson();
		try {
			Path rootDirectory = JsonManagerSystem.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path systemFile = resolveJsonFile(rootDirectory, CONFIG_FILE_NAME);
			JsonStaticSystem.ManagedStaticDocument systemDocument = JsonStaticSystem.readManagedDocument(systemFile);
			JsonObject systemGeneral = systemDocument.general();
			JsonObject systemMain = systemDocument.main();
			boolean generalEnabled = getBoolean(systemGeneral, MadokuEcosystemConfig.FIELD_ENABLED, true);
			MadokuEcosystemConfig.SystemSettings systemSettings = MadokuEcosystemConfig.systemFromJson(systemMain);
			systemGeneral.addProperty(MadokuEcosystemConfig.FIELD_ENABLED, generalEnabled);
			JsonStaticSystem.writeManagedDocument(systemFile, MadokuEcosystemConfig.toSystemJson(systemSettings), systemGeneral);

			Path categoryDirectory = rootDirectory.resolve(CONFIG_CATEGORY_FOLDER_NAME);
			Path naturalGrowthFile = resolveJsonFile(categoryDirectory, CONFIG_FILE_NATURAL_GROWTH);
			JsonObject naturalGrowthNormalized = JsonStaticSystem.ensureManagedFile(naturalGrowthFile, naturalGrowthDefaults);
			MadokuEcosystemConfig.NaturalGrowthSettings naturalGrowthSettings = MadokuEcosystemConfig.naturalGrowthFromJson(naturalGrowthNormalized);
			JsonStaticSystem.writeManagedFile(
				naturalGrowthFile,
				MadokuEcosystemConfig.toNaturalGrowthJson(naturalGrowthSettings),
				naturalGrowthDefaults
			);

			Path naturalErosionFile = resolveJsonFile(categoryDirectory, CONFIG_FILE_NATURAL_EROSION);
			JsonObject naturalErosionNormalized = JsonStaticSystem.ensureManagedFile(naturalErosionFile, naturalErosionDefaults);
			MadokuEcosystemConfig.NaturalErosionSettings naturalErosionSettings = MadokuEcosystemConfig.naturalErosionFromJson(naturalErosionNormalized);
			JsonStaticSystem.writeManagedFile(
				naturalErosionFile,
				MadokuEcosystemConfig.toNaturalErosionJson(naturalErosionSettings),
				naturalErosionDefaults
			);

			settings = new MadokuEcosystemConfig.Settings(systemSettings, naturalGrowthSettings, naturalErosionSettings);
			refreshErosionRuleCache();
			ecosystemEnabled = generalEnabled;
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			refreshErosionRuleCache();
			ecosystemEnabled = true;
			LOGGER.error("Failed to load MadokuEcosystem static config; using defaults.", exception);
		}
	}

	private static void refreshErosionRuleCache() {
		List<MadokuEcosystemConfig.NamedErosionRule> rules = MadokuEcosystemConfig.erosionRulesInPriority(settings.naturalErosion());
		if (rules == null || rules.isEmpty()) {
			cachedErosionRules = List.of();
			return;
		}
		List<MadokuEcosystemConfig.NamedErosionRule> normalized = new ArrayList<>(rules.size());
		for (MadokuEcosystemConfig.NamedErosionRule rule : rules) {
			if (rule == null || rule.rule() == null) {
				continue;
			}
			normalized.add(rule);
		}
		cachedErosionRules = normalized.isEmpty() ? List.of() : List.copyOf(normalized);
	}

	public static void onServerStarted(MinecraftServer server) {
		if (server == null) {
			return;
		}
		resetUnifiedDiscoveryState();
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_GROWTH_ID);
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_EROSION_ID);
		if (!isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled())) {
			resetEcosystemSchedulerState();
			return;
		}

		resetEcosystemSchedulerState();
		if (isNaturalGrowthEnabled()) {
			ecosystemGrowthProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_GROWTH_PROCESS_SCHEDULER_OWNER_ID)
			);
			SchedulerManagerSystem.clearQueuedRequests(ecosystemGrowthProcessSchedulerId);
		}
		if (isNaturalErosionEnabled()) {
			ecosystemErosionProcessSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(ECOSYSTEM_EROSION_PROCESS_SCHEDULER_OWNER_ID)
			);
			SchedulerManagerSystem.clearQueuedRequests(ecosystemErosionProcessSchedulerId);
		}
		requestEcosystemProcessing(server, 1L);
	}

	public static void loadPersistedData(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (!isEnabled()) {
			dirtBlocksByKey.clear();
			dirtKeysByChunk.clear();
			treeCandidatesByChunk.clear();
			grassCandidatesByChunk.clear();
			foliageCandidatesByChunk.clear();
			treeDecayCandidatesByChunk.clear();
			ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_GROWTH_ID);
			ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_EROSION_ID);
			dirty = false;
			resetUnifiedDiscoveryState();
			return;
		}

		JsonObject data = DataManagerSystem.loadWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, createDefaultData());
		applyPersistedData(data);
		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		lastAutosaveBucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		dirty = false;
	}

	public static void autosavePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled())) {
			return;
		}

		long autoSaveIntervalTicks = DataManagerSystem.getAutoSaveIntervalTicks(server, DATA_FOLDER_NAME, DATA_FILE_NAME);
		long bucket = Math.floorDiv(MadokuTicks.getGameplayTicks(), autoSaveIntervalTicks);
		if (bucket == lastAutosaveBucket) {
			return;
		}

		lastAutosaveBucket = bucket;
		if (dirty) {
			savePersistedData(server);
		}
	}

	public static void savePersistedData(MinecraftServer server) {
		if (server == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled())) {
			return;
		}

		DataManagerSystem.saveWorldData(server, DATA_FOLDER_NAME, DATA_FILE_NAME, toPersistedData());
		dirty = false;
	}

	public static void trackPlacedDirtBlock(ServerLevel world, BlockPos dirtPos) {
		if (world == null || dirtPos == null || !isEnabled()) {
			return;
		}
		if (trackAndSpreadAtPosition(world, dirtPos)) {
			requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		}
	}

	public static void syncDirtTrackingAroundBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null || !isEnabled()) {
			return;
		}

		boolean changed = false;
		String atKey = dirtKey(world, pos);
		DirtState trackedAt = dirtBlocksByKey.get(atKey);
		if (trackedAt != null
			&& isModeEnabled(trackedAt.mode)
			&& !isCandidateForMode(world, pos, world.getBlockState(pos), trackedAt.mode)) {
			removeDirtStateByKey(atKey);
			changed = true;
			dirty = true;
		}

		BlockPos belowPos = pos.below();
		String belowKey = dirtKey(world, belowPos);
		DirtState trackedBelow = dirtBlocksByKey.get(belowKey);
		if (trackedBelow != null
			&& isModeEnabled(trackedBelow.mode)
			&& !isCandidateForMode(world, belowPos, world.getBlockState(belowPos), trackedBelow.mode)) {
			removeDirtStateByKey(belowKey);
			changed = true;
			dirty = true;
		}

		if (trackAndSpreadAtPosition(world, pos)) {
			changed = true;
		}
		if (trackAndSpreadAtPosition(world, belowPos)) {
			changed = true;
		}

		if (changed) {
			requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
		}
	}

	private static void runNaturalGrowthProcessTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			ecosystemGrowthProcessSchedulerId = context.getSchedulerId();
		}
		ecosystemGrowthProcessTaskScheduled = false;
		if (server == null || !isNaturalGrowthEnabled()) {
			return;
		}
		emitEcosystemSchedulerTickDebug(server, context, EcosystemTaskSlot.GROWTH_PROCESS, "process_start");
		requestEcosystemTask(server, ECOSYSTEM_SCHEDULER_INTERVAL_TICKS, EcosystemTaskSlot.GROWTH_PROCESS);
		ChunkManagerSystem.runChunkProcessorProcessingStep(server, CHUNK_PROCESSOR_GROWTH_ID);
		emitEcosystemSchedulerTickDebug(server, context, EcosystemTaskSlot.GROWTH_PROCESS, "process_end");
	}

	private static void runNaturalErosionProcessTask(MinecraftServer server, SchedulerManagerSystem.TaskContext context, JsonObject payload) {
		if (context != null) {
			ecosystemErosionProcessSchedulerId = context.getSchedulerId();
		}
		ecosystemErosionProcessTaskScheduled = false;
		if (server == null || !isNaturalErosionEnabled()) {
			return;
		}
		emitEcosystemSchedulerTickDebug(server, context, EcosystemTaskSlot.EROSION_PROCESS, "process_start");
		requestEcosystemTask(server, ECOSYSTEM_SCHEDULER_INTERVAL_TICKS, EcosystemTaskSlot.EROSION_PROCESS);
		ChunkManagerSystem.runChunkProcessorProcessingStep(server, CHUNK_PROCESSOR_EROSION_ID);
		emitEcosystemSchedulerTickDebug(server, context, EcosystemTaskSlot.EROSION_PROCESS, "process_end");
	}

	private static void runUnifiedDiscoveryForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		ChunkManagerSystem.ChunkDiscoverySnapshot snapshot
	) {
		if (world == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled())) {
			return;
		}
		long gameplayTick = MadokuTicks.getGameplayTicks();
		String worldLevelId = levelId(world);
		if (lastUnifiedDiscoveryTick == gameplayTick
			&& chunkX == lastUnifiedDiscoveryChunkX
			&& chunkZ == lastUnifiedDiscoveryChunkZ
			&& worldLevelId.equals(lastUnifiedDiscoveryLevelId)) {
			return;
		}
		lastUnifiedDiscoveryTick = gameplayTick;
		lastUnifiedDiscoveryLevelId = worldLevelId;
		lastUnifiedDiscoveryChunkX = chunkX;
		lastUnifiedDiscoveryChunkZ = chunkZ;
		discoverTrackablesInChunk(world, chunkX, chunkZ, snapshot);
	}

	private static void discoverTrackablesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		ChunkManagerSystem.ChunkDiscoverySnapshot snapshot
	) {
		if (world == null || !isEnabled() || (!isNaturalGrowthEnabled() && !isNaturalErosionEnabled())) {
			return;
		}
		Set<Long> treeGroundCandidates = isNaturalGrowthEnabled() ? new LinkedHashSet<>() : Set.of();
		Set<Long> grassGroundCandidates = isNaturalGrowthEnabled() ? new LinkedHashSet<>() : Set.of();
		Set<Long> wildflowerGroundCandidates = isNaturalGrowthEnabled() ? new LinkedHashSet<>() : Set.of();
		Set<Long> pinkPetalGroundCandidates = isNaturalGrowthEnabled() ? new LinkedHashSet<>() : Set.of();
		Set<Long> wetSeedPositions = isNaturalErosionEnabled() ? new LinkedHashSet<>() : Set.of();
		Set<Long> treeDecayLeafCandidates = isNaturalErosionEnabled() ? new LinkedHashSet<>() : Set.of();
		if (snapshot == null || (snapshot.motionColumns().isEmpty() && snapshot.surfaceColumns().isEmpty())) {
			return;
		}

		for (ChunkManagerSystem.ColumnSample column : snapshot.motionColumns()) {
			if (column == null) {
				continue;
			}
			for (int depth = 0; depth <= 2; depth++) {
				if (!column.hasDepth(depth)) {
					continue;
				}
				BlockPos pos = BlockPos.of(column.posAtDepth(depth));
				BlockState state = column.stateAtDepth(depth);
				discoverNaturalGrowthAtPosition(world, pos, state);
					discoverNaturalErosionAtPosition(world, pos, state, wetSeedPositions, treeDecayLeafCandidates);
					collectTreeGroundCandidate(world, pos, state, treeGroundCandidates);
					collectGrassGroundCandidate(world, pos, state, grassGroundCandidates);
					collectFoliageGroundCandidate(world, pos, state, FOLIAGE_TYPE_WILDFLOWERS, wildflowerGroundCandidates);
				collectFoliageGroundCandidate(world, pos, state, FOLIAGE_TYPE_PINK_PETALS, pinkPetalGroundCandidates);
			}
		}
		for (ChunkManagerSystem.ColumnSample column : snapshot.surfaceColumns()) {
			if (column == null || !isNaturalErosionEnabled()) {
				continue;
			}
			for (int depth = 0; depth <= 2; depth++) {
				if (!column.hasDepth(depth)) {
					continue;
				}
				BlockPos pos = BlockPos.of(column.posAtDepth(depth));
				BlockState state = column.stateAtDepth(depth);
				collectTreeDecayLeafCandidate(world, pos, state, treeDecayLeafCandidates);
			}
		}

		if (isNaturalErosionEnabled() && !wetSeedPositions.isEmpty()) {
			spreadWetTrackingFromSeeds(world, wetSeedPositions);
		}
			if (isNaturalErosionEnabled()) {
				pickTreeDecayCandidateForChunk(world, chunkX, chunkZ, treeDecayLeafCandidates);
			}
			if (isNaturalGrowthEnabled()) {
				ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
				emitTreeChunkDiscoveryDebug(world, chunkKey, treeGroundCandidates.size(), wetSeedPositions.size());
			pickTreeCandidateForChunk(world, chunkX, chunkZ, treeGroundCandidates);
			pickGrassCandidateForChunk(world, chunkX, chunkZ, grassGroundCandidates);
				pickFoliageCandidateForChunk(world, chunkX, chunkZ, FOLIAGE_TYPE_WILDFLOWERS, wildflowerGroundCandidates);
				pickFoliageCandidateForChunk(world, chunkX, chunkZ, FOLIAGE_TYPE_PINK_PETALS, pinkPetalGroundCandidates);
			}
		}

	private static void processNaturalGrowthInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		processDirtInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime, MODE_SURFACE_DIRT);
		processTreeCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
		processGrassCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
		processFoliageCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
	}

	private static void processNaturalErosionInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		processDirtInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime, MODE_WET);
		processTreeDecayCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
	}

	private static void processDirtInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		long currentAbsoluteDayTime,
		String targetMode
	) {
		if (!isEnabled() || !isModeEnabled(targetMode)) {
			return;
		}
		String worldLevelId = levelId(world);
		ChunkRefKey targetChunkKey = new ChunkRefKey(worldLevelId, chunkX, chunkZ);
		Set<String> chunkDirtKeys = new LinkedHashSet<>(dirtKeysByChunk.getOrDefault(targetChunkKey, Set.of()));
		if (chunkDirtKeys.isEmpty()) {
			return;
		}

		List<String> removeKeys = new ArrayList<>();
		for (String dirtEntryKey : chunkDirtKeys) {
			DirtState dirt = dirtBlocksByKey.get(dirtEntryKey);
			if (dirt == null || !dirt.levelId.equals(worldLevelId)) {
				continue;
			}

			BlockPos dirtPos = BlockPos.of(dirt.dirtPos);
			if (!targetChunkKey.equals(chunkRefForPos(worldLevelId, dirt.dirtPos))) {
				continue;
			}

			BlockState state = world.getBlockState(dirtPos);
			if (!isTrackableGroundBlock(state)) {
				removeKeys.add(dirtEntryKey);
				continue;
			}

			if (!targetMode.equals(dirt.mode)) {
				continue;
			}
			if (!isCandidateForMode(world, dirtPos, state, dirt.mode)) {
				removeKeys.add(dirtEntryKey);
				continue;
			}

			double requiredTicks = Math.max(1.0d, dirt.requiredGrowthTicks);

			long previousAbsolute = normalizePreviousAbsoluteTick(dirt.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(requiredTicks, dirt.progressGrowthTicks + elapsedTicks);
				if (updatedProgress > dirt.progressGrowthTicks) {
					dirt.progressGrowthTicks = updatedProgress;
					dirty = true;
				}
			}
			dirt.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (dirt.progressGrowthTicks + 1e-6d >= requiredTicks) {
				Block replacement = MODE_SURFACE_DIRT.equals(dirt.mode)
					? resolveSurfaceDirtGrowthBlock(world, dirtPos)
					: resolveWetGroundReplacementBlock(world, dirtPos, state, dirt.erosionRuleId);
				if (replacement != null && replacement != state.getBlock()) {
					world.setBlockAndUpdate(dirtPos, replacement.defaultBlockState());
				}
				removeKeys.add(dirtEntryKey);
			}
		}

		for (String key : removeKeys) {
			if (removeDirtStateByKey(key) != null) {
				dirty = true;
			}
		}
	}

	private static void processTreeCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled() || !isNaturalGrowthEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		TreeCandidateState candidate = treeCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		if (!candidate.levelId.equals(levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeCandidateInvalidatedDebug(world, chunkKey, candidate, "chunk_mismatch");
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidTreeGroundCandidate(world, groundPos, groundState, candidate.treeType)) {
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeCandidateInvalidatedDebug(world, chunkKey, candidate, "ground_invalid");
			return;
		}

		long previousAbsolute = normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
		long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
		long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
		if (elapsedTicks > 0L) {
			double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
			if (updatedProgress > candidate.progressGrowthTicks) {
				candidate.progressGrowthTicks = updatedProgress;
				dirty = true;
				emitTreeCandidateProgressDebug(world, chunkKey, candidate, elapsedTicks);
			}
		}
		candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

		if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
			boolean grown = tryGrowTreeAtGround(world, groundPos, candidate.treeType);
			removeTreeCandidate(chunkKey);
			dirty = true;
			emitTreeGrowthResultDebug(world, chunkKey, candidate, grown);
			if (grown) {
				requestEcosystemProcessing(world.getServer(), ECOSYSTEM_SCHEDULER_INTERVAL_TICKS);
			}
		}
	}

	private static void processGrassCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled() || !isNaturalGrowthEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<GrassCandidateState> candidates = grassCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			GrassCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			if (!candidate.levelId.equals(levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);
			BlockState groundState = world.getBlockState(groundPos);
			if (!isValidGrassGroundCandidate(world, groundPos, groundState)) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			long previousAbsolute = normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
				if (updatedProgress > candidate.progressGrowthTicks) {
					candidate.progressGrowthTicks = updatedProgress;
					dirty = true;
				}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
				tryGrowGrassAtGround(world, groundPos);
				candidates.remove(index);
				removedAny = true;
				dirty = true;
			}
		}

		if (candidates.isEmpty()) {
			grassCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
		}
	}

	private static void processFoliageCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled() || !isNaturalGrowthEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<FoliageCandidateState> candidates = foliageCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			FoliageCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			if (!candidate.levelId.equals(levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);
			BlockState groundState = world.getBlockState(groundPos);
				if (!isValidFoliageGroundCandidate(world, groundPos, groundState, candidate.foliageType)) {
					candidates.remove(index);
					removedAny = true;
					dirty = true;
				continue;
			}

			long previousAbsolute = normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredGrowthTicks, candidate.progressGrowthTicks + elapsedTicks);
				if (updatedProgress > candidate.progressGrowthTicks) {
					candidate.progressGrowthTicks = updatedProgress;
					dirty = true;
				}
			}
			candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

				if (candidate.progressGrowthTicks + 1e-6d >= candidate.requiredGrowthTicks) {
					tryGrowFoliageAtGround(world, groundPos, candidate.foliageType);
					candidates.remove(index);
					removedAny = true;
					dirty = true;
			}
		}

		if (candidates.isEmpty()) {
			foliageCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
		}
	}

	private static void processTreeDecayCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled() || !isNaturalErosionEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		for (int index = candidates.size() - 1; index >= 0; index--) {
			TreeDecayCandidateState candidate = candidates.get(index);
			if (candidate == null) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

			if (!candidate.levelId.equals(levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				candidates.remove(index);
				removedAny = true;
				dirty = true;
				continue;
			}

				BlockPos targetPos = BlockPos.of(candidate.leafPos);
				if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
					candidates.remove(index);
					removedAny = true;
					dirty = true;
				continue;
			}

			long previousAbsolute = normalizePreviousAbsoluteTick(candidate.lastProcessedAbsoluteDayTime, currentAbsoluteDayTime);
			long safeCurrentAbsolute = Math.max(previousAbsolute, currentAbsoluteDayTime);
			long elapsedTicks = safeCurrentAbsolute - previousAbsolute;
			if (elapsedTicks > 0L) {
				double updatedProgress = Math.min(candidate.requiredDecayTicks, candidate.progressDecayTicks + elapsedTicks);
				if (updatedProgress > candidate.progressDecayTicks) {
					candidate.progressDecayTicks = updatedProgress;
					dirty = true;
				}
			}
				candidate.lastProcessedAbsoluteDayTime = safeCurrentAbsolute;

			if (candidate.progressDecayTicks + 1e-6d >= candidate.requiredDecayTicks) {
				boolean applied = tryApplyTreeDecayAtTarget(world, targetPos);
				if (applied) {
					candidates.remove(index);
					removedAny = true;
					dirty = true;
				}
			}
		}

		if (candidates.isEmpty()) {
			treeDecayCandidatesByChunk.remove(chunkKey);
			syncChunkProcessorTracking(chunkKey);
			return;
		}
		if (removedAny) {
			syncChunkProcessorTracking(chunkKey);
		}
	}

	private static boolean trackExposedDirtCandidate(ServerLevel world, BlockPos dirtPos) {
		if (world == null || dirtPos == null) {
			return false;
		}
		BlockState state = world.getBlockState(dirtPos);
		String mode = resolveCandidateMode(world, dirtPos, state);
		return trackDirtCandidateForMode(world, dirtPos, state, mode);
	}

	private static boolean trackDirtCandidateForMode(ServerLevel world, BlockPos dirtPos, BlockState state, String mode) {
		if (world == null || dirtPos == null || state == null || mode == null || mode.isBlank()) {
			return false;
		}
		if (!isModeEnabled(mode)) {
			return false;
		}
		if (!isCandidateForMode(world, dirtPos, state, mode)) {
			return false;
		}

		String key = dirtKey(world, dirtPos);
		DirtState existing = dirtBlocksByKey.get(key);
		if (existing != null && mode.equals(existing.mode)) {
			return false;
		}

		String erosionRuleId = "";
		double requiredGrowthTicks;
		if (MODE_SURFACE_DIRT.equals(mode)) {
			requiredGrowthTicks = resolveSurfaceDirtRequiredGrowthTicks(world);
		} else {
			MadokuEcosystemConfig.NamedErosionRule erosionRule = resolveErosionRule(world, dirtPos, state, "");
			if (erosionRule == null || erosionRule.rule() == null) {
				return false;
			}
			erosionRuleId = erosionRule.ruleId();
			requiredGrowthTicks = randomDaysToTicks(erosionRule.rule().erosionTime());
		}
		double startingProgress = existing != null && mode.equals(existing.mode) ? existing.progressGrowthTicks : 0.0d;

		putDirtState(key, new DirtState(
			levelId(world),
			dirtPos.asLong(),
			mode,
			erosionRuleId,
			requiredGrowthTicks,
			Math.max(0.0d, Math.min(requiredGrowthTicks, startingProgress)),
			resolveAbsoluteDayTime(world)
		));
		dirty = true;
		return true;
	}

	private static boolean trackAndSpreadAtPosition(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}

		BlockState state = world.getBlockState(pos);
		boolean changed = trackExposedDirtCandidate(world, pos);
		if (isNaturalErosionEnabled() && isWetSeedCandidate(world, pos, state)) {
			changed |= spreadWetTrackingFromSeed(world, pos);
		}
		return changed;
	}

	private static void discoverNaturalGrowthAtPosition(ServerLevel world, BlockPos pos, BlockState state) {
		if (world == null || pos == null || state == null || !isNaturalGrowthEnabled()) {
			return;
		}
		if (isNaturalErosionEnabled() && isWetSeedCandidate(world, pos, state)) {
			return;
		}
		trackDirtCandidateForMode(world, pos, state, MODE_SURFACE_DIRT);
	}

	private static void discoverNaturalErosionAtPosition(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		Set<Long> wetSeedPositions,
		Set<Long> treeDecayLeafPositions
	) {
		if (world == null || pos == null || state == null || wetSeedPositions == null || treeDecayLeafPositions == null || !isNaturalErosionEnabled()) {
			return;
		}
		if (isWetSeedCandidate(world, pos, state)) {
			wetSeedPositions.add(pos.asLong());
		}
		collectTreeDecayLeafCandidate(world, pos, state, treeDecayLeafPositions);
	}

	private static void collectTreeDecayLeafCandidate(ServerLevel world, BlockPos pos, BlockState state, Set<Long> outPositions) {
		if (world == null || pos == null || state == null || outPositions == null || !isNaturalErosionEnabled()) {
			return;
		}
		BlockPos targetPos = resolveTreeDecayTargetPos(world, pos, state);
		if (targetPos == null) {
			return;
		}
		outPositions.add(targetPos.asLong());
	}

	private static void collectTreeGroundCandidate(ServerLevel world, BlockPos pos, BlockState state, Set<Long> outPositions) {
		if (world == null || pos == null || state == null || outPositions == null || !isNaturalGrowthEnabled()) {
			return;
		}
		if (!isTrackableTreeGroundBlock(state)) {
			return;
		}
		if (isSubmerged(world, pos)) {
			return;
		}
		outPositions.add(pos.asLong());
	}

	private static void pickTreeCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeGroundCandidates) {
		if (world == null || treeGroundCandidates == null || treeGroundCandidates.isEmpty() || !isNaturalGrowthEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		if (treeCandidatesByChunk.containsKey(chunkKey)) {
			emitTreeCandidateScanDebug(world, chunkKey, normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world)), treeGroundCandidates.size(), 0, "existing_candidate");
			return;
		}

		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		List<TreeCandidateOption> options = new ArrayList<>();
		for (Long packedPos : treeGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			BlockState groundState = world.getBlockState(groundPos);
			for (String treeType : resolveTreeTypesForBiome(world, groundPos)) {
				if (treeType == null || treeType.isBlank()) {
					continue;
				}
				if (!isValidTreeGroundCandidate(world, groundPos, groundState, treeType)) {
					continue;
				}
				double requiredGrowthTicks = resolveTreeRequiredGrowthTicks(treeType, seasonId);
				if (requiredGrowthTicks <= 0.0d) {
					continue;
				}
				options.add(new TreeCandidateOption(groundPos.asLong(), treeType, requiredGrowthTicks));
			}
		}

		if (options.isEmpty()) {
			emitTreeCandidateScanDebug(world, chunkKey, seasonId, treeGroundCandidates.size(), 0, "no_options");
			return;
		}

		emitTreeCandidateScanDebug(world, chunkKey, seasonId, treeGroundCandidates.size(), options.size(), "picked");
		TreeCandidateOption selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));

		TreeCandidateState candidate = new TreeCandidateState(
			levelId(world),
			chunkX,
			chunkZ,
			selected.groundPos(),
			selected.treeType(),
			seasonId,
			selected.requiredGrowthTicks(),
			0.0d,
			resolveAbsoluteDayTime(world)
		);
		putTreeCandidate(chunkKey, candidate);
		dirty = true;
		emitTreeCandidatePickedDebug(world, chunkKey, candidate, options.size());
	}

	private static void collectGrassGroundCandidate(ServerLevel world, BlockPos pos, BlockState state, Set<Long> outPositions) {
		if (world == null || pos == null || state == null || outPositions == null || !isNaturalGrowthEnabled()) {
			return;
		}
		if (!isValidGrassGroundCandidate(world, pos, state)) {
			return;
		}
		outPositions.add(pos.asLong());
	}

	private static void pickGrassCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> grassGroundCandidates) {
		if (world == null || grassGroundCandidates == null || grassGroundCandidates.isEmpty() || !isNaturalGrowthEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<GrassCandidateState> existingCandidates = grassCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, MAX_GRASS_CANDIDATES_PER_CHUNK - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : grassGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidGrassGroundCandidate(world, groundPos, world.getBlockState(groundPos))) {
				boolean alreadyTracked = false;
				if (existingCandidates != null) {
					for (GrassCandidateState existing : existingCandidates) {
						if (existing != null && existing.groundPos == packedPos.longValue()) {
							alreadyTracked = true;
							break;
						}
					}
				}
				if (alreadyTracked) {
					continue;
				}
				options.add(packedPos);
			}
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveGrassRequiredGrowthTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			GrassCandidateState candidate = new GrassCandidateState(
				levelId(world),
				chunkX,
				chunkZ,
				selectedGroundPos,
				seasonId,
				requiredGrowthTicks,
				0.0d,
				resolveAbsoluteDayTime(world)
			);
			putGrassCandidate(chunkKey, candidate);
			dirty = true;
		}
	}

	private static void collectFoliageGroundCandidate(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String foliageType,
		Set<Long> outPositions
	) {
		if (world == null || pos == null || state == null || outPositions == null || !isNaturalGrowthEnabled()) {
			return;
		}
		if (!isValidFoliageGroundCandidate(world, pos, state, foliageType)) {
			return;
		}
		outPositions.add(pos.asLong());
	}

	private static void pickFoliageCandidateForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		String foliageType,
		Set<Long> foliageGroundCandidates
	) {
		if (world == null || foliageGroundCandidates == null || foliageGroundCandidates.isEmpty() || !isNaturalGrowthEnabled()) {
			return;
		}

		String normalizedFoliageType = normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<FoliageCandidateState> existingCandidates = foliageCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, MAX_FOLIAGE_CANDIDATES_PER_CHUNK - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : foliageGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidFoliageGroundCandidate(world, groundPos, world.getBlockState(groundPos), normalizedFoliageType)) {
				boolean alreadyTracked = false;
				if (existingCandidates != null) {
					for (FoliageCandidateState existing : existingCandidates) {
						if (existing != null
							&& existing.groundPos == packedPos.longValue()
							&& normalizeFoliageType(existing.foliageType).equals(normalizedFoliageType)) {
							alreadyTracked = true;
							break;
						}
					}
				}
				if (alreadyTracked) {
					continue;
				}
				options.add(packedPos);
			}
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveFoliageRequiredGrowthTicks(world, normalizedFoliageType, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			FoliageCandidateState candidate = new FoliageCandidateState(
				levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					normalizedFoliageType,
					seasonId,
					requiredGrowthTicks,
					0.0d,
				resolveAbsoluteDayTime(world)
			);
			putFoliageCandidate(chunkKey, candidate);
			dirty = true;
		}
	}

	private static boolean isTrackableGroundBlock(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		if (block == Blocks.DIRT) {
			return true;
		}
		if (TRACKABLE_WET_GROUND_BLOCKS.contains(block)) {
			return true;
		}
		String blockId = blockId(block);
		for (MadokuEcosystemConfig.NamedErosionRule rule : cachedErosionRules) {
			if (!rule.rule().enabled()) {
				continue;
			}
			if (rule.rule().sourceBlocks().contains(blockId)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTrackableTreeGroundBlock(BlockState state) {
		return state != null && TRACKABLE_TREE_GROUND_BLOCKS.contains(state.getBlock());
	}

	private static String resolveCandidateMode(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return "";
		}
		if (isNaturalErosionEnabled() && isWetSeedCandidate(world, blockPos, state)) {
			return MODE_WET;
		}
		if (isNaturalGrowthEnabled() && isSurfaceDirtCandidate(world, blockPos, state)) {
			return MODE_SURFACE_DIRT;
		}
		return "";
	}

	private static boolean isCandidateForMode(ServerLevel world, BlockPos blockPos, BlockState state, String mode) {
		if (!isModeEnabled(mode)) {
			return false;
		}
		if (MODE_WET.equals(mode)) {
			return isWetTrackedCandidate(world, blockPos, state);
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isSurfaceDirtCandidate(world, blockPos, state);
		}
		return false;
	}

	private static boolean isModeEnabled(String mode) {
		if (MODE_WET.equals(mode)) {
			return isNaturalErosionEnabled();
		}
		if (MODE_SURFACE_DIRT.equals(mode)) {
			return isNaturalGrowthEnabled();
		}
		return false;
	}

	private static boolean isWetSeedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		return isAdjacentToSurfaceWater(world, blockPos);
	}

	private static boolean isWetTrackedCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || !isTrackableGroundBlock(state)) {
			return false;
		}
		if (resolveErosionRule(world, blockPos, state, "") == null) {
			return false;
		}
		return !isSubmerged(world, blockPos);
	}

	private static boolean isSurfaceDirtCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		if (world == null || blockPos == null || state == null || state.getBlock() != Blocks.DIRT) {
			return false;
		}
		if (isSubmerged(world, blockPos)) {
			return false;
		}
		BlockState aboveState = world.getBlockState(blockPos.above());
		return aboveState != null && aboveState.isAir();
	}

	private static List<String> resolveTreeTypesForBiome(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null) {
			return List.of();
		}

		List<String> treeTypes = new ArrayList<>(2);
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(groundPos);
		if (isSpruceBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_SPRUCE);
		}
		if (isBirchBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_BIRCH);
		}
		if (isJungleBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_JUNGLE);
		}
		if (isMangroveBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_MANGROVE);
		}
		if (isAcaciaBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_ACACIA);
		}
		if (isDarkOakBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_DARK_OAK);
		}
		if (isPaleOakBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_PALE_OAK);
		}
		if (isCherryBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_CHERRY);
		}
		if (isOakBiome(biomeHolder)) {
			treeTypes.add(TREE_TYPE_OAK);
		}
		return treeTypes;
	}

	private static double resolveTreeRequiredGrowthTicks(String treeType, String seasonId) {
		MadokuEcosystemConfig.DayRange range = settings.naturalGrowth().treeGrowthForSeason(treeType, seasonId);
		return randomDaysToTicks(range);
	}

	private static double randomDaysToTicks(MadokuEcosystemConfig.DayRange range) {
		if (range == null) {
			return -1.0d;
		}
		return randomDaysToTicks(range.minDays(), range.maxDays());
	}

	private static double defaultErosionGrowthTicks() {
		for (MadokuEcosystemConfig.NamedErosionRule rule : cachedErosionRules) {
			if (!rule.rule().enabled()) {
				continue;
			}
			double ticks = randomDaysToTicks(rule.rule().erosionTime());
			if (ticks > 0.0d) {
				return ticks;
			}
		}
		return 7.0d * MadokuTime.MINECRAFT_TICKS_PER_CYCLE;
	}

	private static double randomDaysToTicks(int minDays, int maxDays) {
		if (minDays <= 0 || maxDays <= 0 || maxDays < minDays) {
			return -1.0d;
		}
		int days = ThreadLocalRandom.current().nextInt(minDays, maxDays + 1);
		return Math.max(1.0d, days * MadokuTime.MINECRAFT_TICKS_PER_CYCLE);
	}

	private static boolean isValidTreeGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String treeType) {
		if (world == null || groundPos == null || groundState == null || treeType == null || treeType.isBlank()) {
			return false;
		}
		if (!isTrackableTreeGroundBlock(groundState)) {
			return false;
		}
		if (isSubmerged(world, groundPos)) {
			return false;
		}

		BlockPos saplingPos = groundPos.above();
		BlockState aboveState = world.getBlockState(saplingPos);
		boolean aboveFree = aboveState.isAir() || aboveState.is(Blocks.SNOW);
		if (!aboveFree) {
			return false;
		}
		return isTreeTypeNaturalForBiome(world, groundPos, treeType);
	}

	private static boolean isTreeTypeNaturalForBiome(ServerLevel world, BlockPos pos, String treeType) {
		if (world == null || pos == null || treeType == null || treeType.isBlank()) {
			return false;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (TREE_TYPE_SPRUCE.equals(treeType)) {
			return isSpruceBiome(biomeHolder);
		}
		if (TREE_TYPE_BIRCH.equals(treeType)) {
			return isBirchBiome(biomeHolder);
		}
		if (TREE_TYPE_JUNGLE.equals(treeType)) {
			return isJungleBiome(biomeHolder);
		}
		if (TREE_TYPE_MANGROVE.equals(treeType)) {
			return isMangroveBiome(biomeHolder);
		}
		if (TREE_TYPE_ACACIA.equals(treeType)) {
			return isAcaciaBiome(biomeHolder);
		}
		if (TREE_TYPE_DARK_OAK.equals(treeType)) {
			return isDarkOakBiome(biomeHolder);
		}
		if (TREE_TYPE_PALE_OAK.equals(treeType)) {
			return isPaleOakBiome(biomeHolder);
		}
		if (TREE_TYPE_CHERRY.equals(treeType)) {
			return isCherryBiome(biomeHolder);
		}
		if (TREE_TYPE_OAK.equals(treeType)) {
			return isOakBiome(biomeHolder);
		}
		return false;
	}

	private static boolean isJungleBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.JUNGLE)
			|| biomeHolder.is(Biomes.SPARSE_JUNGLE)
			|| biomeHolder.is(Biomes.BAMBOO_JUNGLE);
	}

	private static boolean isMangroveBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.SWAMP);
	}

	private static boolean isAcaciaBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.SAVANNA)
			|| biomeHolder.is(Biomes.SAVANNA_PLATEAU)
			|| biomeHolder.is(Biomes.WINDSWEPT_SAVANNA);
	}

	private static boolean isDarkOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.DARK_FOREST);
	}

	private static boolean isPaleOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.PALE_GARDEN);
	}

	private static boolean isCherryBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.CHERRY_GROVE);
	}

	private static boolean isBirchBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.DARK_FOREST)
			|| biomeHolder.is(Biomes.FOREST)
			|| biomeHolder.is(Biomes.BIRCH_FOREST)
			|| biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
			|| biomeHolder.is(Biomes.MEADOW);
	}

	private static boolean isOakBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.BAMBOO_JUNGLE)
			|| biomeHolder.is(Biomes.DARK_FOREST)
			|| biomeHolder.is(Biomes.FOREST)
			|| biomeHolder.is(Biomes.JUNGLE)
			|| biomeHolder.is(Biomes.SPARSE_JUNGLE)
			|| biomeHolder.is(Biomes.PLAINS)
			|| biomeHolder.is(Biomes.RIVER)
			|| biomeHolder.is(Biomes.SAVANNA)
			|| biomeHolder.is(Biomes.SWAMP)
			|| biomeHolder.is(Biomes.WOODED_BADLANDS)
			|| biomeHolder.is(Biomes.WINDSWEPT_FOREST)
			|| biomeHolder.is(Biomes.MEADOW);
	}

	private static boolean isSpruceBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		return biomeHolder.is(Biomes.GROVE)
			|| biomeHolder.is(Biomes.WINDSWEPT_FOREST)
			|| biomeHolder.is(Biomes.TAIGA)
			|| biomeHolder.is(Biomes.SNOWY_PLAINS)
			|| biomeHolder.is(Biomes.SNOWY_TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_PINE_TAIGA)
			|| biomeHolder.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA);
	}

	private static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		if (world == null || groundPos == null || treeType == null || treeType.isBlank()) {
			return false;
		}
		BlockPos treePos = groundPos.above();
		BlockState aboveState = world.getBlockState(treePos);
		if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW)) {
			return false;
		}

		BlockState replacedState = aboveState;
		if (aboveState.is(Blocks.SNOW)) {
			world.setBlockAndUpdate(treePos, Blocks.AIR.defaultBlockState());
		}

		ResourceKey<ConfiguredFeature<?, ?>> featureKey = treeFeatureKeyForType(treeType);
		if (featureKey == null) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				world.setBlockAndUpdate(treePos, replacedState);
			}
			return false;
		}

		HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
		java.util.Optional<Holder.Reference<ConfiguredFeature<?, ?>>> featureHolder = configuredFeatures.get(featureKey);
		if (featureHolder.isEmpty()) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				world.setBlockAndUpdate(treePos, replacedState);
			}
			return false;
		}

		boolean placed = featureHolder.get().value().place(world, world.getChunkSource().getGenerator(), world.getRandom(), treePos);
		if (!placed && replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
			world.setBlockAndUpdate(treePos, replacedState);
		}
		return placed;
	}

	private static ResourceKey<ConfiguredFeature<?, ?>> treeFeatureKeyForType(String treeType) {
		if (TREE_TYPE_BIRCH.equals(treeType)) {
			return TreeFeatures.BIRCH;
		}
		if (TREE_TYPE_JUNGLE.equals(treeType)) {
			return TreeFeatures.JUNGLE_TREE;
		}
		if (TREE_TYPE_MANGROVE.equals(treeType)) {
			return TreeFeatures.MANGROVE;
		}
		if (TREE_TYPE_ACACIA.equals(treeType)) {
			return TreeFeatures.ACACIA;
		}
		if (TREE_TYPE_DARK_OAK.equals(treeType)) {
			return TreeFeatures.DARK_OAK;
		}
		if (TREE_TYPE_PALE_OAK.equals(treeType)) {
			return TreeFeatures.PALE_OAK;
		}
		if (TREE_TYPE_CHERRY.equals(treeType)) {
			return TreeFeatures.CHERRY;
		}
		if (TREE_TYPE_SPRUCE.equals(treeType)) {
			return TreeFeatures.SPRUCE;
		}
		return TreeFeatures.OAK;
	}

	private static boolean isSubmerged(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return false;
		}
		return world.getFluidState(pos).is(FluidTags.WATER) || world.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	private static boolean isAdjacentToSurfaceWater(ServerLevel world, BlockPos blockPos) {
		if (world == null || blockPos == null) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (direction == null) {
				continue;
			}
			if (isSurfaceLevelWater(world, blockPos.relative(direction))) {
				return true;
			}
		}
		return false;
	}

	private static boolean spreadWetTrackingFromSeed(ServerLevel world, BlockPos seedPos) {
		if (world == null || seedPos == null) {
			return false;
		}
		Set<Long> seedPositions = new LinkedHashSet<>();
		seedPositions.add(seedPos.asLong());
		return spreadWetTrackingFromSeeds(world, seedPositions);
	}

	private static boolean spreadWetTrackingFromSeeds(ServerLevel world, Set<Long> seedPositions) {
		if (world == null || seedPositions == null || seedPositions.isEmpty()) {
			return false;
		}

		boolean changed = false;
		Set<Long> visited = new LinkedHashSet<>();
		ArrayDeque<SpreadNode> queue = new ArrayDeque<>();
		for (Long packedPos : seedPositions) {
			if (packedPos == null) {
				continue;
			}
			if (!visited.add(packedPos)) {
				continue;
			}
			queue.addLast(new SpreadNode(BlockPos.of(packedPos), 0));
		}

		while (!queue.isEmpty()) {
			SpreadNode current = queue.removeFirst();
			BlockPos currentPos = current.pos();
			if (currentPos == null) {
				continue;
			}

			BlockState currentState = world.getBlockState(currentPos);
			if (isWetTrackedCandidate(world, currentPos, currentState)) {
				changed |= trackDirtCandidateForMode(world, currentPos, currentState, MODE_WET);
			}
			// Also track one block above so erosion can affect the bank above surface-water level.
			BlockPos abovePos = currentPos.above();
			BlockState aboveState = world.getBlockState(abovePos);
			if (isWetTrackedCandidate(world, abovePos, aboveState)) {
				changed |= trackDirtCandidateForMode(world, abovePos, aboveState, MODE_WET);
			}

			if (current.depth() >= settings.naturalErosion().waterErosionRadius()) {
				continue;
			}

			for (Direction direction : HORIZONTAL_DIRECTIONS) {
				BlockPos nextPos = currentPos.relative(direction);
				long nextPackedPos = nextPos.asLong();
				if (visited.add(nextPackedPos)) {
					queue.addLast(new SpreadNode(nextPos, current.depth() + 1));
				}
			}
		}

		return changed;
	}

	private static boolean isSurfaceLevelWater(ServerLevel world, BlockPos waterPos) {
		if (world == null || waterPos == null) {
			return false;
		}
		if (!world.getFluidState(waterPos).is(FluidTags.WATER)) {
			return false;
		}
		if (world.getFluidState(waterPos.above()).is(FluidTags.WATER)) {
			return false;
		}
		int topY = world.getHeight(Heightmap.Types.WORLD_SURFACE, waterPos.getX(), waterPos.getZ()) - 1;
		return waterPos.getY() >= topY;
	}

	private static void requestEcosystemProcessing(MinecraftServer server, long delayTicks) {
		if (!isEnabled()) {
			return;
		}
		requestEcosystemTask(server, delayTicks, EcosystemTaskSlot.GROWTH_PROCESS);
		requestEcosystemTask(server, delayTicks, EcosystemTaskSlot.EROSION_PROCESS);
	}

	private static void requestEcosystemTask(MinecraftServer server, long delayTicks, EcosystemTaskSlot taskSlot) {
		if (server == null || taskSlot == null || !isEnabled() || !isEcosystemTaskEnabled(taskSlot)) {
			return;
		}
		String schedulerId = schedulerIdForTask(taskSlot);
		boolean queuedBefore = isEcosystemTaskQueued(schedulerId, taskSlot.taskType);
		if (isEcosystemTaskScheduled(taskSlot) && queuedBefore) {
			emitEcosystemScheduleRequestDebug(taskSlot, delayTicks, schedulerId, queuedBefore, "already_queued");
			return;
		}
		setEcosystemTaskScheduled(taskSlot, false);

		schedulerId = ensureEcosystemSchedulerExists(taskSlot);
		SchedulerManagerSystem.EnqueueStatus firstStatus = enqueueEcosystemTask(schedulerId, delayTicks, taskSlot.taskType);
		if (isAcceptedEnqueueStatus(firstStatus)) {
			setEcosystemTaskScheduled(taskSlot, true);
			emitEcosystemScheduleRequestDebug(taskSlot, delayTicks, schedulerId, queuedBefore, firstStatus.name().toLowerCase());
			return;
		}

		String refreshedSchedulerId = SchedulerManagerSystem.createOrGetScheduler(
			SchedulerManagerSystem.SchedulerBinding.global(taskSlot.schedulerOwnerId)
		);
		setSchedulerIdForTask(taskSlot, refreshedSchedulerId);
		SchedulerManagerSystem.EnqueueStatus secondStatus = enqueueEcosystemTask(refreshedSchedulerId, delayTicks, taskSlot.taskType);
		if (isAcceptedEnqueueStatus(secondStatus)) {
			setEcosystemTaskScheduled(taskSlot, true);
			emitEcosystemScheduleRequestDebug(taskSlot, delayTicks, refreshedSchedulerId, queuedBefore, secondStatus.name().toLowerCase());
			return;
		}
		emitEcosystemScheduleRequestDebug(taskSlot, delayTicks, refreshedSchedulerId, queuedBefore, secondStatus.name().toLowerCase());
	}

	private static boolean isEcosystemTaskQueued(String schedulerIdInput, String taskType) {
		String schedulerId = schedulerIdInput == null ? "" : schedulerIdInput.trim();
		if (schedulerId.isEmpty()) {
			return false;
		}
		return SchedulerManagerSystem.hasQueuedTask(schedulerId, taskType);
	}

	private static String ensureEcosystemSchedulerExists(EcosystemTaskSlot taskSlot) {
		String schedulerId = schedulerIdForTask(taskSlot);
		if (schedulerId == null || schedulerId.isBlank()) {
			schedulerId = SchedulerManagerSystem.createOrGetScheduler(
				SchedulerManagerSystem.SchedulerBinding.global(taskSlot.schedulerOwnerId)
			);
			setSchedulerIdForTask(taskSlot, schedulerId);
		}
		return schedulerId;
	}

	private static boolean isEcosystemTaskEnabled(EcosystemTaskSlot taskSlot) {
		return switch (taskSlot) {
			case GROWTH_PROCESS -> isNaturalGrowthEnabled();
			case EROSION_PROCESS -> isNaturalErosionEnabled();
		};
	}

	private static String schedulerIdForTask(EcosystemTaskSlot taskSlot) {
		return switch (taskSlot) {
			case GROWTH_PROCESS -> ecosystemGrowthProcessSchedulerId;
			case EROSION_PROCESS -> ecosystemErosionProcessSchedulerId;
		};
	}

	private static void setSchedulerIdForTask(EcosystemTaskSlot taskSlot, String schedulerId) {
		String normalized = schedulerId == null ? "" : schedulerId;
		switch (taskSlot) {
			case GROWTH_PROCESS -> ecosystemGrowthProcessSchedulerId = normalized;
			case EROSION_PROCESS -> ecosystemErosionProcessSchedulerId = normalized;
		}
	}

	private static boolean isEcosystemTaskScheduled(EcosystemTaskSlot taskSlot) {
		return switch (taskSlot) {
			case GROWTH_PROCESS -> ecosystemGrowthProcessTaskScheduled;
			case EROSION_PROCESS -> ecosystemErosionProcessTaskScheduled;
		};
	}

	private static void setEcosystemTaskScheduled(EcosystemTaskSlot taskSlot, boolean scheduled) {
		switch (taskSlot) {
			case GROWTH_PROCESS -> ecosystemGrowthProcessTaskScheduled = scheduled;
			case EROSION_PROCESS -> ecosystemErosionProcessTaskScheduled = scheduled;
		}
	}

	private static void resetEcosystemSchedulerState() {
		ecosystemGrowthProcessSchedulerId = "";
		ecosystemErosionProcessSchedulerId = "";
		ecosystemGrowthProcessTaskScheduled = false;
		ecosystemErosionProcessTaskScheduled = false;
	}

	private static void resetUnifiedDiscoveryState() {
		lastUnifiedDiscoveryTick = Long.MIN_VALUE;
		lastUnifiedDiscoveryLevelId = "";
		lastUnifiedDiscoveryChunkX = Integer.MIN_VALUE;
		lastUnifiedDiscoveryChunkZ = Integer.MIN_VALUE;
	}

	private static SchedulerManagerSystem.EnqueueStatus enqueueEcosystemTask(String schedulerId, long delayTicks, String taskType) {
		if (schedulerId == null || schedulerId.isBlank()) {
			return SchedulerManagerSystem.EnqueueStatus.SCHEDULER_NOT_FOUND;
		}

		return SchedulerManagerSystem.enqueue(
			schedulerId,
			Math.max(0L, delayTicks),
			taskType,
			new JsonObject(),
			SchedulerManagerSystem.TickDomain.GAMEPLAY
		);
	}

	private static boolean isAcceptedEnqueueStatus(SchedulerManagerSystem.EnqueueStatus status) {
		return status == SchedulerManagerSystem.EnqueueStatus.ACCEPTED
			|| status == SchedulerManagerSystem.EnqueueStatus.QUEUE_FULL;
	}

	private static void emitEcosystemSchedulerTickDebug(
		MinecraftServer server,
		SchedulerManagerSystem.TaskContext context,
		EcosystemTaskSlot taskSlot,
		String phase
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.scheduler_tick")) {
			return;
		}

		MadokuDebug.event("ecosystem.scheduler_tick", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(server != null && server.overworld() != null ? server.overworld().dimension().toString() : "")
			.subject("ecosystem_scheduler")
			.field("task_type", taskSlot == null ? "" : taskSlot.taskType)
			.field("phase", phase == null || phase.isBlank() ? "unknown" : phase)
			.field("scheduler_id", context == null ? "" : context.getSchedulerId())
			.field("request_id", context == null ? -1L : context.getRequestId())
			.field("growth_process_scheduled", ecosystemGrowthProcessTaskScheduled)
			.field("erosion_process_scheduled", ecosystemErosionProcessTaskScheduled)
			.field("growth_process_queued", isEcosystemTaskQueued(ecosystemGrowthProcessSchedulerId, TASK_TYPE_ECOSYSTEM_GROWTH_PROCESS_TICK))
			.field("erosion_process_queued", isEcosystemTaskQueued(ecosystemErosionProcessSchedulerId, TASK_TYPE_ECOSYSTEM_EROSION_PROCESS_TICK))
			.log();
	}

	private static void emitEcosystemScheduleRequestDebug(
		EcosystemTaskSlot taskSlot,
		long delayTicks,
		String schedulerId,
		boolean queuedBefore,
		String outcome
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.scheduler_enqueue")) {
			return;
		}

		MadokuDebug.event("ecosystem.scheduler_enqueue", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.subject("ecosystem_scheduler")
			.field("task_type", taskSlot == null ? "" : taskSlot.taskType)
			.field("delay_ticks", Math.max(0L, delayTicks))
			.field("scheduler_id", schedulerId == null ? "" : schedulerId)
			.field("queued_before", queuedBefore)
			.field("growth_process_scheduled", ecosystemGrowthProcessTaskScheduled)
			.field("erosion_process_scheduled", ecosystemErosionProcessTaskScheduled)
			.field("outcome", outcome == null || outcome.isBlank() ? "unknown" : outcome)
			.log();
	}

	private static DirtState putDirtState(String key, DirtState value) {
		DirtState previous = dirtBlocksByKey.put(key, value);
		ChunkRefKey previousChunkKey = null;
		if (previous != null) {
			previousChunkKey = chunkRefForPos(previous.levelId, previous.dirtPos);
			removeChunkIndex(dirtKeysByChunk, previousChunkKey, key);
		}
		ChunkRefKey nextChunkKey = null;
		if (value != null) {
			nextChunkKey = chunkRefForPos(value.levelId, value.dirtPos);
			addChunkIndex(dirtKeysByChunk, nextChunkKey, key);
		}
		if (previousChunkKey != null) {
			syncChunkProcessorTracking(previousChunkKey);
		}
		if (nextChunkKey != null) {
			syncChunkProcessorTracking(nextChunkKey);
		}
		return previous;
	}

	private static DirtState removeDirtStateByKey(String key) {
		DirtState removed = dirtBlocksByKey.remove(key);
		if (removed != null) {
			ChunkRefKey chunkKey = chunkRefForPos(removed.levelId, removed.dirtPos);
			removeChunkIndex(dirtKeysByChunk, chunkKey, key);
			syncChunkProcessorTracking(chunkKey);
		}
		return removed;
	}

	private static TreeCandidateState putTreeCandidate(ChunkRefKey chunkKey, TreeCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return null;
		}

		TreeCandidateState previous = treeCandidatesByChunk.put(chunkKey, candidate);
		syncChunkProcessorTracking(chunkKey);
		return previous;
	}

	private static void putGrassCandidate(ChunkRefKey chunkKey, GrassCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}

		List<GrassCandidateState> candidates = grassCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (GrassCandidateState existing : candidates) {
			if (existing != null && existing.groundPos == candidate.groundPos) {
				return;
			}
		}
		if (candidates.size() >= MAX_GRASS_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	private static void putFoliageCandidate(ChunkRefKey chunkKey, FoliageCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}

		List<FoliageCandidateState> candidates = foliageCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (FoliageCandidateState existing : candidates) {
			if (existing != null
				&& existing.groundPos == candidate.groundPos
				&& normalizeFoliageType(existing.foliageType).equals(normalizeFoliageType(candidate.foliageType))) {
				return;
			}
		}
		if (candidates.size() >= MAX_FOLIAGE_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	private static void putTreeDecayCandidate(ChunkRefKey chunkKey, TreeDecayCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}

		List<TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new ArrayList<>());
		for (TreeDecayCandidateState existing : candidates) {
			if (existing != null && existing.leafPos == candidate.leafPos) {
				return;
			}
		}
		candidates.add(candidate);
		syncChunkProcessorTracking(chunkKey);
	}

	private static TreeCandidateState removeTreeCandidate(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		TreeCandidateState removed = treeCandidatesByChunk.remove(chunkKey);
		if (removed == null) {
			return null;
		}

		syncChunkProcessorTracking(chunkKey);
		return removed;
	}

	private static void addChunkIndex(Map<ChunkRefKey, Set<String>> indexMap, ChunkRefKey chunkKey, String entryKey) {
		if (indexMap == null || chunkKey == null || entryKey == null || entryKey.isBlank()) {
			return;
		}
		indexMap.computeIfAbsent(chunkKey, ignored -> new LinkedHashSet<>()).add(entryKey);
	}

	private static void removeChunkIndex(Map<ChunkRefKey, Set<String>> indexMap, ChunkRefKey chunkKey, String entryKey) {
		if (indexMap == null || chunkKey == null || entryKey == null || entryKey.isBlank()) {
			return;
		}
		Set<String> keys = indexMap.get(chunkKey);
		if (keys == null) {
			return;
		}
		keys.remove(entryKey);
		if (!keys.isEmpty()) {
			return;
		}
		indexMap.remove(chunkKey);
	}

	private static void syncChunkProcessorTracking(ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}

		boolean growthTracked = treeCandidatesByChunk.containsKey(chunkKey)
			|| (grassCandidatesByChunk.containsKey(chunkKey) && !grassCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty())
			|| (foliageCandidatesByChunk.containsKey(chunkKey) && !foliageCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty())
			|| chunkHasDirtMode(chunkKey, MODE_SURFACE_DIRT);
		boolean erosionTracked = chunkHasDirtMode(chunkKey, MODE_WET)
			|| (treeDecayCandidatesByChunk.containsKey(chunkKey) && !treeDecayCandidatesByChunk.getOrDefault(chunkKey, List.of()).isEmpty());

		if (growthTracked) {
			ChunkManagerSystem.trackChunkForProcessor(CHUNK_PROCESSOR_GROWTH_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		} else {
			ChunkManagerSystem.untrackChunkForProcessor(CHUNK_PROCESSOR_GROWTH_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}

		if (erosionTracked) {
			ChunkManagerSystem.trackChunkForProcessor(CHUNK_PROCESSOR_EROSION_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		} else {
			ChunkManagerSystem.untrackChunkForProcessor(CHUNK_PROCESSOR_EROSION_ID, chunkKey.levelId(), chunkKey.chunkX(), chunkKey.chunkZ());
		}
	}

	private static boolean chunkHasDirtMode(ChunkRefKey chunkKey, String mode) {
		if (chunkKey == null || mode == null || mode.isBlank()) {
			return false;
		}
		Set<String> keys = dirtKeysByChunk.get(chunkKey);
		if (keys == null || keys.isEmpty()) {
			return false;
		}
		for (String key : keys) {
			DirtState dirt = dirtBlocksByKey.get(key);
			if (dirt != null && mode.equals(dirt.mode)) {
				return true;
			}
		}
		return false;
	}

	private static String dirtKey(ServerLevel world, BlockPos dirtPos) {
		return levelId(world) + "|" + (dirtPos == null ? -1L : dirtPos.asLong());
	}

	private static String levelId(ServerLevel world) {
		return ChunkManagerSystem.normalizeLevelId(world);
	}

	private static ChunkRefKey chunkRefForPos(String levelId, long packedBlockPos) {
		return new ChunkRefKey(levelId, BlockPos.getX(packedBlockPos) >> 4, BlockPos.getZ(packedBlockPos) >> 4);
	}

	private static long resolveAbsoluteDayTime(ServerLevel world) {
		if (world == null) {
			return MadokuTime.getCurrentAbsoluteDayTime();
		}
		return MadokuTime.getCurrentAbsoluteDayTime(world);
	}

	private static long normalizePreviousAbsoluteTick(long previousAbsoluteTick, long currentAbsoluteTick) {
		long safePrevious = Math.max(0L, previousAbsoluteTick);
		long safeCurrent = Math.max(0L, currentAbsoluteTick);
		if (safePrevious > safeCurrent + ABSOLUTE_TIME_ROLLBACK_RESET_TICKS) {
			return safeCurrent;
		}
		return safePrevious;
	}

	private static double resolveSurfaceDirtRequiredGrowthTicks(ServerLevel world) {
		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		MadokuEcosystemConfig.DayRange range = settings.naturalGrowth().dirtGrowthForSeason(seasonId);
		return randomDaysToTicks(range);
	}

	private static double resolveGrassRequiredGrowthTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = normalizeSeasonId(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		}
		MadokuEcosystemConfig.DayRange range = settings.naturalGrowth().grassGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	private static double resolveFoliageRequiredGrowthTicks(ServerLevel world, String foliageType, String seasonId) {
		String normalizedSeasonId = normalizeSeasonId(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		}
		MadokuEcosystemConfig.DayRange range = settings.naturalGrowth().foliageGrowthForSeason(foliageType, normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	private static double resolveTreeDecayRequiredTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = normalizeSeasonId(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		}
		MadokuEcosystemConfig.DayRange range = settings.naturalErosion().treeDecayForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	private static boolean isValidGrassGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		if (world == null || groundPos == null || groundState == null || !isNaturalGrowthEnabled()) {
			return false;
		}
		if (groundState.getBlock() != Blocks.GRASS_BLOCK) {
			return false;
		}
		if (isSubmerged(world, groundPos)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = world.getBlockState(growPos);
		return growState != null && growState.isAir();
	}

	private static boolean isValidFoliageGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String foliageType) {
		if (world == null || groundPos == null || groundState == null || !isNaturalGrowthEnabled()) {
			return false;
		}
		if (isSubmerged(world, groundPos)) {
			return false;
		}
		String normalizedFoliageType = normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank() || !isFoliageBiome(world, groundPos, normalizedFoliageType)) {
			return false;
		}

		BlockPos foliagePos = groundPos.above();
		BlockState foliageState = world.getBlockState(foliagePos);
		if (foliageState == null) {
			return false;
		}
		Block foliageBlock = resolveFoliageBlock(normalizedFoliageType);
		if (foliageBlock == null) {
			return false;
		}
		if (foliageState.isAir()) {
			if (groundState.getBlock() != Blocks.GRASS_BLOCK) {
				return false;
			}
			BlockState placed = setFoliageAmount(foliageBlock.defaultBlockState(), 1);
			return placed.canSurvive(world, foliagePos);
		}

		if (foliageState.getBlock() != foliageBlock) {
			return false;
		}
		int amount = getFoliageAmount(foliageState);
		int maxAmount = getFoliageMaxAmount(foliageState);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = setFoliageAmount(foliageState, amount + 1);
		return updated != foliageState && updated.canSurvive(world, foliagePos);
	}

	private static BlockPos resolveTreeDecayTargetPos(ServerLevel world, BlockPos leafPos, BlockState leafState) {
		if (world == null || leafPos == null || leafState == null || !isNaturalErosionEnabled()) {
			return null;
		}
		if (!leafState.is(BlockTags.LEAVES) || !isNaturallyGeneratedLeaf(leafState)) {
			return null;
		}
		return resolveLeafLitterTargetPos(world, leafPos);
	}

	private static boolean isValidTreeDecayTargetCandidate(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null || !isNaturalErosionEnabled()) {
			return false;
		}
		Block leafLitter = resolveBlock(BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState state = world.getBlockState(targetPos);
		if (state == null) {
			return false;
		}
		if (state.getBlock() == leafLitter) {
			return hasLeafLitterSupportBlock(world, targetPos)
				&& getLeafLitterAmount(state) < getLeafLitterMaxAmount(state);
		}
		if (state.isAir()) {
			BlockState placed = setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			return hasLeafLitterSupportBlock(world, targetPos)
				&& placed.canSurvive(world, targetPos);
		}
		return false;
	}

	private static boolean tryGrowGrassAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}

		Block shortGrass = resolveBlock(BLOCK_ID_SHORT_GRASS);
		Block tallGrass = resolveBlock(BLOCK_ID_TALL_GRASS);
		Block bush = resolveBlock(BLOCK_ID_BUSH);
		double roll = ThreadLocalRandom.current().nextDouble();
		if (roll < GRASS_BUSH_GROWTH_CHANCE) {
			if (bush != null) {
				world.setBlockAndUpdate(growPos, bush.defaultBlockState());
				return true;
			}
		}

		boolean growTallGrass = roll < (GRASS_BUSH_GROWTH_CHANCE + GRASS_TALL_GROWTH_CHANCE);
		if (growTallGrass && tallGrass != null && tryPlaceTallGrass(world, growPos, tallGrass)) {
			return true;
		}
		if (shortGrass != null) {
			world.setBlockAndUpdate(growPos, shortGrass.defaultBlockState());
			return true;
		}
		return false;
	}

	private static boolean tryGrowFoliageAtGround(ServerLevel world, BlockPos groundPos, String foliageType) {
		if (world == null || groundPos == null) {
			return false;
		}

		String normalizedFoliageType = normalizeFoliageType(foliageType);
		Block foliageBlock = resolveFoliageBlock(normalizedFoliageType);
		if (foliageBlock == null) {
			return false;
		}

		BlockPos foliagePos = groundPos.above();
		BlockState current = world.getBlockState(foliagePos);
		if (current == null) {
			return false;
		}
		if (current.isAir()) {
			BlockState placed = setFoliageAmount(foliageBlock.defaultBlockState(), 1);
			world.setBlockAndUpdate(foliagePos, placed);
			return true;
		}
		if (current.getBlock() != foliageBlock) {
			return false;
		}

		int amount = getFoliageAmount(current);
		int maxAmount = getFoliageMaxAmount(current);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = setFoliageAmount(current, amount + 1);
		if (updated == current) {
			return false;
		}
		world.setBlockAndUpdate(foliagePos, updated);
		return true;
	}

	private static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		Block leafLitter = resolveBlock(BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState current = world.getBlockState(targetPos);
		if (current == null) {
			return false;
		}

		if (current.isAir()) {
			BlockState placed = setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			if (!placed.canSurvive(world, targetPos)) {
				return false;
			}
			world.setBlockAndUpdate(targetPos, placed);
			return true;
		}
		if (current.getBlock() != leafLitter) {
			return false;
		}

		int amount = getLeafLitterAmount(current);
		int maxAmount = getLeafLitterMaxAmount(current);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = setLeafLitterAmount(current, amount + 1);
		if (updated == current) {
			return false;
		}
		world.setBlockAndUpdate(targetPos, updated);
		return true;
	}

	private static BlockPos resolveLeafLitterTargetPos(ServerLevel world, BlockPos leafPos) {
		if (world == null || leafPos == null) {
			return null;
		}

		Block leafLitter = resolveBlock(BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return null;
		}
		BlockState singleLitter = setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
		int minY = Math.max(world.getMinY() + 1, leafPos.getY() - TREE_DECAY_MAX_DROP_DISTANCE);

		for (int y = leafPos.getY() - 1; y >= minY; y--) {
			BlockPos pos = new BlockPos(leafPos.getX(), y, leafPos.getZ());
			BlockState state = world.getBlockState(pos);
			if (state == null) {
				continue;
			}

			if (state.is(BlockTags.LEAVES)) {
				continue;
			}

			if (state.getBlock() == leafLitter) {
				return hasLeafLitterSupportBlock(world, pos)
					&& getLeafLitterAmount(state) < getLeafLitterMaxAmount(state)
					? pos
					: null;
			}

			if (!state.isAir()) {
				continue;
			}

			if (hasLeafLitterSupportBlock(world, pos) && singleLitter.canSurvive(world, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean hasLeafLitterSupportBlock(ServerLevel world, BlockPos litterPos) {
		if (world == null || litterPos == null) {
			return false;
		}
		BlockState belowState = world.getBlockState(litterPos.below());
		return belowState != null && LEAF_LITTER_SUPPORT_BLOCKS.contains(belowState.getBlock());
	}

	private static boolean isNaturallyGeneratedLeaf(BlockState state) {
		if (state == null || !state.hasProperty(LeavesBlock.PERSISTENT)) {
			return false;
		}
		Boolean persistent = state.getValue(LeavesBlock.PERSISTENT);
		return persistent == null || !persistent;
	}

	private static boolean isFoliageBiome(ServerLevel world, BlockPos pos, String foliageType) {
		if (world == null || pos == null) {
			return false;
		}
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (FOLIAGE_TYPE_PINK_PETALS.equals(foliageType)) {
			return biomeHolder.is(Biomes.CHERRY_GROVE);
		}
		return biomeHolder.is(Biomes.MEADOW)
			|| biomeHolder.is(Biomes.BIRCH_FOREST)
			|| biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST);
	}

	private static Block resolveFoliageBlock(String foliageType) {
		if (FOLIAGE_TYPE_PINK_PETALS.equals(foliageType)) {
			return resolveBlock(BLOCK_ID_PINK_PETALS);
		}
		if (FOLIAGE_TYPE_WILDFLOWERS.equals(foliageType)) {
			return resolveBlock(BLOCK_ID_WILDFLOWERS);
		}
		return null;
	}

	private static String normalizeFoliageType(String foliageType) {
		String normalized = foliageType == null ? "" : foliageType.trim().toLowerCase();
		if (FOLIAGE_TYPE_PINK_PETALS.equals(normalized)) {
			return FOLIAGE_TYPE_PINK_PETALS;
		}
		if (FOLIAGE_TYPE_WILDFLOWERS.equals(normalized)) {
			return FOLIAGE_TYPE_WILDFLOWERS;
		}
		return "";
	}

	private static int getFoliageAmount(BlockState state) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	private static int getFoliageMaxAmount(BlockState state) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
		if (amountProperty == null) {
			return 4;
		}
		int max = 1;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value != null && value > max) {
				max = value;
			}
		}
		return Math.max(1, max);
	}

	private static IntegerProperty findFoliageAmountProperty(BlockState state) {
		if (state == null) {
			return null;
		}
		IntegerProperty fallback = null;
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (!(property instanceof IntegerProperty integerProperty)) {
				continue;
			}
			if ("flower_amount".equals(integerProperty.getName())) {
				return integerProperty;
			}
			if (fallback == null && propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	private static BlockState setFoliageAmount(BlockState state, int targetAmount) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return state;
		}

		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value == null) {
				continue;
			}
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) {
			return state;
		}

		int clamped = Math.max(min, Math.min(max, targetAmount));
		return state.setValue(amountProperty, clamped);
	}

	private static int getLeafLitterAmount(BlockState state) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	private static int getLeafLitterMaxAmount(BlockState state) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null) {
			return 4;
		}
		int max = 1;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value != null && value > max) {
				max = value;
			}
		}
		return Math.max(1, max);
	}

	private static IntegerProperty findLeafLitterAmountProperty(BlockState state) {
		if (state == null) {
			return null;
		}
		IntegerProperty fallback = null;
		for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
			if (!(property instanceof IntegerProperty integerProperty)) {
				continue;
			}
			if ("segment_amount".equals(integerProperty.getName())) {
				return integerProperty;
			}
			if (fallback == null && propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	private static boolean propertyNameLooksLikeAmount(String propertyName) {
		String normalized = propertyName == null ? "" : propertyName.trim().toLowerCase();
		return normalized.contains("amount") || normalized.contains("segments");
	}

	private static BlockState setLeafLitterAmount(BlockState state, int targetAmount) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return state;
		}

		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (Integer value : amountProperty.getPossibleValues()) {
			if (value == null) {
				continue;
			}
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		if (min == Integer.MAX_VALUE || max == Integer.MIN_VALUE) {
			return state;
		}

		int clamped = Math.max(min, Math.min(max, targetAmount));
		return state.setValue(amountProperty, clamped);
	}

	private static boolean tryPlaceTallGrass(ServerLevel world, BlockPos lowerPos, Block tallGrassBlock) {
		if (world == null || lowerPos == null || tallGrassBlock == null) {
			return false;
		}
		BlockPos upperPos = lowerPos.above();
		if (!world.getBlockState(lowerPos).isAir() || !world.getBlockState(upperPos).isAir()) {
			return false;
		}

		BlockState lower = tallGrassBlock.defaultBlockState();
		if (lower.hasProperty(DoublePlantBlock.HALF)) {
			lower = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
		}
		BlockState upper = tallGrassBlock.defaultBlockState();
		if (upper.hasProperty(DoublePlantBlock.HALF)) {
			upper = upper.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
		}

		world.setBlockAndUpdate(lowerPos, lower);
		world.setBlockAndUpdate(upperPos, upper);
		return true;
	}

	private static String normalizeSeasonId(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().toLowerCase();
	}

	private static void emitTreeCandidatePickedDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		int optionCount
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_picked")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_picked", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("season", candidate == null ? "unknown" : candidate.initialSeasonId)
			.field("ground_pos", formatBlockPos(pos))
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.field("candidate_options", optionCount)
			.log();
	}

	private static void emitTreeChunkDiscoveryDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		int treeGroundCandidateCount,
		int wetSeedCount
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_chunk_discovery")) {
			return;
		}

		MadokuDebug.event("ecosystem.tree_chunk_discovery", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_ground_candidates", Math.max(0, treeGroundCandidateCount))
			.field("wet_seed_positions", Math.max(0, wetSeedCount))
			.log();
	}

	private static void emitTreeCandidateScanDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		String seasonId,
		int treeGroundCandidateCount,
		int optionCount,
		String action
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_scan")) {
			return;
		}

		MadokuDebug.event("ecosystem.tree_candidate_scan", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("season", seasonId == null || seasonId.isBlank() ? "unknown" : seasonId)
			.field("tree_ground_candidates", Math.max(0, treeGroundCandidateCount))
			.field("options", Math.max(0, optionCount))
			.field("action", action == null || action.isBlank() ? "unknown" : action)
			.log();
	}

	private static void emitTreeCandidateProgressDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		long elapsedTicks
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_progress")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_progress", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("ground_pos", formatBlockPos(pos))
			.field("elapsed_ticks", Math.max(0L, elapsedTicks))
			.field("progress_ticks", candidate == null ? 0.0d : candidate.progressGrowthTicks)
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.log();
	}

	private static void emitTreeCandidateInvalidatedDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		String reason
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_candidate_invalidated")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_candidate_invalidated", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("reason", reason == null ? "unknown" : reason)
			.field("ground_pos", formatBlockPos(pos))
			.log();
	}

	private static void emitTreeGrowthResultDebug(
		ServerLevel world,
		ChunkRefKey chunkKey,
		TreeCandidateState candidate,
		boolean grown
	) {
		if (!MadokuDebug.shouldEmit(MadokuDebug.Domain.ECOSYSTEM, "ecosystem.tree_growth_result")) {
			return;
		}

		BlockPos pos = candidate == null ? null : BlockPos.of(candidate.groundPos);
		MadokuDebug.event("ecosystem.tree_growth_result", MadokuDebug.Domain.ECOSYSTEM)
			.side(MadokuDebug.Side.SERVER)
			.tick(MadokuTicks.getGameplayTicks())
			.world(world == null ? "" : world.dimension().toString())
			.subject(chunkKey == null ? "chunk:unknown" : "chunk:" + chunkKey.chunkX() + "," + chunkKey.chunkZ())
			.field("tree_type", candidate == null ? "unknown" : candidate.treeType)
			.field("ground_pos", formatBlockPos(pos))
			.field("season", candidate == null ? "unknown" : candidate.initialSeasonId)
			.field("progress_ticks", candidate == null ? 0.0d : candidate.progressGrowthTicks)
			.field("required_ticks", candidate == null ? 0.0d : candidate.requiredGrowthTicks)
			.field("grown", grown)
			.log();
	}

	private static String formatBlockPos(BlockPos pos) {
		if (pos == null) {
			return "unknown";
		}
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static Block resolveWetGroundReplacementBlock(ServerLevel world, BlockPos pos, BlockState state, String preferredRuleId) {
		MadokuEcosystemConfig.NamedErosionRule rule = resolveErosionRule(world, pos, state, preferredRuleId);
		if (rule == null || rule.rule() == null) {
			return null;
		}
		return resolveBlock(rule.rule().targetBlock());
	}

	private static MadokuEcosystemConfig.NamedErosionRule resolveErosionRule(
		ServerLevel world,
		BlockPos pos,
		BlockState state,
		String preferredRuleId
	) {
		if (world == null || pos == null || state == null || !isNaturalErosionEnabled()) {
			return null;
		}
		String blockId = blockId(state.getBlock());
		if (blockId.isBlank()) {
			return null;
		}

		if (preferredRuleId != null && !preferredRuleId.isBlank()) {
			for (MadokuEcosystemConfig.NamedErosionRule candidate : cachedErosionRules) {
				if (!preferredRuleId.equals(candidate.ruleId())) {
					continue;
				}
				if (matchesErosionRule(world, pos, blockId, candidate.rule())) {
					return candidate;
				}
				break;
			}
		}

		for (MadokuEcosystemConfig.NamedErosionRule candidate : cachedErosionRules) {
			if (matchesErosionRule(world, pos, blockId, candidate.rule())) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean matchesErosionRule(
		ServerLevel world,
		BlockPos pos,
		String sourceBlockId,
		MadokuEcosystemConfig.ErosionRule rule
	) {
		if (world == null || pos == null || sourceBlockId == null || sourceBlockId.isBlank() || rule == null || !rule.enabled()) {
			return false;
		}
		if (!rule.sourceBlocks().contains(sourceBlockId)) {
			return false;
		}
		Block targetBlock = resolveBlock(rule.targetBlock());
		if (targetBlock == null) {
			return false;
		}

		List<String> requiredBiomeIds = rule.requiredBiomeIds();
		List<String> requiredBiomeTags = rule.requiredBiomeTags();
		if ((requiredBiomeIds == null || requiredBiomeIds.isEmpty()) && (requiredBiomeTags == null || requiredBiomeTags.isEmpty())) {
			return true;
		}

		Holder<Biome> biomeHolder = world.getBiome(pos);
		if (requiredBiomeIds != null) {
			for (String biomeId : requiredBiomeIds) {
				Identifier id = Identifier.tryParse(biomeId);
				if (id == null) {
					continue;
				}
				if (biomeHolder.is(ResourceKey.create(Registries.BIOME, id))) {
					return true;
				}
			}
		}
		if (requiredBiomeTags != null) {
			for (String biomeTag : requiredBiomeTags) {
				String normalized = biomeTag == null ? "" : biomeTag.trim();
				if (normalized.isEmpty()) {
					continue;
				}
				if (normalized.startsWith(BIOME_TAG_PREFIX)) {
					normalized = normalized.substring(1);
				}
				Identifier id = Identifier.tryParse(normalized);
				if (id == null) {
					continue;
				}
				if (biomeHolder.is(TagKey.create(Registries.BIOME, id))) {
					return true;
				}
			}
		}
		return false;
	}

	private static Block resolveBlock(String blockId) {
		Identifier id = Identifier.tryParse(blockId == null ? "" : blockId.trim());
		if (id == null) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getValue(id);
	}

	private static String blockId(Block block) {
		if (block == null) {
			return "";
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id == null ? "" : id.toString();
	}

	private static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.GRASS_BLOCK;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = world.getBiome(pos);
		if (biomeHolder.is(Biomes.MUSHROOM_FIELDS)) {
			return Blocks.MYCELIUM;
		}
		if (biomeHolder.is(BiomeTags.IS_TAIGA) || biomeHolder.is(BiomeTags.IS_JUNGLE)) {
			return Blocks.PODZOL;
		}
		return Blocks.GRASS_BLOCK;
	}

	private static void applyPersistedData(JsonObject source) {
		dirtBlocksByKey.clear();
		dirtKeysByChunk.clear();
		treeCandidatesByChunk.clear();
		grassCandidatesByChunk.clear();
		foliageCandidatesByChunk.clear();
		treeDecayCandidatesByChunk.clear();
		resetUnifiedDiscoveryState();
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_GROWTH_ID);
		ChunkManagerSystem.resetChunkProcessor(CHUNK_PROCESSOR_EROSION_ID);

		if (source == null) {
			return;
		}

		JsonElement dirtBlocksElement = source.get(FIELD_GROUND_BLOCKS);
		if (dirtBlocksElement != null && dirtBlocksElement.isJsonArray()) {
			for (JsonElement element : dirtBlocksElement.getAsJsonArray()) {
				DirtState dirt = DirtState.fromJson(element);
				if (dirt == null) {
					continue;
				}
				putDirtState(dirt.key(), dirt);
			}
		}

		JsonElement treeCandidatesElement = source.get(FIELD_TREE_CANDIDATES);
		if (treeCandidatesElement != null && treeCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeCandidatesElement.getAsJsonArray()) {
				TreeCandidateState candidate = TreeCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement grassCandidatesElement = source.get(FIELD_GRASS_CANDIDATES);
		if (grassCandidatesElement != null && grassCandidatesElement.isJsonArray()) {
			for (JsonElement element : grassCandidatesElement.getAsJsonArray()) {
				GrassCandidateState candidate = GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putGrassCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement foliageCandidatesElement = source.get(FIELD_FOLIAGE_CANDIDATES);
		if (foliageCandidatesElement != null && foliageCandidatesElement.isJsonArray()) {
			for (JsonElement element : foliageCandidatesElement.getAsJsonArray()) {
				FoliageCandidateState candidate = FoliageCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putFoliageCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement treeDecayCandidatesElement = source.get(FIELD_TREE_DECAY_CANDIDATES);
		if (treeDecayCandidatesElement != null && treeDecayCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeDecayCandidatesElement.getAsJsonArray()) {
				TreeDecayCandidateState candidate = TreeDecayCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeDecayCandidate(new ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	private static void pickTreeDecayCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeDecayLeafCandidates) {
		if (world == null || treeDecayLeafCandidates == null || treeDecayLeafCandidates.isEmpty() || !isNaturalErosionEnabled()) {
			return;
		}

		ChunkRefKey chunkKey = new ChunkRefKey(levelId(world), chunkX, chunkZ);
		List<TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);

		List<Long> options = new ArrayList<>();
		for (Long packedPos : treeDecayLeafCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos targetPos = BlockPos.of(packedPos);
			if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
				continue;
			}
			boolean alreadyTracked = false;
			if (existingCandidates != null) {
				for (TreeDecayCandidateState existing : existingCandidates) {
					if (existing != null && existing.leafPos == packedPos.longValue()) {
						alreadyTracked = true;
						break;
					}
				}
			}
			if (alreadyTracked) {
				continue;
			}
			options.add(packedPos);
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = normalizeSeasonId(MadokuSeason.getCurrentSeasonId(world));
		double requiredDecayTicks = resolveTreeDecayRequiredTicks(world, seasonId);
		if (requiredDecayTicks <= 0.0d) {
			return;
		}

		int availableSlots = options.size();
		for (int i = 0; i < availableSlots; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedLeafPos = options.remove(selectedIndex);
			TreeDecayCandidateState candidate = new TreeDecayCandidateState(
				levelId(world),
				chunkX,
				chunkZ,
				selectedLeafPos,
				seasonId,
				requiredDecayTicks,
				0.0d,
				resolveAbsoluteDayTime(world)
			);
			putTreeDecayCandidate(chunkKey, candidate);
			dirty = true;
		}
	}

	private static JsonObject createDefaultData() {
		JsonObject root = new JsonObject();
		root.add(FIELD_GROUND_BLOCKS, new JsonArray());
		root.add(FIELD_TREE_CANDIDATES, new JsonArray());
		root.add(FIELD_GRASS_CANDIDATES, new JsonArray());
		root.add(FIELD_FOLIAGE_CANDIDATES, new JsonArray());
		root.add(FIELD_TREE_DECAY_CANDIDATES, new JsonArray());
		return root;
	}

	private static JsonObject toPersistedData() {
		JsonObject root = new JsonObject();
		JsonArray dirtBlocks = new JsonArray();
		for (DirtState dirt : dirtBlocksByKey.values()) {
			if (dirt != null) {
				dirtBlocks.add(dirt.toJson());
			}
		}
		root.add(FIELD_GROUND_BLOCKS, dirtBlocks);

		JsonArray treeCandidates = new JsonArray();
		for (TreeCandidateState candidate : treeCandidatesByChunk.values()) {
			if (candidate != null) {
				treeCandidates.add(candidate.toJson());
			}
		}
		root.add(FIELD_TREE_CANDIDATES, treeCandidates);

		JsonArray grassCandidates = new JsonArray();
		for (List<GrassCandidateState> candidateList : grassCandidatesByChunk.values()) {
			if (candidateList == null || candidateList.isEmpty()) {
				continue;
			}
			for (GrassCandidateState candidate : candidateList) {
				if (candidate != null) {
					grassCandidates.add(candidate.toJson());
				}
			}
		}
		root.add(FIELD_GRASS_CANDIDATES, grassCandidates);

		JsonArray foliageCandidates = new JsonArray();
		for (List<FoliageCandidateState> candidateList : foliageCandidatesByChunk.values()) {
			if (candidateList == null || candidateList.isEmpty()) {
				continue;
			}
			for (FoliageCandidateState candidate : candidateList) {
				if (candidate != null) {
					foliageCandidates.add(candidate.toJson());
				}
			}
		}
		root.add(FIELD_FOLIAGE_CANDIDATES, foliageCandidates);

		JsonArray treeDecayCandidates = new JsonArray();
		for (List<TreeDecayCandidateState> candidateList : treeDecayCandidatesByChunk.values()) {
			if (candidateList == null || candidateList.isEmpty()) {
				continue;
			}
			for (TreeDecayCandidateState candidate : candidateList) {
				if (candidate != null) {
					treeDecayCandidates.add(candidate.toJson());
				}
			}
		}
		root.add(FIELD_TREE_DECAY_CANDIDATES, treeDecayCandidates);
		return root;
	}

	private static Path resolveJsonFile(Path directory, String fileName) {
		if (directory == null) {
			return Path.of(fileName + ".json");
		}
		String normalized = fileName == null ? "" : fileName.trim();
		if (normalized.isEmpty()) {
			normalized = "data";
		}
		if (!normalized.endsWith(".json")) {
			normalized += ".json";
		}
		return directory.resolve(normalized);
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			return element.getAsLong();
		} catch (UnsupportedOperationException | IllegalStateException | NumberFormatException ignored) {
			return fallback;
		}
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (UnsupportedOperationException | IllegalStateException | NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		if (object == null || key == null || key.isBlank()) {
			return fallback;
		}
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull()) {
			return fallback;
		}
		try {
			String value = element.getAsString();
			return value == null ? fallback : value;
		} catch (UnsupportedOperationException | IllegalStateException ignored) {
			return fallback;
		}
	}

	private record ChunkRefKey(String levelId, int chunkX, int chunkZ) {
	}

	private record SpreadNode(BlockPos pos, int depth) {
	}

	private record TreeCandidateOption(long groundPos, String treeType, double requiredGrowthTicks) {
	}

	private static final class GrassCandidateState {
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final long groundPos;
		private final String initialSeasonId;
		private final double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private GrassCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			this.initialSeasonId = normalizeSeasonId(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_CHUNK_X, chunkX);
			root.addProperty(FIELD_CHUNK_Z, chunkZ);
			root.addProperty(FIELD_GRASS_GROUND_POS, groundPos);
			root.addProperty(FIELD_INITIAL_SEASON_ID, initialSeasonId);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static GrassCandidateState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
			long groundPos = getLong(source, FIELD_GRASS_GROUND_POS, Long.MIN_VALUE);
			if (groundPos == Long.MIN_VALUE) {
				return null;
			}
			String initialSeasonId = normalizeSeasonId(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, resolveGrassRequiredGrowthTicks(null, initialSeasonId))
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new GrassCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	private static final class FoliageCandidateState {
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final long groundPos;
		private final String foliageType;
		private final String initialSeasonId;
		private final double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private FoliageCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String foliageType,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			String normalizedFoliageType = normalizeFoliageType(foliageType);
			this.foliageType = normalizedFoliageType.isBlank() ? FOLIAGE_TYPE_WILDFLOWERS : normalizedFoliageType;
			this.initialSeasonId = normalizeSeasonId(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_CHUNK_X, chunkX);
			root.addProperty(FIELD_CHUNK_Z, chunkZ);
			root.addProperty(FIELD_FOLIAGE_GROUND_POS, groundPos);
			root.addProperty(FIELD_FOLIAGE_TYPE, foliageType);
			root.addProperty(FIELD_INITIAL_SEASON_ID, initialSeasonId);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static FoliageCandidateState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
				long groundPos = getLong(source, FIELD_FOLIAGE_GROUND_POS, Long.MIN_VALUE);
				if (groundPos == Long.MIN_VALUE) {
					return null;
				}
				String foliageType = normalizeFoliageType(getString(source, FIELD_FOLIAGE_TYPE, FOLIAGE_TYPE_WILDFLOWERS));
				String initialSeasonId = normalizeSeasonId(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
				double requiredGrowthTicks = getDouble(
					source,
					FIELD_REQUIRED_GROWTH_TICKS,
					Math.max(1.0d, resolveFoliageRequiredGrowthTicks(null, foliageType, initialSeasonId))
				);
				double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
				long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
				return new FoliageCandidateState(
					levelId,
					chunkX,
					chunkZ,
					groundPos,
					foliageType,
					initialSeasonId,
					requiredGrowthTicks,
					progressGrowthTicks,
					lastProcessedAbsoluteDayTime
			);
		}
	}

	private static final class TreeDecayCandidateState {
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final long leafPos;
		private final String initialSeasonId;
		private final double requiredDecayTicks;
		private double progressDecayTicks;
		private long lastProcessedAbsoluteDayTime;

		private TreeDecayCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long leafPos,
			String initialSeasonId,
			double requiredDecayTicks,
			double progressDecayTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.leafPos = leafPos;
			this.initialSeasonId = normalizeSeasonId(initialSeasonId);
			this.requiredDecayTicks = Math.max(1.0d, requiredDecayTicks);
			this.progressDecayTicks = Math.max(0.0d, Math.min(this.requiredDecayTicks, progressDecayTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

			private JsonObject toJson() {
				JsonObject root = new JsonObject();
				root.addProperty(FIELD_LEVEL_ID, levelId);
				root.addProperty(FIELD_CHUNK_X, chunkX);
				root.addProperty(FIELD_CHUNK_Z, chunkZ);
				root.addProperty(FIELD_TREE_DECAY_TARGET_POS, leafPos);
				root.addProperty(FIELD_INITIAL_SEASON_ID, initialSeasonId);
				root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredDecayTicks);
				root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressDecayTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static TreeDecayCandidateState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
			long leafPos = getLong(source, FIELD_TREE_DECAY_TARGET_POS, Long.MIN_VALUE);
			if (leafPos == Long.MIN_VALUE) {
				return null;
			}
			String initialSeasonId = normalizeSeasonId(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredDecayTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, resolveTreeDecayRequiredTicks(null, initialSeasonId))
			);
			double progressDecayTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new TreeDecayCandidateState(
				levelId,
				chunkX,
				chunkZ,
				leafPos,
				initialSeasonId,
				requiredDecayTicks,
				progressDecayTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	private static final class TreeCandidateState {
		private final String levelId;
		private final int chunkX;
		private final int chunkZ;
		private final long groundPos;
		private final String treeType;
		private final String initialSeasonId;
		private final double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private TreeCandidateState(
			String levelId,
			int chunkX,
			int chunkZ,
			long groundPos,
			String treeType,
			String initialSeasonId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.groundPos = groundPos;
			if (TREE_TYPE_SPRUCE.equals(treeType)) {
				this.treeType = TREE_TYPE_SPRUCE;
			} else if (TREE_TYPE_BIRCH.equals(treeType)) {
				this.treeType = TREE_TYPE_BIRCH;
			} else if (TREE_TYPE_JUNGLE.equals(treeType)) {
				this.treeType = TREE_TYPE_JUNGLE;
			} else if (TREE_TYPE_MANGROVE.equals(treeType)) {
				this.treeType = TREE_TYPE_MANGROVE;
			} else if (TREE_TYPE_ACACIA.equals(treeType)) {
				this.treeType = TREE_TYPE_ACACIA;
			} else if (TREE_TYPE_DARK_OAK.equals(treeType)) {
				this.treeType = TREE_TYPE_DARK_OAK;
			} else if (TREE_TYPE_PALE_OAK.equals(treeType)) {
				this.treeType = TREE_TYPE_PALE_OAK;
			} else if (TREE_TYPE_CHERRY.equals(treeType)) {
				this.treeType = TREE_TYPE_CHERRY;
			} else {
				this.treeType = TREE_TYPE_OAK;
			}
			this.initialSeasonId = normalizeSeasonId(initialSeasonId);
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_CHUNK_X, chunkX);
			root.addProperty(FIELD_CHUNK_Z, chunkZ);
			root.addProperty(FIELD_TREE_GROUND_POS, groundPos);
			root.addProperty(FIELD_TREE_TYPE, treeType);
			root.addProperty(FIELD_INITIAL_SEASON_ID, initialSeasonId);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static TreeCandidateState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}

			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			int chunkX = (int) getLong(source, FIELD_CHUNK_X, Integer.MIN_VALUE);
			int chunkZ = (int) getLong(source, FIELD_CHUNK_Z, Integer.MIN_VALUE);
			if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
				return null;
			}
			long groundPos = getLong(source, FIELD_TREE_GROUND_POS, Long.MIN_VALUE);
			if (groundPos == Long.MIN_VALUE) {
				return null;
			}
			String treeType = getString(source, FIELD_TREE_TYPE, TREE_TYPE_OAK);
			String initialSeasonId = normalizeSeasonId(getString(source, FIELD_INITIAL_SEASON_ID, "spring"));
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				Math.max(1.0d, resolveTreeRequiredGrowthTicks(treeType, initialSeasonId))
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new TreeCandidateState(
				levelId,
				chunkX,
				chunkZ,
				groundPos,
				treeType,
				initialSeasonId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}

	private static final class DirtState {
		private final String levelId;
		private final long dirtPos;
		private final String mode;
		private final String erosionRuleId;
		private double requiredGrowthTicks;
		private double progressGrowthTicks;
		private long lastProcessedAbsoluteDayTime;

		private DirtState(
			String levelId,
			long dirtPos,
			String mode,
			String erosionRuleId,
			double requiredGrowthTicks,
			double progressGrowthTicks,
			long lastProcessedAbsoluteDayTime
		) {
			this.levelId = levelId == null ? "" : levelId;
			this.dirtPos = dirtPos;
			this.mode = MODE_SURFACE_DIRT.equals(mode) ? MODE_SURFACE_DIRT : MODE_WET;
			this.erosionRuleId = erosionRuleId == null ? "" : erosionRuleId.trim();
			this.requiredGrowthTicks = Math.max(1.0d, requiredGrowthTicks);
			this.progressGrowthTicks = Math.max(0.0d, Math.min(this.requiredGrowthTicks, progressGrowthTicks));
			this.lastProcessedAbsoluteDayTime = Math.max(0L, lastProcessedAbsoluteDayTime);
		}

		private String key() {
			return levelId + "|" + dirtPos;
		}

		private JsonObject toJson() {
			JsonObject root = new JsonObject();
			root.addProperty(FIELD_LEVEL_ID, levelId);
			root.addProperty(FIELD_BLOCK_POS, dirtPos);
			root.addProperty(FIELD_MODE, mode);
			root.addProperty(FIELD_EROSION_RULE_ID, erosionRuleId);
			root.addProperty(FIELD_REQUIRED_GROWTH_TICKS, requiredGrowthTicks);
			root.addProperty(FIELD_PROGRESS_GROWTH_TICKS, progressGrowthTicks);
			root.addProperty(FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, lastProcessedAbsoluteDayTime);
			return root;
		}

		private static DirtState fromJson(JsonElement element) {
			if (element == null || !element.isJsonObject()) {
				return null;
			}
			JsonObject source = element.getAsJsonObject();
			String levelId = getString(source, FIELD_LEVEL_ID, "").trim();
			if (levelId.isEmpty()) {
				return null;
			}
			long dirtPos = getLong(source, FIELD_BLOCK_POS, Long.MIN_VALUE);
			if (dirtPos == Long.MIN_VALUE) {
				return null;
			}
			String mode = getString(source, FIELD_MODE, MODE_WET);
			String erosionRuleId = getString(source, FIELD_EROSION_RULE_ID, "");
			double requiredGrowthTicks = getDouble(
				source,
				FIELD_REQUIRED_GROWTH_TICKS,
				MODE_SURFACE_DIRT.equals(mode) ? 3.0d * MadokuTime.MINECRAFT_TICKS_PER_CYCLE : defaultErosionGrowthTicks()
			);
			double progressGrowthTicks = getDouble(source, FIELD_PROGRESS_GROWTH_TICKS, 0.0d);
			long lastProcessedAbsoluteDayTime = getLong(source, FIELD_LAST_PROCESSED_ABSOLUTE_DAY_TIME, 0L);
			return new DirtState(
				levelId,
				dirtPos,
				mode,
				erosionRuleId,
				requiredGrowthTicks,
				progressGrowthTicks,
				lastProcessedAbsoluteDayTime
			);
		}
	}
}
