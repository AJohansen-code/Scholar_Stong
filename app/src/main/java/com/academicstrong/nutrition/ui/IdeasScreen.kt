package com.academicstrong.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.academicstrong.nutrition.data.MealType

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IdeasScreen(vm: NutritionViewModel) {
    val pantry by vm.pantry.collectAsState()
    val ideas by vm.ideas.collectAsState()
    val skipped by vm.skippedIdeas.collectAsState()
    val ai by vm.ai.collectAsState()
    val profile by vm.profile.collectAsState()
    val day by vm.selectedDay.collectAsState()
    var item by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(MealType.LUNCH) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What's in your pantry?", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(item, { item = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add an item") })
                        Button(onClick = { vm.addPantry(item); item = "" }, enabled = item.isNotBlank()) { Text("Add") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pantry.forEach { p ->
                            InputChip(selected = false, onClick = { vm.removePantry(p) }, label = { Text(p.name) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove ${p.name}", Modifier.size(16.dp)) })
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK).forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.name.lowercase()) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.generateIdeas(type) }, enabled = !ai.busy) { Text("Get healthy ideas") }
                    if (ai.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                ai.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (skipped > 0) Text("Hid $skipped idea(s) that matched an allergy you listed.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(ideas.size) { i ->
            val idea = ideas[i]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(idea.name, style = MaterialTheme.typography.titleMedium)
                    val cal = if (profile.showCalories) "${idea.calories} kcal · " else ""
                    Text("${idea.minutes} min · $cal" + "P ${idea.proteinG}g · C ${idea.carbsG}g · F ${idea.fatG}g", style = MaterialTheme.typography.bodySmall)
                    Text(idea.ingredients.joinToString("\n") { "• $it" })
                    Text(idea.instructions)
                    OutlinedButton(onClick = { vm.logIdea(idea) }) { Text("Log this to ${if (day == vm.today) "today" else "this day"}") }
                }
            }
        }
        item {
            Text(
                "Ideas and nutrition numbers are AI estimates. If you have an allergy, always check ingredients and labels yourself. The allergy filter is a safety net, not a guarantee.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
