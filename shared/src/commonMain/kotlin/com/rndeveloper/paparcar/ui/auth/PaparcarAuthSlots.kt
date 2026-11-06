package com.rndeveloper.paparcar.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.components.PapProviderButton
import com.rndeveloper.paparcar.ui.components.PaparcarImagotipo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apptolast.baselogin.domain.model.IdentityProvider
import com.apptolast.baselogin.presentation.slots.AuthScreenSlots
import com.apptolast.baselogin.presentation.slots.ForgotPasswordScreenSlots
import com.apptolast.baselogin.presentation.slots.LoginScreenSlots
import com.apptolast.baselogin.presentation.slots.RegisterScreenSlots
import com.apptolast.baselogin.presentation.slots.defaultslots.DefaultSuccessContent
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import login.baselogin.generated.resources.Res as LoginRes
import login.baselogin.generated.resources.apple_icon
import login.baselogin.generated.resources.facebook_icon
import login.baselogin.generated.resources.github_icon
import login.baselogin.generated.resources.google_icon
import login.baselogin.generated.resources.login_facebook_button
import login.baselogin.generated.resources.login_github_button
import login.baselogin.generated.resources.login_magic_link_button
import login.baselogin.generated.resources.login_microsoft_button
import login.baselogin.generated.resources.login_phone_button
import login.baselogin.generated.resources.login_twitter_button
import login.baselogin.generated.resources.microsoft_icon
import login.baselogin.generated.resources.twitter_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.auth_cd_hide_password
import paparcar.composeapp.generated.resources.auth_consent_accept
import paparcar.composeapp.generated.resources.auth_consent_privacy_policy
import paparcar.composeapp.generated.resources.auth_cd_show_password
import paparcar.composeapp.generated.resources.auth_field_confirm_password
import paparcar.composeapp.generated.resources.auth_field_email
import paparcar.composeapp.generated.resources.auth_field_name
import paparcar.composeapp.generated.resources.auth_field_password
import paparcar.composeapp.generated.resources.auth_forgot_password
import paparcar.composeapp.generated.resources.auth_forgot_password_description
import paparcar.composeapp.generated.resources.auth_forgot_password_sent_body
import paparcar.composeapp.generated.resources.auth_forgot_password_sent_title
import paparcar.composeapp.generated.resources.auth_header_tagline
import paparcar.composeapp.generated.resources.auth_login_title
import paparcar.composeapp.generated.resources.auth_register_title
import paparcar.composeapp.generated.resources.auth_privacy_notice
import paparcar.composeapp.generated.resources.auth_continue_with_google
import paparcar.composeapp.generated.resources.auth_continue_with_apple

/** Where the consent link points. Same document Settings links to; unify the constant when the
 *  domain sweep lands. [AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001] */
private const val LEGAL_PRIVACY_POLICY_URL = "https://paparcar.com/privacy-policy"

private val HEADER_MARK_TAGLINE_GAP = 8.dp
private val HEADER_BRAND_FORM_GAP = 24.dp
private val FIELD_ICON_SIZE = 20.dp
private val FIELD_CORNER_RADIUS = 14.dp
private val PROVIDER_BUTTON_GAP = 10.dp

/**
 * Keep existing-account sign-in independent of registration. Firebase may create an Auth user
 * during a first provider sign-in, so the provider section explains that before opening Google.
 * The registration checkbox remains on the email creation path; it never blocks a returning user.
 */
@Composable
fun paparcarAuthSlots(
    onLegalConsentAccepted: () -> Unit,
): AuthScreenSlots = AuthScreenSlots(
    login = LoginScreenSlots(
        layoutVerticalArrangement = Arrangement.Top,
        header = { PaparcarAuthHeader(isRegistration = false) },
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
                showPrivacyNotice = true,
            )
        },
    ),
    register = RegisterScreenSlots(
        layoutVerticalArrangement = Arrangement.Top,
        header = { PaparcarAuthHeader(isRegistration = true) },
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
        termsCheckbox = { checked, onCheckedChange ->
            LegalConsentRow(
                checked = checked,
                onCheckedChange = { accepted ->
                    onCheckedChange(accepted)
                    if (accepted) onLegalConsentAccepted()
                },
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
                showPrivacyNotice = false,
            )
        },
    ),
    forgotPassword = ForgotPasswordScreenSlots(
        description = { CompactForgotPasswordDescription() },
        successContent = { CompactForgotPasswordSent() },
    ),
)

/**
 * The consent ask itself: checkbox + "I accept the Privacy Policy", with the policy name a live
 * link to the published document. The link must be reachable BEFORE ticking — that is the point
 * of the row — so it never sits behind the disabled state it controls.
 */
@Composable
private fun LegalConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val policyName = stringResource(Res.string.auth_consent_privacy_policy)
    val sentence = stringResource(Res.string.auth_consent_accept, policyName)
    val linkStart = sentence.indexOf(policyName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = sentence },
        )
        Text(
            text = buildAnnotatedString {
                if (linkStart < 0) {
                    // A translation lost the placeholder — degrade to plain text, never crash.
                    append(sentence)
                } else {
                    append(sentence.substring(0, linkStart))
                    withLink(
                        LinkAnnotation.Url(
                            url = LEGAL_PRIVACY_POLICY_URL,
                            styles = TextLinkStyles(
                                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                            ),
                        ),
                    ) { append(policyName) }
                    append(sentence.substring(linkStart + policyName.length))
                }
            },
            // Legal micro-copy next to a control = LECTURA (label). [TYPO-AUDIT-001]
            style = PaparcarType.current.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Readable before a first provider sign-in, which may create a Firebase account. */
@Composable
private fun LegalPrivacyNotice() {
    val policyName = stringResource(Res.string.auth_consent_privacy_policy)
    val sentence = stringResource(Res.string.auth_privacy_notice, policyName)
    val linkStart = sentence.indexOf(policyName)
    Text(
        text = buildAnnotatedString {
            if (linkStart < 0) {
                append(sentence)
            } else {
                append(sentence.substring(0, linkStart))
                withLink(
                    LinkAnnotation.Url(
                        url = LEGAL_PRIVACY_POLICY_URL,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                ) { append(policyName) }
                append(sentence.substring(linkStart + policyName.length))
            }
        },
        style = PaparcarType.current.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PaparcarAuthHeader(isRegistration: Boolean) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Brand first; the task heading belongs to the form below, not to the wordmark.
        PaparcarImagotipo(width = 168.dp)
        Spacer(modifier = Modifier.height(HEADER_MARK_TAGLINE_GAP))
        Text(
            text = stringResource(Res.string.auth_header_tagline),
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(HEADER_BRAND_FORM_GAP))
        Text(
            text = stringResource(
                if (isRegistration) Res.string.auth_register_title else Res.string.auth_login_title,
            ),
            style = PaparcarType.current.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
    showPrivacyNotice: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PROVIDER_BUTTON_GAP),
    ) {
        if (showPrivacyNotice && providers.isNotEmpty()) {
            LegalPrivacyNotice()
        }
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
            label = stringResource(Res.string.auth_continue_with_google),
            icon = painterResource(LoginRes.drawable.google_icon),
            tint = null,
        )

        IdentityProvider.Apple -> ProviderMark(
            label = stringResource(Res.string.auth_continue_with_apple),
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

/**
 * El copy de "olvidé mi contraseña", que no promete lo que no puede saber.
 * [AUTH-A-FAILED-SIGN-IN-MUST-NAME-THE-OTHER-DOOR-001]
 *
 * La librería promete de más: `forgot_password_screen_description` dice *"we'll send you a link"*.
 * Medido el 30-09-2026 contra `pap-26`: con `emailPrivacyConfig.enableImprovedEmailPrivacy`
 * activada, un reset sobre una cuenta SIN credencial de contraseña — la que se registró con Google
 * — responde OK y **no envía nada**, porque admitirlo filtraría si la cuenta existe. Y no hay señal
 * con la que acertar: `fetchSignInMethodsForEmail` devuelve vacío para un email de Google, para uno
 * con contraseña y para uno inexistente. Así que el texto se vuelve **condicional**: promete el
 * enlace solo si esa cuenta tiene contraseña, que es exactamente lo que el servidor garantiza.
 */
@Composable
private fun CompactForgotPasswordDescription() {
    Text(
        text = stringResource(Res.string.auth_forgot_password_description),
        // Prosa que se lee = el rol `body`. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
        style = PaparcarType.current.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Lo que se lee DESPUÉS de pedir el enlace — el mensaje que costó una tarde.
 * [AUTH-A-FAILED-SIGN-IN-MUST-NAME-THE-OTHER-DOOR-001]
 *
 * El de la librería es *"Check Your Inbox — We've sent a password reset link to your email
 * address."*: afirma un envío que puede no haber ocurrido, y deja al usuario esperando un correo que
 * no existe. El nuestro dice causa, consecuencia y remedio — puede que la cuenta sea de Google,
 * entonces no llega nada, y la salida es entrar con Google — sin revelar en ningún caso si la
 * cuenta existe ni con qué proveedor.
 *
 * ⛔ La MAQUETA es la de la librería a propósito: `DefaultSuccessContent` ya recibe `title` y
 * `description`, así que lo nuestro es el texto, no un segundo dibujo del mismo estado.
 */
@Composable
private fun CompactForgotPasswordSent() {
    DefaultSuccessContent(
        title = stringResource(Res.string.auth_forgot_password_sent_title),
        description = stringResource(Res.string.auth_forgot_password_sent_body),
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
                // Small secondary link = the `label` role via PaparcarType. [TYPO-AUDIT-001]
                style = PaparcarType.current.label,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
