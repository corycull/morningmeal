package app.morningmeal.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bundled, read-only nutrition database (assets/foods.db, built by tools/build_food_db.py).
 *
 * It is plain SQLite rather than Room: it's generated outside the app, never written to,
 * and replaced wholesale whenever the app is updated.
 *
 * Schema (all nutrient values per 100 g):
 *   food(id, name, brand, gtin, kcal, protein, carbs, fat, rank)
 *   portion(food_id, label, grams)
 *   food_fts  -- FTS4 index over food(name, brand)
 */
class FoodDatabase(private val context: Context) {

    private val mutex = Mutex()
    private var db: SQLiteDatabase? = null
    private var unavailable = false

    private suspend fun open(): SQLiteDatabase? = mutex.withLock {
        val existing = db
        if (existing != null || unavailable) return@withLock existing
        withContext(Dispatchers.IO) {
            try {
                val file = context.getDatabasePath(FILE_NAME)
                copyFromAssetsIfNeeded(file)
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                    .also { db = it }
            } catch (e: Exception) {
                Log.w(TAG, "Food database unavailable; search will only show custom foods", e)
                unavailable = true
                null
            }
        }
    }

    /** Re-copy after every install or update so a new APK always brings its new food data. */
    private fun copyFromAssetsIfNeeded(target: File) {
        val prefs = context.getSharedPreferences(TAG, Context.MODE_PRIVATE)
        val installStamp = installStamp()
        if (target.exists() && prefs.getLong(KEY_STAMP, -1L) == installStamp) return

        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        context.assets.open(FILE_NAME).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) error("Could not move food database into place")
        prefs.edit().putLong(KEY_STAMP, installStamp).apply()
    }

    private fun installStamp(): Long {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        return info.lastUpdateTime
    }

    /** Prefix search: "chick bre" matches "Chicken, breast, ...". Generic foods rank above branded. */
    suspend fun search(query: String, limit: Int = 60): List<Food> {
        val match = toMatchExpression(query) ?: return emptyList()
        val database = open() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val foods = mutableListOf<Food>()
            database.rawQuery(
                """
                SELECT f.id, f.name, f.brand, f.kcal, f.protein, f.carbs, f.fat
                FROM food_fts
                JOIN food f ON f.id = food_fts.docid
                WHERE food_fts MATCH ?
                ORDER BY f.rank, length(f.name)
                LIMIT ?
                """.trimIndent(),
                arrayOf(match, limit.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    foods += Food(
                        key = Food.USDA_PREFIX + c.getLong(0),
                        name = c.getString(1),
                        brand = if (c.isNull(2)) null else c.getString(2).ifBlank { null },
                        kcal100 = c.getDouble(3),
                        protein100 = c.getDouble(4),
                        carbs100 = c.getDouble(5),
                        fat100 = c.getDouble(6),
                    )
                }
            }
            foods
        }
    }

    /** In the order the builder wrote them; the first is the default amount. */
    suspend fun portions(foodKey: String): List<Portion> {
        val id = foodKey.removePrefix(Food.USDA_PREFIX).toLongOrNull() ?: return emptyList()
        val database = open() ?: return emptyList()
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<Portion>()
            database.rawQuery(
                "SELECT label, grams FROM portion WHERE food_id = ? ORDER BY rowid",
                arrayOf(id.toString()),
            ).use { c ->
                while (c.moveToNext()) result += Portion(c.getString(0), c.getDouble(1))
            }
            result
        }
    }

    companion object {
        private const val TAG = "FoodDatabase"
        private const val FILE_NAME = "foods.db"
        private const val KEY_STAMP = "install_stamp"

        /** "Chicken, bre" -> "chicken* bre*". Returns null when nothing searchable is left. */
        fun toMatchExpression(query: String): String? {
            val tokens = query.lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
