package app.morningmeal.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morningmeal.data.DayTotals
import app.morningmeal.data.EntryEntity
import app.morningmeal.data.Macros
import app.morningmeal.data.MealEntity
import app.morningmeal.data.Repository
import app.morningmeal.data.total
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class JournalViewModel(repo: Repository) : ViewModel() {
    val totals: StateFlow<Map<Long, DayTotals>> = repo.dayTotals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val goal: StateFlow<Int> = repo.settings.settings
        .map { it.dailyGoal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

data class MealGroup(val meal: MealEntity, val entries: List<EntryEntity>, val total: Macros)

data class DayState(
    val meals: List<MealGroup> = emptyList(),
    val loose: List<EntryEntity> = emptyList(),
    val total: Macros = Macros.Zero,
    val goal: Int = 0,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = meals.isEmpty() && loose.isEmpty()
}

class DayViewModel(private val repo: Repository, day: LocalDate) : ViewModel() {
    val state: StateFlow<DayState> = combine(
        repo.mealsFor(day),
        repo.entriesFor(day),
        repo.settings.settings,
    ) { meals, entries, settings ->
        val byMeal = entries.groupBy { it.mealId }
        DayState(
            meals = meals.map { meal ->
                val list = byMeal[meal.id].orEmpty()
                MealGroup(meal, list, list.total())
            },
            loose = byMeal[null].orEmpty(),
            total = entries.total(),
            goal = settings.dailyGoal,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayState())

    fun updateGrams(entry: EntryEntity, grams: Double) = viewModelScope.launch {
        repo.updateEntry(entry.copy(grams = grams))
    }

    fun deleteEntry(entry: EntryEntity) = viewModelScope.launch { repo.deleteEntry(entry) }
    fun renameMeal(meal: MealEntity, name: String) = viewModelScope.launch { repo.renameMeal(meal.id, name) }
    fun deleteMeal(meal: MealEntity) = viewModelScope.launch { repo.deleteMeal(meal.id) }
}
