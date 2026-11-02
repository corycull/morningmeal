@file:OptIn(ExperimentalMaterial3Api::class)

package app.morningmeal.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.morningmeal.data.AppSettings
import app.morningmeal.data.Repository
import app.morningmeal.data.ThemeMode
import app.morningmeal.ui.components.Hairline
import app.morningmeal.ui.components.PageTitle
import app.morningmeal.ui.components.ValueRow
import app.morningmeal.ui.components.appViewModel
import app.morningmeal.ui.components.kcalText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val repo: Repository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setGoal(goal: Int) = viewModelScope.launch { repo.settings.setDailyGoal(goal) }
    fun setTheme(theme: ThemeMode) = viewModelScope.launch { repo.settings.setTheme(theme) }
    fun clearCalculator() = viewModelScope.launch { repo.clearCalculator() }
    fun deleteJournal() = viewModelScope.launch { repo.deleteAllJournalData() }

    fun export(resolver: ContentResolver, uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val message = try {
            val rows = repo.exportCsv(resolver, uri)
            "Exported $rows ${if (rows == 1) "entry" else "entries"}"
        } catch (e: Exception) {
            "Export failed"
        }
        onResult(message)
    }
}

private enum class Confirm { ClearCalculator, DeleteJournal }

@Composable
fun SettingsScreen() {
    val vm = appViewModel { SettingsViewModel(it) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingGoal by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    // Saves to a file you choose (Downloads, Drive folder, etc.) via the system picker. No network.
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            vm.export(context.contentResolver, uri) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PageTitle("Settings")

            ValueRow(
                title = "Daily goal",
                value = if (settings.dailyGoal > 0) "${kcalText(settings.dailyGoal.toDouble())} kcal" else "Off",
                subtitle = "Shown in the journal",
                onClick = { editingGoal = true },
            )
            Hairline(Modifier.padding(horizontal = 24.dp))

            Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
            }
            Hairline(Modifier.padding(horizontal = 24.dp))

            ValueRow(
                title = "Export journal",
                subtitle = "Save everything as a CSV file",
                value = "",
                onClick = { exporter.launch("morningmeal-${LocalDate.now()}.csv") },
            )
            Hairline(Modifier.padding(horizontal = 24.dp))

            ValueRow(
                title = "Clear calculator",
                value = "",
                onClick = { confirm = Confirm.ClearCalculator },
            )
            Hairline(Modifier.padding(horizontal = 24.dp))

            ValueRow(
                title = "Delete all journal data",
                subtitle = "Meals and foods on every day",
                value = "",
                onClick = { confirm = Confirm.DeleteJournal },
            )
            Hairline(Modifier.padding(horizontal = 24.dp))

            Text(
                "Everything stays on this device. Nutrition data from USDA FoodData Central (public domain).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }

    if (editingGoal) {
        GoalDialog(
            current = settings.dailyGoal,
            onDismiss = { editingGoal = false },
            onSave = {
                vm.setGoal(it)
                editingGoal = false
            },
        )
    }

    confirm?.let { which ->
        val (title, body, action) = when (which) {
            Confirm.ClearCalculator -> Triple("Clear calculator?", "Removes everything you've added on the Count page.", "Clear")
            Confirm.DeleteJournal -> Triple(
                "Delete all journal data?",
                "Every meal and food on every day will be deleted. This can't be undone.",
                "Delete",
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(onClick = {
                    when (which) {
                        Confirm.ClearCalculator -> vm.clearCalculator()
                        Confirm.DeleteJournal -> vm.deleteJournal()
                    }
                    confirm = null
                }) { Text(action, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Daily goal") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(5) },
                suffix = { Text("kcal") },
                placeholder = { Text("e.g. 2000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = { value?.let(onSave) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onSave(0) }) { Text("Turn off") }
        },
    )
}
