package madoku.craft.java.ecosystem;

/** Built-in provider for natural erosion. */
public final class MadokuNaturalErosionProvider implements NaturalErosionProvider {
	@Override public void initialize() { EcosystemNaturalErosionManager.initialize(); }
	@Override public void reset() { EcosystemNaturalErosionManager.reset(); }
	@Override public NaturalErosionConfigManager.Settings getSettings() { return EcosystemNaturalErosionManager.getSettings(); }
	@Override public boolean isEnabled() { return EcosystemNaturalErosionManager.isEnabled(); }
	@Override public void onChunkTick(EcosystemChunkTickEvent event) {
		EcosystemNaturalErosionManager.onChunkTick(event);
	}
}
