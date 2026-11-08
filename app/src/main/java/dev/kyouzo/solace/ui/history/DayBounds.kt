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
