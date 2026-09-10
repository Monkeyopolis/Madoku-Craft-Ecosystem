package madoku.craft.java.ecosystem;

public final class MadokuEcosystemChunkTickProvider implements EcosystemChunkTickProvider {
	@Override public void initialize() { EcosystemChunkTickManager.initialize(); }
	@Override public void reset() { EcosystemChunkTickManager.reset(); }
	@Override public void registerListener(EcosystemChunkTickListener listener) { EcosystemChunkTickManager.registerListener(listener); }
	@Override public void unregisterListener(EcosystemChunkTickListener listener) { EcosystemChunkTickManager.unregisterListener(listener); }
	@Override public void dispatch(EcosystemChunkTickEvent event) { EcosystemChunkTickManager.dispatch(event); }
}
