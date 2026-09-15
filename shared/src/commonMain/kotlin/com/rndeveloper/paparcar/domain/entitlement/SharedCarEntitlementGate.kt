package com.rndeveloper.paparcar.domain.entitlement

/**
 * Who may open a shared car and how big the group gets, decided in ONE place. [VEH-SHARED-001]
 *
 * *Free asks, Plus watches and remembers* ([plus-alerts-and-tiers-001]): a shared car is the app
 * telling you what somebody ELSE did while you were not looking, so **creating the group is the
 * owner's Plus**. The people who join do not need Plus and never will — the owner's subscription
 * covers the car (decision 06/07-09).
 */
fun canShareVehicle(isPlus: Boolean): Boolean = isPlus

/**
 * Whether one more person fits. The cap is a product decision, not an entitlement one: Plus does
 * NOT buy a bigger group. A car with six drivers is a fleet, and that is not what this feature is.
 *
 * @param currentMembers people already in, the owner NOT counted.
 */
fun canAddSharedCarMember(currentMembers: Int): Boolean = currentMembers < SHARED_CAR_MAX_MEMBERS

const val SHARED_CAR_MAX_MEMBERS = 4
