package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Public API for the shared server block-state change source. */
public final class EcosystemBlockChangeAPIManager {
	private static final EcosystemBlockChangeProvider UNAVAILABLE_PROVIDER = new EcosystemBlockChangeProvider() { };
	private static volatile EcosystemBlockChangeProvider provider = UNAVAILABLE_PROVIDER;

	private EcosystemBlockChangeAPIManager() {
	}

	public static void registerProvider(EcosystemBlockChangeProvider candidate) {
		if (candidate == null) {
			throw new IllegalArgumentException("Ecosystem block-change provider must not be null.");
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

	public static void registerListener(EcosystemBlockChangeListener listener) {
		provider.registerListener(listener);
	}

	public static void unregisterListener(EcosystemBlockChangeListener listener) {
		provider.unregisterListener(listener);
	}

	public static void dispatch(ServerLevel level, BlockPos position, BlockState previousState, BlockState newState) {
		if (level != null && position != null && previousState != null && newState != null) {
			provider.dispatch(new EcosystemBlockChangeEvent(level, position, previousState, newState));
		}
	}
}
