package io.apptolast.paparcar.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.PaparcarLogo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apptolast.customlogin.presentation.slots.AuthScreenSlots
import com.apptolast.customlogin.presentation.slots.LoginScreenSlots
import com.apptolast.customlogin.presentation.slots.RegisterScreenSlots
import com.apptolast.customlogin.presentation.slots.defaultslots.SocialLoginButtonsSection
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.auth_cd_hide_password
import paparcar.composeapp.generated.resources.auth_header_app_name
import paparcar.composeapp.generated.resources.auth_cd_show_password
import paparcar.composeapp.generated.resources.auth_field_confirm_password
import paparcar.composeapp.generated.resources.auth_field_email
import paparcar.composeapp.generated.resources.auth_field_name
import paparcar.composeapp.generated.resources.auth_field_password
import paparcar.composeapp.generated.resources.auth_forgot_password
import paparcar.composeapp.generated.resources.auth_header_tagline

private val LOGO_BADGE_SIZE = 72.dp
private val LOGO_BADGE_ELEVATION = 6.dp
private val HEADER_TOP_SPACING = 4.dp
private val HEADER_LOGO_TEXT_GAP = 14.dp
private val HEADER_TITLE_TAGLINE_GAP = 4.dp
private val HEADER_BOTTOM_SPACING = 8.dp
private val FIELD_ICON_SIZE = 20.dp
private val FIELD_CORNER_RADIUS = 14.dp

fun paparcarAuthSlots(): AuthScreenSlots = AuthScreenSlots(
    login = LoginScreenSlots(
        layoutVerticalArrangement = Arrangement.Top,
        header = { PaparcarAuthHeader() },
        emailField = { value, onValueChange, error, enabled ->
            CompactEmailField(
                value = value,
                onValueChange = onValueChange,
                error = error,
                enabled = enabled,
            )
        },
        passwordField = { value, onValueChange, error, enabled ->
            CompactPasswordField(
                value = value,
                onValueChange = onValueChange,
                error = error,
                enabled = enabled,
                imeAction = ImeAction.Done,
            )
        },
        submitButton = { onClick, isLoading, enabled, text ->
            CompactSubmitButton(onClick = onClick, isLoading = isLoading, enabled = enabled, text = text)
        },
        forgotPasswordLink = { onClick ->
            CompactForgotPasswordLink(onClick = onClick)
        },
        socialProviders = { providers, loadingProvider, onProviderClick ->
            SocialLoginButtonsSection(
                providers = providers,
                loadingProvider = loadingProvider,
                onProviderClick = onProviderClick,
            )
        },
    ),
    register = RegisterScreenSlots(
        layoutVerticalArrangement = Arrangement.Top,
        header = { PaparcarAuthHeader() },
        nameField = { value, onValueChange, error, enabled ->
            CompactNameField(value = value, onValueChange = onValueChange, error = error, enabled = enabled)
        },
        emailField = { value, onValueChange, error, enabled ->
            CompactEmailField(value = value, onValueChange = onValueChange, error = error, enabled = enabled)
        },
        passwordField = { value, onValueChange, error, enabled ->
            CompactPasswordField(value = value, onValueChange = onValueChange, error = error, enabled = enabled)
        },
        confirmPasswordField = { value, onValueChange, error, enabled ->
            CompactPasswordField(
                value = value,
                onValueChange = onValueChange,
                error = error,
                enabled = enabled,
                label = stringResource(Res.string.auth_field_confirm_password),
                imeAction = ImeAction.Done,
            )
        },
        submitButton = { onClick, isLoading, enabled, text ->
            CompactSubmitButton(onClick = onClick, isLoading = isLoading, enabled = enabled, text = text)
        },
        socialProviders = { providers, loadingProvider, onProviderClick ->
            SocialLoginButtonsSection(
                providers = providers,
                loadingProvider = loadingProvider,
                onProviderClick = onProviderClick,
            )
        },
    ),
)

@Composable
private fun PaparcarAuthHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.height(HEADER_TOP_SPACING))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HEADER_LOGO_TEXT_GAP),
        ) {
            // Brand badge — the self-contained design-system logo (neon-green disc +
            // forest car glyph). Drawn with a CircleShape drop-shadow so it keeps the
            // raised "app icon" reading without an extra coloured Surface behind it.
            PaparcarLogo(
                modifier = Modifier.shadow(LOGO_BADGE_ELEVATION, CircleShape),
                size = LOGO_BADGE_SIZE,
            )

            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = stringResource(Res.string.auth_header_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(HEADER_TITLE_TAGLINE_GAP))

                Text(
                    text = stringResource(Res.string.auth_header_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }

        Spacer(modifier = Modifier.height(HEADER_BOTTOM_SPACING))
    }
}

@Composable
private fun CompactEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.auth_field_email)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = null,
                modifier = Modifier.size(FIELD_ICON_SIZE),
            )
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(FIELD_CORNER_RADIUS),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun CompactNameField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.auth_field_name)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(FIELD_ICON_SIZE),
            )
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(FIELD_CORNER_RADIUS),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun CompactPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    label: String = stringResource(Res.string.auth_field_password),
    imeAction: ImeAction = ImeAction.Next,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(FIELD_ICON_SIZE),
            )
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(FIELD_CORNER_RADIUS),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (passwordVisible) Res.string.auth_cd_hide_password
                        else Res.string.auth_cd_show_password,
                    ),
                )
            }
        },
    )
}

@Composable
private fun CompactSubmitButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    text: String,
) {
    PapPrimaryButton(
        label = text,
        onClick = onClick,
        isLoading = isLoading,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CompactForgotPasswordLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                text = stringResource(Res.string.auth_forgot_password),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
