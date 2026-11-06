/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import mozilla.components.support.base.log.logger.Logger
import org.json.JSONObject
import java.io.IOException
import java.net.URL

/**
 * What the signed-in Firefox Account provides for Sync: the token server, an OAuth access token,
 * and the `oldsync` scoped key (its id is the token server's `X-KeyID`; [scopedKey] is `k`).
 */
data class SyncCredentials(
    val tokenServerUrl: String,
    val accessToken: String,
    val keyId: String,
    val scopedKey: String,
)

/** The Firefox Account Zen Sync uses. Null credentials mean "not signed in". */
fun interface ZenAccount {
    suspend fun credentials(): SyncCredentials?
}

/** The decrypted `spaces` collection. */
data class ZenSnapshot(
    val records: List<ZenRecord>,
    /** Ids of records deleted on the server (encrypted tombstones). */
    val tombstones: Set<String>,
    /** Records that couldn't be decrypted or parsed (kept on the server; never overwritten). */
    val unreadable: Set<String>,
    /** Server timestamp per record id. */
    val modified: Map<String, Double>,
    val lastModified: Double?,
) {
    val layout: ZenLayout? get() = records.filterIsInstance<ZenLayout>().firstOrNull()

    /** What desktop has, as the Zen Sync settings show it. */
    fun summary(): ZenSummary {
        val tabs = records.filterIsInstance<ZenTab>().filterNot { it.isNormalTab }
        return ZenSummary(
            spaces = records.count { it is ZenSpace },
            pinnedTabs = tabs.count { !it.isEssential },
            essentials = tabs.count { it.isEssential },
            folders = records.count { it is ZenFolder },
        )
    }
}

data class ZenSummary(
    val spaces: Int,
    val pinnedTabs: Int,
    val essentials: Int,
    val folders: Int,
)

/** A connection to the account's Sync storage, with the `spaces` collection's key. */
class ZenConnection(
    val storage: SyncStorage,
    val keys: KeyBundle,
    private val prefsKeys: () -> KeyBundle = { keys },
) {
    /** Explicit support and opt-in; an empty desktop tab list is not a capability signal. */
    fun normalTabsEnabled(): Boolean {
        val key = prefsKeys()
        var enabled = false
        storage.readAll("prefs").records.forEach { record ->
            val values = runCatching {
                JSONObject(String(RecordCrypto.decrypt(record.payload, key), Charsets.UTF_8)).optJSONObject("value")
            }.getOrNull() ?: return@forEach
            if (values.has("zen.spaces-sync.normal-tabs")) enabled = ZenWire.flag(values.opt("zen.spaces-sync.normal-tabs")) == true
        }
        return enabled
    }

    /** Reads and decrypts the whole `spaces` collection. Blocking. */
    fun readSnapshot(): ZenSnapshot {
        val read = storage.readAll(SPACES_COLLECTION)
        val records = mutableListOf<ZenRecord>()
        val tombstones = mutableSetOf<String>()
        val unreadable = mutableSetOf<String>()
        read.records.forEach { stored ->
            val cleartext = runCatching { JSONObject(String(RecordCrypto.decrypt(stored.payload, keys), Charsets.UTF_8)) }.getOrNull()
            when {
                cleartext == null -> unreadable += stored.id
                ZenWire.isTombstone(cleartext) -> tombstones += stored.id
                else -> ZenWire.decode(cleartext)?.let(records::add) ?: run { if (!isKnownIgnored(cleartext)) unreadable += stored.id }
            }
        }
        return ZenSnapshot(records, tombstones, unreadable, read.records.associate { it.id to it.modified }, read.lastModified)
    }

    /**
     * The version of Zen's `spaces` engine as declared in `meta/global` (cleartext), or null when
     * it isn't declared. Sideraven understands up to [SUPPORTED_ENGINE_VERSION].
     */
    fun engineVersion(): Int? {
        val meta = storage.read("meta", "global") ?: return null
        return runCatching {
            JSONObject(JSONObject(meta.payload).getString("payload"))
        }.recoverCatching { JSONObject(meta.payload) }
            .getOrNull()
            ?.optJSONObject("engines")
            ?.optJSONObject(SPACES_COLLECTION)
            ?.let { engine -> (engine.opt("version") as? Number)?.toInt() }
    }

    /** Containers and normal records are dropped on purpose; anything else undecodable is unreadable. */
    private fun isKnownIgnored(cleartext: JSONObject) = cleartext.opt("kind") == "container"

    companion object {
        /** The newest `spaces` engine version this Sideraven writes (Zen's `ZenSpacesSyncEngine`). */
        const val SUPPORTED_ENGINE_VERSION = 3

        /**
         * Exchanges the account's token for storage credentials, then reads `crypto/keys` with the
         * scoped key. Never creates `crypto/keys`.
         */
        fun open(
            credentials: SyncCredentials,
            transport: SyncHttpTransport = UrlConnectionTransport(),
        ): ZenConnection {
            val tokenUrl = URL(tokenEndpoint(credentials.tokenServerUrl))
            if (tokenUrl.protocol != "https") throw SyncError.Protocol("insecure sync endpoint")
            val response =
                try {
                    transport.execute(
                        SyncHttpRequest(
                            "GET",
                            tokenUrl,
                            mapOf("Authorization" to "Bearer ${credentials.accessToken}", "X-KeyID" to credentials.keyId, "Accept" to "application/json"),
                            null,
                        ),
                    )
                } catch (e: IOException) {
                    throw SyncError.Http(0, "network: ${e.message}")
                }
            if (response.status == 401 || response.status == 403) throw SyncError.Auth("token server refused (${response.status})")
            if (response.status !in 200..299) throw SyncError.Http(response.status)
            val token = runCatching { JSONObject(String(response.body, Charsets.UTF_8)) }.getOrNull() ?: throw SyncError.Auth("token server response")
            val endpoint = ZenWire.string(token, "api_endpoint") ?: throw SyncError.Auth("token server response")
            val id = ZenWire.string(token, "id") ?: throw SyncError.Auth("token server response")
            val key = ZenWire.string(token, "key") ?: throw SyncError.Auth("token server response")
            val storage = HttpSyncStorage(endpoint, id, key.toByteArray(Charsets.UTF_8), transport)
            val syncKey = KeyBundle.fromScopedKey(credentials.scopedKey)
            return ZenConnection(storage, collectionKeys(storage, syncKey)) { collectionKeys(storage, syncKey, "prefs") }
        }

        /**
         * Fenix's account gives the token server's base URL; Firefox's own sync (and Z-Sync) call
         * its Sync 1.5 endpoint under it. Accepts either form.
         */
        internal fun tokenEndpoint(base: String): String {
            val trimmed = base.trimEnd('/')
            return if (trimmed.endsWith(TOKEN_PATH)) trimmed else trimmed + TOKEN_PATH
        }

        private const val TOKEN_PATH = "/1.0/sync/1.5"

        /** The `spaces` collection key from `crypto/keys`, falling back to the default key. */
        internal fun collectionKeys(
            storage: SyncStorage,
            syncKey: KeyBundle,
            collection: String = SPACES_COLLECTION,
        ): KeyBundle {
            val stored = storage.read("crypto", "keys") ?: throw SyncError.KeysMissing()
            val keys = JSONObject(String(RecordCrypto.decrypt(stored.payload, syncKey), Charsets.UTF_8))
            val pair = keys.optJSONObject("collections")?.optJSONArray(collection) ?: keys.optJSONArray("default") ?: throw SyncError.KeysMissing()
            return KeyBundle.fromBase64Pair(pair.getString(0), pair.getString(1))
        }
    }
}

/** What the Zen Sync settings show about the connection. */
sealed interface ZenSyncStatus {
    data object SignedOut : ZenSyncStatus

    data class Connected(val summary: ZenSummary) : ZenSyncStatus

    /** Signed in, but desktop hasn't synced any Spaces (Zen's Spaces sync is off, or release Zen). */
    data object NothingFromDesktop : ZenSyncStatus

    data class Failed(val reason: Reason) : ZenSyncStatus

    enum class Reason { KeysMissing, Network, SignIn, NewerVersion, Other }
}

/** Reads (never writes) what the account holds. Blocking network: call off the main thread. */
object ZenSyncCheck {
    suspend fun run(
        account: ZenAccount,
        transport: SyncHttpTransport = UrlConnectionTransport(),
    ): ZenSyncStatus {
        val credentials =
            runCatching { account.credentials() }.getOrElse { return ZenSyncStatus.Failed(ZenSyncStatus.Reason.SignIn) }
                ?: return ZenSyncStatus.SignedOut
        return try {
            val snapshot = ZenConnection.open(credentials, transport).readSnapshot()
            val summary = snapshot.summary()
            if (summary.spaces == 0 && summary.essentials == 0) ZenSyncStatus.NothingFromDesktop else ZenSyncStatus.Connected(summary)
        } catch (e: SyncError) {
            logger.warn("Zen Sync check failed: ${e::class.simpleName}: ${e.message}", e)
            ZenSyncStatus.Failed(reasonOf(e))
        } catch (e: IOException) {
            logger.warn("Zen Sync check failed: network", e)
            ZenSyncStatus.Failed(ZenSyncStatus.Reason.Network)
        }
    }

    private val logger = Logger("ZenSync")

    fun reasonOf(error: SyncError): ZenSyncStatus.Reason =
        when (error) {
            is SyncError.KeysMissing -> ZenSyncStatus.Reason.KeysMissing
            is SyncError.Auth -> ZenSyncStatus.Reason.SignIn
            is SyncError.Http -> if (error.status == 0) ZenSyncStatus.Reason.Network else ZenSyncStatus.Reason.Other
            else -> ZenSyncStatus.Reason.Other
        }
}
