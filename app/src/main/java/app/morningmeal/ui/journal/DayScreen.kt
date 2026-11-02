package app.morningmeal.ui.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morningmeal.data.EntryEntity
import app.morningmeal.data.Macros
import app.morningmeal.data.MealEntity
import app.morningmeal.data.macros
import app.morningmeal.ui.components.BackBar
import app.morningmeal.ui.components.GramsDialog
import app.morningmeal.ui.components.Hairline
import app.morningmeal.ui.components.ValueRow
import app.morningmeal.ui.components.appViewModel
import app.morningmeal.ui.components.dayLabel
import app.morningmeal.ui.components.gramsText
import app.morningmeal.ui.components.kcalText
import app.morningmeal.ui.components.macroLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

@Composable
fun DayScreen(day: LocalDate, onBack: () -> Unit) {
    val vm = appViewModel(key = "day-${day.toEpochDay()}") { DayViewModel(it, day) }
    val state by vm.state.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<EntryEntity?>(null) }
    var renaming by remember { mutableStateOf<MealEntity?>(null) }
    var deletingMeal by remember { mutableStateOf<MealEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        BackBar(title = dayLabel(day), subtitle = day.format(fullDate), onBack = onBack)

        // Day total, big but not as big as the calculator's.
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(kcalText(state.total.kcal), style = MaterialTheme.typography.displaySmall)
            Text(
                text = buildString {
                    append("kcal")
                    if (state.goal > 0) append(" of ${kcalText(state.goal.toDouble())}")
                    if (!state.isEmpty) append("  ·  ").append(macroLine(state.total))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.loaded && state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            DayList(
                state = state,
                onEdit = { editing = it },
                onRename = { renaming = it },
                onDeleteMeal = { deletingMeal = it },
            )
        }
    }

    editing?.let { entry ->
        GramsDialog(
            title = entry.name,
            initialGrams = entry.grams,
            kcalPer100 = entry.kcal100,
            onDismiss = { editing = null },
            onConfirm = { grams ->
                vm.updateGrams(entry, grams)
                editing = null
            },
            onRemove = {
                vm.deleteEntry(entry)
                editing = null
            },
        )
    }

    renaming?.let { meal ->
        var name by remember(meal.id) { mutableStateOf(meal.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Rename meal") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.renameMeal(meal, name)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deletingMeal?.let { meal ->
        AlertDialog(
            onDismissRequest = { deletingMeal = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Delete “${meal.name}”?") },
            text = { Text("The meal and every food in it will be removed from this day.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMeal(meal)
                    deletingMeal = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingMeal = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayList(
    state: DayState,
    onEdit: (EntryEntity) -> Unit,
    onRename: (MealEntity) -> Unit,
    onDeleteMeal: (MealEntity) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        state.meals.forEach { group ->
            item(key = "meal-${group.meal.id}") {
                SectionHeader(
                    title = group.meal.name,
                    total = group.total,
                    onRename = { onRename(group.meal) },
                    onDelete = { onDeleteMeal(group.meal) },
                )
            }
            items(group.entries, key = { "entry-${it.id}" }) { entry ->
                EntryRow(entry) { onEdit(entry) }
            }
        }
        if (state.loose.isNotEmpty()) {
            item(key = "loose") {
                SectionHeader(
                    title = if (state.meals.isEmpty()) "Foods" else "Other",
                    total = state.loose.fold(Macros.Zero) { acc, e -> acc + e.macros },
                )
            }
            items(state.loose, key = { "entry-${it.id}" }) { entry ->
                EntryRow(entry) { onEdit(entry) }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    total: Macros,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = if (onRename != null) 8.dp else 24.dp, top = 20.dp, bottom = 6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                "${kcalText(total.kcal)} kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRename != null && onDelete != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Meal options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
        Hairline(Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun EntryRow(entry: EntryEntity, onClick: () -> Unit) {
    val m = entry.macros
    ValueRow(
        title = entry.name,
        subtitle = gramsText(entry.grams),
        value = kcalText(m.kcal),
        valueSubtitle = macroLine(m),
        onClick = onClick,
    )
}
