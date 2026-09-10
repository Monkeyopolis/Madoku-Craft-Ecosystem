package madoku.craft.java.ecosystem;

@FunctionalInterface
public interface EcosystemChunkTickListener {
	void onChunkTick(EcosystemChunkTickEvent event);
}
