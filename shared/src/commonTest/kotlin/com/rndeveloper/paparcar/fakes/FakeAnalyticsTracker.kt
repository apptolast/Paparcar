package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.AnalyticsUserProperty

/** Recording [AnalyticsTracker] for use-case tests. [ANALYTICS-EVENTS-001] */
class FakeAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }

    /** Kept in a SEPARATE list from [events] on purpose: a property is a state the install lives
     *  in, an event is something that happened, and a test that folded them together could not tell
     *  "reported once per change" from "reported once per emission" — which is the whole contract of
     *  [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]. */
    val properties = mutableListOf<AnalyticsUserProperty>()
    override fun setUserProperty(property: AnalyticsUserProperty) {
        properties += property
    }
}
