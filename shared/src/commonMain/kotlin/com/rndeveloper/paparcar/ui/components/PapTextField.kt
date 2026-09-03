package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_chars_left

/**
 * Branded text input field.
 *
 * Applies [MaterialTheme.shapes.small] (8 dp corners) and the design system
 * color tokens. Supports optional [leadingIcon] / [trailingIcon] and error state.
 *
 * When [showClearButton] is true (default) and the caller does not provide a
 * [trailingIcon], an automatic clear (X) button is rendered while the field is
 * editable and has content.
 *
 * @param maxChars caps the input at that many characters and shows the remaining
 *  count once it starts to matter. The cap lives HERE rather than at each call
 *  site so the next bounded field cannot invent its own half of the behaviour —
 *  and so a field that counts always also cuts.
 *  [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
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
    maxChars: Int? = null,
) {
    val resolvedTrailingIcon: @Composable (() -> Unit)? = trailingIcon
        ?: if (showClearButton && value.isNotEmpty() && enabled && !readOnly) {
            { PapClearIconButton(onClick = { onValueChange("") }) }
        } else null

    // A hard stop, not an error state: typing past the ceiling is not a mistake the user made,
    // so the field simply stops accepting instead of turning red at them.
    val remaining = maxChars?.let { it - value.length }

    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(if (maxChars != null) new.take(maxChars) else new) },
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
        // An error outranks the counter — there is one supporting line, and "this is wrong" beats
        // "you have room left". The counter only appears once the ceiling is close: a permanent
        // 0/500 is noise on a field most people never fill.
        supportingText = when {
            errorMessage != null -> {
                {
                    Text(
                        text = errorMessage,
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            remaining != null && remaining <= COUNTER_VISIBLE_WITHIN -> {
                {
                    Text(
                        text = pluralStringResource(Res.plurals.common_chars_left, remaining, remaining),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
            }
            else -> null
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        enabled = enabled,
        readOnly = readOnly,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PapColor.focus,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** How close to [PapTextField]'s `maxChars` the remaining-character line starts showing. */
private const val COUNTER_VISIBLE_WITHIN = 50
