package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemChunkTickAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives ecosystem systems one callback for each active vanilla chunk tick. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEcosystemChunkTickMixin {
	@Inject(method = "tickChunk", at = @At("TAIL"))
	private void madokuCraft$dispatchChunkTick(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
		EcosystemChunkTickAPIManager.dispatch((ServerLevel) (Object) this, chunk);
	}
}
