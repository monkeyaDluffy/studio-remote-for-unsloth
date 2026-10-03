package io.github.monkeyadluffy.studioremote.ui

import java.util.Locale

// Human-readable numbers shown across the screens.

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    else -> String.format(Locale.US, "%.0f MB", bytes / (1L shl 20).toDouble())
}

/** Context sizes: 32768 → "32K". */
fun formatTokens(n: Int): String = if (n >= 1024) "${n / 1024}K" else "$n"

/** Download and like counts: 8649679 → "8.6M". */
fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
    else -> "$n"
}

/** Thinking time: whole seconds, then minutes. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${maxOf(s, 1)} sn" else "${s / 60} dk ${s % 60} sn"
}

/** Clip and audio lengths: 2.987 → "3.0 sn". */
fun formatSeconds(seconds: Double): String = String.format(Locale.US, "%.1f sn", seconds)
