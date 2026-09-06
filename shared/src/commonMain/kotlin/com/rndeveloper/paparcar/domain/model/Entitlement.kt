package com.rndeveloper.paparcar.domain.model

/**
 * What the user is entitled to. Mapped from RevenueCat's `plus` entitlement by the data
 * layer — domain code never talks to the SDK. RevenueCat is the ONLY source of truth for
 * subscription state: nothing in Paparcar persists or mirrors expiration dates. [MONEY-001]
 */
enum class Entitlement {
    FREE,
    PLUS,
}
