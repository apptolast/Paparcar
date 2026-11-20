package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.detection.ParkingAskBudget

class FakeParkingAskBudget : ParkingAskBudget {
    private val stamps = mutableMapOf<String, Long>()
    override fun lastAskAtMs(vehicleId: String) = stamps[vehicleId]
    override fun stampAsk(vehicleId: String, atMs: Long) { stamps[vehicleId] = atMs }
}
