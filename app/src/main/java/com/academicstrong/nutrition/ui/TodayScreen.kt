package com.academicstrong.nutrition.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academicstrong.nutrition.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(vm: NutritionViewModel) {
    val day by vm.selectedDay.collectAsState()
    val entries by vm.dayEntries.collectAsState()
    val profile by vm.profile.collectAsState()
    val moods by vm.moods.collectAsState()
    val ai by vm.ai.collectAsState()
    val insight by vm.insight.collectAsState()
    var text by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showGoals by remember { mutableStateOf(false) }
    val totals = entries.totals()
    val mood = moods.firstOrNull { it.epochDay == day }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.shiftDay(-1) }, enabled = day > vm.firstDay) { Text("‹") }
                Text(
                    if (day == vm.today) "Today" else DateTimeFormatter.ofPattern("EEE, MMM d").format(LocalDate.ofEpochDay(day)),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = { vm.shiftDay(1) }, enabled = day < vm.today) { Text("›") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showGoals = true }) { Text("Goals") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profile.showCalories) GoalBar("Calories", totals.calories, profile.calorieGoal, "kcal")
                    GoalBar("Protein", totals.proteinG, profile.proteinGoalG, "g")
                    GoalBar("Carbs", totals.carbsG, profile.carbsGoalG, "g")
                    GoalBar("Fat", totals.fatG, profile.fatGoalG, "g")
                    GoalBar("Fluids", totals.fluidMl, profile.fluidGoalMl, "ml")
                    if (totals.caffeineMg > 0) Text("Caffeine so far: about ${totals.caffeineMg} mg", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("What did you eat or drink?") },
                        placeholder = { Text("2 eggs, toast and a latte") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.logFromText(text) { text = "" } }, enabled = text.isNotBlank() && !ai.busy) {
                            Text("Estimate & log")
                        }
                        OutlinedButton(onClick = { showAdd = true }) { Text("Add manually") }
                        if (ai.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    ai.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text("Numbers from Estimate & log are AI estimates.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        MealType.values().forEach { type ->
            val group = entries.filter { it.mealType == type }
            if (group.isNotEmpty()) {
                item { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium) }
                items(group.size) { i ->
                    val e = group[i]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name)
                            val cal = if (profile.showCalories) "${e.calories} kcal · " else ""
                            Text(
                                "$cal" + "P ${e.proteinG}g · C ${e.carbsG}g · F ${e.fatG}g" + if (e.isEstimate) " · est." else "",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = { vm.deleteEntry(e) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${e.name}") }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How are you feeling?", style = MaterialTheme.typography.titleMedium)
                    ScaleRow("Mood (1 low, 5 great)", mood?.mood) { vm.saveMood(it, mood?.stress ?: 3) }
                    ScaleRow("Stress (1 calm, 5 high)", mood?.stress) { vm.saveMood(mood?.mood ?: 3, it) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.refreshInsight() }, enabled = entries.isNotEmpty() && !ai.busy) { Text("Get feedback on this day") }
                    insight?.let { Text(it) }
                }
            }
        }
    }

    if (showAdd) AddEntryDialog(onDismiss = { showAdd = false }) { vm.addEntry(it); showAdd = false }
    if (showGoals) GoalsDialog(profile, onDismiss = { showGoals = false }) { vm.saveProfile(it); showGoals = false }
}

@Composable
private fun GoalBar(label: String, value: Int, goal: Int, unit: String) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text("$value / $goal $unit", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { if (goal > 0) (value.toFloat() / goal).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ScaleRow(label: String, selected: Int?, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { n -> FilterChip(selected = selected == n, onClick = { onPick(n) }, label = { Text("$n") }) }
    }
}

@Composable
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit)) }, label = { Text(label) },
        singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onSave: (FoodLogEntry) -> Unit) {
    var type by remember { mutableStateOf(MealType.SNACK) }
    var name by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }; var f by remember { mutableStateOf("") }
    var ml by remember { mutableStateOf("") }; var caf by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add food or drink") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealType.values().forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.lowercase()) }) }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumField("Calories", cal) { cal = it }
                NumField("Protein (g)", p) { p = it }
                NumField("Carbs (g)", c) { c = it }
                NumField("Fat (g)", f) { f = it }
                if (type == MealType.DRINK) { NumField("Volume (ml)", ml) { ml = it }; NumField("Caffeine (mg)", caf) { caf = it } }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(FoodLogEntry(epochDay = 0, mealType = type, name = name.trim(), calories = cal.toIntOrNull() ?: 0,
                    proteinG = p.toIntOrNull() ?: 0, carbsG = c.toIntOrNull() ?: 0, fatG = f.toIntOrNull() ?: 0,
                    volumeMl = ml.toIntOrNull() ?: 0, caffeineMg = caf.toIntOrNull() ?: 0))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoalsDialog(current: NutritionProfile, onDismiss: () -> Unit, onSave: (NutritionProfile) -> Unit) {
    var cal by remember { mutableStateOf(current.calorieGoal.toString()) }
    var p by remember { mutableStateOf(current.proteinGoalG.toString()) }
    var c by remember { mutableStateOf(current.carbsGoalG.toString()) }
    var f by remember { mutableStateOf(current.fatGoalG.toString()) }
    var fl by remember { mutableStateOf(current.fluidGoalMl.toString()) }
    var diet by remember { mutableStateOf(current.dietLabels) }
    var allergies by remember { mutableStateOf(current.allergies) }
    var show by remember { mutableStateOf(current.showCalories) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your goals") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Defaults are placeholders. Set targets that fit you, ideally with a dietitian or your campus health center.", style = MaterialTheme.typography.bodySmall)
                NumField("Calories", cal) { cal = it }
                NumField("Protein (g)", p) { p = it }
                NumField("Carbs (g)", c) { c = it }
                NumField("Fat (g)", f) { f = it }
                NumField("Fluids (ml)", fl) { fl = it }
                OutlinedTextField(diet, { diet = it }, label = { Text("Diet labels (Vegan, Keto...)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies, comma separated") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show calorie numbers", Modifier.weight(1f)); Switch(checked = show, onCheckedChange = { show = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(current.copy(
                    calorieGoal = cal.toIntOrNull() ?: current.calorieGoal, proteinGoalG = p.toIntOrNull() ?: current.proteinGoalG,
                    carbsGoalG = c.toIntOrNull() ?: current.carbsGoalG, fatGoalG = f.toIntOrNull() ?: current.fatGoalG,
                    fluidGoalMl = fl.toIntOrNull() ?: current.fluidGoalMl, dietLabels = diet.trim(),
                    allergies = allergies.trim(), showCalories = show))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
