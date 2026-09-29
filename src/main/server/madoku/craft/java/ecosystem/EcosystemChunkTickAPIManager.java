package madoku.craft.java.ecosystem;

import madoku.craft.java.core.runtime.AdaptiveIntervalAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Public contract for the active-chunk heartbeat shared by ecosystem systems. */
public final class EcosystemChunkTickAPIManager {
	static final String CANDIDATE_BUDGET_ADAPTIVE_SYSTEM_ID = "madoku-ecosystem-candidate-budget";
	private static final long MIN_CANDIDATE_BUDGET_INTERVAL_TICKS = 1L;
	private static final long MAX_CANDIDATE_BUDGET_INTERVAL_TICKS = 4L;
	private static final EcosystemChunkTickProvider UNAVAILABLE_PROVIDER = new EcosystemChunkTickProvider() { };
	private static volatile EcosystemChunkTickProvider provider = UNAVAILABLE_PROVIDER;

	private EcosystemChunkTickAPIManager() { }

	public static void registerProvider(EcosystemChunkTickProvider candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Ecosystem chunk-tick provider must not be null.");
		}
		provider = candidate;
	}

	public static void unregisterProvider() {
		provider = UNAVAILABLE_PROVIDER;
	}

	public static void initialize() {
		provider.initialize();
	}

	public static void reset() {
		provider.reset();
	}

	static void clearAdaptiveCandidateBudget() {
		AdaptiveIntervalAPIManager.clearSystem(CANDIDATE_BUDGET_ADAPTIVE_SYSTEM_ID);
	}

	public static void registerListener(EcosystemChunkTickListener listener) {
		provider.registerListener(listener);
	}

	public static void unregisterListener(EcosystemChunkTickListener listener) {
		provider.unregisterListener(listener);
	}

	public static void dispatch(ServerLevel level, LevelChunk chunk) {
		dispatch(level, chunk, -1);
	}

	/** Dispatches an active chunk heartbeat with the random tick speed used by vanilla. */
	public static void dispatch(ServerLevel level, LevelChunk chunk, int randomTickSpeed) {
		if (level == null || chunk == null || !EcosystemAPIManager.isEnabled()
			|| (!EcosystemNaturalGrowthManager.isEnabled()
				&& !EcosystemNaturalErosionManager.isEnabled()
				&& !EcosystemNaturalDecayManager.isEnabled())) {
			return;
		}

		String currentLevelId = EcosystemAPIManager.levelId(level);
		EcosystemAPIManager.ChunkRefKey chunkKey = new EcosystemAPIManager.ChunkRefKey(
			currentLevelId,
			chunk.getPos().x(),
			chunk.getPos().z()
		);
		long currentGameTime = level.getGameTime();
		long currentAbsoluteDayTime = EcosystemAPIManager.resolveCachedAbsoluteDayTime(
			level,
			currentLevelId,
			currentGameTime
		);
		boolean growthCandidateWork = EcosystemNaturalGrowthManager.hasDueCandidateWork(
			chunkKey, currentAbsoluteDayTime
		);
		boolean decayCandidateWork = EcosystemNaturalDecayManager.hasDueCandidateWork(
			chunkKey, currentAbsoluteDayTime
		);
		boolean candidateWork = growthCandidateWork || decayCandidateWork;
		BlockPos surfaceGroundPosition = EcosystemAPIManager.nextSurfaceGroundPosition(
			level,
			chunk,
			chunkKey,
			currentGameTime,
			candidateWork
		);
		if (surfaceGroundPosition == null && !candidateWork) {
			return;
		}

		BlockState surfaceGroundState = surfaceGroundPosition == null ? null : level.getBlockState(surfaceGroundPosition);
		BlockState surfaceAboveState = surfaceGroundPosition == null ? null : level.getBlockState(surfaceGroundPosition.above());
		long adaptiveIntervalTicks = AdaptiveIntervalAPIManager.resolve(
			CANDIDATE_BUDGET_ADAPTIVE_SYSTEM_ID,
			level.getServer(),
			MIN_CANDIDATE_BUDGET_INTERVAL_TICKS,
			MAX_CANDIDATE_BUDGET_INTERVAL_TICKS
		);
		EcosystemChunkTickWorkBudget workBudget = new EcosystemChunkTickWorkBudget(
			EcosystemChunkTickWorkBudget.maxCandidateOperationsForInterval(adaptiveIntervalTicks),
			adaptiveIntervalTicks
		);
		provider.dispatch(new EcosystemChunkTickEvent(
			level,
			chunk,
			surfaceGroundPosition,
			surfaceGroundState,
			surfaceAboveState,
			workBudget,
			currentAbsoluteDayTime,
			growthCandidateWork,
			decayCandidateWork
		));
	}
}
