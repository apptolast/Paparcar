package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample

/**
 * Recording fake for [UiLocationLogger]. Captures every sample in order so tests can assert the
 * foreground-scoped location stream emitted SUBSCRIBED / FIX / STOPPED as expected. [UI-LOC-FOREGROUND-001]
 */
class FakeUiLocationLogger : UiLocationLogger {

    val samples: MutableList<UiLocationSample> = mutableListOf()

    override fun log(sample: UiLocationSample) {
        samples.add(sample)
    }

    fun kinds(): List<UiLocationSample.Kind> = samples.map { it.kind }
}
