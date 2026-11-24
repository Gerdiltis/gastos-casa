package com.gastoscasa.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.gastoscasa.ui.theme.AppColors

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(label, color = AppColors.Muted) },
        singleLine = singleLine,
        leadingIcon = leading,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        visualTransformation = visual,
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Ink,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = AppColors.Soft,
            unfocusedContainerColor = AppColors.Soft,
            cursorColor = AppColors.Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
