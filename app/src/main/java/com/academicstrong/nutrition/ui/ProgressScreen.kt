package com.academicstrong.nutrition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The "visual history" from the pitch: intake, mood and stress side by side over the last 14 days. */
@Composable
fun ProgressScreen(vm: NutritionViewModel) {
    val history by vm.history.collectAsState()
    val moods by vm.moods.collectAsState()
    val profile by vm.profile.collectAsState()
    val insight by vm.moodInsight.collectAsState()
    val days = (vm.firstDay..vm.today).toList()
    val byDay = history.groupBy { it.epochDay }
    val moodByDay = moods.associateBy { it.epochDay }
    val fmt = DateTimeFormatter.ofPattern("MMM d")

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (profile.showCalories) {
            ChartCard("Calories per day", days.map { d -> (byDay[d]?.sumOf { it.calories } ?: 0).toFloat() }, profile.calorieGoal.toFloat())
        }
        ChartCard("Protein (g) per day", days.map { d -> (byDay[d]?.sumOf { it.proteinG } ?: 0).toFloat() }, profile.proteinGoalG.toFloat())
        ChartCard("Mood (1-5)", days.map { d -> (moodByDay[d]?.mood ?: 0).toFloat() }, null)
        ChartCard("Stress (1-5)", days.map { d -> (moodByDay[d]?.stress ?: 0).toFloat() }, null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt.format(LocalDate.ofEpochDay(vm.firstDay)), style = MaterialTheme.typography.bodySmall)
            Text("Today", style = MaterialTheme.typography.bodySmall)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("What your data says", style = MaterialTheme.typography.titleMedium)
                Text(insight ?: "Log your food and a mood check-in on at least 5 days, with a mix of higher and lower eating days, to see how they line up.")
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, values: List<Float>, goal: Float?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            val bar = MaterialTheme.colorScheme.primary
            val line = MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val maxV = maxOf(values.maxOrNull() ?: 0f, goal ?: 0f) * 1.15f
                if (maxV <= 0f) return@Canvas
                val slot = size.width / values.size
                values.forEachIndexed { i, v ->
                    val h = size.height * (v / maxV)
                    drawRect(bar, topLeft = Offset(i * slot + slot * 0.15f, size.height - h), size = Size(slot * 0.7f, h))
                }
                if (goal != null) {
                    val y = size.height * (1 - goal / maxV)
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                }
            }
            if (goal != null) Text("Dashed line = your goal", style = MaterialTheme.typography.bodySmall)
        }
    }
}
