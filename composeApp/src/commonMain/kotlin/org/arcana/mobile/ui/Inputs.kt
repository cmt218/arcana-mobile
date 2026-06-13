package org.arcana.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
import org.arcana.mobile.theme.Danger
import org.arcana.mobile.theme.Ink
import org.arcana.mobile.theme.Mist
import org.arcana.mobile.theme.Moss

/**
 * Hairline-underline text field — the design's gateway input. No boxed fill:
 * a Mist hairline that thickens to Moss on focus, an "ACTIVE" stamp beside the
 * label, and a Moss caret. Pairs with [Overline] for the label.
 */
@Composable
fun ArcanaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    secure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    // Keyboard auto-capitalization (e.g. Words for name fields).
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    // OS AutoFill hint (iOS textContentType / Android autofill) — e.g.
    // PersonFirstName makes iOS suggest the member's name from their contact card.
    contentType: ContentType? = null,
    // When non-null, the label + underline turn Danger and the message renders
    // below the field. Used for server-driven field validation (e.g. password).
    error: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = when {
        error != null -> Danger
        focused -> Moss
        else -> Mist
    }
    val labelColor = when {
        error != null -> Danger
        focused -> Moss
        else -> Ash
    }

    Column(modifier = modifier) {
        Overline(text = label, color = labelColor)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (contentType != null) {
                        Modifier.semantics { this.contentType = contentType }
                    } else {
                        Modifier
                    }
                ),
            interactionSource = interaction,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = Ink,
            ),
            cursorBrush = SolidColor(Moss),
            visualTransformation = if (secure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
                capitalization = capitalization,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
                onGo = { onImeAction() },
            ),
            decorationBox = { inner ->
                Column {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            BodyTextPlaceholder(placeholder)
                        }
                        inner()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (focused || error != null) 2.dp else 1.dp)
                            .background(accent)
                    )
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Caption(text = error, size = 13, color = Danger)
        }
    }
}

/**
 * Multiline variant of [ArcanaTextField] for free-form prose (the concierge
 * message box). Same hairline-underline treatment and Moss caret, but grows
 * across [minLines]–[maxLines], enforces a hard [maxLength], and shows a
 * live character counter under the field.
 */
@Composable
fun ArcanaMultilineTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minLines: Int = 4,
    maxLines: Int = 8,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = if (focused) Moss else Mist

    Column(modifier = modifier) {
        Overline(text = label, color = if (focused) Moss else Ash)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            // Enforce the cap at the edit boundary; truncate so a long paste
            // keeps its first chars rather than being rejected wholesale.
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interaction,
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = TextStyle(
                fontFamily = Arcana.fonts.body,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = Ink,
            ),
            cursorBrush = SolidColor(Moss),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default,
            ),
            decorationBox = { inner ->
                Column {
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            BodyTextPlaceholder(placeholder)
                        }
                        inner()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (focused) 2.dp else 1.dp)
                            .background(accent)
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Overline(text = "${value.length}/$maxLength", size = 10, color = Ash2)
        }
    }
}

@Composable
private fun BodyTextPlaceholder(text: String) {
    androidx.compose.material3.Text(
        text = text,
        style = TextStyle(
            fontFamily = Arcana.fonts.body,
            fontWeight = FontWeight.Normal,
            fontSize = 18.sp,
            color = Ash2,
        ),
    )
}
