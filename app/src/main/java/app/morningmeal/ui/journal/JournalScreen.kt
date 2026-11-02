package app.morningmeal.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.morningmeal.data.DayTotals
import app.morningmeal.data.macros
import app.morningmeal.ui.components.Hairline
import app.morningmeal.ui.components.PageTitle
import app.morningmeal.ui.components.ValueRow
import app.morningmeal.ui.components.appViewModel
import app.morningmeal.ui.components.dayLabel
import app.morningmeal.ui.components.kcalText
import app.morningmeal.ui.components.macroLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** About a century of days. Rows are computed from the index, so this costs nothing until scrolled to. */
private const val DAYS_BACK = 36_525

private val shortDate = DateTimeFormatter.ofPattern("MMM d")

/**
 * Every calendar day, newest first, scrolling back indefinitely. Empty days are shown dimmed
 * so gaps are visible, and each row opens that day.
 */
@Composable
fun JournalScreen(onOpenDay: (LocalDate) -> Unit) {
    val vm = appViewModel { JournalViewModel(it) }
    val totals by vm.totals.collectAsStateWithLifecycle()
    val goal by vm.goal.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        PageTitle("Journal")
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(
                count = DAYS_BACK,
                key = { index -> today.minusDays(index.toLong()).toEpochDay() },
            ) { index ->
                val day = today.minusDays(index.toLong())
                DayRow(
                    day = day,
                    today = today,
                    totals = totals[day.toEpochDay()],
                    goal = goal,
                    onClick = { onOpenDay(day) },
                )
                Hairline(Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun DayRow(
    day: LocalDate,
    today: LocalDate,
    totals: DayTotals?,
    goal: Int,
    onClick: () -> Unit,
) {
    val label = dayLabel(day, today)
    // Relative labels ("Today", "Monday") get the actual date underneath.
    val recent = ChronoUnit.DAYS.between(day, today) in 0..6
    val hasData = totals != null && totals.kcal > 0.5

    Box {
        ValueRow(
            title = label,
            subtitle = if (recent) day.format(shortDate) else null,
            value = if (hasData) kcalText(totals!!.kcal) else "—",
            valueSubtitle = if (hasData) macroLine(totals!!.macros) else null,
            dim = !hasData,
            onClick = onClick,
        )
        // A hairline progress bar toward the daily goal, if one is set.
        if (goal > 0 && hasData) {
            val fraction = (totals!!.kcal / goal).toFloat().coerceIn(0f, 1f)
            val over = totals.kcal > goal
            Box(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomStart),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(
                            if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                        ),
                )
            }
        }
    }
}
