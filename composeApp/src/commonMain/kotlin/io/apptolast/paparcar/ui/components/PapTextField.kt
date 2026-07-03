package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.apptolast.paparcar.ui.theme.PaparcarType

/**
 * Branded text input field.
 *
 * Applies [MaterialTheme.shapes.small] (8 dp corners) and the design system
 * color tokens. Supports optional [leadingIcon] / [trailingIcon] and error state.
 *
 * When [showClearButton] is true (default) and the caller does not provide a
 * [trailingIcon], an automatic clear (X) button is rendered while the field is
 * editable and has content.
 */
@Composable
fun PapTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    showClearButton: Boolean = true,
) {
    val resolvedTrailingIcon: @Composable (() -> Unit)? = trailingIcon
        ?: if (showClearButton && value.isNotEmpty() && enabled && !readOnly) {
            { PapClearIconButton(onClick = { onValueChange("") }) }
        } else null

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, style = PaparcarType.current.body) },
        placeholder = placeholder?.let { { Text(it, style = PaparcarType.current.body) } },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingIcon = resolvedTrailingIcon,
        isError = isError,
        supportingText = errorMessage?.let {
            {
                Text(
                    text = it,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        readOnly = readOnly,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
    )
}
