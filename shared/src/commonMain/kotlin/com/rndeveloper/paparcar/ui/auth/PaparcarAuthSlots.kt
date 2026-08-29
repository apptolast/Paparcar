package com.rndeveloper.paparcar.ui.auth

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
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.components.PapProviderButton
import com.rndeveloper.paparcar.ui.components.PaparcarLogo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apptolast.customlogin.domain.model.IdentityProvider
import com.apptolast.customlogin.presentation.slots.AuthScreenSlots
import com.apptolast.customlogin.presentation.slots.LoginScreenSlots
import com.apptolast.customlogin.presentation.slots.RegisterScreenSlots
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import login.custom_login.generated.resources.Res as LoginRes
import login.custom_login.generated.resources.apple_icon
import login.custom_login.generated.resources.facebook_icon
import login.custom_login.generated.resources.github_icon
import login.custom_login.generated.resources.google_icon
import login.custom_login.generated.resources.login_apple_button
import login.custom_login.generated.resources.login_facebook_button
import login.custom_login.generated.resources.login_github_button
import login.custom_login.generated.resources.login_google_button
import login.custom_login.generated.resources.login_magic_link_button
import login.custom_login.generated.resources.login_microsoft_button
import login.custom_login.generated.resources.login_phone_button
import login.custom_login.generated.resources.login_twitter_button
import login.custom_login.generated.resources.microsoft_icon
import login.custom_login.generated.resources.twitter_icon
import org.jetbrains.compose.resources.painterResource
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
private val PROVIDER_BUTTON_GAP = 10.dp

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
            PaparcarSocialProviders(
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
            PaparcarSocialProviders(
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
        // Start, not centred: the container lays every child out full-width, so a centred header
        // would float on its own axis while the fields, the CTA and the provider button all begin
        // at the same left edge. The brand block belongs on that edge too.
        // [UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001]
        horizontalAlignment = Alignment.Start,
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
                    // App/brand name = a title → Outfit (screenTitle) via PaparcarType, not the raw
                    // Material scale. [TYPO-AUDIT-001]
                    style = PaparcarType.current.screenTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                )

                Spacer(modifier = Modifier.height(HEADER_TITLE_TAGLINE_GAP))

                Text(
                    text = stringResource(Res.string.auth_header_tagline),
                    // Tagline = prose → Inter (body) via PaparcarType. [TYPO-AUDIT-001]
                    style = PaparcarType.current.body,
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
    // No leading icon: this is the sole submit of a single-purpose screen — the
    // screen context ("Log in" / "Register") already names the action, so a Login
    // arrow would just be redundant noise. [UI-SHEET-002]
    PapPrimaryButton(
        label = text,
        onClick = onClick,
        isLoading = isLoading,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The sign-in-with-<provider> buttons, in Paparcar's button shape.
 *
 * Replaces the library's default section for one reason: its buttons are 12dp-radius rectangles,
 * so stacked under our pill-shaped submit button they read as if they came from another app. The
 * shell is ours ([PapProviderButton]); the brand mark and the already-translated label stay the
 * library's, so adding a provider never means shipping a new string to nine locales.
 * [UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001]
 *
 * While one provider is signing in, the others are disabled: two concurrent auth requests would
 * race for the same session.
 */
@Composable
private fun PaparcarSocialProviders(
    providers: List<IdentityProvider>,
    loadingProvider: IdentityProvider?,
    onProviderClick: (IdentityProvider) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PROVIDER_BUTTON_GAP),
    ) {
        providers.forEach { provider ->
            val mark = providerMark(provider) ?: return@forEach
            PapProviderButton(
                label = mark.label,
                icon = mark.icon,
                onClick = { onProviderClick(provider) },
                modifier = Modifier.fillMaxWidth(),
                tint = mark.tint,
                isLoading = loadingProvider == provider,
                enabled = loadingProvider == null,
            )
        }
    }
}

/** How a provider presents itself on its button: its name, its mark, and how the mark is coloured. */
private data class ProviderMark(val label: String, val icon: Painter, val tint: Color?)

/**
 * Maps every provider the library can hand us — not just the ones
 * [com.rndeveloper.paparcar.di.paparcarLoginConfig] offers today — so enabling one later cannot
 * produce a blank or off-style button. `null` [ProviderMark.tint] means the mark is multicolour
 * and must be drawn as-is.
 */
@Composable
private fun providerMark(provider: IdentityProvider): ProviderMark? {
    val monochrome = MaterialTheme.colorScheme.onSurface
    return when (provider) {
        IdentityProvider.Google -> ProviderMark(
            label = stringResource(LoginRes.string.login_google_button),
            icon = painterResource(LoginRes.drawable.google_icon),
            tint = null,
        )

        IdentityProvider.Apple -> ProviderMark(
            label = stringResource(LoginRes.string.login_apple_button),
            icon = painterResource(LoginRes.drawable.apple_icon),
            tint = monochrome,
        )

        IdentityProvider.Facebook -> ProviderMark(
            label = stringResource(LoginRes.string.login_facebook_button),
            icon = painterResource(LoginRes.drawable.facebook_icon),
            tint = null,
        )

        IdentityProvider.GitHub -> ProviderMark(
            label = stringResource(LoginRes.string.login_github_button),
            icon = painterResource(LoginRes.drawable.github_icon),
            tint = monochrome,
        )

        IdentityProvider.Microsoft -> ProviderMark(
            label = stringResource(LoginRes.string.login_microsoft_button),
            icon = painterResource(LoginRes.drawable.microsoft_icon),
            tint = null,
        )

        IdentityProvider.Twitter -> ProviderMark(
            label = stringResource(LoginRes.string.login_twitter_button),
            icon = painterResource(LoginRes.drawable.twitter_icon),
            tint = monochrome,
        )

        // Passwordless methods have no brand mark: a system icon names the channel instead.
        IdentityProvider.MagicLink -> ProviderMark(
            label = stringResource(LoginRes.string.login_magic_link_button),
            icon = rememberVectorPainter(Icons.Rounded.Email),
            tint = monochrome,
        )

        IdentityProvider.Phone -> ProviderMark(
            label = stringResource(LoginRes.string.login_phone_button),
            icon = rememberVectorPainter(Icons.Rounded.Phone),
            tint = monochrome,
        )

        // A custom provider brings neither name nor mark, so there is nothing honest to draw —
        // same call the library's own section makes.
        is IdentityProvider.Custom -> null
    }
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
                // Small secondary link = label (Inter, == labelMedium) via PaparcarType. [TYPO-AUDIT-001]
                style = PaparcarType.current.label,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
