/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * An in-memory Firefox Sync: a token server and a Sync 1.5 storage node in one HTTP transport.
 * Records are stored encrypted with test keys, exactly as Zen desktop would leave them; reads
 * page, writes honour `X-If-Unmodified-Since` (412), and every request is recorded.
 */
class FakeSyncServer(
    /** Whether `crypto/keys` exists (an account that has synced before). */
    var hasKeys: Boolean = true,
    /** Records per page, to exercise paging. */
    private val pageSize: Int = 1000,
) : SyncHttpTransport {
    /** The `spaces` engine version `meta/global` declares (null: not declared). */
    var engineVersion: Int? = null

    /** Desktop's synced normal-tab preference; null means no capability advertised. */
    var normalTabsPref: Boolean? = null

    val syncKey = KeyBundle(ByteArray(32) { 1 }, ByteArray(32) { 2 })
    val collectionKey = KeyBundle(ByteArray(32) { 3 }, ByteArray(32) { 4 })
    val prefsKey = KeyBundle(ByteArray(32) { 5 }, ByteArray(32) { 6 })

    /** The account credentials Fenix would hand over. */
    val credentials =
        SyncCredentials(
            // What Fenix's account gives: the token server's base, without the Sync path.
            tokenServerUrl = "https://token.example.com",
            accessToken = "access-token",
            keyId = "1700000000000-abc",
            scopedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(syncKey.encryptionKey + syncKey.hmacKey),
        )

    private data class Stored(val payload: String, val modified: Double)

    private val spaces = linkedMapOf<String, Stored>()
    private var clock = 1700000000.0
    val requests = mutableListOf<SyncHttpRequest>()

    /** Desktop writes: put a record's cleartext as Zen would. */
    fun put(cleartext: JSONObject) {
        clock += 1
        spaces[cleartext.getString("id")] = Stored(RecordCrypto.encrypt(cleartext.toString().toByteArray(), collectionKey), clock)
    }

    /** Desktop deletes: an encrypted tombstone. */
    fun delete(id: String) = put(ZenWire.tombstone(id))

    /** The decrypted cleartext stored under [id], or null. */
    fun cleartext(id: String): JSONObject? = spaces[id]?.let { JSONObject(String(RecordCrypto.decrypt(it.payload, collectionKey))) }

    fun ids(): Set<String> = spaces.keys

    val writes: List<SyncHttpRequest> get() = requests.filter { it.method != "GET" }

    override fun execute(request: SyncHttpRequest): SyncHttpResponse {
        requests += request
        val path = request.url.path
        if (request.url.host == "token.example.com") {
            // Like the real token server: only the Sync 1.5 path answers.
            if (path != "/1.0/sync/1.5") return json(404, JSONObject())
            if (request.headers["Authorization"] != "Bearer access-token") return json(401, JSONObject())
            return json(
                200,
                JSONObject().put("id", "hawk-id").put("key", "hawk-key").put("uid", 1).put("api_endpoint", "https://storage.example.com/1.5/1").put("duration", 3600),
            )
        }
        require(request.headers["Authorization"]!!.startsWith("Hawk id=\"hawk-id\""))
        val resource = path.removePrefix("/1.5/1")
        return when {
            resource == "/storage/crypto/keys" && request.method == "GET" -> {
                if (!hasKeys) return SyncHttpResponse(404, emptyMap(), ByteArray(0))
                val keys =
                    JSONObject().put("id", "keys").put(
                        "default",
                        JSONArray().put(b64(collectionKey.encryptionKey)).put(b64(collectionKey.hmacKey)),
                    )
                keys.put("collections", JSONObject().put("prefs", JSONArray().put(b64(prefsKey.encryptionKey)).put(b64(prefsKey.hmacKey))))
                json(200, JSONObject().put("id", "keys").put("payload", RecordCrypto.encrypt(keys.toString().toByteArray(), syncKey)).put("modified", 1.0))
            }
            resource == "/storage/meta/global" && request.method == "GET" -> {
                val version = engineVersion ?: return SyncHttpResponse(404, emptyMap(), ByteArray(0))
                val meta = JSONObject().put("engines", JSONObject().put(SPACES_COLLECTION, JSONObject().put("version", version)))
                json(200, JSONObject().put("id", "global").put("payload", meta.toString()).put("modified", 1.0))
            }
            resource == "/storage/prefs" && request.method == "GET" -> {
                val value = JSONObject().apply { normalTabsPref?.let { put("zen.spaces-sync.normal-tabs", it) } }
                val prefs = JSONObject().put("id", "preferences").put("value", value)
                val record = JSONObject().put("id", "preferences").put("modified", 1.0)
                    .put("payload", RecordCrypto.encrypt(prefs.toString().toByteArray(), prefsKey))
                SyncHttpResponse(200, emptyMap(), JSONArray().put(record).toString().toByteArray())
            }
            resource == "/storage/$SPACES_COLLECTION" && request.method == "GET" -> page(request.url.query.orEmpty())
            resource.startsWith("/storage/$SPACES_COLLECTION/") && request.method == "PUT" -> write(resource, request)
            else -> SyncHttpResponse(404, emptyMap(), ByteArray(0))
        }
    }

    private fun page(query: String): SyncHttpResponse {
        if (spaces.isEmpty()) return SyncHttpResponse(404, emptyMap(), ByteArray(0))
        val offset = Regex("offset=(\\d+)").find(query)?.groupValues?.get(1)?.toInt() ?: 0
        val all = spaces.entries.toList()
        val slice = all.drop(offset).take(pageSize)
        val body = JSONArray()
        slice.forEach { (id, stored) -> body.put(JSONObject().put("id", id).put("payload", stored.payload).put("modified", stored.modified)) }
        val headers = mutableMapOf("x-last-modified" to formatSyncTimestamp(clock))
        if (offset + pageSize < all.size) headers["x-weave-next-offset"] = (offset + pageSize).toString()
        return SyncHttpResponse(200, headers, body.toString().toByteArray())
    }

    private fun write(
        resource: String,
        request: SyncHttpRequest,
    ): SyncHttpResponse {
        val id = java.net.URLDecoder.decode(resource.removePrefix("/storage/$SPACES_COLLECTION/"), "UTF-8")
        request.headers["X-If-Unmodified-Since"]?.toDouble()?.let { condition ->
            val current = spaces[id]?.modified ?: 0.0
            if (formatSyncTimestamp(current) != formatSyncTimestamp(condition)) {
                return SyncHttpResponse(412, mapOf("x-last-modified" to formatSyncTimestamp(current)), ByteArray(0))
            }
        }
        clock += 1
        spaces[id] = Stored(JSONObject(String(request.body!!)).getString("payload"), clock)
        return SyncHttpResponse(200, mapOf("x-last-modified" to formatSyncTimestamp(clock)), formatSyncTimestamp(clock).toByteArray())
    }

    private fun json(
        status: Int,
        body: JSONObject,
    ) = SyncHttpResponse(status, emptyMap(), body.toString().toByteArray())

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
}

/** Builders for desktop-shaped records in tests. */
object ZenRecords {
    fun space(
        uuid: String,
        name: String,
        icon: String = "💼",
        children: List<String> = emptyList(),
        theme: JSONObject? = null,
    ) = ZenWire.cleartext(
        uuid,
        "space",
        JSONObject().put("uuid", uuid).put("name", name).put("icon", icon).put("children", JSONArray(children)).apply { theme?.let { put("theme", it) } },
    )

    fun pinned(
        tabId: String,
        url: String,
        space: String,
        title: String = url,
        folder: String? = null,
        staticLabel: String? = null,
    ) = ZenWire.cleartext(
        tabId,
        "tab",
        JSONObject().put("tabId", tabId).put("url", url).put("title", title).put("pinned", true).put("essential", false)
            .put("workspaceUuid", space).apply {
                folder?.let { put("folderId", it) }
                staticLabel?.let { put("staticLabel", it) }
            },
    )

    fun essential(
        tabId: String,
        url: String,
        title: String = url,
    ) = ZenWire.cleartext(
        tabId,
        "tab",
        JSONObject().put("tabId", tabId).put("url", url).put("title", title).put("pinned", true).put("essential", true).put("workspaceUuid", JSONObject.NULL),
    )

    fun normal(
        tabId: String,
        url: String,
        space: String,
    ) = ZenWire.cleartext(tabId, "tab", JSONObject().put("tabId", tabId).put("url", url).put("pinned", false).put("workspaceUuid", space))

    fun folder(
        folderId: String,
        name: String,
        space: String,
        children: List<String> = emptyList(),
        parent: String? = null,
    ) = ZenWire.cleartext(
        folderId,
        "folder",
        JSONObject().put("folderId", folderId).put("name", name).put("icon", "📁").put("workspaceUuid", space).put("children", JSONArray(children))
            .apply { parent?.let { put("parentFolderId", it) } },
    )

    fun layout(
        spaces: List<String>,
        essentials: Map<String, List<String>> = emptyMap(),
    ) = ZenWire.cleartext(
        LAYOUT_RECORD_ID,
        "layout",
        JSONObject().put("spaces", JSONArray(spaces)).put("essentials", JSONObject().apply { essentials.forEach { (k, v) -> put(k, JSONArray(v)) } }),
    )
}
