package madoku.craft.java.ecosystem;

/** Built-in provider for natural decay. */
public final class MadokuNaturalDecayProvider implements NaturalDecayProvider {
	@Override public void initialize() { EcosystemNaturalDecayManager.initialize(); }
	@Override public void reset() { EcosystemNaturalDecayManager.reset(); }
	@Override public NaturalDecayConfigManager.Settings getSettings() { return EcosystemNaturalDecayManager.getSettings(); }
	@Override public boolean isEnabled() { return EcosystemNaturalDecayManager.isEnabled(); }
	@Override public void onChunkTick(EcosystemChunkTickEvent event) {
		EcosystemNaturalDecayManager.onChunkTick(event);
	}
}
