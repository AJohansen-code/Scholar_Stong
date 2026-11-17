package com.academicstrong.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.academicstrong.nutrition.data.NutritionProfile
import com.academicstrong.nutrition.data.totals
import com.team2.studentfitness.database.MacroLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(vm: NutritionViewModel) {
    val day by vm.selectedDay.collectAsState()
    val entries by vm.dayEntries.collectAsState()
    val profile by vm.profile.collectAsState()
    val goals by vm.goals.collectAsState()
    val ai by vm.ai.collectAsState()
    val insight by vm.insight.collectAsState()
    var text by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var showPrefs by remember { mutableStateOf(false) }
    val totals = entries.totals()

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
                TextButton(onClick = { showPrefs = true }) { Text("Diet & Allergies") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (profile.showCalories) GoalBar("Calories", totals.calories, goals.calories, "kcal")
                    GoalBar("Protein", totals.proteinG, goals.proteinG, "g")
                    GoalBar("Carbs", totals.carbsG, goals.carbsG, "g")
                    GoalBar("Fat", totals.fatG, goals.fatG, "g")
                    GoalBar("Fiber", totals.fiberG, goals.fiberG, "g")
                    Text(
                        "Sugar ${totals.sugarG} / ${goals.sugarG} g · Sodium ${totals.sodiumMg} / ${goals.sodiumMg} mg",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("What did you eat or drink?") },
                        placeholder = { Text("2 eggs, toast, and a latte") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.logFromText(text) { text = "" } }, enabled = text.isNotBlank() && !ai.busy) {
                            Text("Estimate & Log")
                        }
                        OutlinedButton(onClick = { showAdd = true }) { Text("Add Manually") }
                        if (ai.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    ai.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (entries.isNotEmpty()) item { Text("Logged", style = MaterialTheme.typography.titleMedium) }
        items(entries.size) { i ->
            val e = entries[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name)
                    val cal = if (profile.showCalories) "${e.calories} kcal · " else ""
                    Text("$cal" + "P ${e.protein}g · C ${e.carbs}g · F ${e.fat}g", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.deleteEntry(e) }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${e.name}") }
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
    if (showPrefs) PrefsDialog(profile, onDismiss = { showPrefs = false }) { vm.saveProfile(it); showPrefs = false }
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
private fun NumField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit)) }, label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onSave: (MacroLog) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cal by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }; var f by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }; var sugar by remember { mutableStateOf("") }
    var sodium by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add food or drink") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumField("Calories", cal) { cal = it }
                NumField("Protein (g)", p) { p = it }
                NumField("Carbs (g)", c) { c = it }
                NumField("Fat (g)", f) { f = it }
                NumField("Fiber (g)", fiber) { fiber = it }
                NumField("Sugar (g)", sugar) { sugar = it }
                NumField("Sodium (mg)", sodium) { sodium = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(MacroLog(
                    name = name.trim(), calories = cal.toIntOrNull() ?: 0, protein = p.toIntOrNull() ?: 0,
                    carbs = c.toIntOrNull() ?: 0, fat = f.toIntOrNull() ?: 0, fiber = fiber.toIntOrNull() ?: 0,
                    sugar = sugar.toIntOrNull() ?: 0, sodium = sodium.toIntOrNull() ?: 0
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PrefsDialog(current: NutritionProfile, onDismiss: () -> Unit, onSave: (NutritionProfile) -> Unit) {
    var diet by remember { mutableStateOf(current.dietLabels) }
    var allergies by remember { mutableStateOf(current.allergies) }
    var show by remember { mutableStateOf(current.showCalories) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diet & allergies") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Used for AI meal ideas. Calorie and macro goals come from your fitness profile.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(diet, { diet = it }, label = { Text("Diet labels (Vegan, Keto...)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies, comma separated") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show calorie numbers in NutriTrack", Modifier.weight(1f)); Switch(checked = show, onCheckedChange = { show = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(current.copy(dietLabels = diet.trim(), allergies = allergies.trim(), showCalories = show)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
