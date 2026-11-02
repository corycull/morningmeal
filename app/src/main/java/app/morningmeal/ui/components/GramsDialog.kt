package app.morningmeal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Change the amount of something already added (calculator item or journal entry). */
@Composable
fun GramsDialog(
    title: String,
    initialGrams: Double,
    kcalPer100: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onRemove: () -> Unit,
) {
    val initialText = gramsText(initialGrams).removeSuffix(" g")
    var field by remember {
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(0, initialText.length)))
    }
    val grams = parseAmount(field.text)
    val valid = grams != null && grams > 0
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    suffix = { Text("g") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                // Inside the dialog's own composition, so the field exists when we focus it.
                LaunchedEffect(Unit) { focus.requestFocus() }
                Text(
                    text = if (valid) "${kcalText(kcalPer100 * grams!! / 100)} kcal" else " ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { grams?.let(onConfirm) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onRemove) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
