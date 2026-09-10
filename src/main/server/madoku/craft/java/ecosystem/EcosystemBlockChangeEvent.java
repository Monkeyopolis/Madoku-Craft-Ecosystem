package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Event emitted after a loaded server block position changes state. */
public record EcosystemBlockChangeEvent(
	ServerLevel level,
	BlockPos position,
	BlockState previousState,
	BlockState newState
) {
}
