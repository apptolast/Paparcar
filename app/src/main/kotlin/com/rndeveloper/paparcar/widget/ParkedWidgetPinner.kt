package com.rndeveloper.paparcar.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.rndeveloper.paparcar.domain.service.WidgetPinRequester
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Android [WidgetPinRequester]: opens the system "add widget" sheet for [ParkedWidgetReceiver].
 * [WIDGET-ACTIONS-002]
 *
 * `isRequestPinAppWidgetSupported` is the launcher's answer, not the OS version's — some launchers
 * (and most OEM lite modes) say no, and then the Settings row simply never renders.
 */
class ParkedWidgetPinner(private val context: Context) : WidgetPinRequester {

    private val manager: AppWidgetManager? get() = context.getSystemService(AppWidgetManager::class.java)

    override val isPinSupported: Boolean
        get() = manager?.isRequestPinAppWidgetSupported == true

    override fun requestPin() {
        runCatching {
            manager?.requestPinAppWidget(
                ComponentName(context, ParkedWidgetReceiver::class.java),
                null,
                null,
            )
        }.onFailure { e -> PaparcarLogger.w(TAG, "requestPinAppWidget failed: ${e.message}") }
    }

    private companion object {
        const val TAG = "ParkedWidgetPinner"
    }
}
