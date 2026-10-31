package app.duinch.adbagent.features.tunnel.application

import kotlin.math.min
import kotlin.random.Random

class ComputeReconnectBackoffUseCase(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 30_000L,
    private val jitterRatio: Double = 0.2
) {
    operator fun invoke(attempt: Int, random: Random = Random.Default): Long {
        val safeAttempt = attempt.coerceAtLeast(1).coerceAtMost(10)
        val exponential = baseDelayMs * (1L shl (safeAttempt - 1))
        val capped = min(exponential, maxDelayMs)
        val maxJitter = (capped * jitterRatio).toLong().coerceAtLeast(1L)
        val offset = random.nextLong(-maxJitter, maxJitter + 1)
        return (capped + offset).coerceIn(baseDelayMs / 2, maxDelayMs)
    }
}
