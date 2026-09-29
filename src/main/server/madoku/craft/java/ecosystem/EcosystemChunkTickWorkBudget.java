package madoku.craft.java.ecosystem;

/**
 * Shared per-chunk candidate-operation budget for one ecosystem heartbeat.
 *
 * <p>This is deliberately local to one chunk tick. It is not a global
 * workload cap, and surface discovery does not consume it.</p>
 */
public final class EcosystemChunkTickWorkBudget {
	public static final int DEFAULT_MAX_CANDIDATE_OPERATIONS = 4;
	private static final long MIN_ADAPTIVE_INTERVAL_TICKS = 1L;
	private static final long MAX_ADAPTIVE_INTERVAL_TICKS = 4L;
	static final int CANDIDATE_DIRT = 0;
	static final int CANDIDATE_TREE = 1;
	static final int CANDIDATE_CACTUS = 2;
	static final int CANDIDATE_GRASS = 3;
	static final int CANDIDATE_DESERT_FOLIAGE = 4;
	static final int CANDIDATE_FOLIAGE = 5;
	static final int CANDIDATE_DECAY = 6;
	static final int CANDIDATE_CATEGORY_COUNT = 7;

	private static final int LISTENER_CATEGORY_COUNT = 3;

	private final int maxCandidateOperations;
	private final long adaptiveIntervalTicks;
	private int candidateOperations;
	private boolean limitReached;
	private final int[] candidateOperationsByCategory = new int[CANDIDATE_CATEGORY_COUNT];
	private final long[] listenerNanos = new long[LISTENER_CATEGORY_COUNT];

	public EcosystemChunkTickWorkBudget() {
		this(DEFAULT_MAX_CANDIDATE_OPERATIONS, 0L);
	}

	public EcosystemChunkTickWorkBudget(int maxCandidateOperations) {
		this(maxCandidateOperations, 0L);
	}

	EcosystemChunkTickWorkBudget(int maxCandidateOperations, long adaptiveIntervalTicks) {
		if (maxCandidateOperations < 0) {
			throw new IllegalArgumentException("Maximum candidate operations must not be negative.");
		}
		this.maxCandidateOperations = maxCandidateOperations;
		this.adaptiveIntervalTicks = adaptiveIntervalTicks;
	}

	/** Maps the adaptive interval to the per-chunk candidate allowance for one heartbeat. */
	public static int maxCandidateOperationsForInterval(long adaptiveIntervalTicks) {
		long interval = Math.max(MIN_ADAPTIVE_INTERVAL_TICKS,
			Math.min(MAX_ADAPTIVE_INTERVAL_TICKS, adaptiveIntervalTicks));
		return (int) (MAX_ADAPTIVE_INTERVAL_TICKS + MIN_ADAPTIVE_INTERVAL_TICKS - interval);
	}

	/** Consumes one candidate operation from this chunk tick's allowance. */
	public boolean tryConsumeCandidateOperation() {
		return tryConsumeCandidateOperation(-1);
	}

	boolean tryConsumeCandidateOperation(int category) {
		if (candidateOperations >= maxCandidateOperations) {
			limitReached = true;
			return false;
		}
		candidateOperations++;
		if (category >= 0 && category < candidateOperationsByCategory.length) {
			candidateOperationsByCategory[category]++;
		}
		return true;
	}

	public int maxCandidateOperations() {
		return maxCandidateOperations;
	}

	/** Returns the adaptive interval that selected this budget, or {@code 0} for an unbound budget. */
	public long adaptiveIntervalTicks() {
		return adaptiveIntervalTicks;
	}

	public int candidateOperations() {
		return candidateOperations;
	}

	public int remainingCandidateOperations() {
		return maxCandidateOperations - candidateOperations;
	}

	public boolean hasRemaining() {
		return candidateOperations < maxCandidateOperations;
	}

	public boolean limitReached() {
		return limitReached || candidateOperations >= maxCandidateOperations;
	}

	int candidateOperationsFor(int category) {
		return category >= 0 && category < candidateOperationsByCategory.length
			? candidateOperationsByCategory[category]
			: 0;
	}

	void recordListenerNanos(int subsystem, long elapsedNanos) {
		if (subsystem >= 0 && subsystem < listenerNanos.length) {
			listenerNanos[subsystem] += Math.max(0L, elapsedNanos);
		}
	}

	long listenerNanosFor(int subsystem) {
		return subsystem >= 0 && subsystem < listenerNanos.length ? listenerNanos[subsystem] : 0L;
	}
}
