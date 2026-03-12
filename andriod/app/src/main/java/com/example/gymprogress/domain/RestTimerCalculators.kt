package com.example.gymprogress

internal fun computeRemainingSeconds(endElapsedRealtimeMs: Long, nowElapsedRealtimeMs: Long): Int {
    val millisRemaining = (endElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
    return ((millisRemaining + 999L) / 1_000L).toInt()
}

internal fun computeDelayUntilNextTick(endElapsedRealtimeMs: Long, nowElapsedRealtimeMs: Long): Long {
    val millisRemaining = (endElapsedRealtimeMs - nowElapsedRealtimeMs).coerceAtLeast(0L)
    if (millisRemaining == 0L) {
        return 0L
    }
    val secondsRemaining = computeRemainingSeconds(endElapsedRealtimeMs, nowElapsedRealtimeMs)
    return (millisRemaining - ((secondsRemaining.toLong() - 1L) * 1_000L)).coerceAtLeast(1L)
}

internal fun computeRemainingSecondsFromEpochMillis(endEpochMillis: Long, nowEpochMillis: Long): Int {
    val millisRemaining = (endEpochMillis - nowEpochMillis).coerceAtLeast(0L)
    return ((millisRemaining + 999L) / 1_000L).toInt()
}
