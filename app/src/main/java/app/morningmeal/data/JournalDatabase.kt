package app.morningmeal.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Days are stored as LocalDate.toEpochDay() so grouping and sorting are plain integer ops.

@Entity(tableName = "meal", indices = [Index("day")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One food in the journal. mealId == null means a loose food attached only to the day. */
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = MealEntity::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("day"), Index("mealId")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val mealId: Long?,
    val name: String,
    override val grams: Double,
    override val kcal100: Double,
    override val protein100: Double,
    override val carbs100: Double,
    override val fat100: Double,
    val createdAt: Long = System.currentTimeMillis(),
) : Portioned

/** An item currently in the calculator on the landing page. Persisted so it survives restarts. */
@Entity(tableName = "calc_item")
data class CalcItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodKey: String,
    val name: String,
    override val grams: Double,
    override val kcal100: Double,
    override val protein100: Double,
    override val carbs100: Double,
    override val fat100: Double,
    val createdAt: Long = System.currentTimeMillis(),
) : Portioned

@Entity(tableName = "custom_food")
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    /** Optional serving, e.g. "1 bar" = 45 g, offered as a quick amount. */
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class DayTotals(
    val day: Long,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

val DayTotals.macros: Macros get() = Macros(kcal, protein, carbs, fat)

/** Flat row used for CSV export. */
data class ExportRow(
    val day: Long,
    @ColumnInfo(name = "mealName") val mealName: String?,
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
)

@Dao
interface JournalDao {
    @Query(
        """
        SELECT day,
               SUM(grams * kcal100 / 100.0)    AS kcal,
               SUM(grams * protein100 / 100.0) AS protein,
               SUM(grams * carbs100 / 100.0)   AS carbs,
               SUM(grams * fat100 / 100.0)     AS fat
        FROM entry
        GROUP BY day
        """
    )
    fun dayTotals(): Flow<List<DayTotals>>

    @Query("SELECT * FROM meal WHERE day = :day ORDER BY createdAt, id")
    fun mealsForDay(day: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM entry WHERE day = :day ORDER BY createdAt, id")
    fun entriesForDay(day: Long): Flow<List<EntryEntity>>

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<EntryEntity>)

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Delete
    suspend fun deleteEntry(entry: EntryEntity)

    @Query("UPDATE meal SET name = :name WHERE id = :id")
    suspend fun renameMeal(id: Long, name: String)

    /** Entries in the meal go with it (ON DELETE CASCADE). */
    @Query("DELETE FROM meal WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Query(
        """
        SELECT e.day, m.name AS mealName, e.name, e.grams,
               e.kcal100, e.protein100, e.carbs100, e.fat100
        FROM entry e LEFT JOIN meal m ON m.id = e.mealId
        ORDER BY e.day, m.createdAt, e.createdAt, e.id
        """
    )
    suspend fun exportRows(): List<ExportRow>

    @Query("DELETE FROM entry")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM meal")
    suspend fun deleteAllMeals()
}

@Dao
interface CalculatorDao {
    @Query("SELECT * FROM calc_item ORDER BY createdAt, id")
    fun items(): Flow<List<CalcItemEntity>>

    @Query("SELECT * FROM calc_item ORDER BY createdAt, id")
    suspend fun itemsNow(): List<CalcItemEntity>

    @Insert
    suspend fun insert(item: CalcItemEntity)

    @Update
    suspend fun update(item: CalcItemEntity)

    @Delete
    suspend fun delete(item: CalcItemEntity)

    @Query("DELETE FROM calc_item")
    suspend fun clear()
}

@Dao
interface CustomFoodDao {
    @Query("SELECT * FROM custom_food WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 30")
    suspend fun search(query: String): List<CustomFoodEntity>

    @Query("SELECT * FROM custom_food ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CustomFoodEntity>

    @Insert
    suspend fun insert(food: CustomFoodEntity): Long

    @Query("DELETE FROM custom_food WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [MealEntity::class, EntryEntity::class, CalcItemEntity::class, CustomFoodEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journal(): JournalDao
    abstract fun calculator(): CalculatorDao
    abstract fun customFoods(): CustomFoodDao

    companion object {
        fun create(context: Context): JournalDatabase =
            Room.databaseBuilder(context, JournalDatabase::class.java, "journal.db").build()
    }
}
