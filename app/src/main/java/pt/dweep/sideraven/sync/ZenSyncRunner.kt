/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import mozilla.components.support.base.log.logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Where Zen Sync stands, for the settings status line and the Space row's problem dot.
 *
 * @property lastSynced When the last sync succeeded (epoch ms), or null.
 * @property problem Why the last sync failed, or null when it succeeded.
 */
data class ZenSyncRunStatus(
    val running: Boolean = false,
    val lastSynced: Long? = null,
    val problem: ZenSyncStatus.Reason? = null,
)

/**
 * Runs Zen Sync while the app is open (never in the background). "Sync now" always syncs. With
 * Sync automatically on (the default) it also syncs at app start and whenever the app comes to the
 * front, every [PERIOD_MS] while it is in front, a few seconds after the user changes Spaces, pins,
 * Folders or Essentials, and alongside Fenix's own sync. One sync at a time.
 */
class ZenSyncRunner(
    private val zenSync: ZenSync,
    private val settings: ZenSyncSettingsStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private val state = MutableStateFlow(ZenSyncRunStatus())
    val status: StateFlow<ZenSyncRunStatus> = state.asStateFlow()
    private val previews = MutableStateFlow<List<DesktopSendPreview>>(emptyList())
    val sending: StateFlow<List<DesktopSendPreview>> = previews.asStateFlow()

    private val mutex = Mutex()
    private var pending: Job? = null
    private var ticking: Job? = null
    private val activeSends = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, Job>()

    /** Explicit sends also work with automatic sync off. The phone tab is never modified. */
    fun sendToDesktop(spaceId: String, url: String, title: String, onResult: (DesktopSendResult) -> Unit): Job {
        val key = spaceId to url
        // A repeated tap retries the same operation rather than creating another row or copy.
        activeSends[key]?.takeIf { it.isActive }?.let { return it }
        val request = previews.value.firstOrNull { it.spaceId == spaceId && it.url == url }
            ?: DesktopSendPreview(pt.dweep.sideraven.newPinId(), spaceId, url, title)
        // Publish synchronously: the UI does not wait for credentials, disk, network, or another sync.
        previews.update { current -> if (current.any { it.requestId == request.requestId }) current else listOf(request) + current }
        pending?.cancel()
        val job = scope.launch {
            mutex.withLock {
                state.value = state.value.copy(running = true)
                var sendId: String? = null
                var failure: ZenSyncStatus.Reason? = null
                var result = DesktopSendResult.Unavailable
                withContext(io) {
                    try {
                        val queuedId = zenSync.queueDesktopTab(spaceId, url, title)
                        sendId = queuedId
                        previews.update { list -> list.map { if (it.requestId == request.requestId) it.copy(recordId = queuedId) else it } }
                        if (zenSync.sendDesktopTabNow(queuedId) == SyncOutcome.ReadOnly) {
                            failure = ZenSyncStatus.Reason.NewerVersion
                            result = DesktopSendResult.Queued
                        } else {
                            result = if (zenSync.isDesktopSendPending(sendId)) DesktopSendResult.Queued else DesktopSendResult.Uploaded
                        }
                    } catch (e: SyncError) {
                        failure = ZenSyncCheck.reasonOf(e)
                        result = sendId?.let { if (zenSync.isDesktopSendPending(it)) DesktopSendResult.Queued else DesktopSendResult.Uploaded }
                            ?: DesktopSendResult.Unavailable
                    } catch (_: IOException) {
                        failure = ZenSyncStatus.Reason.Network
                        result = sendId?.let { if (zenSync.isDesktopSendPending(it)) DesktopSendResult.Queued else DesktopSendResult.Uploaded }
                            ?: DesktopSendResult.Unavailable
                    } finally {
                        if (sendId == null) previews.update { list -> list.filterNot { it.requestId == request.requestId } }
                        refreshSendPreviews()
                    }
                }
                state.value = if (failure == null) {
                    ZenSyncRunStatus(lastSynced = clock())
                } else {
                    state.value.copy(running = false, problem = failure)
                }
                onResult(result)
            }
            // Explicit sends trigger a full sync immediately, even when automatic sync is off.
            syncNow()
        }
        activeSends[key] = job
        job.invokeOnCompletion { activeSends.remove(key, job) }
        return job
    }

    /** Called on IO under the sync mutex; restore queued rows and clear completed uploads. */
    private fun refreshSendPreviews() {
        val queued = zenSync.pendingDesktopSends().asReversed().map {
            DesktopSendPreview(it.id, it.spaceId, it.url, it.title, recordId = it.id)
        }
        previews.update { current -> current.filter { it.recordId == null } + queued }
    }

    private val automatic get() = settings.settings.value.let { it.enabled && it.firstSyncDone && it.syncAutomatically }

    /** An automatic sync (app start, Fenix's sync): only with Sync automatically on. */
    fun autoSync() {
        if (automatic) syncNow()
    }

    /** The app came to the front (sync now, then every [PERIOD_MS]) or went to the back (stop). */
    fun inForeground(front: Boolean) {
        ticking?.cancel()
        ticking = null
        if (!front) return
        // Ticks while in front; each tick syncs only if Sync automatically is on by then, so turning
        // it on (or finishing First Sync) mid-session counts from the next tick.
        ticking =
            scope.launch {
                while (true) {
                    if (automatic) syncNow().join()
                    delay(PERIOD_MS)
                }
            }
    }

    /** Sync now (app start, "Sync now", Fenix's sync). */
    fun syncNow(): Job =
        scope.launch {
            if (!settings.settings.value.let { it.enabled && it.firstSyncDone }) return@launch
            mutex.withLock {
                state.value = state.value.copy(running = true)
                val failure =
                    withContext(io) {
                        try {
                            if (zenSync.sync() == SyncOutcome.ReadOnly) ZenSyncStatus.Reason.NewerVersion else null
                        } catch (e: SyncError) {
                            Logger("ZenSync").warn("Zen Sync failed: ${e::class.simpleName}: ${e.message}", e)
                            ZenSyncCheck.reasonOf(e)
                        } catch (_: IOException) {
                            ZenSyncStatus.Reason.Network
                        } finally {
                            refreshSendPreviews()
                        }
                    }
                state.value =
                    if (failure == null) {
                        ZenSyncRunStatus(running = false, lastSynced = clock(), problem = null)
                    } else {
                        state.value.copy(running = false, problem = failure)
                    }
            }
        }

    /** A local change: sync a few seconds later, restarting the wait on further changes. */
    fun localChanged() {
        if (!automatic) return
        pending?.cancel()
        pending =
            scope.launch {
                delay(debounceMs)
                syncNow().join()
            }
    }

    companion object {
        const val DEBOUNCE_MS = 3_000L

        /** How often an app in front syncs on its own. */
        const val PERIOD_MS = 5 * 60_000L
    }
}

enum class DesktopSendResult { Uploaded, Queued, Unavailable }

/** The synced view of the tablet's data, compared to decide whether a local change needs a sync. */
data class SyncedView(
    val spaces: List<pt.dweep.sideraven.Space>,
    val pinned: List<pt.dweep.sideraven.PinnedTab>,
    val folders: List<pt.dweep.sideraven.Folder>,
    val essentials: List<pt.dweep.sideraven.Essential>,
) {
    companion object {
        fun of(
            data: pt.dweep.sideraven.SpacesData?,
            essentials: List<pt.dweep.sideraven.Essential>,
        ) = SyncedView(
            spaces = data?.spaces.orEmpty().map { it.copy(lastSelectedTabId = null) },
            pinned = data?.pinned.orEmpty(),
            folders = data?.folders.orEmpty(),
            essentials = essentials,
        )
    }
}
