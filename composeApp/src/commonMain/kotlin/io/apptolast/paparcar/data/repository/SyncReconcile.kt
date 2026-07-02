package io.apptolast.paparcar.data.repository

/**
 * Generic inbound-sync merge that preserves un-synced local edits — the shared core behind the
 * per-entity reconcilers (vehicles, zones, …). [SYNC-RECONCILE-001]
 *
 * Per id: a local row that is [pendingSync] AND strictly newer than remote is kept (an offline edit
 * the server hasn't caught up to); otherwise remote wins (via [onTakeRemote], which may carry over
 * on-device-only fields from the local row). Once the server catches up (remote.updatedAt >= local)
 * the row is taken from remote again, so the pending flag self-heals across process kills and
 * multi-device edits converge. Local-only rows are kept ONLY if pending (created offline, not yet
 * uploaded); clean local-only rows are dropped (remote deletions). The caller re-applies any
 * invariants (e.g. single-active) on the result.
 */
fun <T> reconcilePending(
    local: List<T>,
    remote: List<T>,
    id: (T) -> String,
    updatedAt: (T) -> Long,
    pendingSync: (T) -> Boolean,
    onTakeRemote: (remoteRow: T, localRow: T?) -> T = { r, _ -> r },
): List<T> {
    val localById = local.associateBy(id)
    val remoteIds = remote.mapTo(HashSet()) { id(it) }
    val merged = remote.map { r ->
        val l = localById[id(r)]
        if (l != null && pendingSync(l) && updatedAt(l) > updatedAt(r)) l else onTakeRemote(r, l)
    }
    val localOnlyPending = local.filter { id(it) !in remoteIds && pendingSync(it) }
    return merged + localOnlyPending
}
