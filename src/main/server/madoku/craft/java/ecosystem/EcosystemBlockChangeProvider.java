package madoku.craft.java.ecosystem;

/** API provider for server block-state changes used by ecosystem invalidation. */
public interface EcosystemBlockChangeProvider {
	default void initialize() { }
	default void reset() { }
	default void registerListener(EcosystemBlockChangeListener listener) { }
	default void unregisterListener(EcosystemBlockChangeListener listener) { }
	default void dispatch(EcosystemBlockChangeEvent event) { }
}
