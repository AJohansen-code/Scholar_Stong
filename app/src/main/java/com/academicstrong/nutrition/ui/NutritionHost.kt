package com.academicstrong.nutrition.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Entry point. Drop this into Academic Strong's navigation as one destination:
 *   NutritionHost()
 * If the app already has its own bottom bar, remove the Scaffold here and keep the `when` block.
 */
@Composable
fun NutritionHost() {
    val app = LocalContext.current.applicationContext as Application
    val vm: NutritionViewModel = viewModel(factory = NutritionViewModel.Factory(app))
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Today" to Icons.Default.List, "Ideas" to Icons.Default.Star,
        "Progress" to Icons.Default.DateRange, "Learn" to Icons.Default.Info)

    Scaffold(bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i },
                    icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> TodayScreen(vm)
                1 -> IdeasScreen(vm)
                2 -> ProgressScreen(vm)
                else -> LearnScreen()
            }
        }
    }
}
