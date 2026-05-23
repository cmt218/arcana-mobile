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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arcana.mobile.theme.Arcana
import org.arcana.mobile.theme.Ash
import org.arcana.mobile.theme.Ash2
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
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val accent = if (focused) Moss else Mist

    Column(modifier = modifier) {
        Overline(text = label, color = if (focused) Moss else Ash)
        Spacer(Modifier.height(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
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
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
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
                            .height(if (focused) 2.dp else 1.dp)
                            .background(accent)
                    )
                }
            },
        )
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
