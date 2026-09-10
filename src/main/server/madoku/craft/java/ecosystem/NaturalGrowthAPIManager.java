package madoku.craft.java.ecosystem;

/** Public contract for the ecosystem's natural growth subsystem. */
public final class NaturalGrowthAPIManager {
	private static final NaturalGrowthProvider UNAVAILABLE_PROVIDER = new NaturalGrowthProvider() { };
	private static volatile NaturalGrowthProvider provider = UNAVAILABLE_PROVIDER;

	private NaturalGrowthAPIManager() {
	}

	public static void registerProvider(NaturalGrowthProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Natural growth provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }

	public static void reset() { provider.reset(); }

	public static NaturalGrowthConfigManager.Settings getSettings() { return provider.getSettings(); }

	public static boolean isEnabled() { return provider.isEnabled(); }

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		provider.onChunkTick(event);
	}
}
