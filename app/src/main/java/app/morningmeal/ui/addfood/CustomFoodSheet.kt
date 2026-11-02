@file:OptIn(ExperimentalMaterial3Api::class)

package app.morningmeal.ui.addfood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.morningmeal.ui.components.parseAmount

/** Enter a food straight off its nutrition label. Values are per serving. */
@Composable
fun CustomFoodSheet(
    initialName: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, servingGrams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double) -> Unit,
) {
    var name by remember { mutableStateOf(initialName.replaceFirstChar { it.uppercase() }) }
    var serving by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    val servingG = parseAmount(serving)?.takeIf { it > 0 }
    val kcalV = parseAmount(kcal)?.takeIf { it >= 0 }
    // Macros are optional; blank means 0.
    fun opt(s: String) = if (s.isBlank()) 0.0 else parseAmount(s)?.takeIf { it >= 0 }
    val p = opt(protein)
    val c = opt(carbs)
    val f = opt(fat)
    val valid = name.isNotBlank() && servingG != null && kcalV != null && p != null && c != null && f != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("New food", style = MaterialTheme.typography.titleLarge)
            Text(
                "Copy the numbers from the label, per serving.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Serving", serving, { serving = it }, "g", Modifier.weight(1f))
                NumberField("Calories", kcal, { kcal = it }, "kcal", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Protein", protein, { protein = it }, "g", Modifier.weight(1f))
                NumberField("Carbs", carbs, { carbs = it }, "g", Modifier.weight(1f))
                NumberField("Fat", fat, { fat = it }, "g", Modifier.weight(1f), last = true)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (valid) onCreate(name, servingG!!, kcalV!!, p!!, c!!, f!!)
                },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Create") }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7)) },
        label = { Text(label) },
        suffix = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        modifier = modifier,
    )
}
