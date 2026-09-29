package madoku.craft.mixin.core;

import madoku.craft.java.ecosystem.EcosystemBlockChangeAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Routes successful server block-state changes to ecosystem invalidation listeners. */
@Mixin(Level.class)
public abstract class LevelEcosystemBlockChangeMixin {
	@Redirect(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;updateIndirectNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V"
		)
	)
	private void madokuCraft$skipEcosystemIndirectNeighbourShapes(
		BlockState state,
		LevelAccessor level,
		BlockPos position,
		int flags,
		int updateLimit
	) {
		if (!EcosystemBlockChangeAPIManager.shouldSkipIndirectShapeUpdates()) {
			state.updateIndirectNeighbourShapes(level, position, flags, updateLimit);
		}
	}

	@Redirect(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;updateNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V"
		)
	)
	private void madokuCraft$skipEcosystemNeighbourShapes(
		BlockState state,
		LevelAccessor level,
		BlockPos position,
		int flags,
		int updateLimit
	) {
		if (!EcosystemBlockChangeAPIManager.shouldSkipIndirectShapeUpdates()) {
			state.updateNeighbourShapes(level, position, flags, updateLimit);
		}
	}

	@Redirect(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState madokuCraft$dispatchBlockChange(
		LevelChunk chunk,
		BlockPos position,
		BlockState newState,
		int flags
	) {
		BlockState previousState = chunk.setBlockState(position, newState, flags);
		if (previousState != null
			&& !previousState.equals(newState)
			&& (Object) this instanceof net.minecraft.server.level.ServerLevel level) {
			EcosystemBlockChangeAPIManager.dispatch(
				level,
				position,
				previousState,
				newState
			);
		}
		return previousState;
	}
}
