package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults store of the durable outbox: one key per op (`sync_outbox.<opKey>` → JSON),
 * REPLACE by construction. An unparseable record (schema drift across app versions) is dropped
 * WITH A LOG — an op we cannot read is an op we cannot safely replay.
 * [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
class IosSyncOutbox(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SyncOutbox {

    private val json = Json { ignoreUnknownKeys = true }

    override fun persist(op: OutboxOp) {
        defaults.setObject(json.encodeToString(OutboxOp.serializer(), op), forKey = KEY_NAMESPACE + op.key)
    }

    override fun pending(): List<OutboxOp> =
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(KEY_NAMESPACE) }
            .mapNotNull { key ->
                val raw = defaults.objectForKey(key) as? String ?: return@mapNotNull null
                runCatching { json.decodeFromString(OutboxOp.serializer(), raw) }
                    .onFailure {
                        PaparcarLogger.w(TAG, "unparseable outbox op at $key — dropped")
                        defaults.removeObjectForKey(key)
                    }
                    .getOrNull()
            }

    override fun remove(key: String) {
        defaults.removeObjectForKey(KEY_NAMESPACE + key)
    }

    private companion object {
        const val TAG = "PARKDIAG/IosOutbox"
        const val KEY_NAMESPACE = "sync_outbox."
    }
}
