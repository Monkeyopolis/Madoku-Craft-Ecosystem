package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Public contract for the active-chunk heartbeat shared by ecosystem systems. */
public final class EcosystemChunkTickAPIManager {
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

	public static void registerListener(EcosystemChunkTickListener listener) {
		provider.registerListener(listener);
	}

	public static void unregisterListener(EcosystemChunkTickListener listener) {
		provider.unregisterListener(listener);
	}

	public static void dispatch(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null || !EcosystemAPIManager.isEnabled()
			|| (!EcosystemNaturalGrowthManager.isEnabled()
				&& !EcosystemNaturalErosionManager.isEnabled()
				&& !EcosystemNaturalDecayManager.isEnabled())) {
			return;
		}

		BlockPos surfaceGroundPosition = EcosystemAPIManager.nextSurfaceGroundPosition(level, chunk);
		BlockState surfaceGroundState = surfaceGroundPosition == null ? null : level.getBlockState(surfaceGroundPosition);
		BlockState surfaceAboveState = surfaceGroundPosition == null ? null : level.getBlockState(surfaceGroundPosition.above());
		provider.dispatch(new EcosystemChunkTickEvent(
			level,
			chunk,
			surfaceGroundPosition,
			surfaceGroundState,
			surfaceAboveState
		));
	}
}
