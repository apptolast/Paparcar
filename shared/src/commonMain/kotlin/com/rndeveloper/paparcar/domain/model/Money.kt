package com.rndeveloper.paparcar.domain.model

/**
 * An amount in a named currency. Domain carries the pair; FORMATTING (symbol, separators, locale)
 * is presentation's job — a domain object printing "9,50 €" would be deciding a locale.
 * [STATS-CORE-001]
 */
data class Money(
    val amount: Double,
    /** ISO 4217 code ("EUR", "USD"). */
    val currency: String,
)
