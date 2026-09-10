package madoku.craft.java.ecosystem;

import madoku.craft.java.core.data.DataSaveParticipant;
import madoku.craft.java.core.data.DataSaveParticipantAPIManager;
import net.minecraft.server.MinecraftServer;

/** Orchestrates the ecosystem subsystem through its public API contract. */
public final class MadokuEcosystemManager {
	private static final DataSaveParticipant DATA_SAVE_PARTICIPANT = new DataSaveParticipant() {
		@Override public String id() { return "ecosystem"; }
		@Override public void autosavePersistedData(MinecraftServer server) { MadokuEcosystemManager.autosavePersistedData(server); }
		@Override public void savePersistedData(MinecraftServer server) { MadokuEcosystemManager.savePersistedData(server); }
	};

	private static final EcosystemChunkTickListener GROWTH_CHUNK_TICK_LISTENER =
		MadokuEcosystemManager::dispatchGrowthChunkTick;
	private static final EcosystemChunkTickListener EROSION_CHUNK_TICK_LISTENER =
		MadokuEcosystemManager::dispatchErosionChunkTick;
	private static final EcosystemChunkTickListener DECAY_CHUNK_TICK_LISTENER =
		MadokuEcosystemManager::dispatchDecayChunkTick;
	private static final EcosystemBlockChangeListener CANDIDATE_INVALIDATION_LISTENER =
		EcosystemAPIManager::invalidateCandidatesAt;

	private MadokuEcosystemManager() {
	}

	private static void dispatchGrowthChunkTick(EcosystemChunkTickEvent event) {
		dispatchChunkTick(event, NaturalGrowthAPIManager::onChunkTick);
	}

	private static void dispatchErosionChunkTick(EcosystemChunkTickEvent event) {
		dispatchChunkTick(event, NaturalErosionAPIManager::onChunkTick);
	}

	private static void dispatchDecayChunkTick(EcosystemChunkTickEvent event) {
		dispatchChunkTick(event, NaturalDecayAPIManager::onChunkTick);
	}

	private static void dispatchChunkTick(
		EcosystemChunkTickEvent event,
		EcosystemChunkTickListener listener
	) {
		if (event == null) {
			return;
		}
		listener.onChunkTick(event);
	}

	/** Initializes the shared ecosystem runtime and each ecosystem subsystem. */
	public static void initialize() {
		DataSaveParticipantAPIManager.register(DATA_SAVE_PARTICIPANT);
		EcosystemAPIManager.initialize();
		EcosystemChunkTickAPIManager.registerProvider(new MadokuEcosystemChunkTickProvider());
		EcosystemChunkTickAPIManager.initialize();
		EcosystemBlockChangeAPIManager.registerProvider(new MadokuEcosystemBlockChangeProvider());
		EcosystemBlockChangeAPIManager.initialize();
		NaturalGrowthAPIManager.registerProvider(new MadokuNaturalGrowthProvider());
		NaturalErosionAPIManager.registerProvider(new MadokuNaturalErosionProvider());
		NaturalDecayAPIManager.registerProvider(new MadokuNaturalDecayProvider());
		NaturalGrowthAPIManager.initialize();
		NaturalErosionAPIManager.initialize();
		NaturalDecayAPIManager.initialize();
		registerChunkTickListeners();
		registerBlockChangeListeners();
		EcosystemAPIManager.refreshSettings();
	}

	private static void registerChunkTickListeners() {
		EcosystemChunkTickAPIManager.registerListener(GROWTH_CHUNK_TICK_LISTENER);
		EcosystemChunkTickAPIManager.registerListener(EROSION_CHUNK_TICK_LISTENER);
		EcosystemChunkTickAPIManager.registerListener(DECAY_CHUNK_TICK_LISTENER);
	}

	private static void registerBlockChangeListeners() {
		EcosystemBlockChangeAPIManager.registerListener(CANDIDATE_INVALIDATION_LISTENER);
	}

	/** Resets each ecosystem subsystem and the shared ecosystem runtime. */
	public static void reset() {
		EcosystemChunkTickAPIManager.unregisterListener(GROWTH_CHUNK_TICK_LISTENER);
		EcosystemChunkTickAPIManager.unregisterListener(EROSION_CHUNK_TICK_LISTENER);
		EcosystemChunkTickAPIManager.unregisterListener(DECAY_CHUNK_TICK_LISTENER);
		EcosystemBlockChangeAPIManager.unregisterListener(CANDIDATE_INVALIDATION_LISTENER);
		NaturalGrowthAPIManager.reset();
		NaturalErosionAPIManager.reset();
		NaturalDecayAPIManager.reset();
		EcosystemChunkTickAPIManager.reset();
		EcosystemBlockChangeAPIManager.reset();
		EcosystemAPIManager.reset();
	}

	public static void onServerTick(MinecraftServer server) { EcosystemAPIManager.onServerTick(server); }
	public static void onServerStarted(MinecraftServer server) {
		registerChunkTickListeners();
		registerBlockChangeListeners();
		EcosystemAPIManager.onServerStarted(server);
	}
	public static void loadPersistedData(MinecraftServer server) { EcosystemAPIManager.loadPersistedData(server); }
	public static void autosavePersistedData(MinecraftServer server) { EcosystemAPIManager.autosavePersistedData(server); }
	public static void savePersistedData(MinecraftServer server) { EcosystemAPIManager.savePersistedData(server); }
}
