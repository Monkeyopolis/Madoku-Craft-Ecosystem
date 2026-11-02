package madoku.craft.java.ecosystem;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import madoku.craft.java.core.module.MadokuModuleIds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Ecosystem jar. */
public final class MadokuEcosystemInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public net.minecraft.resources.Identifier id() { return MadokuModuleIds.ECOSYSTEM; }
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuEcosystemManager.initialize(); }
	@Override public void reset() { MadokuEcosystemManager.reset(); }
	@Override public void loadPersistedData(MinecraftServer server) { MadokuEcosystemManager.loadPersistedData(server); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuEcosystemManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuEcosystemManager.onServerTick(server); }
}
