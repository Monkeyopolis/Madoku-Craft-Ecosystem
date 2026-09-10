package madoku.craft.java.ecosystem;

import java.util.concurrent.CopyOnWriteArrayList;

/** Internal listener registry for server block-state changes. */
final class EcosystemBlockChangeManager {
	private static final CopyOnWriteArrayList<EcosystemBlockChangeListener> LISTENERS = new CopyOnWriteArrayList<>();

	private EcosystemBlockChangeManager() {
	}

	static void initialize() {
	}

	static void reset() {
		LISTENERS.clear();
	}

	static void registerListener(EcosystemBlockChangeListener listener) {
		if (listener != null && !LISTENERS.contains(listener)) {
			LISTENERS.add(listener);
		}
	}

	static void unregisterListener(EcosystemBlockChangeListener listener) {
		if (listener != null) {
			LISTENERS.remove(listener);
		}
	}

	static void dispatch(EcosystemBlockChangeEvent event) {
		if (event == null) {
			return;
		}
		for (EcosystemBlockChangeListener listener : LISTENERS) {
			listener.onBlockChange(event);
		}
	}
}
