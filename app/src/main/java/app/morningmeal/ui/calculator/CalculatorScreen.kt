@file:OptIn(ExperimentalMaterial3Api::class)

package app.morningmeal.ui.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morningmeal.data.CalcItemEntity
import app.morningmeal.data.macros
import app.morningmeal.ui.components.GramsDialog
import app.morningmeal.ui.components.Hairline
import app.morningmeal.ui.components.ValueRow
import app.morningmeal.ui.components.appViewModel
import app.morningmeal.ui.components.dayLabel
import app.morningmeal.ui.components.gramsText
import app.morningmeal.ui.components.kcalText
import app.morningmeal.ui.components.macroLine
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun CalculatorScreen(
    onAddFood: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val vm = appViewModel { CalculatorViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showItems by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    val shown by animateIntAsState(
        targetValue = state.total.kcal.roundToInt(),
        animationSpec = tween(durationMillis = 450),
        label = "kcal",
    )
    val hasItems = state.items.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            // The number. Tapping it reveals what's in it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    enabled = hasItems,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { showItems = true },
            ) {
                Text(
                    text = kcalText(shown.toDouble()),
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (hasItems) {
                        "kcal  ·  ${state.items.size} ${if (state.items.size == 1) "food" else "foods"}"
                    } else {
                        "kcal"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnimatedVisibility(visible = hasItems, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = macroLine(state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onAddFood,
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
            ) {
                Text("Add food", style = MaterialTheme.typography.titleMedium)
            }

            // Fixed-height slot so the button doesn't jump when this appears.
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(visible = hasItems, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = { showSave = true }) {
                        Text("Save to journal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }

    if (showItems) {
        ItemsSheet(
            items = state.items,
            onDismiss = { showItems = false },
            onUpdate = { item, grams -> vm.updateGrams(item, grams) },
            onRemove = { item ->
                vm.remove(item)
                if (state.items.size <= 1) showItems = false
            },
            onClear = {
                vm.clear()
                showItems = false
            },
            onSave = {
                showItems = false
                showSave = true
            },
        )
    }

    if (showSave) {
        SaveSheet(
            onDismiss = { showSave = false },
            onSave = { target, day, mealName ->
                vm.save(target, day, mealName) {
                    showSave = false
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            message = "Saved to ${dayLabel(day).lowercaseFirstIfRelative()}",
                            actionLabel = "View",
                        )
                        if (result == SnackbarResult.ActionPerformed) onOpenDay(day)
                    }
                }
            },
        )
    }
}

private fun String.lowercaseFirstIfRelative(): String =
    if (this == "Today" || this == "Yesterday" || this == "Tomorrow") lowercase() else this

@Composable
private fun ItemsSheet(
    items: List<CalcItemEntity>,
    onDismiss: () -> Unit,
    onUpdate: (CalcItemEntity, Double) -> Unit,
    onRemove: (CalcItemEntity) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    var editing by remember { mutableStateOf<CalcItemEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(items, key = { it.id }) { item ->
                    val m = item.macros
                    ValueRow(
                        title = item.name,
                        subtitle = gramsText(item.grams),
                        value = kcalText(m.kcal),
                        valueSubtitle = "kcal",
                        onClick = { editing = item },
                    )
                    Hairline(Modifier.padding(horizontal = 24.dp))
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onSave) { Text("Save to journal") }
            }
        }
    }

    editing?.let { item ->
        GramsDialog(
            title = item.name,
            initialGrams = item.grams,
            kcalPer100 = item.kcal100,
            onDismiss = { editing = null },
            onConfirm = { grams ->
                onUpdate(item, grams)
                editing = null
            },
            onRemove = {
                onRemove(item)
                editing = null
            },
        )
    }
}
