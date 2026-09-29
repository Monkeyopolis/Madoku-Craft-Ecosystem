package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Public API for the shared server block-state change source. */
public final class EcosystemBlockChangeAPIManager {
	private static final EcosystemBlockChangeProvider UNAVAILABLE_PROVIDER = new EcosystemBlockChangeProvider() { };
	private static volatile EcosystemBlockChangeProvider provider = UNAVAILABLE_PROVIDER;
	private static final ThreadLocal<Boolean> SKIP_INDIRECT_SHAPE_UPDATES = new ThreadLocal<>();

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

	/**
	 * Temporarily skips vanilla's recursive indirect shape propagation for
	 * ecosystem-owned placement work on the current server thread.
	 */
	public static IndirectShapeUpdateScope beginIndirectShapeUpdateOverride() {
		Boolean previous = SKIP_INDIRECT_SHAPE_UPDATES.get();
		SKIP_INDIRECT_SHAPE_UPDATES.set(Boolean.TRUE);
		return new IndirectShapeUpdateScope(previous);
	}

	/** Used by the Level mixin to check the current scoped override. */
	public static boolean shouldSkipIndirectShapeUpdates() {
		return Boolean.TRUE.equals(SKIP_INDIRECT_SHAPE_UPDATES.get());
	}

	public static void dispatch(ServerLevel level, BlockPos position, BlockState previousState, BlockState newState) {
		if (level != null && position != null && previousState != null && newState != null) {
			provider.dispatch(new EcosystemBlockChangeEvent(level, position, previousState, newState));
		}
	}

	public static final class IndirectShapeUpdateScope implements AutoCloseable {
		private final Boolean previous;
		private boolean closed;

		private IndirectShapeUpdateScope(Boolean previous) {
			this.previous = previous;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (previous == null) {
				SKIP_INDIRECT_SHAPE_UPDATES.remove();
			} else {
				SKIP_INDIRECT_SHAPE_UPDATES.set(previous);
			}
		}
	}

}
