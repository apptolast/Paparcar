package com.rndeveloper.paparcar.domain.model

/**
 * The two Paparcar Plus billing periods. They map 1:1 to the packages of the `default`
 * offering in RevenueCat ($rc_monthly / $rc_annual); the data layer owns that mapping.
 * There is deliberately no lifetime plan. [MONEY-001]
 */
enum class PlusPlan {
    MONTHLY,
    YEARLY,
}
