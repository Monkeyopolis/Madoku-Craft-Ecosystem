package madoku.craft.java.ecosystem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import madoku.craft.java.core.json.JSONFormatAPIManager;
import madoku.craft.java.core.json.JSONAPIManager;
import madoku.craft.java.core.season.SeasonAPIManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.tags.TagKey;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class EcosystemNaturalGrowthManager {
	private static final String TREE_TYPE_OAK = "oak";
	private static final String TREE_TYPE_SPRUCE = "spruce";
	private static final String TREE_TYPE_BIRCH = "birch";
	private static final String TREE_TYPE_JUNGLE = "jungle";
	private static final String TREE_TYPE_MANGROVE = "mangrove";
	private static final String TREE_TYPE_ACACIA = "acacia";
	private static final String TREE_TYPE_DARK_OAK = "dark_oak";
	private static final String TREE_TYPE_PALE_OAK = "pale_oak";
	private static final String TREE_TYPE_CHERRY = "cherry";

	private static final Logger LOGGER = LoggerFactory.getLogger(EcosystemNaturalGrowthManager.class);
	private static final String CONFIG_FOLDER_NAME = "madoku-craft-ecosystem";
	private static final String CONFIG_FILE_NAME = "madoku-natural-growth";
	private static final int MAX_GRASS_CANDIDATES_PER_CHUNK = 4;
	private static final int MAX_DESERT_FOLIAGE_GROWTH_CANDIDATES_PER_CHUNK = 4;
	private static final int MAX_FOLIAGE_CANDIDATES_PER_CHUNK = 4;
	private static final int NATURAL_GROWTH_CLEARANCE_RADIUS = 3;
	private static final String BLOCK_ID_SHORT_GRASS = "minecraft:short_grass";
	private static final String BLOCK_ID_TALL_GRASS = "minecraft:tall_grass";
	private static final String BLOCK_ID_BUSH = "minecraft:bush";
	private static final String BLOCK_ID_DEAD_BUSH = "minecraft:dead_bush";
	private static final String BLOCK_ID_SHORT_DRY_GRASS = "minecraft:short_dry_grass";
	private static final String BLOCK_ID_TALL_DRY_GRASS = "minecraft:tall_dry_grass";
	private static final TagKey<Biome> BADLANDS_BIOME_TAG = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("minecraft", "is_badlands")
	);
	private static final TagKey<Biome> DESERT_BIOME_TAG = TagKey.create(
		Registries.BIOME,
		Identifier.fromNamespaceAndPath("minecraft", "is_desert")
	);

	private static volatile NaturalGrowthConfigManager.Settings settings = NaturalGrowthConfigManager.defaults();
	private static final Map<Holder<Biome>, List<String>> TREE_TYPES_BY_BIOME = new LinkedHashMap<>();
	static final Map<EcosystemAPIManager.ChunkRefKey, EcosystemAPIManager.TreeCandidateState> treeCandidatesByChunk = new LinkedHashMap<>();
	static final Map<EcosystemAPIManager.ChunkRefKey, EcosystemAPIManager.CactusCandidateState> cactusCandidatesByChunk = new LinkedHashMap<>();
	static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.GrassCandidateState>> grassCandidatesByChunk = new LinkedHashMap<>();
	static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.GrassCandidateState>> desertFoliageGrowthCandidatesByChunk = new LinkedHashMap<>();
	static final Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.FoliageCandidateState>> foliageCandidatesByChunk = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Double> NEXT_CANDIDATE_DUE_BY_CHUNK = new LinkedHashMap<>();
	private static final Map<EcosystemAPIManager.ChunkRefKey, Long> MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK = new LinkedHashMap<>();

	private EcosystemNaturalGrowthManager() {
	}

	public static void initialize() {
		loadConfig();
	}

	public static void reset() {
		clearTrackedCandidateState();
		TREE_TYPES_BY_BIOME.clear();
	}

	static void clearTrackedCandidateState() {
		treeCandidatesByChunk.clear();
		cactusCandidatesByChunk.clear();
		grassCandidatesByChunk.clear();
		desertFoliageGrowthCandidatesByChunk.clear();
		foliageCandidatesByChunk.clear();
		NEXT_CANDIDATE_DUE_BY_CHUNK.clear();
		MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.clear();
	}

	/** Materializes lazy absolute-time progress before ecosystem data is persisted. */
	static void materializeCandidateProgressForSave(ServerLevel world) {
		if (world == null) {
			return;
		}

		String currentLevelId = EcosystemAPIManager.levelId(world);
		long currentAbsoluteDayTime = EcosystemAPIManager.resolveCachedAbsoluteDayTime(world);
		Set<EcosystemAPIManager.ChunkRefKey> chunkKeys = collectTrackedChunkKeys();
		chunkKeys.addAll(EcosystemAPIManager.dirtKeysByChunk.keySet());
		for (EcosystemAPIManager.ChunkRefKey chunkKey : chunkKeys) {
			if (chunkKey == null || !currentLevelId.equals(chunkKey.levelId())) {
				continue;
			}

			boolean changed = false;
			Set<String> dirtKeys = EcosystemAPIManager.dirtKeysByChunk.get(chunkKey);
			if (dirtKeys != null) {
				for (String dirtKey : dirtKeys) {
					EcosystemAPIManager.DirtState dirt = EcosystemAPIManager.dirtBlocksByKey.get(dirtKey);
					changed |= materializeDirtProgress(dirt, currentAbsoluteDayTime);
				}
			}

			changed |= materializeTreeProgress(treeCandidatesByChunk.get(chunkKey), currentAbsoluteDayTime);
			changed |= materializeCactusProgress(cactusCandidatesByChunk.get(chunkKey), currentAbsoluteDayTime);
			changed |= materializeGrassProgress(grassCandidatesByChunk.get(chunkKey), currentAbsoluteDayTime);
			changed |= materializeGrassProgress(desertFoliageGrowthCandidatesByChunk.get(chunkKey), currentAbsoluteDayTime);
			changed |= materializeFoliageProgress(foliageCandidatesByChunk.get(chunkKey), currentAbsoluteDayTime);

			if (changed) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
			refreshCandidateSchedule(chunkKey);
		}
	}

	private static boolean materializeDirtProgress(EcosystemAPIManager.DirtState candidate, long currentAbsoluteDayTime) {
		if (candidate == null) {
			return false;
		}
		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressGrowthTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredGrowthTicks
		);
		boolean changed = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressGrowthTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		return changed;
	}

	private static boolean materializeTreeProgress(EcosystemAPIManager.TreeCandidateState candidate, long currentAbsoluteDayTime) {
		if (candidate == null) {
			return false;
		}
		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressGrowthTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredGrowthTicks
		);
		boolean changed = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressGrowthTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		return changed;
	}

	private static boolean materializeCactusProgress(EcosystemAPIManager.CactusCandidateState candidate, long currentAbsoluteDayTime) {
		if (candidate == null) {
			return false;
		}
		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressGrowthTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredGrowthTicks
		);
		boolean changed = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressGrowthTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		return changed;
	}

	private static boolean materializeGrassProgress(
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates,
		long currentAbsoluteDayTime
	) {
		if (candidates == null || candidates.isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (EcosystemAPIManager.GrassCandidateState candidate : candidates.values()) {
			if (candidate == null) {
				continue;
			}
			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressGrowthTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredGrowthTicks
			);
			changed |= candidate.progressGrowthTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressGrowthTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		}
		return changed;
	}

	private static boolean materializeFoliageProgress(
		Map<Long, EcosystemAPIManager.FoliageCandidateState> candidates,
		long currentAbsoluteDayTime
	) {
		if (candidates == null || candidates.isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (EcosystemAPIManager.FoliageCandidateState candidate : candidates.values()) {
			if (candidate == null) {
				continue;
			}
			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressGrowthTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredGrowthTicks
			);
			changed |= candidate.progressGrowthTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressGrowthTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		}
		return changed;
	}

	static void evictTrackedCandidateState(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}

		EcosystemAPIManager.TreeCandidateState tree = treeCandidatesByChunk.remove(chunkKey);
		if (tree != null) {
			EcosystemAPIManager.removeCandidatePositionBit(tree.levelId, tree.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
		}
		EcosystemAPIManager.CactusCandidateState cactus = cactusCandidatesByChunk.remove(chunkKey);
		if (cactus != null) {
			EcosystemAPIManager.removeCandidatePositionBit(cactus.levelId, cactus.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
		}

		Map<Long, EcosystemAPIManager.GrassCandidateState> grass = grassCandidatesByChunk.remove(chunkKey);
		removeGrassCandidateBits(grass, EcosystemAPIManager.CANDIDATE_GRASS);
		Map<Long, EcosystemAPIManager.GrassCandidateState> desertFoliage = desertFoliageGrowthCandidatesByChunk.remove(chunkKey);
		removeGrassCandidateBits(desertFoliage, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		Map<Long, EcosystemAPIManager.FoliageCandidateState> foliage = foliageCandidatesByChunk.remove(chunkKey);
		if (foliage != null) {
			for (EcosystemAPIManager.FoliageCandidateState candidate : foliage.values()) {
				if (candidate != null) {
					EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				}
			}
		}
		refreshCandidateSchedule(chunkKey);
	}

	private static void removeGrassCandidateBits(
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates,
		int candidateBit
	) {
		if (candidates == null) {
			return;
		}
		for (EcosystemAPIManager.GrassCandidateState candidate : candidates.values()) {
			if (candidate != null) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, candidateBit);
			}
		}
	}

	static Set<EcosystemAPIManager.ChunkRefKey> collectTrackedChunkKeys() {
		Set<EcosystemAPIManager.ChunkRefKey> keys = new LinkedHashSet<>();
		keys.addAll(treeCandidatesByChunk.keySet());
		keys.addAll(cactusCandidatesByChunk.keySet());
		keys.addAll(grassCandidatesByChunk.keySet());
		keys.addAll(desertFoliageGrowthCandidatesByChunk.keySet());
		keys.addAll(foliageCandidatesByChunk.keySet());
		return keys;
	}

	static EcosystemAPIManager.TreeCandidateState getTreeCandidate(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? null : treeCandidatesByChunk.get(chunkKey);
	}

	static EcosystemAPIManager.CactusCandidateState getCactusCandidate(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? null : cactusCandidatesByChunk.get(chunkKey);
	}

	static Collection<EcosystemAPIManager.GrassCandidateState> getGrassCandidates(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : grassCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static Collection<EcosystemAPIManager.GrassCandidateState> getDesertFoliageGrowthCandidates(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : desertFoliageGrowthCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static Collection<EcosystemAPIManager.FoliageCandidateState> getFoliageCandidates(EcosystemAPIManager.ChunkRefKey chunkKey) {
		return chunkKey == null ? List.of() : foliageCandidatesByChunk.getOrDefault(chunkKey, Map.of()).values();
	}

	static EcosystemAPIManager.TreeCandidateState putTreeCandidate(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		EcosystemAPIManager.TreeCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return null;
		}
		EcosystemAPIManager.TreeCandidateState previous = treeCandidatesByChunk.put(chunkKey, candidate);
		if (previous != null) EcosystemAPIManager.removeCandidatePositionBit(previous.levelId, previous.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
		registerCandidateSchedule(chunkKey, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
		return previous;
	}

	static EcosystemAPIManager.CactusCandidateState putCactusCandidate(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		EcosystemAPIManager.CactusCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return null;
		}
		EcosystemAPIManager.CactusCandidateState previous = cactusCandidatesByChunk.put(chunkKey, candidate);
		if (previous != null) EcosystemAPIManager.removeCandidatePositionBit(previous.levelId, previous.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
		registerCandidateSchedule(chunkKey, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
		return previous;
	}

	static void putGrassCandidate(EcosystemAPIManager.ChunkRefKey chunkKey, EcosystemAPIManager.GrassCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates = grassCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (candidates.containsKey(candidate.groundPos)) return;
		if (candidates.size() >= MAX_GRASS_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.put(candidate.groundPos, candidate);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_GRASS);
		registerCandidateSchedule(chunkKey, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
	}

	static void putDesertFoliageGrowthCandidate(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		EcosystemAPIManager.GrassCandidateState candidate
	) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates = desertFoliageGrowthCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		if (candidates.containsKey(candidate.groundPos)) return;
		if (candidates.size() >= MAX_DESERT_FOLIAGE_GROWTH_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.put(candidate.groundPos, candidate);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		registerCandidateSchedule(chunkKey, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
	}

	static void putFoliageCandidate(EcosystemAPIManager.ChunkRefKey chunkKey, EcosystemAPIManager.FoliageCandidateState candidate) {
		if (chunkKey == null || candidate == null) {
			return;
		}
		Map<Long, EcosystemAPIManager.FoliageCandidateState> candidates = foliageCandidatesByChunk.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>());
		EcosystemAPIManager.FoliageCandidateState existing = candidates.get(candidate.groundPos);
		if (existing != null && NaturalGrowthConfigManager.normalizeFoliageType(existing.foliageType).equals(NaturalGrowthConfigManager.normalizeFoliageType(candidate.foliageType))) return;
		if (candidates.size() >= MAX_FOLIAGE_CANDIDATES_PER_CHUNK) {
			return;
		}
		candidates.put(candidate.groundPos, candidate);
		EcosystemAPIManager.addCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		registerCandidateSchedule(chunkKey, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
	}

	static EcosystemAPIManager.TreeCandidateState removeTreeCandidate(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		EcosystemAPIManager.TreeCandidateState removed = treeCandidatesByChunk.remove(chunkKey);
		if (removed != null) {
			EcosystemAPIManager.removeCandidatePositionBit(removed.levelId, removed.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
		}
		refreshCandidateSchedule(chunkKey);
		return removed;
	}

	static EcosystemAPIManager.CactusCandidateState removeCactusCandidate(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}
		EcosystemAPIManager.CactusCandidateState removed = cactusCandidatesByChunk.remove(chunkKey);
		if (removed != null) {
			EcosystemAPIManager.removeCandidatePositionBit(removed.levelId, removed.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
		}
		refreshCandidateSchedule(chunkKey);
		return removed;
	}

	static JsonObject createChunkPersistedData(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return null;
		}

		JSONFormatAPIManager.ArrayBuilder treeCandidates = JSONFormatAPIManager.array();
		EcosystemAPIManager.TreeCandidateState treeCandidate = getTreeCandidate(chunkKey);
		if (treeCandidate != null) {
			treeCandidates.add(treeCandidate.toJson());
		}

		JSONFormatAPIManager.ArrayBuilder cactusCandidates = JSONFormatAPIManager.array();
		EcosystemAPIManager.CactusCandidateState cactusCandidate = getCactusCandidate(chunkKey);
		if (cactusCandidate != null) {
			cactusCandidates.add(cactusCandidate.toJson());
		}

		JSONFormatAPIManager.ArrayBuilder grassCandidates = JSONFormatAPIManager.array();
		Collection<EcosystemAPIManager.GrassCandidateState> grassCandidateList = getGrassCandidates(chunkKey);
		if (grassCandidateList != null) {
			for (EcosystemAPIManager.GrassCandidateState candidate : grassCandidateList) {
				if (candidate != null) {
					grassCandidates.add(candidate.toJson());
				}
			}
		}

		JSONFormatAPIManager.ArrayBuilder desertFoliageGrowthCandidates = JSONFormatAPIManager.array();
		Collection<EcosystemAPIManager.GrassCandidateState> desertFoliageGrowthCandidateList = getDesertFoliageGrowthCandidates(chunkKey);
		if (desertFoliageGrowthCandidateList != null) {
			for (EcosystemAPIManager.GrassCandidateState candidate : desertFoliageGrowthCandidateList) {
				if (candidate != null) {
					desertFoliageGrowthCandidates.add(candidate.toJson());
				}
			}
		}

		JSONFormatAPIManager.ArrayBuilder foliageCandidates = JSONFormatAPIManager.array();
		Collection<EcosystemAPIManager.FoliageCandidateState> foliageCandidateList = getFoliageCandidates(chunkKey);
		if (foliageCandidateList != null) {
			for (EcosystemAPIManager.FoliageCandidateState candidate : foliageCandidateList) {
				if (candidate != null) {
					foliageCandidates.add(candidate.toJson());
				}
			}
		}

		boolean hasData = treeCandidate != null
			|| cactusCandidate != null
			|| (grassCandidateList != null && !grassCandidateList.isEmpty())
			|| (desertFoliageGrowthCandidateList != null && !desertFoliageGrowthCandidateList.isEmpty())
			|| (foliageCandidateList != null && !foliageCandidateList.isEmpty());
		if (!hasData) {
			return null;
		}

		return EcosystemAPIManager.buildChunkPersistedData(builder -> builder
			.put("tree-candidates", treeCandidates.build())
			.put("cactus-candidates", cactusCandidates.build())
			.put("grass-candidates", grassCandidates.build())
			.put("desert-foliage-growth-candidates", desertFoliageGrowthCandidates.build())
			.put("foliage-candidates", foliageCandidates.build()));
	}

	static void applyPersistedData(JsonObject source) {
		if (source == null || source.isJsonNull()) {
			return;
		}

		JsonElement treeCandidatesElement = source.get("tree-candidates");
		if (treeCandidatesElement != null && treeCandidatesElement.isJsonArray()) {
			for (JsonElement element : treeCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.TreeCandidateState candidate = EcosystemAPIManager.TreeCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putTreeCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement cactusCandidatesElement = source.get("cactus-candidates");
		if (cactusCandidatesElement != null && cactusCandidatesElement.isJsonArray()) {
			for (JsonElement element : cactusCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.CactusCandidateState candidate = EcosystemAPIManager.CactusCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putCactusCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement grassCandidatesElement = source.get("grass-candidates");
		if (grassCandidatesElement != null && grassCandidatesElement.isJsonArray()) {
			for (JsonElement element : grassCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.GrassCandidateState candidate = EcosystemAPIManager.GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putGrassCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement desertFoliageGrowthCandidatesElement = source.get("desert-foliage-growth-candidates");
		if (desertFoliageGrowthCandidatesElement != null && desertFoliageGrowthCandidatesElement.isJsonArray()) {
			for (JsonElement element : desertFoliageGrowthCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.GrassCandidateState candidate = EcosystemAPIManager.GrassCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putDesertFoliageGrowthCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}

		JsonElement foliageCandidatesElement = source.get("foliage-candidates");
		if (foliageCandidatesElement != null && foliageCandidatesElement.isJsonArray()) {
			for (JsonElement element : foliageCandidatesElement.getAsJsonArray()) {
				EcosystemAPIManager.FoliageCandidateState candidate = EcosystemAPIManager.FoliageCandidateState.fromJson(element);
				if (candidate == null) {
					continue;
				}
				putFoliageCandidate(new EcosystemAPIManager.ChunkRefKey(candidate.levelId, candidate.chunkX, candidate.chunkZ), candidate);
			}
		}
	}

	static boolean isTreeGrowthEnabled(String treeType) {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.treeGrowth() != null && growthSettings.treeGrowth().isEnabled(treeType);
	}

	static boolean isVegetationGrowthEnabled(String foliageType) {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.vegetationGrowth() != null && growthSettings.vegetationGrowth().isEnabled(foliageType);
	}

	static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType) {
		return tryGrowTreeAtGround(world, groundPos, treeType, false);
	}

	private static boolean tryGrowTreeAtGround(ServerLevel world, BlockPos groundPos, String treeType, boolean clearanceAlreadyChecked) {
		if (world == null || groundPos == null || treeType == null || treeType.isBlank() || !isTreeGrowthEnabled(treeType)) {
			return false;
		}
		BlockPos treePos = groundPos.above();
		BlockState aboveState = world.getBlockState(treePos);
		if (!aboveState.isAir() && !aboveState.is(Blocks.SNOW)) {
			return false;
		}
		if (!clearanceAlreadyChecked && !hasNaturalGrowthClearance(world, treePos)) {
			return false;
		}

		BlockState replacedState = aboveState;
		if (aboveState.is(Blocks.SNOW)) {
			setBlockAndUpdate(world, treePos, Blocks.AIR.defaultBlockState());
		}

		ResourceKey<ConfiguredFeature<?, ?>> featureKey = treeFeatureKeyForType(treeType);
		if (featureKey == null) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				setBlockAndUpdate(world, treePos, replacedState);
			}
			return false;
		}

		HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = world.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE);
		java.util.Optional<Holder.Reference<ConfiguredFeature<?, ?>>> featureHolder = configuredFeatures.get(featureKey);
		if (featureHolder.isEmpty()) {
			if (replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
				setBlockAndUpdate(world, treePos, replacedState);
			}
			return false;
		}

		boolean placed = featureHolder.get().value().place(world, world.getChunkSource().getGenerator(), world.getRandom(), treePos);
		EcosystemAPIManager.invalidateCachedGroundPosition();
		if (!placed && replacedState.is(Blocks.SNOW) && world.getBlockState(treePos).isAir()) {
			setBlockAndUpdate(world, treePos, replacedState);
		}
		return placed;
	}

	static boolean tryGrowGrassAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}

		return tryPlaceWeightedFoliageTarget(world, growPos, buildGrassFoliagePlacements());
	}

	static boolean tryGrowFoliageAtGround(ServerLevel world, BlockPos groundPos, String foliageType) {
		if (world == null || groundPos == null || !isVegetationGrowthEnabled(foliageType)) {
			return false;
		}

		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
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
			setBlockAndUpdate(world, foliagePos, placed);
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
		setBlockAndUpdate(world, foliagePos, updated);
		return true;
	}

	static boolean tryGrowDesertFoliageAtGround(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null || !EcosystemAPIManager.isDesertFoliageGrowthEnabled()) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		if (!world.getBlockState(growPos).isAir()) {
			return false;
		}
		return placeSummerDesertTarget(world, growPos);
	}

	static boolean tryGrowCactusAtGround(ServerLevel world, BlockPos groundPos) {
		return tryGrowCactusAtGround(world, groundPos, false);
	}

	private static boolean tryGrowCactusAtGround(ServerLevel world, BlockPos groundPos, boolean clearanceAlreadyChecked) {
		if (world == null || groundPos == null || !EcosystemAPIManager.isCactusGrowthEnabled()) {
			return false;
		}
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidCactusGroundCandidate(world, groundPos, groundState, null, clearanceAlreadyChecked, null, null)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState next = Blocks.CACTUS.defaultBlockState();
		if (!next.canSurvive(world, growPos)) {
			return false;
		}
		setBlockAndUpdate(world, growPos, next);
		return true;
	}

	static int getFoliageAmount(BlockState state) {
		IntegerProperty amountProperty = findFoliageAmountProperty(state);
		if (amountProperty == null || state == null || !state.hasProperty(amountProperty)) {
			return 1;
		}
		Integer value = state.getValue(amountProperty);
		return value == null ? 1 : Math.max(1, value);
	}

	static int getFoliageMaxAmount(BlockState state) {
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

	static IntegerProperty findFoliageAmountProperty(BlockState state) {
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

	private static boolean propertyNameLooksLikeAmount(String propertyName) {
		return propertyName != null && propertyName.toLowerCase().contains("amount");
	}

	static BlockState setFoliageAmount(BlockState state, int targetAmount) {
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

	static Block resolveSurfaceDirtGrowthBlock(ServerLevel world, BlockPos pos) {
		if (world == null || pos == null) {
			return Blocks.GRASS_BLOCK;
		}

		NaturalGrowthConfigManager.Settings growthSettings = settings;
		NaturalGrowthConfigManager.BlockGrowthSettings blockGrowth = growthSettings.blockGrowth();
		NaturalGrowthConfigManager.DirtGrowthSettings dirtGrowth = blockGrowth == null ? null : blockGrowth.dirt();
		List<String> targetBlocks = dirtGrowth == null ? List.of() : dirtGrowth.targetBlocks();
		for (String targetBlockId : targetBlocks) {
			Block targetBlock = EcosystemConfigManager.resolveBlock(targetBlockId);
			if (targetBlock == null) {
				continue;
			}
			BlockState placed = targetBlock.defaultBlockState();
			if (placed.canSurvive(world, pos)) {
				return targetBlock;
			}
		}
		return Blocks.GRASS_BLOCK;
	}

	private static boolean tryPlaceTallGrass(ServerLevel world, BlockPos lowerPos, Block tallGrassBlock) {
		if (world == null || lowerPos == null || tallGrassBlock == null) {
			return false;
		}
		BlockPos upperPos = lowerPos.above();
		if (!world.getBlockState(lowerPos).isAir()) {
			return false;
		}

		BlockState lower = tallGrassBlock.defaultBlockState();
		if (!lower.hasProperty(DoublePlantBlock.HALF)) {
			if (!lower.canSurvive(world, lowerPos)) {
				return false;
			}
			setBlockAndUpdate(world, lowerPos, lower);
			return true;
		}

		if (!world.getBlockState(upperPos).isAir()) {
			return false;
		}

		if (lower.hasProperty(DoublePlantBlock.HALF)) {
			lower = lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
		}
		BlockState upper = tallGrassBlock.defaultBlockState();
		if (upper.hasProperty(DoublePlantBlock.HALF)) {
			upper = upper.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
		}

		setBlockAndUpdate(world, lowerPos, lower);
		setBlockAndUpdate(world, upperPos, upper);
		return true;
	}

	private static boolean setBlockAndUpdate(ServerLevel world, BlockPos position, BlockState state) {
		boolean changed = world.setBlockAndUpdate(position, state);
		if (changed) {
			EcosystemAPIManager.invalidateCachedGroundPosition();
		}
		return changed;
	}

	private static boolean tryPlaceWeightedFoliageTarget(ServerLevel world, BlockPos growPos, List<WeightedFoliagePlacement> placements) {
		if (world == null || growPos == null || placements == null || placements.isEmpty()) {
			return false;
		}

		List<WeightedFoliagePlacement> remaining = new ArrayList<>();
		for (WeightedFoliagePlacement placement : placements) {
			if (placement != null && placement.block() != null && placement.weight() > 0) {
				remaining.add(placement);
			}
		}
		if (remaining.isEmpty()) {
			return false;
		}

		while (!remaining.isEmpty()) {
			WeightedFoliagePlacement selected = pickWeightedFoliagePlacement(remaining);
			if (selected == null) {
				return false;
			}

			boolean placed = selected.tall()
				? tryPlaceTallGrass(world, growPos, selected.block())
				: tryPlaceSingleBlock(world, growPos, selected.block());
			if (placed) {
				return true;
			}
			remaining.remove(selected);
		}
		return false;
	}

	private static boolean tryPlaceSingleBlock(ServerLevel world, BlockPos pos, Block block) {
		if (world == null || pos == null || block == null) {
			return false;
		}
		BlockState next = block.defaultBlockState();
		if (!next.canSurvive(world, pos)) {
			return false;
		}
		setBlockAndUpdate(world, pos, next);
		return true;
	}

	private static WeightedFoliagePlacement pickWeightedFoliagePlacement(List<WeightedFoliagePlacement> placements) {
		if (placements == null || placements.isEmpty()) {
			return null;
		}

		int totalWeight = 0;
		for (WeightedFoliagePlacement placement : placements) {
			if (placement == null) {
				continue;
			}
			totalWeight += Math.max(0, placement.weight());
		}
		if (totalWeight <= 0) {
			return null;
		}

		int roll = ThreadLocalRandom.current().nextInt(totalWeight);
		int running = 0;
		for (WeightedFoliagePlacement placement : placements) {
			if (placement == null) {
				continue;
			}
			running += Math.max(0, placement.weight());
			if (roll < running) {
				return placement;
			}
		}
		return placements.get(placements.size() - 1);
	}

	private static List<WeightedFoliagePlacement> buildGrassFoliagePlacements() {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		NaturalGrowthConfigManager.FoliageGrowthSettings foliageGrowth = growthSettings.foliageGrowth();
		List<WeightedFoliagePlacement> placements = new ArrayList<>(3);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_SHORT_GRASS), foliageGrowth == null ? 0 : foliageGrowth.shortGrass().weight(), false);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_TALL_GRASS), foliageGrowth == null ? 0 : foliageGrowth.tallGrass().weight(), true);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_BUSH), foliageGrowth == null ? 0 : foliageGrowth.bush().weight(), false);
		return placements;
	}

	private static List<WeightedFoliagePlacement> buildDesertFoliagePlacements() {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		NaturalGrowthConfigManager.FoliageGrowthSettings desertGrowth = growthSettings.desertFoliageGrowth();
		List<WeightedFoliagePlacement> placements = new ArrayList<>(3);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_SHORT_DRY_GRASS), desertGrowth == null ? 0 : desertGrowth.shortGrass().weight(), false);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_TALL_DRY_GRASS), desertGrowth == null ? 0 : desertGrowth.tallGrass().weight(), true);
		addFoliagePlacement(placements, EcosystemConfigManager.resolveBlock(BLOCK_ID_DEAD_BUSH), desertGrowth == null ? 0 : desertGrowth.bush().weight(), false);
		return placements;
	}

	private static void addFoliagePlacement(List<WeightedFoliagePlacement> placements, Block block, int weight, boolean tall) {
		if (placements == null || block == null || weight <= 0) {
			return;
		}
		placements.add(new WeightedFoliagePlacement(block, weight, tall));
	}

	private static boolean placeSummerDesertTarget(ServerLevel world, BlockPos growPos) {
		return tryPlaceWeightedFoliageTarget(world, growPos, buildDesertFoliagePlacements());
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

	static boolean isBlockGrowthEnabled() {
		NaturalGrowthConfigManager.Settings growthSettings = settings;
		return isEnabled() && growthSettings.blockGrowth() != null && growthSettings.blockGrowth().isEnabled();
	}

	record WeightedFoliagePlacement(Block block, int weight, boolean tall) {
	}

	public static NaturalGrowthConfigManager.Settings getSettings() {
		return settings;
	}

	public static boolean isEnabled() {
		return EcosystemAPIManager.isEnabled() && settings.isEnabled();
	}

	static void discoverSurfaceSample(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.SurfaceDiscoverySample sample
	) {
		if (world == null || sample == null || sample.groundPos() == null || sample.groundState() == null || !isEnabled()) {
			return;
		}
		BlockPos groundPos = sample.groundPos();
		BlockState groundState = sample.groundState();
		BlockState aboveState = sample.aboveState();
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);

		boolean treeGround = isTrackableTreeGroundBlock(groundState);
		boolean cactusGround = EcosystemAPIManager.isCactusGrowthEnabled()
			&& EcosystemAPIManager.CACTUS_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock());
		boolean desertFoliageGround = EcosystemAPIManager.isDesertFoliageGrowthEnabled()
			&& EcosystemAPIManager.DESERT_FOLIAGE_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock());
		boolean grassGround = EcosystemAPIManager.isFoliageGrowthEnabled()
			&& groundState.getBlock() == Blocks.GRASS_BLOCK;
		boolean checkWildflowers = shouldCheckFoliageSurface(groundState, aboveState, NaturalGrowthConfigManager.FIELD_WILDFLOWERS);
		boolean checkPinkPetals = shouldCheckFoliageSurface(groundState, aboveState, NaturalGrowthConfigManager.FIELD_PINK_PETALS);
		boolean needsBiome = treeGround || cactusGround || desertFoliageGround || checkWildflowers || checkPinkPetals;
		boolean needsSubmerged = (EcosystemAPIManager.isBlockGrowthEnabled() && groundState.getBlock() == Blocks.DIRT)
			|| treeGround
			|| cactusGround
			|| desertFoliageGround
			|| grassGround
			|| checkWildflowers
			|| checkPinkPetals;
		Holder<net.minecraft.world.level.biome.Biome> sampledBiome = needsBiome ? world.getBiome(groundPos) : null;
		Boolean sampledSubmerged = needsSubmerged
			? Boolean.valueOf(EcosystemNaturalErosionManager.isSubmerged(world, groundPos))
			: null;
		boolean aboveMaySupportGrowth = aboveState == null || aboveState.isAir() || aboveState.is(Blocks.SNOW);
		boolean aboveMaySupportAirGrowth = aboveState == null || aboveState.isAir();
		boolean hasPotentialCandidate = (treeGround && aboveMaySupportGrowth)
			|| ((cactusGround || desertFoliageGround || grassGround) && aboveMaySupportAirGrowth)
			|| checkWildflowers
			|| checkPinkPetals;
		String seasonId = hasPotentialCandidate
			? EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world))
			: null;

		if (groundState.getBlock() == Blocks.DIRT) {
			EcosystemAPIManager.trackDirtCandidateForMode(world, groundPos, groundState, "surface_dirt", aboveState, sampledSubmerged);
		}
		if (treeGround && aboveMaySupportGrowth) {
			pickTreeCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, sampledBiome, sampledSubmerged, seasonId);
		}
		if (cactusGround && aboveMaySupportAirGrowth) {
			pickCactusCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, sampledBiome, sampledSubmerged, seasonId);
		}
		if (grassGround && aboveMaySupportAirGrowth) {
			pickGrassCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, sampledSubmerged, seasonId);
		}
		if (desertFoliageGround && aboveMaySupportAirGrowth) {
			pickDesertFoliageGrowthCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, sampledBiome, sampledSubmerged, seasonId);
		}
		if (checkWildflowers) {
			pickFoliageCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, NaturalGrowthConfigManager.FIELD_WILDFLOWERS, sampledBiome, sampledSubmerged, seasonId);
		}
		if (checkPinkPetals) {
			pickFoliageCandidateAtSurface(world, chunkX, chunkZ, chunkKey, groundPos, groundState, aboveState, NaturalGrowthConfigManager.FIELD_PINK_PETALS, sampledBiome, sampledSubmerged, seasonId);
		}

	}

	private static BlockState discoveredGroundState(
		ServerLevel world,
		BlockPos groundPos,
		EcosystemAPIManager.SurfaceDiscoverySample sample
	) {
		return sample != null && groundPos.equals(sample.groundPos()) ? sample.groundState() : world.getBlockState(groundPos);
	}

	private static BlockState discoveredAboveState(
		BlockPos groundPos,
		EcosystemAPIManager.SurfaceDiscoverySample sample
	) {
		return sample != null && groundPos.equals(sample.groundPos()) ? sample.aboveState() : null;
	}

	private static boolean shouldCheckFoliageSurface(
		BlockState groundState,
		BlockState aboveState,
		String foliageType
	) {
		if (!isVegetationGrowthEnabled(foliageType)) {
			return false;
		}
		if (groundState == null || aboveState == null) {
			return true;
		}
		if (aboveState.isAir()) {
			return groundState.getBlock() == Blocks.GRASS_BLOCK;
		}
		Block foliageBlock = resolveFoliageBlock(foliageType);
		return foliageBlock != null && aboveState.getBlock() == foliageBlock;
	}

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		if (event == null || !isEnabled()) {
			return;
		}
		ServerLevel world = event.level();
		if (world == null || event.chunk() == null) {
			return;
		}

		long currentAbsoluteDayTime = EcosystemAPIManager.resolveCachedAbsoluteDayTime(world);
		int chunkX = event.chunk().getPos().x();
		int chunkZ = event.chunk().getPos().z();
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		if (shouldProcessCandidates(world, chunkKey, currentAbsoluteDayTime)) {
			processDirtCandidatesInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			processTreeCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			processCactusCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			processGrassCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			processDesertFoliageGrowthCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			processFoliageCandidateInChunk(world, chunkX, chunkZ, currentAbsoluteDayTime);
			refreshCandidateSchedule(chunkKey);
		}

		BlockPos surfaceGroundPosition = event.surfaceGroundPosition();
		BlockState surfaceGroundState = event.surfaceGroundState();
		if (surfaceGroundPosition == null || surfaceGroundState == null) {
			return;
		}
		EcosystemAPIManager.SurfaceDiscoverySample sample = new EcosystemAPIManager.SurfaceDiscoverySample(
			surfaceGroundPosition,
			surfaceGroundState,
			event.surfaceAboveState()
		);
		discoverSurfaceSample(world, chunkX, chunkZ, sample);
	}

	private static boolean hasTrackedCandidateInChunk(ServerLevel world, int chunkX, int chunkZ) {
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		return EcosystemAPIManager.dirtKeysByChunk.containsKey(chunkKey)
			|| treeCandidatesByChunk.containsKey(chunkKey)
			|| cactusCandidatesByChunk.containsKey(chunkKey)
			|| grassCandidatesByChunk.containsKey(chunkKey)
			|| desertFoliageGrowthCandidatesByChunk.containsKey(chunkKey)
			|| foliageCandidatesByChunk.containsKey(chunkKey);
	}

	private static boolean shouldProcessCandidates(
		ServerLevel world,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		long currentAbsoluteDayTime
	) {
		if (world == null || chunkKey == null || !hasTrackedCandidateInChunk(world, chunkKey.chunkX(), chunkKey.chunkZ())) {
			if (chunkKey != null) {
				NEXT_CANDIDATE_DUE_BY_CHUNK.remove(chunkKey);
				MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.remove(chunkKey);
			}
			return false;
		}

		Double nextDue = NEXT_CANDIDATE_DUE_BY_CHUNK.get(chunkKey);
		Long maxLastProcessed = MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.get(chunkKey);
		return nextDue == null
			|| currentAbsoluteDayTime >= nextDue
			|| (maxLastProcessed != null && currentAbsoluteDayTime < maxLastProcessed);
	}

	static void registerCandidateSchedule(
		EcosystemAPIManager.ChunkRefKey chunkKey,
		double progressGrowthTicks,
		long lastProcessedAbsoluteDayTime,
		double requiredGrowthTicks
	) {
		if (chunkKey == null) {
			return;
		}
		CandidateSchedule schedule = new CandidateSchedule();
		includeCandidateSchedule(schedule, progressGrowthTicks, lastProcessedAbsoluteDayTime, requiredGrowthTicks);
		Double existingNextDue = NEXT_CANDIDATE_DUE_BY_CHUNK.get(chunkKey);
		if (existingNextDue == null || schedule.nextDue < existingNextDue) {
			NEXT_CANDIDATE_DUE_BY_CHUNK.put(chunkKey, schedule.nextDue);
		}
		Long existingMaxLastProcessed = MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.get(chunkKey);
		if (existingMaxLastProcessed == null || schedule.maxLastProcessed > existingMaxLastProcessed) {
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.put(chunkKey, schedule.maxLastProcessed);
		}
	}

	static void refreshCandidateSchedule(EcosystemAPIManager.ChunkRefKey chunkKey) {
		if (chunkKey == null) {
			return;
		}

		CandidateSchedule schedule = new CandidateSchedule();
		Set<String> dirtKeys = EcosystemAPIManager.dirtKeysByChunk.get(chunkKey);
		if (dirtKeys != null) {
			for (String dirtKey : dirtKeys) {
				EcosystemAPIManager.DirtState dirt = EcosystemAPIManager.dirtBlocksByKey.get(dirtKey);
				if (dirt != null) {
					includeCandidateSchedule(schedule, dirt.progressGrowthTicks, dirt.lastProcessedAbsoluteDayTime, dirt.requiredGrowthTicks);
				}
			}
		}

		includeCandidateSchedule(schedule, treeCandidatesByChunk.get(chunkKey));
		includeCandidateSchedule(schedule, cactusCandidatesByChunk.get(chunkKey));
		includeGrassCandidateSchedule(schedule, grassCandidatesByChunk.get(chunkKey));
		includeGrassCandidateSchedule(schedule, desertFoliageGrowthCandidatesByChunk.get(chunkKey));
		includeFoliageCandidateSchedule(schedule, foliageCandidatesByChunk.get(chunkKey));

		if (schedule.nextDue == Double.POSITIVE_INFINITY) {
			NEXT_CANDIDATE_DUE_BY_CHUNK.remove(chunkKey);
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.remove(chunkKey);
		} else {
			NEXT_CANDIDATE_DUE_BY_CHUNK.put(chunkKey, schedule.nextDue);
			MAX_CANDIDATE_LAST_PROCESSED_BY_CHUNK.put(chunkKey, schedule.maxLastProcessed);
		}
	}

	private static void includeCandidateSchedule(
		CandidateSchedule schedule,
		EcosystemAPIManager.TreeCandidateState candidate
	) {
		if (candidate != null) {
			includeCandidateSchedule(schedule, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
		}
	}

	private static void includeCandidateSchedule(
		CandidateSchedule schedule,
		EcosystemAPIManager.CactusCandidateState candidate
	) {
		if (candidate != null) {
			includeCandidateSchedule(schedule, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
		}
	}

	private static void includeGrassCandidateSchedule(
		CandidateSchedule schedule,
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates
	) {
		if (candidates == null) {
			return;
		}
		for (EcosystemAPIManager.GrassCandidateState candidate : candidates.values()) {
			if (candidate != null) {
				includeCandidateSchedule(schedule, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
			}
		}
	}

	private static void includeFoliageCandidateSchedule(
		CandidateSchedule schedule,
		Map<Long, EcosystemAPIManager.FoliageCandidateState> candidates
	) {
		if (candidates == null) {
			return;
		}
		for (EcosystemAPIManager.FoliageCandidateState candidate : candidates.values()) {
			if (candidate != null) {
				includeCandidateSchedule(schedule, candidate.progressGrowthTicks, candidate.lastProcessedAbsoluteDayTime, candidate.requiredGrowthTicks);
			}
		}
	}

	private static void includeCandidateSchedule(
		CandidateSchedule schedule,
		double progressGrowthTicks,
		long lastProcessedAbsoluteDayTime,
		double requiredGrowthTicks
	) {
		if (schedule == null) {
			return;
		}
		double required = Double.isFinite(requiredGrowthTicks) ? Math.max(1.0d, requiredGrowthTicks) : 1.0d;
		double progress = Double.isFinite(progressGrowthTicks)
			? Math.max(0.0d, Math.min(required, progressGrowthTicks))
			: 0.0d;
		long lastProcessed = Math.max(0L, lastProcessedAbsoluteDayTime);
		double due = lastProcessed + Math.max(0.0d, required - progress);
		schedule.nextDue = Math.min(schedule.nextDue, due);
		schedule.maxLastProcessed = Math.max(schedule.maxLastProcessed, lastProcessed);
	}

	private static final class CandidateSchedule {
		private double nextDue = Double.POSITIVE_INFINITY;
		private long maxLastProcessed = Long.MIN_VALUE;
	}

	private static void processDirtCandidatesInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			EcosystemAPIManager.levelId(world), chunkX, chunkZ
		);
		Set<String> dirtKeys = EcosystemAPIManager.dirtKeysByChunk.get(chunkKey);
		if (dirtKeys == null || dirtKeys.isEmpty()) {
			return;
		}
		for (String dirtKey : List.copyOf(dirtKeys)) {
			EcosystemAPIManager.DirtState dirt = EcosystemAPIManager.dirtBlocksByKey.get(dirtKey);
			if (dirt != null) {
				processDirtAtPosition(world, chunkX, chunkZ, currentAbsoluteDayTime, dirt.mode, dirt.dirtPos);
			}
		}
	}

	static void removeCandidatesAt(EcosystemAPIManager.ChunkRefKey chunkKey, long packedPosition) {
		if (chunkKey == null || packedPosition == Long.MIN_VALUE) {
			return;
		}
		EcosystemAPIManager.TreeCandidateState tree = treeCandidatesByChunk.get(chunkKey);
		if (tree != null && tree.groundPos == packedPosition) {
			removeTreeCandidate(chunkKey);
		}
		EcosystemAPIManager.CactusCandidateState cactus = cactusCandidatesByChunk.get(chunkKey);
		if (cactus != null && cactus.groundPos == packedPosition) {
			removeCactusCandidate(chunkKey);
		}
		removeGrassCandidateAt(grassCandidatesByChunk, chunkKey, packedPosition, EcosystemAPIManager.CANDIDATE_GRASS);
		removeGrassCandidateAt(desertFoliageGrowthCandidatesByChunk, chunkKey, packedPosition, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		Map<Long, EcosystemAPIManager.FoliageCandidateState> foliage = foliageCandidatesByChunk.get(chunkKey);
		if (foliage != null && foliage.remove(packedPosition) != null) {
			EcosystemAPIManager.removeCandidatePositionBit(
				chunkKey.levelId(), packedPosition, EcosystemAPIManager.CANDIDATE_FOLIAGE
			);
			if (foliage.isEmpty()) {
				foliageCandidatesByChunk.remove(chunkKey);
			}
		}
		refreshCandidateSchedule(chunkKey);
	}

	private static void removeGrassCandidateAt(
		Map<EcosystemAPIManager.ChunkRefKey, Map<Long, EcosystemAPIManager.GrassCandidateState>> candidatesByChunk,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		long packedPosition,
		int candidateBit
	) {
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates = candidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.remove(packedPosition) == null) {
			return;
		}
		EcosystemAPIManager.removeCandidatePositionBit(chunkKey.levelId(), packedPosition, candidateBit);
		if (candidates.isEmpty()) {
			candidatesByChunk.remove(chunkKey);
		}
	}

	static boolean isTrackableTreeGroundBlock(BlockState state) {
		return state != null && EcosystemAPIManager.TRACKABLE_TREE_GROUND_BLOCKS.contains(state.getBlock());
	}

	static boolean isSurfaceDirtCandidate(ServerLevel world, BlockPos blockPos, BlockState state) {
		return isSurfaceDirtCandidate(world, blockPos, state, null);
	}

	static boolean isSurfaceDirtCandidate(ServerLevel world, BlockPos blockPos, BlockState state, BlockState discoveredAboveState) {
		if (world == null || blockPos == null || state == null || !isBlockGrowthEnabled() || state.getBlock() != Blocks.DIRT) {
			return false;
		}
		return isSurfaceDirtCandidate(world, blockPos, state, discoveredAboveState, null);
	}

	static boolean isSurfaceDirtCandidate(
		ServerLevel world,
		BlockPos blockPos,
		BlockState state,
		BlockState discoveredAboveState,
		Boolean discoveredSubmerged
	) {
		if (world == null || blockPos == null || state == null || !isBlockGrowthEnabled() || state.getBlock() != Blocks.DIRT) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, blockPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}
		BlockState aboveState = discoveredAboveState == null ? world.getBlockState(blockPos.above()) : discoveredAboveState;
		return aboveState != null && aboveState.isAir();
	}

	static List<String> resolveTreeTypesForBiome(ServerLevel world, BlockPos groundPos) {
		if (world == null || groundPos == null) {
			return List.of();
		}

		return resolveTreeTypesForBiome(world.getBiome(groundPos));
	}

	static List<String> resolveTreeTypesForBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return List.of();
		}

		List<String> cachedTreeTypes = TREE_TYPES_BY_BIOME.get(biomeHolder);
		if (cachedTreeTypes != null) {
			return cachedTreeTypes;
		}
		List<String> treeTypes = new ArrayList<>(2);
		if (isSpruceBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_SPRUCE)) {
			treeTypes.add(TREE_TYPE_SPRUCE);
		}
		if (isBirchBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_BIRCH)) {
			treeTypes.add(TREE_TYPE_BIRCH);
		}
		if (isJungleBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_JUNGLE)) {
			treeTypes.add(TREE_TYPE_JUNGLE);
		}
		if (isMangroveBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_MANGROVE)) {
			treeTypes.add(TREE_TYPE_MANGROVE);
		}
		if (isAcaciaBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_ACACIA)) {
			treeTypes.add(TREE_TYPE_ACACIA);
		}
		if (isDarkOakBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_DARK_OAK)) {
			treeTypes.add(TREE_TYPE_DARK_OAK);
		}
		if (isPaleOakBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_PALE_OAK)) {
			treeTypes.add(TREE_TYPE_PALE_OAK);
		}
		if (isCherryBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_CHERRY)) {
			treeTypes.add(TREE_TYPE_CHERRY);
		}
		if (isOakBiome(biomeHolder) && EcosystemAPIManager.naturalGrowthSettings.treeGrowth().isEnabled(TREE_TYPE_OAK)) {
			treeTypes.add(TREE_TYPE_OAK);
		}
		List<String> result = treeTypes.isEmpty() ? List.of() : List.copyOf(treeTypes);
		TREE_TYPES_BY_BIOME.put(biomeHolder, result);
		return result;
	}

	static double resolveSurfaceDirtRequiredGrowthTicks(ServerLevel world) {
		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.dirtGrowthForSeason(seasonId);
		return randomDaysToTicks(range);
	}

	static double resolveTreeRequiredGrowthTicks(String treeType, String seasonId) {
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.treeGrowthForSeason(treeType, seasonId);
		return randomDaysToTicks(range);
	}

	static double resolveGrassRequiredGrowthTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.grassGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveDesertFoliageGrowthRequiredTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.desertFoliageGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveCactusRequiredGrowthTicks(ServerLevel world, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.cactusGrowthForSeason(normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static double resolveFoliageRequiredGrowthTicks(ServerLevel world, String foliageType, String seasonId) {
		String normalizedSeasonId = EcosystemConfigManager.normalize(seasonId);
		if (normalizedSeasonId.isBlank()) {
			normalizedSeasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		}
		EcosystemConfigManager.DayRange range = EcosystemAPIManager.naturalGrowthSettings.foliageGrowthForSeason(foliageType, normalizedSeasonId);
		return randomDaysToTicks(range);
	}

	static Block resolveFoliageBlock(String foliageType) {
		if (NaturalGrowthConfigManager.FIELD_PINK_PETALS.equals(foliageType)) {
			return EcosystemConfigManager.resolveBlock("minecraft:pink_petals");
		}
		if (NaturalGrowthConfigManager.FIELD_WILDFLOWERS.equals(foliageType)) {
			return EcosystemConfigManager.resolveBlock("minecraft:wildflowers");
		}
		return null;
	}

	static boolean isFoliageBiome(ServerLevel world, BlockPos pos, String foliageType) {
		if (world == null || pos == null) {
			return false;
		}
		return isFoliageBiome(world.getBiome(pos), foliageType);
	}

	static boolean isFoliageBiome(
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder,
		String foliageType
	) {
		if (biomeHolder == null) {
			return false;
		}
		if (isDesertOrBadlandsBiome(biomeHolder)) {
			return false;
		}
		if (NaturalGrowthConfigManager.FIELD_PINK_PETALS.equals(foliageType)) {
			return biomeHolder.is(Biomes.CHERRY_GROVE);
		}
		return biomeHolder.is(Biomes.MEADOW)
			|| biomeHolder.is(Biomes.BIRCH_FOREST)
			|| biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST);
	}

	static boolean isDesertOrBadlandsBiome(Holder<net.minecraft.world.level.biome.Biome> biomeHolder) {
		if (biomeHolder == null) {
			return false;
		}
		if (biomeHolder.is(BiomeTags.IS_BADLANDS)) {
			return true;
		}
		if (biomeHolder.is(Biomes.BADLANDS)
			|| biomeHolder.is(Biomes.ERODED_BADLANDS)
			|| biomeHolder.is(Biomes.WOODED_BADLANDS)) {
			return true;
		}
		if (biomeHolder.is(BADLANDS_BIOME_TAG)) {
			return true;
		}
		if (biomeHolder.is(DESERT_BIOME_TAG)) {
			return true;
		}
		return biomeHolder.is(Biomes.DESERT);
	}

	private static boolean isTreeTypeNaturalForBiome(
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder,
		String treeType
	) {
		if (biomeHolder == null || treeType == null || treeType.isBlank()) {
			return false;
		}

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

	static boolean isValidTreeGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String treeType) {
		return isValidTreeGroundCandidate(world, groundPos, groundState, treeType, null);
	}

	static boolean isValidTreeGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String treeType, BlockState discoveredAboveState) {
		return isValidTreeGroundCandidate(world, groundPos, groundState, treeType, discoveredAboveState, null, null);
	}

	static boolean isValidTreeGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		String treeType,
		BlockState discoveredAboveState,
		Holder<net.minecraft.world.level.biome.Biome> discoveredBiome,
		Boolean discoveredSubmerged
	) {
		if (world == null || groundPos == null || groundState == null || treeType == null || treeType.isBlank() || !isTreeGrowthEnabled(treeType)) {
			return false;
		}
		if (!isTrackableTreeGroundBlock(groundState)) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, groundPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}

		BlockPos saplingPos = groundPos.above();
		BlockState aboveState = discoveredAboveState == null ? world.getBlockState(saplingPos) : discoveredAboveState;
		boolean aboveFree = aboveState.isAir() || aboveState.is(Blocks.SNOW);
		if (!aboveFree) {
			return false;
		}
		if (!hasNaturalGrowthClearance(world, saplingPos)) {
			return false;
		}
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = discoveredBiome == null
			? world.getBiome(groundPos)
			: discoveredBiome;
		return isTreeTypeNaturalForBiome(biomeHolder, treeType);
	}

	static boolean isValidGrassGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		return isValidGrassGroundCandidate(world, groundPos, groundState, null);
	}

	static boolean isValidGrassGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, BlockState discoveredAboveState) {
		return isValidGrassGroundCandidate(world, groundPos, groundState, discoveredAboveState, null);
	}

	static boolean isValidGrassGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		BlockState discoveredAboveState,
		Boolean discoveredSubmerged
	) {
		if (world == null || groundPos == null || groundState == null || !EcosystemAPIManager.isFoliageGrowthEnabled()) {
			return false;
		}
		if (groundState.getBlock() != Blocks.GRASS_BLOCK) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, groundPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = discoveredAboveState == null ? world.getBlockState(growPos) : discoveredAboveState;
		return growState != null && growState.isAir();
	}

	static boolean isValidDesertFoliageGrowthGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		return isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState, null);
	}

	static boolean isValidDesertFoliageGrowthGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, BlockState discoveredAboveState) {
		return isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState, discoveredAboveState, null, null);
	}

	static boolean isValidDesertFoliageGrowthGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		BlockState discoveredAboveState,
		Holder<net.minecraft.world.level.biome.Biome> discoveredBiome,
		Boolean discoveredSubmerged
	) {
		if (world == null || groundPos == null || groundState == null || !EcosystemAPIManager.isDesertFoliageGrowthEnabled()) {
			return false;
		}
		if (!EcosystemAPIManager.DESERT_FOLIAGE_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock())) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, groundPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = discoveredBiome == null
			? world.getBiome(groundPos)
			: discoveredBiome;
		if (!isDesertOrBadlandsBiome(biomeHolder)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = discoveredAboveState == null ? world.getBlockState(growPos) : discoveredAboveState;
		return growState != null && growState.isAir();
	}

	static boolean isValidCactusGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState) {
		return isValidCactusGroundCandidate(world, groundPos, groundState, null);
	}

	static boolean isValidCactusGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, BlockState discoveredAboveState) {
		return isValidCactusGroundCandidate(world, groundPos, groundState, discoveredAboveState, false, null, null);
	}

	static boolean isValidCactusGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		BlockState discoveredAboveState,
		Holder<net.minecraft.world.level.biome.Biome> discoveredBiome,
		Boolean discoveredSubmerged
	) {
		return isValidCactusGroundCandidate(world, groundPos, groundState, discoveredAboveState, false, discoveredBiome, discoveredSubmerged);
	}

	private static boolean isValidCactusGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		BlockState discoveredAboveState,
		boolean clearanceAlreadyChecked,
		Holder<net.minecraft.world.level.biome.Biome> discoveredBiome,
		Boolean discoveredSubmerged
	) {
		if (world == null || groundPos == null || groundState == null || !EcosystemAPIManager.isCactusGrowthEnabled()) {
			return false;
		}
		if (!EcosystemAPIManager.CACTUS_GROWTH_GROUND_BLOCKS.contains(groundState.getBlock())) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, groundPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = discoveredBiome == null
			? world.getBiome(groundPos)
			: discoveredBiome;
		if (!isDesertOrBadlandsBiome(biomeHolder)) {
			return false;
		}
		BlockPos growPos = groundPos.above();
		BlockState growState = discoveredAboveState == null ? world.getBlockState(growPos) : discoveredAboveState;
		if (growState == null || !growState.isAir()) {
			return false;
		}
		if (!clearanceAlreadyChecked && !hasNaturalGrowthClearance(world, growPos)) {
			return false;
		}
		return Blocks.CACTUS.defaultBlockState().canSurvive(world, growPos);
	}

	/**
	 * Keeps natural trees and cacti from growing into nearby structures or one
	 * another. The support block is below the target and is intentionally not
	 * part of the scan; the target itself may be air or replaceable snow.
	 */
	private static boolean hasNaturalGrowthClearance(ServerLevel world, BlockPos targetPos) {
		if (world == null || targetPos == null) {
			return false;
		}

		int radius = NATURAL_GROWTH_CLEARANCE_RADIUS;
		int radiusSquared = radius * radius;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = 0; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx == 0 && dy == 0 && dz == 0
						|| dx * dx + dy * dy + dz * dz > radiusSquared) {
						continue;
					}
					BlockPos position = targetPos.offset(dx, dy, dz);
					BlockState state = world.getBlockState(position);
					if (!state.isAir()
						&& !state.canBeReplaced()
						&& !state.getCollisionShape(world, position).isEmpty()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	static boolean isValidFoliageGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String foliageType) {
		return isValidFoliageGroundCandidate(world, groundPos, groundState, foliageType, null);
	}

	static boolean isValidFoliageGroundCandidate(ServerLevel world, BlockPos groundPos, BlockState groundState, String foliageType, BlockState discoveredAboveState) {
		return isValidFoliageGroundCandidate(world, groundPos, groundState, foliageType, discoveredAboveState, null, null);
	}

	static boolean isValidFoliageGroundCandidate(
		ServerLevel world,
		BlockPos groundPos,
		BlockState groundState,
		String foliageType,
		BlockState discoveredAboveState,
		Holder<net.minecraft.world.level.biome.Biome> discoveredBiome,
		Boolean discoveredSubmerged
	) {
		if (world == null || groundPos == null || groundState == null || !isVegetationGrowthEnabled(foliageType)) {
			return false;
		}
		boolean submerged = discoveredSubmerged == null
			? EcosystemNaturalErosionManager.isSubmerged(world, groundPos)
			: discoveredSubmerged;
		if (submerged) {
			return false;
		}
		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = discoveredBiome == null
			? world.getBiome(groundPos)
			: discoveredBiome;
		if (normalizedFoliageType.isBlank() || !isFoliageBiome(biomeHolder, normalizedFoliageType)) {
			return false;
		}

		BlockPos foliagePos = groundPos.above();
		BlockState foliageState = discoveredAboveState == null ? world.getBlockState(foliagePos) : discoveredAboveState;
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
			BlockState placed = EcosystemAPIManager.setFoliageAmount(foliageBlock.defaultBlockState(), 1);
			return placed.canSurvive(world, foliagePos);
		}

		if (foliageState.getBlock() != foliageBlock) {
			return false;
		}
		int amount = EcosystemAPIManager.getFoliageAmount(foliageState);
		int maxAmount = EcosystemAPIManager.getFoliageMaxAmount(foliageState);
		if (amount >= maxAmount) {
			return false;
		}
		BlockState updated = EcosystemAPIManager.setFoliageAmount(foliageState, amount + 1);
		return updated != foliageState && updated.canSurvive(world, foliagePos);
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

	private static String resolveSampleSeasonId(ServerLevel world, String seasonId) {
		return seasonId == null
			? EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world))
			: seasonId;
	}

	private static void pickTreeCandidateAtSurface(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		BlockPos groundPos,
		BlockState groundState,
		BlockState aboveState,
		Holder<net.minecraft.world.level.biome.Biome> sampledBiome,
		Boolean sampledSubmerged,
		String seasonId
	) {
		if (world == null || chunkKey == null || groundPos == null || groundState == null
			|| !EcosystemAPIManager.isNaturalGrowthEnabled()
			|| treeCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = sampledBiome == null
			? world.getBiome(groundPos)
			: sampledBiome;
		String resolvedSeasonId = resolveSampleSeasonId(world, seasonId);
		List<EcosystemAPIManager.TreeCandidateOption> options = new ArrayList<>();
		for (String treeType : resolveTreeTypesForBiome(biomeHolder)) {
			if (treeType == null || treeType.isBlank()
				|| !isValidTreeGroundCandidate(world, groundPos, groundState, treeType, aboveState, biomeHolder, sampledSubmerged)) {
				continue;
			}
			double requiredGrowthTicks = resolveTreeRequiredGrowthTicks(treeType, resolvedSeasonId);
			if (requiredGrowthTicks > 0.0d) {
				options.add(new EcosystemAPIManager.TreeCandidateOption(groundPos.asLong(), treeType, requiredGrowthTicks));
			}
		}
		selectTreeCandidateForChunk(world, chunkX, chunkZ, chunkKey, resolvedSeasonId, options);
	}

	private static void pickCactusCandidateAtSurface(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		BlockPos groundPos,
		BlockState groundState,
		BlockState aboveState,
		Holder<net.minecraft.world.level.biome.Biome> sampledBiome,
		Boolean sampledSubmerged,
		String seasonId
	) {
		if (world == null || chunkKey == null || groundPos == null || groundState == null
			|| !EcosystemAPIManager.isNaturalGrowthEnabled()
			|| cactusCandidatesByChunk.containsKey(chunkKey)
			|| !isValidCactusGroundCandidate(world, groundPos, groundState, aboveState, sampledBiome, sampledSubmerged)) {
			return;
		}

		String resolvedSeasonId = resolveSampleSeasonId(world, seasonId);
		double requiredGrowthTicks = resolveCactusRequiredGrowthTicks(world, resolvedSeasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}
		putCactusCandidate(
			chunkKey,
			new EcosystemAPIManager.CactusCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				groundPos.asLong(),
				resolvedSeasonId,
				requiredGrowthTicks,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world)
			)
		);
		EcosystemAPIManager.dirty = true;
	}

	private static void pickGrassCandidateAtSurface(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		BlockPos groundPos,
		BlockState groundState,
		BlockState aboveState,
		Boolean sampledSubmerged,
		String seasonId
	) {
		if (world == null || chunkKey == null || groundPos == null || groundState == null
			|| !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}
		Map<Long, EcosystemAPIManager.GrassCandidateState> existingCandidates = grassCandidatesByChunk.get(chunkKey);
		if (existingCandidates != null && existingCandidates.size() >= MAX_GRASS_CANDIDATES_PER_CHUNK) {
			return;
		}
		long packedGroundPos = groundPos.asLong();
		if (existingCandidates != null && existingCandidates.containsKey(packedGroundPos)
			|| !isValidGrassGroundCandidate(world, groundPos, groundState, aboveState, sampledSubmerged)) {
			return;
		}

		String resolvedSeasonId = resolveSampleSeasonId(world, seasonId);
		double requiredGrowthTicks = resolveGrassRequiredGrowthTicks(world, resolvedSeasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}
		long currentAbsoluteDayTime = TimeAPIManager.getCurrentAbsoluteDayTime(world);
		grassCandidatesByChunk
			.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
			.put(packedGroundPos, new EcosystemAPIManager.GrassCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				packedGroundPos,
				resolvedSeasonId,
				requiredGrowthTicks,
				0.0d,
				currentAbsoluteDayTime
			));
		EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), packedGroundPos, EcosystemAPIManager.CANDIDATE_GRASS);
		registerCandidateSchedule(chunkKey, 0.0d, currentAbsoluteDayTime, requiredGrowthTicks);
		EcosystemAPIManager.dirty = true;
	}

	private static void pickDesertFoliageGrowthCandidateAtSurface(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		BlockPos groundPos,
		BlockState groundState,
		BlockState aboveState,
		Holder<net.minecraft.world.level.biome.Biome> sampledBiome,
		Boolean sampledSubmerged,
		String seasonId
	) {
		if (world == null || chunkKey == null || groundPos == null || groundState == null
			|| !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}
		Map<Long, EcosystemAPIManager.GrassCandidateState> existingCandidates = desertFoliageGrowthCandidatesByChunk.get(chunkKey);
		if (existingCandidates != null && existingCandidates.size() >= MAX_DESERT_FOLIAGE_GROWTH_CANDIDATES_PER_CHUNK) {
			return;
		}
		long packedGroundPos = groundPos.asLong();
		if (existingCandidates != null && existingCandidates.containsKey(packedGroundPos)
			|| !isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState, aboveState, sampledBiome, sampledSubmerged)) {
			return;
		}

		String resolvedSeasonId = resolveSampleSeasonId(world, seasonId);
		double requiredGrowthTicks = resolveDesertFoliageGrowthRequiredTicks(world, resolvedSeasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}
		long currentAbsoluteDayTime = TimeAPIManager.getCurrentAbsoluteDayTime(world);
		desertFoliageGrowthCandidatesByChunk
			.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
			.put(packedGroundPos, new EcosystemAPIManager.GrassCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				packedGroundPos,
				resolvedSeasonId,
				requiredGrowthTicks,
				0.0d,
				currentAbsoluteDayTime
			));
		EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), packedGroundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		registerCandidateSchedule(chunkKey, 0.0d, currentAbsoluteDayTime, requiredGrowthTicks);
		EcosystemAPIManager.dirty = true;
	}

	private static void pickFoliageCandidateAtSurface(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		BlockPos groundPos,
		BlockState groundState,
		BlockState aboveState,
		String foliageType,
		Holder<net.minecraft.world.level.biome.Biome> sampledBiome,
		Boolean sampledSubmerged,
		String seasonId
	) {
		if (world == null || chunkKey == null || groundPos == null || groundState == null
			|| !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}
		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank()) {
			return;
		}
		Map<Long, EcosystemAPIManager.FoliageCandidateState> existingCandidates = foliageCandidatesByChunk.get(chunkKey);
		if (existingCandidates != null && existingCandidates.size() >= MAX_FOLIAGE_CANDIDATES_PER_CHUNK) {
			return;
		}
		long packedGroundPos = groundPos.asLong();
		EcosystemAPIManager.FoliageCandidateState existing = existingCandidates == null ? null : existingCandidates.get(packedGroundPos);
		if (existing != null && NaturalGrowthConfigManager.normalizeFoliageType(existing.foliageType).equals(normalizedFoliageType)) {
			return;
		}
		if (!isValidFoliageGroundCandidate(world, groundPos, groundState, normalizedFoliageType, aboveState, sampledBiome, sampledSubmerged)) {
			return;
		}

		String resolvedSeasonId = resolveSampleSeasonId(world, seasonId);
		double requiredGrowthTicks = resolveFoliageRequiredGrowthTicks(world, normalizedFoliageType, resolvedSeasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}
		long currentAbsoluteDayTime = TimeAPIManager.getCurrentAbsoluteDayTime(world);
		foliageCandidatesByChunk
			.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
			.put(packedGroundPos, new EcosystemAPIManager.FoliageCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				packedGroundPos,
				normalizedFoliageType,
				resolvedSeasonId,
				requiredGrowthTicks,
				0.0d,
				currentAbsoluteDayTime
			));
		EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), packedGroundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
		registerCandidateSchedule(chunkKey, 0.0d, currentAbsoluteDayTime, requiredGrowthTicks);
		EcosystemAPIManager.dirty = true;
	}

	static void pickTreeCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeGroundCandidates) {
		pickTreeCandidateForChunk(world, chunkX, chunkZ, treeGroundCandidates, null);
	}

	static void pickTreeCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> treeGroundCandidates, EcosystemAPIManager.SurfaceDiscoverySample sample) {
		if (world == null || treeGroundCandidates == null || treeGroundCandidates.isEmpty() || !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		if (treeCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		List<EcosystemAPIManager.TreeCandidateOption> options = resolveTreeCandidateOptions(
			world,
			treeGroundCandidates,
			sample,
			seasonId
		);
		selectTreeCandidateForChunk(world, chunkX, chunkZ, chunkKey, seasonId, options);
	}

	private static List<EcosystemAPIManager.TreeCandidateOption> resolveTreeCandidateOptions(
		ServerLevel world,
		Set<Long> treeGroundCandidates,
		EcosystemAPIManager.SurfaceDiscoverySample sample,
		String seasonId
	) {
		List<EcosystemAPIManager.TreeCandidateOption> options = new ArrayList<>();
		for (Long packedPos : treeGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			BlockState groundState = discoveredGroundState(world, groundPos, sample);
			for (String treeType : resolveTreeTypesForBiome(world, groundPos)) {
				if (treeType == null || treeType.isBlank()) {
					continue;
				}
				if (!isValidTreeGroundCandidate(world, groundPos, groundState, treeType, discoveredAboveState(groundPos, sample))) {
					continue;
				}
				double requiredGrowthTicks = resolveTreeRequiredGrowthTicks(treeType, seasonId);
				if (requiredGrowthTicks <= 0.0d) {
					continue;
				}
				options.add(new EcosystemAPIManager.TreeCandidateOption(groundPos.asLong(), treeType, requiredGrowthTicks));
			}
		}
		return options;
	}

	private static void selectTreeCandidateForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		EcosystemAPIManager.ChunkRefKey chunkKey,
		String seasonId,
		List<EcosystemAPIManager.TreeCandidateOption> options
	) {
		if (world == null || chunkKey == null || options == null || options.isEmpty()
			|| treeCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		EcosystemAPIManager.TreeCandidateOption selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
		putTreeCandidate(
			chunkKey,
			new EcosystemAPIManager.TreeCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				selected.groundPos(),
				selected.treeType(),
				seasonId,
				selected.requiredGrowthTicks(),
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world)
			)
		);
		EcosystemAPIManager.dirty = true;
	}

	static void pickCactusCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> cactusGroundCandidates) {
		pickCactusCandidateForChunk(world, chunkX, chunkZ, cactusGroundCandidates, null);
	}

	static void pickCactusCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> cactusGroundCandidates, EcosystemAPIManager.SurfaceDiscoverySample sample) {
		if (world == null || cactusGroundCandidates == null || cactusGroundCandidates.isEmpty() || !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		if (cactusCandidatesByChunk.containsKey(chunkKey)) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveCactusRequiredGrowthTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : cactusGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (!isValidCactusGroundCandidate(world, groundPos, discoveredGroundState(world, groundPos, sample), discoveredAboveState(groundPos, sample))) {
				continue;
			}
			options.add(packedPos);
		}
		if (options.isEmpty()) {
			return;
		}

		long selectedGroundPos = options.get(ThreadLocalRandom.current().nextInt(options.size()));
		putCactusCandidate(
			chunkKey,
			new EcosystemAPIManager.CactusCandidateState(
				EcosystemAPIManager.levelId(world),
				chunkX,
				chunkZ,
				selectedGroundPos,
				seasonId,
				requiredGrowthTicks,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world)
			)
		);
		EcosystemAPIManager.dirty = true;
	}

	static void pickGrassCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> grassGroundCandidates) {
		pickGrassCandidateForChunk(world, chunkX, chunkZ, grassGroundCandidates, null);
	}

	static void pickGrassCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> grassGroundCandidates, EcosystemAPIManager.SurfaceDiscoverySample sample) {
		if (world == null || grassGroundCandidates == null || grassGroundCandidates.isEmpty() || !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.GrassCandidateState> existingCandidates = grassCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : grassGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidGrassGroundCandidate(world, groundPos, discoveredGroundState(world, groundPos, sample), discoveredAboveState(groundPos, sample))) {
				if (existingCandidates != null && existingCandidates.containsKey(packedPos)) {
					continue;
				}
				options.add(packedPos);
			}
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveGrassRequiredGrowthTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			grassCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
				.put(selectedGroundPos, new EcosystemAPIManager.GrassCandidateState(
					EcosystemAPIManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					TimeAPIManager.getCurrentAbsoluteDayTime(world)
				));
			EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), selectedGroundPos, EcosystemAPIManager.CANDIDATE_GRASS);
			registerCandidateSchedule(
				chunkKey,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world),
				requiredGrowthTicks
			);
			EcosystemAPIManager.dirty = true;
		}
	}

	static void pickDesertFoliageGrowthCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> groundCandidates) {
		pickDesertFoliageGrowthCandidateForChunk(world, chunkX, chunkZ, groundCandidates, null);
	}

	static void pickDesertFoliageGrowthCandidateForChunk(ServerLevel world, int chunkX, int chunkZ, Set<Long> groundCandidates, EcosystemAPIManager.SurfaceDiscoverySample sample) {
		if (world == null || groundCandidates == null || groundCandidates.isEmpty() || !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.GrassCandidateState> existingCandidates = desertFoliageGrowthCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : groundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidDesertFoliageGrowthGroundCandidate(world, groundPos, discoveredGroundState(world, groundPos, sample), discoveredAboveState(groundPos, sample))) {
				if (existingCandidates != null && existingCandidates.containsKey(packedPos)) {
					continue;
				}
				options.add(packedPos);
			}
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveDesertFoliageGrowthRequiredTicks(world, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			desertFoliageGrowthCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
				.put(selectedGroundPos, new EcosystemAPIManager.GrassCandidateState(
					EcosystemAPIManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					TimeAPIManager.getCurrentAbsoluteDayTime(world)
				));
			EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), selectedGroundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
			registerCandidateSchedule(
				chunkKey,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world),
				requiredGrowthTicks
			);
			EcosystemAPIManager.dirty = true;
		}
	}

	static void pickFoliageCandidateForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		String foliageType,
		Set<Long> foliageGroundCandidates
	) {
		pickFoliageCandidateForChunk(world, chunkX, chunkZ, foliageType, foliageGroundCandidates, null);
	}

	static void pickFoliageCandidateForChunk(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		String foliageType,
		Set<Long> foliageGroundCandidates,
		EcosystemAPIManager.SurfaceDiscoverySample sample
	) {
		if (world == null || foliageGroundCandidates == null || foliageGroundCandidates.isEmpty() || !EcosystemAPIManager.isNaturalGrowthEnabled()) {
			return;
		}

		String normalizedFoliageType = NaturalGrowthConfigManager.normalizeFoliageType(foliageType);
		if (normalizedFoliageType.isBlank()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.FoliageCandidateState> existingCandidates = foliageCandidatesByChunk.get(chunkKey);
		int existingCount = existingCandidates == null ? 0 : existingCandidates.size();
		int availableSlots = Math.max(0, 4 - existingCount);
		if (availableSlots <= 0) {
			return;
		}

		List<Long> options = new ArrayList<>();
		for (Long packedPos : foliageGroundCandidates) {
			if (packedPos == null) {
				continue;
			}
			BlockPos groundPos = BlockPos.of(packedPos);
			if (isValidFoliageGroundCandidate(world, groundPos, discoveredGroundState(world, groundPos, sample), normalizedFoliageType, discoveredAboveState(groundPos, sample))) {
				EcosystemAPIManager.FoliageCandidateState existing = existingCandidates == null ? null : existingCandidates.get(packedPos);
				if (existing != null && NaturalGrowthConfigManager.normalizeFoliageType(existing.foliageType).equals(normalizedFoliageType)) {
					continue;
				}
				options.add(packedPos);
			}
		}

		if (options.isEmpty()) {
			return;
		}

		String seasonId = EcosystemConfigManager.normalize(SeasonAPIManager.getCurrentSeasonId(world));
		double requiredGrowthTicks = resolveFoliageRequiredGrowthTicks(world, normalizedFoliageType, seasonId);
		if (requiredGrowthTicks <= 0.0d) {
			return;
		}

		int candidatesToAdd = Math.min(availableSlots, options.size());
		for (int i = 0; i < candidatesToAdd; i++) {
			int selectedIndex = ThreadLocalRandom.current().nextInt(options.size());
			long selectedGroundPos = options.remove(selectedIndex);
			foliageCandidatesByChunk
				.computeIfAbsent(chunkKey, ignored -> new LinkedHashMap<>())
				.put(selectedGroundPos, new EcosystemAPIManager.FoliageCandidateState(
					EcosystemAPIManager.levelId(world),
					chunkX,
					chunkZ,
					selectedGroundPos,
					normalizedFoliageType,
					seasonId,
					requiredGrowthTicks,
					0.0d,
					TimeAPIManager.getCurrentAbsoluteDayTime(world)
				));
			EcosystemAPIManager.addCandidatePositionBit(EcosystemAPIManager.levelId(world), selectedGroundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
			registerCandidateSchedule(
				chunkKey,
				0.0d,
				TimeAPIManager.getCurrentAbsoluteDayTime(world),
				requiredGrowthTicks
			);
			EcosystemAPIManager.dirty = true;
		}
	}

	static void processDirtAtPosition(
		ServerLevel world,
		int chunkX,
		int chunkZ,
		long currentAbsoluteDayTime,
		String targetMode,
		long selectedPosition
	) {
		if (!EcosystemAPIManager.isEnabled() || !EcosystemAPIManager.isModeEnabled(targetMode)) {
			return;
		}
		String worldLevelId = EcosystemAPIManager.levelId(world);
		EcosystemAPIManager.DirtState dirt = EcosystemAPIManager.dirtStateAt(worldLevelId, selectedPosition);
		if (dirt == null || !targetMode.equals(dirt.mode)) {
			return;
		}
		EcosystemAPIManager.ChunkRefKey targetChunkKey = new EcosystemAPIManager.ChunkRefKey(worldLevelId, chunkX, chunkZ);
		BlockPos dirtPos = BlockPos.of(dirt.dirtPos);

		double requiredTicks = Math.max(1.0d, dirt.requiredGrowthTicks);
		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			dirt.progressGrowthTicks,
			dirt.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			requiredTicks
		);
		boolean progressChanged = dirt.progressGrowthTicks != advanced.progressGrowthTicks()
			|| dirt.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| dirt.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		dirt.progressGrowthTicks = advanced.progressGrowthTicks();
		dirt.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		dirt.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		if (progressChanged) {
			EcosystemAPIManager.markChunkDirty(targetChunkKey);
		}
		double currentProgress = advanced.progressGrowthTicks();
		boolean due = currentProgress + 1e-6d >= requiredTicks;
		if (!due) {
			return;
		}
		BlockState state = world.getBlockState(dirtPos);
		boolean stillEligible = "wet".equals(dirt.mode)
			? EcosystemNaturalErosionManager.isWetTrackedCandidate(world, dirtPos, state, dirt.erosionRuleId)
			: EcosystemAPIManager.isCandidateForMode(world, dirtPos, state, dirt.mode);
		if (!EcosystemNaturalErosionManager.isTrackableGroundBlock(state) || !stillEligible) {
			EcosystemAPIManager.removeDirtStateByKey(dirt.key());
			return;
		}

		Block replacement = "surface_dirt".equals(dirt.mode)
			? resolveSurfaceDirtGrowthBlock(world, dirtPos)
			: EcosystemNaturalErosionManager.resolveWetGroundReplacementBlock(world, dirtPos, state, dirt.erosionRuleId);
		boolean replacementApplied = replacement != null && replacement != state.getBlock();
		if (replacementApplied) {
			setBlockAndUpdate(world, dirtPos, replacement.defaultBlockState());
		}
		EcosystemAPIManager.removeDirtStateByKey(dirt.key());
	}

	static void processTreeCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		EcosystemAPIManager.TreeCandidateState candidate = treeCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
			treeCandidatesByChunk.remove(chunkKey);
			EcosystemAPIManager.markChunkDirty(chunkKey);
			return;
		}

		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressGrowthTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredGrowthTicks
		);
		boolean progressChanged = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressGrowthTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		if (progressChanged) {
			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
		double currentProgress = advanced.progressGrowthTicks();
		boolean due = currentProgress + 1e-6d >= candidate.requiredGrowthTicks;
		if (!due) {
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidTreeGroundCandidate(world, groundPos, groundState, candidate.treeType)) {
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
			treeCandidatesByChunk.remove(chunkKey);
			EcosystemAPIManager.markChunkDirty(chunkKey);
			return;
		}

		if (currentProgress + 1e-6d >= candidate.requiredGrowthTicks) {
			tryGrowTreeAtGround(world, groundPos, candidate.treeType, true);
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_TREE);
			treeCandidatesByChunk.remove(chunkKey);
			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
	}

	static void processCactusCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		EcosystemAPIManager.CactusCandidateState candidate = cactusCandidatesByChunk.get(chunkKey);
		if (candidate == null) {
			return;
		}

		if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
			cactusCandidatesByChunk.remove(chunkKey);
			EcosystemAPIManager.markChunkDirty(chunkKey);
			return;
		}

		EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
			candidate.progressGrowthTicks,
			candidate.lastProcessedAbsoluteDayTime,
			currentAbsoluteDayTime,
			candidate.requiredGrowthTicks
		);
		boolean progressChanged = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
			|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
			|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
		candidate.progressGrowthTicks = advanced.progressGrowthTicks();
		candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
		candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
		if (progressChanged) {
			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
		double currentProgress = advanced.progressGrowthTicks();
		boolean due = currentProgress + 1e-6d >= candidate.requiredGrowthTicks;
		if (!due) {
			return;
		}

		BlockPos groundPos = BlockPos.of(candidate.groundPos);
		BlockState groundState = world.getBlockState(groundPos);
		if (!isValidCactusGroundCandidate(world, groundPos, groundState)) {
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
			cactusCandidatesByChunk.remove(chunkKey);
			EcosystemAPIManager.markChunkDirty(chunkKey);
			return;
		}

		if (currentProgress + 1e-6d >= candidate.requiredGrowthTicks) {
			tryGrowCactusAtGround(world, groundPos, true);
			EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_CACTUS);
			cactusCandidatesByChunk.remove(chunkKey);

			EcosystemAPIManager.markChunkDirty(chunkKey);
		}
	}

	static void processGrassCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates = grassCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		Iterator<EcosystemAPIManager.GrassCandidateState> iterator = candidates.values().iterator();
		while (iterator.hasNext()) {
			EcosystemAPIManager.GrassCandidateState candidate = iterator.next();
			if (candidate == null) {
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_GRASS);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);

			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressGrowthTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredGrowthTicks
			);
			boolean progressChanged = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressGrowthTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
			if (progressChanged) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
			double currentProgress = advanced.progressGrowthTicks();
			boolean due = currentProgress + 1e-6d >= candidate.requiredGrowthTicks;
			if (!due) {
				continue;
			}

			BlockState groundState = world.getBlockState(groundPos);
			if (!isValidGrassGroundCandidate(world, groundPos, groundState)) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_GRASS);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			if (currentProgress + 1e-6d >= candidate.requiredGrowthTicks) {
				tryGrowGrassAtGround(world, groundPos);
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_GRASS);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
		}

		if (candidates.isEmpty()) {
			grassCandidatesByChunk.remove(chunkKey);

			return;
		}
		if (removedAny) {

		}
	}


	static void processDesertFoliageGrowthCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.GrassCandidateState> candidates = desertFoliageGrowthCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		Iterator<EcosystemAPIManager.GrassCandidateState> iterator = candidates.values().iterator();
		while (iterator.hasNext()) {
			EcosystemAPIManager.GrassCandidateState candidate = iterator.next();
			if (candidate == null) {
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);

			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressGrowthTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredGrowthTicks
			);
			boolean progressChanged = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressGrowthTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
			if (progressChanged) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
			double currentProgress = advanced.progressGrowthTicks();
			boolean due = currentProgress + 1e-6d >= candidate.requiredGrowthTicks;
			if (!due) {
				continue;
			}

			BlockState groundState = world.getBlockState(groundPos);
			if (!isValidDesertFoliageGrowthGroundCandidate(world, groundPos, groundState)) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			if (currentProgress + 1e-6d >= candidate.requiredGrowthTicks) {
				tryGrowDesertFoliageAtGround(world, groundPos);
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
		}

		if (candidates.isEmpty()) {
			desertFoliageGrowthCandidatesByChunk.remove(chunkKey);

			return;
		}
		if (removedAny) {

		}
	}


	static void processFoliageCandidateInChunk(ServerLevel world, int chunkX, int chunkZ, long currentAbsoluteDayTime) {
		if (world == null || !isEnabled()) {
			return;
		}

		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(EcosystemAPIManager.levelId(world), chunkX, chunkZ);
		Map<Long, EcosystemAPIManager.FoliageCandidateState> candidates = foliageCandidatesByChunk.get(chunkKey);
		if (candidates == null || candidates.isEmpty()) {
			return;
		}

		boolean removedAny = false;
		Iterator<EcosystemAPIManager.FoliageCandidateState> iterator = candidates.values().iterator();
		while (iterator.hasNext()) {
			EcosystemAPIManager.FoliageCandidateState candidate = iterator.next();
			if (candidate == null) {
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}
			if (!candidate.levelId.equals(EcosystemAPIManager.levelId(world)) || candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			BlockPos groundPos = BlockPos.of(candidate.groundPos);

			EcosystemAPIManager.CandidateProgress advanced = EcosystemAPIManager.advanceCandidateProgress(
				candidate.progressGrowthTicks,
				candidate.lastProcessedAbsoluteDayTime,
				currentAbsoluteDayTime,
				candidate.requiredGrowthTicks
			);
			boolean progressChanged = candidate.progressGrowthTicks != advanced.progressGrowthTicks()
				|| candidate.lastProcessedAbsoluteDayTime != advanced.lastProcessedAbsoluteDayTime()
				|| candidate.startedAbsoluteDayTime != advanced.startedAbsoluteDayTime();
			candidate.progressGrowthTicks = advanced.progressGrowthTicks();
			candidate.lastProcessedAbsoluteDayTime = advanced.lastProcessedAbsoluteDayTime();
			candidate.startedAbsoluteDayTime = advanced.startedAbsoluteDayTime();
			if (progressChanged) {
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
			double currentProgress = advanced.progressGrowthTicks();
			boolean due = currentProgress + 1e-6d >= candidate.requiredGrowthTicks;
			if (!due) {
				continue;
			}

			BlockState groundState = world.getBlockState(groundPos);
			if (!isValidFoliageGroundCandidate(world, groundPos, groundState, candidate.foliageType)) {
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
				continue;
			}

			if (currentProgress + 1e-6d >= candidate.requiredGrowthTicks) {
				tryGrowFoliageAtGround(world, groundPos, candidate.foliageType);
				EcosystemAPIManager.removeCandidatePositionBit(candidate.levelId, candidate.groundPos, EcosystemAPIManager.CANDIDATE_FOLIAGE);
				iterator.remove();
				removedAny = true;
				EcosystemAPIManager.markChunkDirty(chunkKey);
			}
		}

		if (candidates.isEmpty()) {
			foliageCandidatesByChunk.remove(chunkKey);

			return;
		}
		if (removedAny) {

		}
	}

	static double randomDaysToTicks(EcosystemConfigManager.DayRange range) {
		if (range == null) {
			return -1.0d;
		}
		return randomDaysToTicks(range.minDays(), range.maxDays());
	}

	private static double randomDaysToTicks(double minDays, double maxDays) {
		double min = Math.max(0.0d, minDays);
		double max = Math.max(min, maxDays);
		if (max <= 0.0d) {
			return -1.0d;
		}
		double days = min + ThreadLocalRandom.current().nextDouble(max - min + 1.0d);
		return Math.max(1.0d, days * TimeAPIManager.MINECRAFT_TICKS_PER_CYCLE);
	}

	private static void loadConfig() {
		NaturalGrowthConfigManager.Settings fallback = NaturalGrowthConfigManager.defaults();
		TREE_TYPES_BY_BIOME.clear();
		JsonObject defaults = NaturalGrowthConfigManager.buildDefaultsJson();
		try {
			Path rootDirectory = JSONAPIManager.getOrCreateGlobalSystemDirectory(CONFIG_FOLDER_NAME);
			Path file = rootDirectory.resolve(CONFIG_FILE_NAME + ".json");
			JsonObject normalized = JSONFormatAPIManager.ensureManagedFile(file, defaults);
			settings = NaturalGrowthConfigManager.fromJson(normalized);
			JSONFormatAPIManager.writeManagedFile(file, NaturalGrowthConfigManager.toJson(settings), defaults);
		} catch (IOException | RuntimeException exception) {
			settings = fallback;
			LOGGER.error("Failed to load EcosystemNaturalGrowthManager config; using defaults.", exception);
		}
	}


}
