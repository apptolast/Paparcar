package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker

/** Recording [AnalyticsTracker] for use-case tests. [ANALYTICS-EVENTS-001] */
class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
}
