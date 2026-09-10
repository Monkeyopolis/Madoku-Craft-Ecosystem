package madoku.craft.java.ecosystem;

/** Public contract for the ecosystem's natural erosion subsystem. */
public final class NaturalErosionAPIManager {
	private static final NaturalErosionProvider UNAVAILABLE_PROVIDER = new NaturalErosionProvider() { };
	private static volatile NaturalErosionProvider provider = UNAVAILABLE_PROVIDER;

	private NaturalErosionAPIManager() {
	}

	public static void registerProvider(NaturalErosionProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Natural erosion provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }

	public static void reset() { provider.reset(); }

	public static NaturalErosionConfigManager.Settings getSettings() { return provider.getSettings(); }

	public static boolean isEnabled() { return provider.isEnabled(); }

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		provider.onChunkTick(event);
	}
}
