package madoku.craft.java.ecosystem;

import java.util.concurrent.CopyOnWriteArrayList;

final class EcosystemChunkTickManager {
	private static final CopyOnWriteArrayList<EcosystemChunkTickListener> LISTENERS = new CopyOnWriteArrayList<>();

	private EcosystemChunkTickManager() { }

	static void initialize() { }

	static void reset() {
		LISTENERS.clear();
	}

	static void registerListener(EcosystemChunkTickListener listener) {
		if (listener != null && !LISTENERS.contains(listener)) {
			LISTENERS.add(listener);
		}
	}

	static void unregisterListener(EcosystemChunkTickListener listener) {
		if (listener != null) {
			LISTENERS.remove(listener);
		}
	}

	static void dispatch(EcosystemChunkTickEvent event) {
		if (event == null) {
			return;
		}
		for (EcosystemChunkTickListener listener : LISTENERS) {
			listener.onChunkTick(event);
		}
	}
}
