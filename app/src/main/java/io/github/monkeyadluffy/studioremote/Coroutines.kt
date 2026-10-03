package io.github.monkeyadluffy.studioremote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Launches [block], handing any failure (but not cancellation) to [report]. */
fun CoroutineScope.launchReporting(report: (Throwable) -> Unit, block: suspend () -> Unit): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        report(e)
    }
}

/** Tolerates up to [maxFailures] failed polls in a row; a success resets the count. */
class Retry(private val maxFailures: Int) {
    private var failures = 0

    /** [block]'s result, or null after a failure still within budget; past it, the failure is thrown. */
    suspend fun <T> attempt(block: suspend () -> T): T? = try {
        block().also { failures = 0 }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        if (++failures > maxFailures) throw e
        null
    }
}
