package madoku.craft.java.ecosystem;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** One active-chunk heartbeat used by all ecosystem subsystems. */
public record EcosystemChunkTickEvent(
	ServerLevel level,
	LevelChunk chunk,
	BlockPos surfaceGroundPosition,
	BlockState surfaceGroundState,
	BlockState surfaceAboveState,
	EcosystemChunkTickWorkBudget workBudget,
	long currentAbsoluteDayTime,
	boolean growthCandidateWork,
	boolean decayCandidateWork
) {
	public EcosystemChunkTickEvent(
		ServerLevel level,
		LevelChunk chunk,
		BlockPos surfaceGroundPosition,
		BlockState surfaceGroundState,
		BlockState surfaceAboveState
	) {
		this(
			level,
			chunk,
			surfaceGroundPosition,
			surfaceGroundState,
			surfaceAboveState,
			new EcosystemChunkTickWorkBudget(),
			Long.MIN_VALUE,
			false,
			false
		);
	}

	public EcosystemChunkTickEvent(
		ServerLevel level,
		LevelChunk chunk,
		BlockPos surfaceGroundPosition,
		BlockState surfaceGroundState,
		BlockState surfaceAboveState,
		EcosystemChunkTickWorkBudget workBudget
	) {
		this(
			level,
			chunk,
			surfaceGroundPosition,
			surfaceGroundState,
			surfaceAboveState,
			workBudget,
			Long.MIN_VALUE,
			false,
			false
		);
	}

	public EcosystemChunkTickEvent {
		workBudget = workBudget == null ? new EcosystemChunkTickWorkBudget() : workBudget;
	}
}
