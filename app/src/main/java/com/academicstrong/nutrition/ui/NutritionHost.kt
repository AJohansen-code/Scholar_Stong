package com.academicstrong.nutrition.ui

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun NutritionHost() {
    val app = LocalContext.current.applicationContext as Application
    val vm: NutritionViewModel = viewModel(factory = NutritionViewModel.Factory(app))
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Today" to Icons.Default.List, "Ideas" to Icons.Default.Star,
        "Progress" to Icons.Default.DateRange, "Learn" to Icons.Default.Info)

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
            tabs.forEachIndexed { i, (label, icon) ->
                NavigationBarItem(selected = tab == i, onClick = { Log.d("NutriTab", "tapped $label"); tab = i },
                    icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Box(Modifier.padding(padding)) {
            Log.d("NutriTab", "showing tab $tab")
            when (tab) {
                0 -> TodayScreen(vm)
                1 -> IdeasScreen(vm)
                2 -> ProgressScreen(vm)
                else -> LearnScreen()
            }
        }
    }
}
