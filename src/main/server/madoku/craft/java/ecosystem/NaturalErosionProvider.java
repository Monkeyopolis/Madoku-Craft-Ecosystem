package madoku.craft.java.ecosystem;

/** Provider contract implemented by the module that owns natural erosion. */
public interface NaturalErosionProvider {
	default void initialize() { }
	default void reset() { }
	default NaturalErosionConfigManager.Settings getSettings() { return NaturalErosionConfigManager.defaults(); }
	default boolean isEnabled() { return false; }
	default void onChunkTick(EcosystemChunkTickEvent event) { }
}
