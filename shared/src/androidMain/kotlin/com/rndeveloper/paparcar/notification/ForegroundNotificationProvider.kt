package com.rndeveloper.paparcar.notification

import android.app.Notification

interface ForegroundNotificationProvider {
    fun buildDetectionNotification(): Notification

    /** [DET-RESIDENT-FGS-001 · F3] The resident-sentry FGS notification: minimum importance,
     *  silent, plain-language copy (no internal mechanics) — shown between parkings while the
     *  service watches for the departure with the GPS off. */
    fun buildSentryNotification(): Notification
}