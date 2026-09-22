package com.rndeveloper.paparcar.domain.detection.ports

/**
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Remembers the car MACs the user said "no, not my car"
 * to, so the opportunistic link offer never asks about the same device twice.
 *
 * A port because the memory is durable platform storage (iOS `NSUserDefaults`), while the rule
 * that consumes it — "don't offer a declined MAC" — is pure. Without this the offer would re-fire
 * every trip the passenger's headset or a colleague's car framed, which is nagging: the doctrine
 * is to ask ONCE and take no for an answer.
 *
 * MACs are stored uppercased (the same normalisation everything else applies). On any platform
 * with no audio-route sensor the set is simply never written and reads empty.
 */
interface CarLinkDeclineStore {
    suspend fun declined(): Set<String>
    suspend fun decline(mac: String)
}
