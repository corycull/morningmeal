package app.morningmeal.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morningmeal.data.CalcItemEntity
import app.morningmeal.data.Macros
import app.morningmeal.data.Repository
import app.morningmeal.data.total
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalculatorState(
    val items: List<CalcItemEntity> = emptyList(),
    val total: Macros = Macros.Zero,
    val loaded: Boolean = false,
)

enum class SaveTarget { Meal, Day }

class CalculatorViewModel(private val repo: Repository) : ViewModel() {

    val state: StateFlow<CalculatorState> = repo.calculatorItems
        .map { items -> CalculatorState(items = items, total = items.total(), loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalculatorState())

    fun updateGrams(item: CalcItemEntity, grams: Double) = viewModelScope.launch {
        repo.updateCalculatorItem(item.copy(grams = grams))
    }

    fun remove(item: CalcItemEntity) = viewModelScope.launch { repo.removeCalculatorItem(item) }

    fun clear() = viewModelScope.launch { repo.clearCalculator() }

    fun save(target: SaveTarget, day: LocalDate, mealName: String, onSaved: () -> Unit) = viewModelScope.launch {
        when (target) {
            SaveTarget.Meal -> repo.importCalculatorAsMeal(day, mealName.ifBlank { "Meal" })
            SaveTarget.Day -> repo.importCalculatorToDay(day)
        }
        onSaved()
    }
}
