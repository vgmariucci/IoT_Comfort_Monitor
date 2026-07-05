package com.example.comfortplaces

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.comfortplaces.ui.dashboard.DashboardScreen
import com.example.comfortplaces.ui.dashboard.DashboardViewModel
import com.example.comfortplaces.ui.floorplan.FloorPlanScreen
import com.example.comfortplaces.ui.language.AppLanguage
import com.example.comfortplaces.ui.language.LanguageSelector
import com.example.comfortplaces.ui.language.LocalAppLanguage
import com.example.comfortplaces.ui.language.LocalAppStrings
import com.example.comfortplaces.ui.language.strings
import com.example.comfortplaces.ui.login.LoginScreen
import com.example.comfortplaces.ui.theme.ComfortPlacesTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class MainTab(val icon: ImageVector) {
    DASHBOARD(Icons.Default.Home),
    FLOOR_PLAN(Icons.Default.Place),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentLanguage by remember { mutableStateOf(AppLanguage.ENGLISH) }
            val strings = currentLanguage.strings()

            ComfortPlacesTheme {
                CompositionLocalProvider(
                    LocalAppLanguage provides currentLanguage,
                    LocalAppStrings  provides strings
                ) {
                    val navController = rememberNavController()

                    NavHost(navController, startDestination = "login") {
                        composable(route = "login") {
                            LoginScreen(
                                currentLanguage  = currentLanguage,
                                onLanguageChange = { currentLanguage = it },
                                onLoginSuccess   = {
                                    navController.navigate(route = "main") {
                                        popUpTo(route = "login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(route = "main") {
                            MainScreenWithTabs(
                                currentLanguage  = currentLanguage,
                                onLanguageChange = { currentLanguage = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreenWithTabs(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit
) {
    val viewModel: DashboardViewModel = hiltViewModel()
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    val strings = LocalAppStrings.current

    Scaffold(
        topBar = {
            Surface(
                color          = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text  = "Ambi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.weight(1f))
                    LanguageSelector(
                        currentLanguage  = currentLanguage,
                        onLanguageChange = onLanguageChange
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == MainTab.DASHBOARD,
                    onClick  = { currentTab = MainTab.DASHBOARD },
                    icon     = { Icon(MainTab.DASHBOARD.icon, contentDescription = strings.dashboard) },
                    label    = { Text(strings.dashboard, maxLines = 1) }
                )
                NavigationBarItem(
                    selected = currentTab == MainTab.FLOOR_PLAN,
                    onClick  = { currentTab = MainTab.FLOOR_PLAN },
                    icon     = { Icon(MainTab.FLOOR_PLAN.icon, contentDescription = strings.floorPlan) },
                    label    = { Text(strings.floorPlan, maxLines = 1) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                MainTab.DASHBOARD  -> DashboardScreen(viewModel = viewModel)
                MainTab.FLOOR_PLAN -> FloorPlanScreen(
                    readingsByLocation = viewModel.readingsByLocation
                )
            }
        }
    }
}
