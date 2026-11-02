@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package app.morningmeal.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.morningmeal.data.CustomFoodEntity
import app.morningmeal.data.Food
import app.morningmeal.data.Portion
import app.morningmeal.data.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val searched: Boolean = false,
)

class AddFoodViewModel(private val repo: Repository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refresh = MutableStateFlow(0)

    val search: StateFlow<SearchState> = combine(query, refresh) { q, _ -> q }
        .debounce { if (it.isBlank()) 0L else 120L }
        .mapLatest { q -> SearchState(query = q, results = repo.searchFoods(q), searched = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState())

    fun setQuery(text: String) {
        query.value = text
    }

    suspend fun portionsFor(food: Food): List<Portion> = repo.portionsFor(food)

    fun add(food: Food, grams: Double, onDone: () -> Unit) = viewModelScope.launch {
        repo.addToCalculator(food, grams)
        onDone()
    }

    /** Label values are per serving, which is how people read them; stored per 100 g. */
    fun createCustom(
        name: String,
        servingGrams: Double,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        onCreated: (Food) -> Unit,
    ) = viewModelScope.launch {
        val per100 = 100.0 / servingGrams
        val food = repo.createCustomFood(
            CustomFoodEntity(
                name = name.trim(),
                kcal100 = kcal * per100,
                protein100 = protein * per100,
                carbs100 = carbs * per100,
                fat100 = fat * per100,
                servingLabel = if (servingGrams != 100.0) "1 serving" else null,
                servingGrams = if (servingGrams != 100.0) servingGrams else null,
            ),
        )
        onCreated(food)
    }

    fun deleteCustom(food: Food) = viewModelScope.launch {
        repo.deleteCustomFood(food)
        refresh.value++ // re-run the current search so the row disappears
    }
}
