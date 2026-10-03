package io.github.monkeyadluffy.studioremote.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.monkeyadluffy.studioremote.R
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
@Composable
fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return if (s < 60) stringResource(R.string.duration_seconds, maxOf(s, 1))
    else stringResource(R.string.duration_minutes, s / 60, s % 60)
}

/** Clip and audio lengths: 2.987 → "3.0 s". */
@Composable
fun formatSeconds(seconds: Double): String = stringResource(R.string.duration_seconds_decimal, seconds)
