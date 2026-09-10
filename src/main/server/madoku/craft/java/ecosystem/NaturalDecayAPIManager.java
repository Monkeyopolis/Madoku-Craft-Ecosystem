package madoku.craft.java.ecosystem;

/** Public contract for the ecosystem's natural decay subsystem. */
public final class NaturalDecayAPIManager {
	private static final NaturalDecayProvider UNAVAILABLE_PROVIDER = new NaturalDecayProvider() { };
	private static volatile NaturalDecayProvider provider = UNAVAILABLE_PROVIDER;

	private NaturalDecayAPIManager() {
	}

	public static void registerProvider(NaturalDecayProvider candidate) { if (candidate == null) throw new IllegalArgumentException("Natural decay provider must not be null."); provider = candidate; }
	public static void unregisterProvider() { provider = UNAVAILABLE_PROVIDER; }
	public static void initialize() { provider.initialize(); }

	public static void reset() { provider.reset(); }

	public static NaturalDecayConfigManager.Settings getSettings() { return provider.getSettings(); }

	public static boolean isEnabled() { return provider.isEnabled(); }

	public static void onChunkTick(EcosystemChunkTickEvent event) {
		provider.onChunkTick(event);
	}
}
