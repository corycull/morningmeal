package app.morningmeal.ui.components

import app.morningmeal.data.Macros
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val intFormat: NumberFormat = NumberFormat.getIntegerInstance()

fun kcalText(kcal: Double): String = intFormat.format(kcal.roundToInt())

fun gramsText(grams: Double): String =
    if (grams % 1.0 == 0.0) "${grams.toInt()} g" else String.format(Locale.getDefault(), "%.1f g", grams)

fun macroLine(m: Macros): String =
    "P ${m.protein.roundToInt()}  ·  C ${m.carbs.roundToInt()}  ·  F ${m.fat.roundToInt()}"

private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE")
private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dateYearFormat = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

/** "Today", "Yesterday", "Monday" (within a week), then "Tue, Oct 14" or with the year if older. */
fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String {
    val diff = ChronoUnit.DAYS.between(day, today)
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Yesterday"
        diff == -1L -> "Tomorrow"
        diff in 2..6 -> day.format(weekdayFormat)
        day.year == today.year -> day.format(dateFormat)
        else -> day.format(dateYearFormat)
    }
}

/** Parses user-typed numbers, accepting either "," or "." as decimal separator. */
fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
