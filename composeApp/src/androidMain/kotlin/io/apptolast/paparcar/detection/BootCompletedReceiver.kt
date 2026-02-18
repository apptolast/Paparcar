package io.apptolast.paparcar.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receiver para reiniciar el AccelerometerManager cuando el dispositivo arranca.
 * Esto asegura que el acelerómetro vuelva a estar escuchando después de un reinicio.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val accelerometerManager: AccelerometerManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reiniciar el listener del acelerómetro
            accelerometerManager.resetDetection()
            accelerometerManager.startListening()
        }
    }
}
