package com.souravgoswami.framefinder.work

import java.text.NumberFormat

object WorkContract {
    const val INDEX_WORK = "framefinder-photo-index"

    const val KEY_PHASE = "phase"
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_ESTIMATED_REMAINING_MS = "estimated_remaining_ms"
    const val KEY_ERROR = "error"
    const val KEY_ANALYZE_ALL = "analyze_all"
    const val KEY_FORCE_REBUILD = "force_rebuild"
    const val KEY_MODEL_ID = "model_id"

    const val PHASE_PREPARE = "Preparing bundled AI model"
    const val PHASE_SCAN = "Finding photos"
    const val PHASE_RESUME = "Checking saved analysis"
    const val PHASE_INDEX = "Analyzing photos"
    const val PHASE_PAUSING = "Pausing"
    const val PHASE_CLEANUP = "Updating photo index"
}

internal fun estimateRemainingMillis(done: Int, total: Int, elapsedMillis: Long): Long? {
    if (done <= 0 || total <= done || elapsedMillis <= 0L) return null
    return ((elapsedMillis.toDouble() / done) * (total - done))
        .toLong()
        .coerceAtLeast(1L)
}

fun formatScanProgress(done: Int, total: Int): String? {
    if (total <= 0) return null
    val completed = done.coerceIn(0, total)
    val percent = ((completed.toLong() * 100L) / total).coerceIn(0L, 100L)
    val formatter = NumberFormat.getIntegerInstance()
    return "${formatter.format(completed)} of ${formatter.format(total)} photos, $percent%"
}

fun formatEstimatedRemaining(remainingMillis: Long): String {
    if (remainingMillis < 60_000L) return "Less than a minute remaining"
    val minutes = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
    if (minutes < 60L) {
        val unit = if (minutes == 1L) "minute" else "minutes"
        return "About $minutes $unit remaining"
    }
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return if (remainder == 0L) {
        "About $hours h remaining"
    } else {
        "About $hours h $remainder min remaining"
    }
}
