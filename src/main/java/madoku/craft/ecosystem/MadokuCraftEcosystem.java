package madoku.craft.ecosystem;

import madoku.craft.ecosystem.system.MadokuEcosystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class MadokuCraftEcosystem implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-ecosystem";

	@Override
	public void onInitialize() {
		MadokuEcosystem.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuEcosystem.reset();
			MadokuEcosystem.loadPersistedData(server);
			MadokuEcosystem.onServerStarted(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuEcosystem.savePersistedData(server);
			MadokuEcosystem.reset();
		});

		ServerTickEvents.END_SERVER_TICK.register(MadokuEcosystem::autosavePersistedData);
	}
}
