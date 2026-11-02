package app.morningmeal.data

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

/** The one place screens talk to. Everything here is on-device; nothing touches the network. */
class Repository(
    private val foods: FoodDatabase,
    private val db: JournalDatabase,
    val settings: SettingsStore,
) {
    private val journal = db.journal()
    private val calc = db.calculator()
    private val custom = db.customFoods()

    // ---------- Food search ----------

    /** Custom foods first (they're yours, so likely what you mean), then the bundled database. */
    suspend fun searchFoods(query: String): List<Food> {
        val q = query.trim()
        if (q.isEmpty()) return custom.recent(20).map { it.toFood() }
        val mine = custom.search(q).map { it.toFood() }
        val bundled = foods.search(q)
        return mine + bundled
    }

    suspend fun portionsFor(food: Food): List<Portion> =
        if (food.isCustom) food.portions else foods.portions(food.key)

    suspend fun createCustomFood(food: CustomFoodEntity): Food {
        val id = custom.insert(food)
        return food.copy(id = id).toFood()
    }

    suspend fun deleteCustomFood(food: Food) {
        food.key.removePrefix(Food.CUSTOM_PREFIX).toLongOrNull()?.let { custom.delete(it) }
    }

    // ---------- Calculator ----------

    val calculatorItems: Flow<List<CalcItemEntity>> = calc.items()

    suspend fun addToCalculator(food: Food, grams: Double) {
        calc.insert(
            CalcItemEntity(
                foodKey = food.key,
                name = food.displayName(),
                grams = grams,
                kcal100 = food.kcal100,
                protein100 = food.protein100,
                carbs100 = food.carbs100,
                fat100 = food.fat100,
            ),
        )
    }

    suspend fun updateCalculatorItem(item: CalcItemEntity) = calc.update(item)
    suspend fun removeCalculatorItem(item: CalcItemEntity) = calc.delete(item)
    suspend fun clearCalculator() = calc.clear()

    /** Moves everything in the calculator into the journal as one named meal, then clears it. */
    suspend fun importCalculatorAsMeal(day: LocalDate, mealName: String) = db.withTransaction {
        val items = calc.itemsNow()
        if (items.isEmpty()) return@withTransaction
        val mealId = journal.insertMeal(MealEntity(day = day.toEpochDay(), name = mealName.trim()))
        journal.insertEntries(items.map { it.toEntry(day, mealId) })
        calc.clear()
    }

    /** Moves everything into the journal as loose foods on that day, then clears the calculator. */
    suspend fun importCalculatorToDay(day: LocalDate) = db.withTransaction {
        val items = calc.itemsNow()
        if (items.isEmpty()) return@withTransaction
        journal.insertEntries(items.map { it.toEntry(day, mealId = null) })
        calc.clear()
    }

    // ---------- Journal ----------

    /** Totals for every day that has anything logged, keyed by epoch day. */
    val dayTotals: Flow<Map<Long, DayTotals>> =
        journal.dayTotals().map { list -> list.associateBy { it.day } }

    fun mealsFor(day: LocalDate): Flow<List<MealEntity>> = journal.mealsForDay(day.toEpochDay())
    fun entriesFor(day: LocalDate): Flow<List<EntryEntity>> = journal.entriesForDay(day.toEpochDay())

    suspend fun updateEntry(entry: EntryEntity) = journal.updateEntry(entry)
    suspend fun deleteEntry(entry: EntryEntity) = journal.deleteEntry(entry)
    suspend fun renameMeal(id: Long, name: String) = journal.renameMeal(id, name.trim())
    suspend fun deleteMeal(id: Long) = journal.deleteMeal(id)

    suspend fun deleteAllJournalData() = db.withTransaction {
        journal.deleteAllEntries()
        journal.deleteAllMeals()
    }

    /** Writes the whole journal as CSV to a file the user picked (Storage Access Framework). */
    suspend fun exportCsv(resolver: ContentResolver, uri: Uri): Int = withContext(Dispatchers.IO) {
        val rows = journal.exportRows()
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
            out.write("date,meal,food,grams,kcal,protein_g,carbs_g,fat_g\n")
            for (r in rows) {
                val g = r.grams
                out.write(
                    listOf(
                        LocalDate.ofEpochDay(r.day).toString(),
                        csv(r.mealName ?: ""),
                        csv(r.name),
                        fmt(g),
                        fmt(r.kcal100 * g / 100),
                        fmt(r.protein100 * g / 100),
                        fmt(r.carbs100 * g / 100),
                        fmt(r.fat100 * g / 100),
                    ).joinToString(","),
                )
                out.write("\n")
            }
        } ?: error("Could not open the export file")
        rows.size
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun fmt(d: Double): String = String.format(Locale.US, "%.1f", d)
}

private fun CalcItemEntity.toEntry(day: LocalDate, mealId: Long?) = EntryEntity(
    day = day.toEpochDay(),
    mealId = mealId,
    name = name,
    grams = grams,
    kcal100 = kcal100,
    protein100 = protein100,
    carbs100 = carbs100,
    fat100 = fat100,
)

private fun CustomFoodEntity.toFood() = Food(
    key = Food.CUSTOM_PREFIX + id,
    name = name,
    brand = null,
    kcal100 = kcal100,
    protein100 = protein100,
    carbs100 = carbs100,
    fat100 = fat100,
    portions = if (servingGrams != null && servingGrams > 0) {
        listOf(Portion(servingLabel?.ifBlank { null } ?: "1 serving", servingGrams))
    } else {
        emptyList()
    },
)

fun Food.displayName(): String = if (brand.isNullOrBlank()) name else "$name · $brand"
