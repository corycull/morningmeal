@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.morningmeal.ui.addfood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.morningmeal.data.Food
import app.morningmeal.data.Portion
import app.morningmeal.data.macrosFor
import app.morningmeal.ui.components.gramsText
import app.morningmeal.ui.components.kcalText
import app.morningmeal.ui.components.macroLine
import app.morningmeal.ui.components.parseAmount

/** How much of this food? Shows a live calorie preview while you type. */
@Composable
fun AmountSheet(
    food: Food,
    loadPortions: suspend (Food) -> List<Portion>,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
) {
    var portions by remember(food.key) { mutableStateOf(food.portions) }
    var field by remember(food.key) { mutableStateOf(amountField(food.portions.firstOrNull()?.grams ?: 100.0)) }
    var touched by remember(food.key) { mutableStateOf(false) }

    LaunchedEffect(food.key) {
        val loaded = loadPortions(food)
        portions = loaded
        // Default to the first household portion (e.g. "1 medium") unless you've already typed.
        if (!touched && loaded.isNotEmpty()) field = amountField(loaded.first().grams)
    }

    val grams = parseAmount(field.text)?.takeIf { it > 0 }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(food.name, style = MaterialTheme.typography.titleLarge, maxLines = 3)
            if (!food.brand.isNullOrBlank()) {
                Text(
                    food.brand,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        touched = true
                    },
                    suffix = { Text("g") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { grams?.let(onAdd) }),
                    modifier = Modifier
                        .width(150.dp)
                        .focusRequester(focus),
                )
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = grams?.let { kcalText(food.macrosFor(it).kcal) } ?: "–",
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        "kcal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }

            Text(
                text = grams?.let { macroLine(food.macrosFor(it)) } ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(16.dp))
            val options = listOf(Portion("100 g", 100.0)) + portions.filter { it.grams > 0 }.take(8)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                options.forEach { p ->
                    FilterChip(
                        selected = grams == p.grams,
                        onClick = {
                            field = amountField(p.grams)
                            touched = true
                        },
                        label = {
                            Text(if (p.label == "100 g") p.label else "${p.label} · ${gramsText(p.grams)}")
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { grams?.let(onAdd) },
                enabled = grams != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Add") }
        }
    }
}

private fun amountField(grams: Double): TextFieldValue {
    val text = gramsText(grams).removeSuffix(" g")
    return TextFieldValue(text, selection = TextRange(0, text.length))
}
