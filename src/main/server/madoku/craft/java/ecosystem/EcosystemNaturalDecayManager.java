package madoku.craft.java.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

public final class EcosystemNaturalDecayManager {

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalDecayManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "madoku-natural-decay";

	private static volatile NaturalDecayConfigManager.Settings settings = NaturalDecayConfigManager.defaults();
	private static final Predicate<BlockState> LEAF_BLOCK_STATE = state -> state != null && state.is(BlockTags.LEAVES);
	static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.TreeDecayCandidateState>> treeDecayCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, Long>> treeDecayTargetOwnersByChunk = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Double> NEXT_CANDIDATE_DUE_BY_CHUNK = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Long> MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, PriorityQueue<CandidateDueEntry>> CANDIDATE_DUE_QUEUES = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, Long>> CANDIDATE_DUE_VERSIONS = new LinkedHashMap<>();

	private EcosystemNaturalDecayManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		clearTrackedCandidateState();
	}

	static void clearTrackedCandidateState() {
		treeDecayCandidatesByChunk.clear();
		treeDecayTargetOwnersByChunk.clear();
		NEXT_CANDIDATE_DUE_BY_CHUNK.clear();
		MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.clear();
		CANDIDATE_DUE_QUEUES.clear();
		CANDIDATE_DUE_VERSIONS.clear();
	}

	static void evictTrackedCandidateState(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.remove(chunkKey);
		treeDecayTargetOwnersByChunk.remove(chunkKey);
		NEXT_CANDIDATE_DUE_BY_CHUNK.remove(chunkKey);
		MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.remove(chunkKey);
		CANDIDATE_DUE_QUEUES.remove(chunkKey);
		CANDIDATE_DUE_VERSIONS.remove(chunkKey);
		if (candidates != null) {
			for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
				if (candidate != null) {
					EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY);
				}
			}
		}
	}

	static Set<EcosystemAPIManager.ChunkRefKey> collectTrackedChunkKeys() {
		return new java.util.LinkedHashSet<>(treeDecayCandidatesByChunk.keySet());
	}

	/** Materializes lazy decay progress before autosave or chunk eviction. */
	static void materializeCandidateProgressForSave(ServerLevel world) {
		if (world == null) {
			return;
		}
		long currentAbsoluteDayTime = EcosystemAPIManager.resolveCachedAbsoluteDayTime(world);
		String currentLevelId = EcosystemAPIManager.levelId(world);
		for (EcosystemAPIManager.ChunkRefKey chunkKey : new java.util.LinkedHashSet<>(treeDecayCandidatesByChunk.keySet())) {
			if (chunkKey == null || !currentLevelId.equals(chunkKey.levelId())) {
				continue;
			}
			materializeCandidateProgressForSave(world, chunkKey, currentAbsoluteDayTime);
		}
	}

	static void materializeCandidateProgressForSave(
		ServerLevel world,
		EcosystemAPIManager.ChunkRefKey chunkKey
	) {
		if (world == null || chunkKey == null) {
			return;
		}
		materializeCandidateProgressForSave(
			world,
			chunkKey,
			EcosystemAPIManager.resolveCachedAbsoluteDayTime(world)
		);
	}

	private static void materializeCandidateProgressForSave(
		ServerLevel world,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		long currentAbsoluteDayTime
	) {
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
			if (candidate == null) {
				continue;
			}
			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressDecayTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredDecayTicks
			);
			boolean changed = candidate.progressDecayTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressDecayTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
			enqueueCandidateDue(chunkKey, candidate);
			if (changed) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
		}
		refreshCandidateSchedule(chunkKey);
	}

	static long trackedCandidateCount() {
		long count = 0L;
		for (Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates : treeDecayCandidatesByChunk.values()) {
			if (candidates != null) {
				count += candidates.size();
			}
		}
		return count;
	}

	static Collection<EcosystemAPIManager.TreeDecayCandidateState> getTreeDecayCandidates(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : treeDecayCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static boolean putTreeDecayCandidate(EcosystemAPIManager.ChunkRefKey chunkKey, EcosystemAPIManager.TreeDecayCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return false;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates != null && candidates.containsKey(candidate.leafPos)) return false;
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		if (targetOwners != null && targetOwners.containsKey(candidate.targetPos)) return false;
		if (!EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.leafPos, EcosystemAPIManager.CANDIDATE_DECAY)) {
			return false;
		}
		candidates = treeDecayCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		targetOwners = treeDecayTargetOwnersByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		candidates.put(candidate.leafPos, candidate);
		targetOwners.put(candidate.targetPos, candidate.leafPos);
		registerCandidateSchedule(chunkKey, candidate.progressDecayTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredDecayTicks);
		enqueueCandidateDue(chunkKey, candidate);
		return true;
	}

	static JsonObject createChunkPersistedData(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		Collection<EcosystemAPIManager.TreeDecayCandidateState> treeDecayCandidateList = getTreeDecayCandidates(chunkKey);
		if (treeDecayCandidateList == null || treeDecayCandidateList.isEmpty()) {
			return null;
		}

		JSONFormatAPIManager.ArrayBuilder treeDecayCandidates = JSONFormatAPIManager.array();
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : treeDecayCandidateList) {
			if (candidate != null) {
				treeDecayCandidates.add(candidate.toJson());
			}
		}

		return EcosystemAPIManager.buildChunkPersistedData(builder -> builder
			.put("tree-decay-candidates", treeDecayCandidates.build()));
	}

	static void applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return;
		}

		JsonElement treeDecayCandidatesElement = source.get("tree-decay-candidates");
		if (treeDecayCandidatesElement != null && treeDecayCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeDecayCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.TreeDecayCandidateState candidate = EcosystemAPIManager.TreeDecayCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeDecayCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	static boolean tryApplyTreeDecayAtTarget(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
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
			try (
				@SuppressWarnings("unused")
				EcosystemBlockChangeAPIManager.IndirectShapeUpdateScope ignored = EcosystemBlockChangeAPIManager.beginIndirectShapeUpdateOverride()
			) {
				world.setBlockAndUpdate(targetPos, placed);
			}
			EcosystemAPIManager.invalidateCachedGroundPosition();
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
		try (
			@SuppressWarnings("unused")
			EcosystemBlockChangeAPIManager.IndirectShapeUpdateScope ignored = EcosystemBlockChangeAPIManager.beginIndirectShapeUpdateOverride()
		) {
			world.setBlockAndUpdate(targetPos, updated);
		}
		EcosystemAPIManager.invalidateCachedGroundPosition();
		return true;
	}

	static boolean isNaturallyGeneratedLeaf(BlockState state) {
		if (state == null || !state.hasProperty(LeavesBlock.PERSISTENT)) {
			return false;
		}
		Boolean persistent = state.getValue(LeavesBlock.PERSISTENT);
		return persistent == null || !persistent;
	}

	static int getLeafLitterAmount(BlockState state) {
		IntegerProperty amountProperty = findLeafLitterAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	static int getLeafLitterMaxAmount(BlockState state) {
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

	static IntegerProperty findLeafLitterAmountProperty(BlockState state) {
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
			if (fallback == null && NaturalGrowthConfigManager.propertyNameLooksLikeAmount(integerProperty.getName())) {
				fallback = integerProperty;
			}
		}
		return fallback;
	}

	static BlockState setLeafLitterAmount(BlockState state, int targetAmount) {
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

	public static NaturalDecayConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return EcosystemAPIManager.isEnabled() && settings.isEnabled();
	}

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		if (event == null) {
			return;
		}
		ServerLevel world = event.level();
		if (world == null || event.chunk() == null || !isEnabled()) {
			return;
		}

		long currentAbsoluteDayTime = event.currentAbsoluteDayTime() == Long.MIN_VALUE
			? EcosystemAPIManager.resolveCachedAbsoluteDayTime(world)
			: event.currentAbsoluteDayTime();
		int chunkX = event.chunk().getPos().x();
		int chunkZ = event.chunk().getPos().z();
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		boolean decayCandidateWork = event.currentAbsoluteDayTime() == Long.MIN_VALUE
			? hasDueCandidateWork(chunkKey, currentAbsoluteDayTime)
			: event.decayCandidateWork();
		if (decayCandidateWork) {
			processTreeDecayCandidatesInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime, event.workBudget());
			refreshCandidateSchedule(chunkKey);
		}
		if (event.surfaceGroundPosition() != null) {
			discoverTreeDecayCandidatesInColumn(world, event.chunk(), event.surfaceGroundPosition());
		}
	}

	static boolean hasDueCandidateWork(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		long currentAbsoluteDayTime
	) {
		if (world == null) {
			return false;
		}
		return hasDueCandidateWork(
			new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ),
			currentAbsoluteDayTime
		);
	}

	static boolean hasDueCandidateWork(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		long currentAbsoluteDayTime
	) {
		if (chunkKey == null || !isEnabled()) {
			return false;
		}
		Double nextDue = NEXT_CANDIDATE_DUE_BY_CHUNK.get(chunkKey);
		Long maxLastProcessed = MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.get(chunkKey);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			clearCandidateDueIndex(chunkKey);
			return false;
		}
		PriorityQueue<CandidateDueEntry> queue = ensureCandidateDueQueue(chunkKey, candidates);
		pruneCandidateDueQueue(chunkKey, candidates, queue);
		CandidateDueEntry first = queue.peek();
		if (first == null) {
			clearCandidateDueIndex(chunkKey);
			return false;
		}
		nextDue = first.nextDue;
		NEXT_CANDIDATE_DUE_BY_CHUNK.put(chunkKey, nextDue);
		return (
			currentAbsoluteDayTime >= nextDue
			|| (maxLastProcessed != null && currentAbsoluteDayTime < maxLastProcessed)
		);
	}

	private static void registerCandidateSchedule(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		double progressDecayTicks,
		long lastProcessedAbsoluteDayTime,
		double requiredDecayTicks
	) {
		if (chunkKey == null) {
			return;
		}
		double required = Double.isFinite(requiredDecayTicks) ? Math.max(1.0d, requiredDecayTicks) : 1.0d;
		double progress = Double.isFinite(progressDecayTicks)
			? Math.max(0.0d, Math.min(required, progressDecayTicks))
			: 0.0d;
		long lastProcessed = Math.max(0L, lastProcessedAbsoluteDayTime);
		double nextDue = lastProcessed + Math.max(0.0d, required - progress);
		Double existingNextDue = NEXT_CANDIDATE_DUE_BY_CHUNK.get(chunkKey);
		if (existingNextDue == null || nextDue < existingNextDue) {
			NEXT_CANDIDATE_DUE_BY_CHUNK.put(chunkKey, nextDue);
		}
		Long existingMaxLastProcessed = MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.get(chunkKey);
		if (existingMaxLastProcessed == null || lastProcessed > existingMaxLastProcessed) {
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.put(chunkKey, lastProcessed);
		}
	}

	private static void refreshCandidateSchedule(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			clearCandidateDueIndex(chunkKey);
			return;
		}
		PriorityQueue<CandidateDueEntry> queue = ensureCandidateDueQueue(chunkKey, candidates);
		pruneCandidateDueQueue(chunkKey, candidates, queue);
		if (queue.isEmpty()) {
			clearCandidateDueIndex(chunkKey);
			return;
		}
		if (queue.size() > candidates.size() * 2L + 16L) {
			rebuildCandidateDueQueue(chunkKey, candidates);
			queue = CANDIDATE_DUE_QUEUES.get(chunkKey);
		}
		CandidateDueEntry first = queue.peek();
		NEXT_CANDIDATE_DUE_BY_CHUNK.put(chunkKey, first.nextDue);
	}

	private static PriorityQueue<CandidateDueEntry> ensureCandidateDueQueue(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates
	) {
		PriorityQueue<CandidateDueEntry> queue = CANDIDATE_DUE_QUEUES.get(chunkKey);
		if (queue == null) {
			rebuildCandidateDueQueue(chunkKey, candidates);
			queue = CANDIDATE_DUE_QUEUES.get(chunkKey);
		}
		return queue;
	}

	private static void rebuildCandidateDueQueue(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates
	) {
		PriorityQueue<CandidateDueEntry> queue = new PriorityQueue<>((left, right) -> {
			int dueComparison = Double.compare(left.nextDue, right.nextDue);
			return dueComparison != 0 ? dueComparison : Long.compare(left.leafPos, right.leafPos);
		});
		Map<Long, Long> versions = new LinkedHashMap<>();
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
			if (candidate != null) {
				versions.put(candidate.leafPos, 1L);
				queue.add(new CandidateDueEntry(candidate.leafPos, candidateNextDue(candidate), 1L));
			}
		}
		CANDIDATE_DUE_QUEUES.put(chunkKey, queue);
		CANDIDATE_DUE_VERSIONS.put(chunkKey, versions);
	}

	private static void enqueueCandidateDue(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		EcosystemAPIManager.TreeDecayCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}
		PriorityQueue<CandidateDueEntry> queue = ensureCandidateDueQueue(chunkKey, candidates);
		Map<Long, Long> versions = CANDIDATE_DUE_VERSIONS.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		long version = versions.getOrDefault(candidate.leafPos, 0L) + 1L;
		versions.put(candidate.leafPos, version);
		queue.add(new CandidateDueEntry(candidate.leafPos, candidateNextDue(candidate), version));
	}

	private static void pruneCandidateDueQueue(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates,
		PriorityQueue<CandidateDueEntry> queue
	) {
		Map<Long, Long> versions = CANDIDATE_DUE_VERSIONS.get(chunkKey);
		while (!queue.isEmpty()) {
			CandidateDueEntry entry = queue.peek();
			EcosystemAPIManager.TreeDecayCandidateState candidate = candidates.get(entry.leafPos);
			Long currentVersion = versions == null ? null : versions.get(entry.leafPos);
			if (candidate != null && currentVersion != null && currentVersion == entry.version
				&& Double.compare(candidateNextDue(candidate), entry.nextDue) == 0) {
				return;
			}
			queue.poll();
		}
	}

	private static void clearCandidateDueIndex(EcosystemAPIManager.ChunkRefKey chunkKey) {
		NEXT_CANDIDATE_DUE_BY_CHUNK.remove(chunkKey);
		MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.remove(chunkKey);
		CANDIDATE_DUE_QUEUES.remove(chunkKey);
		CANDIDATE_DUE_VERSIONS.remove(chunkKey);
	}

	private static double candidateNextDue(EcosystemAPIManager.TreeDecayCandidateState candidate) {
		double required = Double.isFinite(candidate.requiredDecayTicks) ? Math.max(1.0d, candidate.requiredDecayTicks) : 1.0d;
		double progress = Double.isFinite(candidate.progressDecayTicks)
			? Math.max(0.0d, Math.min(required, candidate.progressDecayTicks))
			: 0.0d;
		long lastProcessed = Math.max(0L, candidate.lastProcessedAbsoluteDayTime);
		return lastProcessed + Math.max(0.0d, required - progress);
	}

	private static void refreshMaxLastProcessed(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates
	) {
		long maxLastProcessed = Long.MIN_VALUE;
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
			if (candidate != null) {
				maxLastProcessed = Math.max(maxLastProcessed, Math.max(0L, candidate.lastProcessedAbsoluteDayTime));
			}
		}
		if (maxLastProcessed == Long.MIN_VALUE) {
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.remove(chunkKey);
		} else {
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.put(chunkKey, maxLastProcessed);
		}
	}

	private record CandidateDueEntry(long leafPos, double nextDue, long version) {
	}

	private static void processTreeDecayCandidatesInChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		long currentAbsoluteDayTime,
		EcosystemChunkTickWorkBudget workBudget
	) {
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}
		if (!workBudget.hasRemaining()) {
			return;
		}
		Long maxLastProcessed = MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.get(chunkKey);
		if (maxLastProcessed != null && currentAbsoluteDayTime < maxLastProcessed) {
			processTreeDecayCandidatesAfterClockRollback(
				world, candidates, currentAbsoluteDayTime, workBudget
			);
			refreshMaxLastProcessed(chunkKey, candidates);
			refreshCandidateSchedule(chunkKey);
			return;
		}

		PriorityQueue<CandidateDueEntry> queue = ensureCandidateDueQueue(chunkKey, candidates);
		pruneCandidateDueQueue(chunkKey, candidates, queue);
		while (workBudget.hasRemaining() && !queue.isEmpty()) {
			CandidateDueEntry entry = queue.peek();
			if (entry == null || currentAbsoluteDayTime < entry.nextDue) {
				break;
			}
			queue.poll();
			Map<Long, Long> versions = CANDIDATE_DUE_VERSIONS.get(chunkKey);
			Long currentVersion = versions == null ? null : versions.get(entry.leafPos);
			EcosystemAPIManager.TreeDecayCandidateState candidate = candidates.get(entry.leafPos);
			if (candidate == null || currentVersion == null || currentVersion != entry.version
				|| Double.compare(candidateNextDue(candidate), entry.nextDue) != 0) {
				continue;
			}
			if (!EcosystemAPIManager.isCandidateDue(
				candidate.progressDecayTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredDecayTicks
			)) {
				enqueueCandidateDue(chunkKey, candidate);
				continue;
			}
			processTreeDecayCandidate(world, candidate, currentAbsoluteDayTime, workBudget);
		}
		refreshCandidateSchedule(chunkKey);
	}

	private static void processTreeDecayCandidatesAfterClockRollback(
		ServerLevel world,
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates,
		long currentAbsoluteDayTime,
		EcosystemChunkTickWorkBudget workBudget
	) {
		for (EcosystemAPIManager.TreeDecayCandidateState candidate : candidates.values()) {
			if (!workBudget.hasRemaining()) {
				return;
			}
			if (candidate == null || currentAbsoluteDayTime >= candidate.lastProcessedAbsoluteDayTime
				|| !workBudget.tryConsumeCandidateOperation(EcosystemChunkTickWorkBudget.CANDIDATE_DECAY)) {
				continue;
			}
			processTreeDecayCandidateMeasured(world, candidate, currentAbsoluteDayTime);
		}
	}

	private static void processTreeDecayCandidate(
		ServerLevel world,
		EcosystemAPIManager.TreeDecayCandidateState candidate,
		long currentAbsoluteDayTime,
		EcosystemChunkTickWorkBudget workBudget
	) {
		if (!workBudget.tryConsumeCandidateOperation(EcosystemChunkTickWorkBudget.CANDIDATE_DECAY)) {
			return;
		}
		processTreeDecayCandidateMeasured(world, candidate, currentAbsoluteDayTime);
	}

	private static void processTreeDecayCandidateMeasured(
		ServerLevel world,
		EcosystemAPIManager.TreeDecayCandidateState candidate,
		long currentAbsoluteDayTime
	) {
		BlockPos candidatePosition = BlockPos.of(candidate.leafPos);
		processTreeDecayCandidateAt(world, candidatePosition, currentAbsoluteDayTime);
	}

	private static void discoverTreeDecayCandidatesInColumn(
		ServerLevel world,
		LevelChunk chunk,
		BlockPos surfaceGroundPosition
	) {
		if (world == null || chunk == null || surfaceGroundPosition == null || !isEnabled()) {
			return;
		}

		int x = surfaceGroundPosition.getX();
		int z = surfaceGroundPosition.getZ();
		int chunkX = chunk.getPos().x();
		int chunkZ = chunk.getPos().z();
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);
		int topY = Math.min(
			world.getMaxY() - 1,
			chunk.getHeight(
				net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
				x & 15,
				z & 15
			) - 1
		);
		if (topY <= surfaceGroundPosition.getY()) {
			return;
		}

		LevelChunkSection[] sections = chunk.getSections();
		int firstSectionIndex = Math.max(
			0,
			(surfaceGroundPosition.getY() + 1 - world.getMinY()) >> 4
		);
		int lastSectionIndex = Math.min(
			sections.length - 1,
			(topY - world.getMinY()) >> 4
		);
		for (int sectionIndex = firstSectionIndex; sectionIndex <= lastSectionIndex; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || !section.maybeHas(LEAF_BLOCK_STATE)) {
				continue;
			}

			int sectionMinY = world.getMinY() + sectionIndex * 16;
			int firstY = Math.max(surfaceGroundPosition.getY() + 1, sectionMinY);
			int lastY = Math.min(topY, sectionMinY + 15);
			for (int y = firstY; y <= lastY; y++) {
				BlockPos leafPosition = new BlockPos(x, y, z);
				if (existingCandidates != null && existingCandidates.containsKey(leafPosition.asLong())) {
					continue;
				}
				BlockState leafState = world.getBlockState(leafPosition);
				if (!leafState.is(BlockTags.LEAVES) || !isNaturallyGeneratedLeaf(leafState)) {
					continue;
				}
				BlockPos targetPosition = resolveTreeDecayTargetPos(world, leafPosition, leafState);
				if (targetPosition != null) {
					pickTreeDecayCandidateForPosition(
						world,
						chunkX,
						chunkZ,
						leafPosition.asLong(),
						targetPosition.asLong()
					);
				}
			}
		}
	}

	static void removeCandidateAt(EcosystemAPIManager.ChunkRefKey chunkKey, long packedPosition) {
		if (chunkKey == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.remove(packedPosition) == null) {
			return;
		}
		EcosystemAPIManager.removeCandidatePositionBit(chunkKey.levelId(), packedPosition, EcosystemAPIManager.CANDIDATE_DECAY);
		Map<Long, Long> owners = treeDecayTargetOwnersByChunk.get(chunkKey);
		if (owners != null) {
			owners.values().removeIf(value -> value == packedPosition);
			if (owners.isEmpty()) {
				treeDecayTargetOwnersByChunk.remove(chunkKey);
			}
		}
		removeCandidateDue(chunkKey, packedPosition);
		if (candidates.isEmpty()) {
			treeDecayCandidatesByChunk.remove(chunkKey);
		}
		refreshCandidateSchedule(chunkKey);
	}

	private static void removeCandidateDue(EcosystemAPIManager.ChunkRefKey chunkKey, long leafPos) {
		Map<Long, Long> versions = CANDIDATE_DUE_VERSIONS.get(chunkKey);
		if (versions != null) {
			versions.remove(leafPos);
		}
	}

	static double resolveTreeDecayRequiredTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			 normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalDecaySettings.treeDecayForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static BlockPos resolveTreeDecayTargetPos(ServerLevel world, BlockPos leafPos, BlockState leafState) {
		if (world == null || leafPos == null || leafState == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return null;
		}
		if (!leafState.is(BlockTags.LEAVES) || !EcosystemAPIManager.isNaturallyGeneratedLeaf(leafState)) {
			return null;
		}
		return resolveLeafLitterTargetPos(world, leafPos);
	}

	static boolean isValidTreeDecayTargetCandidate(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return false;
		}
		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return false;
		}

		BlockState state = world.getBlockState(targetPos);
		if (state == null) {
			return false;
		}
		if (state.getBlock() == leafLitter) {
			return hasLeafLitterSupportBlock(world, targetPos)
				&& EcosystemAPIManager.getLeafLitterAmount(state) < EcosystemAPIManager.getLeafLitterMaxAmount(state);
		}
		if (state.isAir()) {
			BlockState placed = EcosystemAPIManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
			return hasLeafLitterSupportBlock(world, targetPos)
				&& placed.canSurvive(world, targetPos);
		}
		return false;
	}

	static boolean pickTreeDecayCandidateForPosition(ServerLevel world, int chunkX, int chunkZ, long leafPos, long targetPos) {
		if (world == null || !EcosystemAPIManager.isNaturalDecayEnabled()) {
			return false;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> existingCandidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (leafPos == Long.MIN_VALUE || targetPos == Long.MIN_VALUE) {
			return false;
		}
		// resolveTreeDecayTargetPos already validated this target while discovering it.
		if (existingCandidates != null && existingCandidates.containsKey(leafPos)) {
			return false;
		}
		Map<Long, Long> targetOwners = treeDecayTargetOwnersByChunk.get(chunkKey);
		if (targetOwners != null && targetOwners.containsKey(targetPos)) {
			return false;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredDecayTicks = resolveTreeDecayRequiredTicks(world, seasonId);
		if (requiredDecayTicks <= 0.0d) {
			return false;
		}

		boolean created = putTreeDecayCandidate(chunkKey, new EcosystemAPIManager.TreeDecayCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				leafPos,
				targetPos,
				seasonId,
				requiredDecayTicks,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world)
			));
		EcosystemAPIManager.dirty = true;
		return created;
	}

	private static double randomDaysToTicks(EcosystemConfigManager.DayRange range) {
		if (range == null) {
			return -1.0d;
		}
		return EcosystemNaturalGrowthManager.randomDaysToTicks(range);
	}

	private static BlockPos resolveLeafLitterTargetPos(ServerLevel world, BlockPos leafPos) {
		if (world == null || leafPos == null) {
			return null;
		}

		Block leafLitter = EcosystemConfigManager.resolveBlock(EcosystemAPIManager.BLOCK_ID_LEAF_LITTER);
		if (leafLitter == null) {
			return null;
		}
		BlockState singleLitter = EcosystemAPIManager.setLeafLitterAmount(leafLitter.defaultBlockState(), 1);
		int minY = Math.max(world.getMinY() + 1, leafPos.getY() - EcosystemAPIManager.TREE_DECAY_MAX_DROP_DISTANCE);

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
					&& EcosystemAPIManager.getLeafLitterAmount(state) < EcosystemAPIManager.getLeafLitterMaxAmount(state)
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
		return belowState != null && EcosystemAPIManager.LEAF_LITTER_SUPPORT_BLOCKS.contains(belowState.getBlock());
	}

	private static void processTreeDecayCandidateAt(
		ServerLevel world,
		BlockPos position,
		long currentAbsoluteDayTime
	) {
		if (world == null || position == null || !isEnabled()) {
			return;
		}
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world),
			position.getX() >> 4,
			position.getZ() >> 4
		);
		Map<Long, EcosystemAPIManager.TreeDecayCandidateState> candidates = treeDecayCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}
		EcosystemAPIManager.TreeDecayCandidateState candidate = candidates.get(position.asLong());
		if (candidate == null) {
			return;
		}
		BlockPos targetPos = BlockPos.of(candidate.targetPos);
		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressDecayTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredDecayTicks
		);
		boolean progressChanged = candidate.progressDecayTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressDecayTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		enqueueCandidateDue(chunkKey, candidate);
		if (progressChanged) {
			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
		boolean due = advanced.progressGrowthTicks() + 1e-6d >= candidate.requiredDecayTicks;
		if (!due) {
			return;
		}
		if (!isValidTreeDecayTargetCandidate(world, targetPos)) {
			removeCandidateAt(chunkKey, candidate.leafPos);
			EcosystemAPIManager.markChunkDirty(chunkKey);
			return;
		}
		boolean replacementApplied = tryApplyTreeDecayAtTarget(world, targetPos);
		if (replacementApplied) {
			removeCandidateAt(chunkKey, candidate.leafPos);
			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
	}

	private static void loadConfig() {
		NaturalDecayConfigManager.Settings fallback = NaturalDecayConfigManager.defaults();
		JsonObject defaults = NaturalDecayConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults);
			settings = NaturalDecayConfigManager.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, NaturalDecayConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalDecayManager config; using defaults.", exception);
		}
	}

}
