@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package app.morningmeal.ui.addfood

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morningmeal.data.Food
import app.morningmeal.ui.components.Hairline
import app.morningmeal.ui.components.appViewModel
import app.morningmeal.ui.components.kcalText

@Composable
fun AddFoodScreen(onDone: () -> Unit) {
    val vm = appViewModel { AddFoodViewModel(it) }
    val search by vm.search.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Food?>(null) }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Search bar: just a back arrow and words.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        "Search foods",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.setQuery(it)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    query = ""
                    vm.setQuery("")
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        }
        Hairline()

        val results = search.results
        LazyColumn(Modifier.weight(1f)) {
            if (query.isBlank() && results.isNotEmpty()) {
                item {
                    SectionLabel("Your foods")
                }
            }
            items(results, key = { it.key }) { food ->
                FoodRow(
                    food = food,
                    onClick = { picked = food },
                    onLongClick = if (food.isCustom) ({ confirmDelete = food }) else null,
                )
            }
            if (query.isNotBlank()) {
                if (search.searched && search.query == query && results.isEmpty()) {
                    item {
                        Text(
                            "No matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        )
                    }
                }
                item {
                    TextButton(
                        onClick = { creating = query.trim() },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("+  Create “${query.trim()}”")
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Text(
                        "Type to search. Foods you create show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }

    picked?.let { food ->
        AmountSheet(
            food = food,
            loadPortions = { vm.portionsFor(it) },
            onDismiss = { picked = null },
            onAdd = { grams ->
                vm.add(food, grams) {
                    picked = null
                    onDone()
                }
            },
        )
    }

    creating?.let { name ->
        CustomFoodSheet(
            initialName = name,
            onDismiss = { creating = null },
            onCreate = { n, serving, kcal, p, c, f ->
                vm.createCustom(n, serving, kcal, p, c, f) { food ->
                    creating = null
                    picked = food
                }
            },
        )
    }

    confirmDelete?.let { food ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Delete “${food.name}”?") },
            text = { Text("It's removed from search. Anything already in your journal stays.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCustom(food)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun FoodRow(food: Food, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            food.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val origin = when {
            food.isCustom -> "Yours"
            !food.brand.isNullOrBlank() -> food.brand
            else -> null
        }
        Text(
            listOfNotNull(origin, "${kcalText(food.kcal100)} kcal / 100 g").joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
