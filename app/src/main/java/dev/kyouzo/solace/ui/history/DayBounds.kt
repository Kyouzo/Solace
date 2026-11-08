package dev.kyouzo.solace.ui.history

import java.util.Calendar

/** Inclusive [start, end] epoch-millis of the local calendar day containing [millis]. */
internal fun dayBounds(millis: Long): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    return start to calendar.timeInMillis - 1
}

/** Hours, minutes and seconds (each the remainder within its unit) of a duration. */
internal fun durationParts(millis: Long): Triple<Long, Long, Long> {
    val totalSeconds = (millis.coerceAtLeast(0L)) / 1000
    return Triple(totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}

/** Evenly thins [items] to at most [max] entries, always keeping the first and the last. */
internal fun <T> downsample(items: List<T>, max: Int): List<T> {
    if (max < 2 || items.size <= max) return items
    val step = (items.size - 1).toDouble() / (max - 1)
    return List(max) { index -> items[(index * step).toInt().coerceAtMost(items.size - 1)] }.let { sampled ->
        // rounding can leave the last sample one short of the true end
        if (sampled.last() === items.last()) sampled else sampled.dropLast(1) + items.last()
    }
}
