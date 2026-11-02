@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.morningmeal.ui.calculator

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.morningmeal.ui.components.dayLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val mealPresets = listOf("Breakfast", "Lunch", "Dinner", "Snack")

private fun mealForNow(now: LocalTime = LocalTime.now()): String = when {
    now.hour < 11 -> "Breakfast"
    now.hour < 16 -> "Lunch"
    now.hour < 21 -> "Dinner"
    else -> "Snack"
}

/** Where the calculator goes: which day, and as a named meal or as loose foods on that day. */
@Composable
fun SaveSheet(
    onDismiss: () -> Unit,
    onSave: (SaveTarget, LocalDate, String) -> Unit,
) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    var target by remember { mutableStateOf(SaveTarget.Meal) }
    var mealName by remember { mutableStateOf(mealForNow()) }
    var pickingDate by remember { mutableStateOf(false) }

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
            Text("Save to journal", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))

            // Day
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickingDate = true }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    "Day",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(dayLabel(day), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Change day")
            }

            Spacer(Modifier.height(12.dp))

            // Meal or loose foods
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = target == SaveTarget.Meal,
                    onClick = { target = SaveTarget.Meal },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("As a meal") }
                SegmentedButton(
                    selected = target == SaveTarget.Day,
                    onClick = { target = SaveTarget.Day },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("To the day") }
            }

            if (target == SaveTarget.Meal) {
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    mealPresets.forEach { preset ->
                        FilterChip(
                            selected = mealName == preset,
                            onClick = { mealName = preset },
                            label = { Text(preset) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = mealName,
                    onValueChange = { mealName = it.take(40) },
                    label = { Text("Meal name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Foods are added to the day without a meal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(target, day, mealName) },
                enabled = target == SaveTarget.Day || mealName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            // DatePicker works in UTC midnight millis.
            initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
