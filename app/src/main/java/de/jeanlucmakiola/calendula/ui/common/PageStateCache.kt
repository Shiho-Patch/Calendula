package de.jeanlucmakiola.calendula.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

/**
 * One shared state per pager page — a week, a day, a month — so a page and the
 * screen's anchor read the same query, and the pages either side stay loaded
 * across a swipe.
 *
 * Every entry shares on its own job, cancelled when [keep] lets it go: a
 * `stateIn` on the ViewModel's scope would stay running, holding its last state,
 * until the ViewModel itself was cleared. Main thread only.
 *
 * @param keep whether a cached key is still worth holding once [get] asks for another.
 */
class PageStateCache<K, S>(
    private val scope: CoroutineScope,
    private val initial: S,
    private val keep: (cached: K, requested: K) -> Boolean,
    private val load: (K) -> Flow<S>,
) {
    private class Entry<S>(val state: StateFlow<S>, val job: Job)

    private val entries = HashMap<K, Entry<S>>()

    /** The number of entries held, for tests. */
    internal val size: Int get() = entries.size

    fun get(key: K): StateFlow<S> {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (cached, entry) = iterator.next()
            if (cached != key && !keep(cached, key)) {
                entry.job.cancel()
                iterator.remove()
            }
        }
        return entries.getOrPut(key) {
            val job = SupervisorJob(scope.coroutineContext[Job])
            Entry(
                state = load(key).stateIn(scope + job, SharingStarted.WhileSubscribed(5_000L), initial),
                job = job,
            )
        }.state
    }
}
