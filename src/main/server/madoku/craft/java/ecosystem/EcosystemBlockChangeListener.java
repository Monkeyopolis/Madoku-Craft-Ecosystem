package madoku.craft.java.ecosystem;

@FunctionalInterface
public interface EcosystemBlockChangeListener {
	void onBlockChange(EcosystemBlockChangeEvent event);
}
