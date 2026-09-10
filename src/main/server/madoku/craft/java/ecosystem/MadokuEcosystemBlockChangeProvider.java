package madoku.craft.java.ecosystem;

public final class MadokuEcosystemBlockChangeProvider implements EcosystemBlockChangeProvider {
	@Override public void initialize() { EcosystemBlockChangeManager.initialize(); }
	@Override public void reset() { EcosystemBlockChangeManager.reset(); }
	@Override public void registerListener(EcosystemBlockChangeListener listener) { EcosystemBlockChangeManager.registerListener(listener); }
	@Override public void unregisterListener(EcosystemBlockChangeListener listener) { EcosystemBlockChangeManager.unregisterListener(listener); }
	@Override public void dispatch(EcosystemBlockChangeEvent event) { EcosystemBlockChangeManager.dispatch(event); }
}
