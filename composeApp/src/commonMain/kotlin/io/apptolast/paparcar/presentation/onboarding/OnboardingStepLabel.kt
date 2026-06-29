package io.apptolast.paparcar.presentation.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.onboarding_step_counter

/**
 * Número total de pasos del flujo lineal de alta (cold-start):
 * Welcome · How it works · Why permissions · Automate (rationale) · Grant.
 * Fuente única de verdad para el indicador "Paso N de N" en todas las pantallas del flujo. El aviso
 * de GPS posterior queda fuera del conteo (es una nota, no un paso de configuración).
 */
const val ONBOARDING_FLOW_STEPS = 5

/** Indicador de progreso textual coherente del flujo de onboarding/permisos. [ONB-IDENTITY-001 G] */
@Composable
fun OnboardingStepLabel(
    step: Int,
    modifier: Modifier = Modifier,
    total: Int = ONBOARDING_FLOW_STEPS,
) {
    Text(
        text = stringResource(Res.string.onboarding_step_counter, step, total),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
