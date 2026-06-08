package io.apptolast.paparcar.ui.auth

import android.content.res.Configuration
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.apptolast.customlogin.domain.model.IdentityProvider
import com.apptolast.customlogin.presentation.screens.components.DefaultAuthContainer
import com.apptolast.customlogin.presentation.slots.LoginScreenSlots
import com.apptolast.customlogin.presentation.slots.defaultslots.DefaultDivider
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "Login — Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaparcarLoginDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MockLoginScreen(state = MockLoginState())
        }
    }
}

@Preview(name = "Login — Claro", showBackground = true)
@Composable
private fun PaparcarLoginLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MockLoginScreen(state = MockLoginState())
        }
    }
}

@Preview(name = "Login — con datos · Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaparcarLoginFilledDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MockLoginScreen(
                state = MockLoginState(
                    email = "ana@paparcar.io",
                    password = "MiPassword123",
                ),
            )
        }
    }
}

@Preview(name = "Login — error email · Claro", showBackground = true)
@Composable
private fun PaparcarLoginErrorLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MockLoginScreen(
                state = MockLoginState(
                    email = "no-es-un-email",
                    password = "123",
                    emailError = "Formato de email no válido",
                    passwordError = "Mínimo 8 caracteres",
                ),
            )
        }
    }
}

@Preview(name = "Login — cargando · Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaparcarLoginLoadingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MockLoginScreen(
                state = MockLoginState(
                    email = "ana@paparcar.io",
                    password = "MiPassword123",
                    isLoading = true,
                ),
            )
        }
    }
}

private data class MockLoginState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val availableProviders: List<IdentityProvider> = listOf(
        IdentityProvider.Google,
        IdentityProvider.Apple,
    ),
)

@Composable
private fun MockLoginScreen(state: MockLoginState) {
    val slots: LoginScreenSlots = paparcarAuthSlots().login
    val isFormValid = state.email.isNotBlank() && state.password.isNotBlank()

    DefaultAuthContainer(
        modifier = Modifier,
        verticalArrangement = slots.layoutVerticalArrangement,
    ) {
        slots.header()

        Spacer(modifier = Modifier.height(16.dp))

        slots.emailField(state.email, {}, state.emailError, !state.isLoading)

        Spacer(modifier = Modifier.height(8.dp))

        slots.passwordField(state.password, {}, state.passwordError, !state.isLoading)

        slots.forgotPasswordLink {}

        Spacer(modifier = Modifier.height(16.dp))

        slots.submitButton({}, state.isLoading, isFormValid && !state.isLoading, "Iniciar sesión")

        if (slots.socialProviders != null && state.availableProviders.isNotEmpty()) {
            DefaultDivider("O")
            slots.socialProviders!!.invoke(state.availableProviders, null) {}
            Spacer(Modifier.height(8.dp))
        }

        slots.registerLink {}
    }
}
