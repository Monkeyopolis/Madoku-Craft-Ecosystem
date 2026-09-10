package madoku.craft.java.ecosystem;

public interface EcosystemChunkTickProvider {
	default void initialize() { }
	default void reset() { }
	default void registerListener(EcosystemChunkTickListener listener) { }
	default void unregisterListener(EcosystemChunkTickListener listener) { }
	default void dispatch(EcosystemChunkTickEvent event) { }
}
