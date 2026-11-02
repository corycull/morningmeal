package app.morningmeal.data

/** Totals for some amount of food. */
data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        kcal + other.kcal,
        protein + other.protein,
        carbs + other.carbs,
        fat + other.fat,
    )

    companion object {
        val Zero = Macros()
    }
}

/**
 * Anything that is "this many grams of a food with these per-100 g values".
 * Everything stored in the journal and the calculator is a snapshot in this shape,
 * so editing or replacing the food database never rewrites history.
 */
interface Portioned {
    val grams: Double
    val kcal100: Double
    val protein100: Double
    val carbs100: Double
    val fat100: Double
}

// An extension rather than an interface member, so Room never mistakes it for a column.
val Portioned.macros: Macros
    get() = Macros(
        kcal = kcal100 * grams / 100.0,
        protein = protein100 * grams / 100.0,
        carbs = carbs100 * grams / 100.0,
        fat = fat100 * grams / 100.0,
    )

fun Iterable<Portioned>.total(): Macros = fold(Macros.Zero) { acc, p -> acc + p.macros }

/** A food you can pick from search: bundled (USDA) or user-created. Values are per 100 g. */
data class Food(
    val key: String,
    val name: String,
    val brand: String?,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val portions: List<Portion> = emptyList(),
) {
    val isCustom: Boolean get() = key.startsWith(CUSTOM_PREFIX)

    companion object {
        const val USDA_PREFIX = "usda:"
        const val CUSTOM_PREFIX = "custom:"
    }
}

data class Portion(val label: String, val grams: Double)

fun Food.macrosFor(grams: Double): Macros = Macros(
    kcal = kcal100 * grams / 100.0,
    protein = protein100 * grams / 100.0,
    carbs = carbs100 * grams / 100.0,
    fat = fat100 * grams / 100.0,
)
