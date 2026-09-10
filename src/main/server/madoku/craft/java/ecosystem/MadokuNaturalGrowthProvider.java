package madoku.craft.java.ecosystem;

/** Built-in provider for natural growth. */
public final class MadokuNaturalGrowthProvider implements NaturalGrowthProvider {
	@Override public void initialize() { EcosystemNaturalGrowthManager.initialize(); }
	@Override public void reset() { EcosystemNaturalGrowthManager.reset(); }
	@Override public NaturalGrowthConfigManager.Settings getSettings() { return EcosystemNaturalGrowthManager.getSettings(); }
	@Override public boolean isEnabled() { return EcosystemNaturalGrowthManager.isEnabled(); }
	@Override public void onChunkTick(EcosystemChunkTickEvent event) {
		EcosystemNaturalGrowthManager.onChunkTick(event);
	}
}
