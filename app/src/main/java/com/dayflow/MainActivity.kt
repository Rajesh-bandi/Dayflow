package com.dayflow

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dayflow.ui.ActionsScreen
import com.dayflow.ui.EditorScreen
import com.dayflow.ui.FocusScreen
import com.dayflow.ui.Ink
import com.dayflow.ui.Lavender
import com.dayflow.ui.Muted
import com.dayflow.ui.ProgressScreen
import com.dayflow.ui.Raised
import com.dayflow.ui.SetupScreen
import com.dayflow.ui.Surface
import com.dayflow.ui.TodayScreen
import com.dayflow.ui.WeekScreen
import com.dayflow.ui.RoutinesScreen
import com.dayflow.ui.SanctuaryScreen
import com.dayflow.ui.SettingsScreen
import com.dayflow.ui.ArchivedTasksScreen
import com.dayflow.ui.LumiScreen
import com.dayflow.ui.LightDayflowPalette
import com.dayflow.ui.DarkDayflowPalette
import com.dayflow.ui.LocalDayflowPalette
import com.dayflow.ui.Local24HourTime
import com.dayflow.ui.LocalAgentName
import com.dayflow.ui.Text as UiText
import com.dayflow.R

private val DayflowFont = FontFamily(Font(R.font.inter_regular, FontWeight.Normal))
private val DayflowTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = DayflowFont),
        displayMedium = base.displayMedium.copy(fontFamily = DayflowFont),
        displaySmall = base.displaySmall.copy(fontFamily = DayflowFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = DayflowFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = DayflowFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = DayflowFont),
        titleLarge = base.titleLarge.copy(fontFamily = DayflowFont),
        titleMedium = base.titleMedium.copy(fontFamily = DayflowFont),
        titleSmall = base.titleSmall.copy(fontFamily = DayflowFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = DayflowFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = DayflowFont),
        bodySmall = base.bodySmall.copy(fontFamily = DayflowFont),
        labelLarge = base.labelLarge.copy(fontFamily = DayflowFont),
        labelMedium = base.labelMedium.copy(fontFamily = DayflowFont),
        labelSmall = base.labelSmall.copy(fontFamily = DayflowFont)
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchAction = intent?.action
        setContent {
            DayflowApp(
                startWithVoice = launchAction == ACTION_LUMI_VOICE,
                // Opened by the focus guard after it stopped a blocked app, or by a finished session.
                startAtFocus = launchAction == ACTION_FOCUS_RETURN
            )
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            ACTION_LUMI_VOICE -> setContent { DayflowApp(startWithVoice = true) }
            ACTION_FOCUS_RETURN -> setContent { DayflowApp(startAtFocus = true) }
        }
    }
    companion object {
        const val ACTION_LUMI_VOICE = "com.dayflow.action.LUMI_VOICE"
        const val ACTION_FOCUS_RETURN = "com.dayflow.action.FOCUS_RETURN"
    }
}

@Composable fun DayflowApp(startWithVoice: Boolean = false, startAtFocus: Boolean = false) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showNavigation = route != "setup" && route?.startsWith("editor") != true
    val app = LocalContext.current.applicationContext as DayflowApplication
    val prefs by app.preferences.state.collectAsStateWithLifecycle()
    val startDestination = remember(startWithVoice, startAtFocus, prefs.onboardingComplete) {
        when {
            startWithVoice -> "lumi?voice=true"
            startAtFocus -> "focus"
            prefs.onboardingComplete -> "today"
            else -> "setup"
        }
    }
    val palette = if (prefs.lightTheme) LightDayflowPalette else DarkDayflowPalette
    CompositionLocalProvider(LocalDayflowPalette provides palette, Local24HourTime provides prefs.use24HourTime, LocalAgentName provides prefs.agentName) {
    MaterialTheme(colorScheme = if (prefs.lightTheme) lightColorScheme(background = palette.ink, surface = palette.surface, primary = palette.lavender, onPrimary = palette.ink, onSurface = palette.text) else darkColorScheme(background = palette.ink, surface = palette.surface, primary = palette.lavender, onPrimary = palette.ink, onSurface = palette.text), typography = DayflowTypography) {
    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(fontFamily = DayflowFont)) {
        Scaffold(containerColor = Ink, bottomBar = { if (showNavigation) BottomDock(nav) }, floatingActionButton = {
            if (showNavigation && route?.startsWith("lumi") != true) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                    FloatingActionButton(onClick = { nav.navigate("lumi?voice=true") }, containerColor = Raised, contentColor = Lavender, shape = CircleShape) { Icon(Icons.Default.Mic, "Talk to ${prefs.agentName}") }
                    FloatingActionButton(onClick = { nav.openEditor() }, containerColor = Lavender, contentColor = Ink, shape = CircleShape) { Icon(Icons.Default.Add, "Add task") }
                }
            }
        }, floatingActionButtonPosition = FabPosition.End) { pad ->
            NavHost(navController = nav, startDestination = startDestination, modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
                composable("today") { TodayScreen(nav) }
                composable("week") { WeekScreen(nav) }
                composable(
                    route = "focus?taskId={taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.LongType; defaultValue = -1L })
                ) { entry -> FocusScreen(nav, entry.arguments?.getLong("taskId") ?: -1L) }
                composable("progress") { ProgressScreen(nav) }
                composable(
                    route = "editor?taskId={taskId}&dateEpochDay={dateEpochDay}&title={title}&type={type}&minutes={minutes}&editOccurrenceOfTaskId={editOccurrenceOfTaskId}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("dateEpochDay") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("type") { type = NavType.StringType; defaultValue = "" },
                        navArgument("minutes") { type = NavType.IntType; defaultValue = -1 },
                        navArgument("editOccurrenceOfTaskId") { type = NavType.LongType; defaultValue = -1L }
                    )
                ) { entry ->
                    EditorScreen(
                        nav = nav,
                        taskId = entry.arguments?.getLong("taskId") ?: -1L,
                        dateEpochDay = entry.arguments?.getLong("dateEpochDay") ?: -1L,
                        presetTitle = entry.arguments?.getString("title") ?: "",
                        presetType = entry.arguments?.getString("type") ?: "",
                        presetMinutes = entry.arguments?.getInt("minutes") ?: -1,
                        editOccurrenceOfTaskId = entry.arguments?.getLong("editOccurrenceOfTaskId") ?: -1L
                    )
                }
                composable("actions") { ActionsScreen(nav) }
                composable("setup") { SetupScreen(nav) }
                composable("routines") { RoutinesScreen(nav) }
                composable("sanctuary") { SanctuaryScreen(nav) }
                composable("settings") { SettingsScreen(nav) }
                composable("archive") { ArchivedTasksScreen(nav) }
                composable(
                    route = "lumi?voice={voice}",
                    arguments = listOf(navArgument("voice") { type = NavType.BoolType; defaultValue = startWithVoice })
                ) { entry -> LumiScreen(nav, startVoice = entry.arguments?.getBoolean("voice") == true || startWithVoice) }
            }
        }
    }
    }
    }
}

/** Opens the task editor, optionally editing an existing task or prefiling a new one. */
fun NavHostController.openEditor(
    taskId: Long = -1L,
    dateEpochDay: Long = -1L,
    title: String = "",
    type: String = "",
    minutes: Int = -1,
    editOccurrenceOfTaskId: Long = -1L
) {
    val query = "taskId=$taskId&dateEpochDay=$dateEpochDay" +
        "&title=${Uri.encode(title)}&type=$type&minutes=$minutes&editOccurrenceOfTaskId=$editOccurrenceOfTaskId"
    navigate("editor?$query") { launchSingleTop = true }
}

@Composable private fun BottomDock(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState(); val route = entry?.destination?.route
    NavigationBar(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, androidx.compose.ui.graphics.Color(0xFF494552).copy(alpha = .6f), RoundedCornerShape(24.dp)),
        containerColor = Surface.copy(alpha = .96f), tonalElevation = 0.dp
    ) {
        listOf(Triple("today", "Today", Icons.Default.Today), Triple("week", "Week", Icons.Default.DateRange), Triple("focus", "Focus", Icons.Default.Timer), Triple("progress", "Progress", Icons.Default.Insights), Triple("actions", "Apps", Icons.Default.Apps)).forEach { (dest, label, icon) ->
            NavigationBarItem(selected = route == dest || (dest == "focus" && route?.startsWith("focus?") == true) || (dest == "today" && route?.startsWith("editor") == true), onClick = { nav.navigate(dest) { launchSingleTop = true } }, icon = { Icon(icon, null) }, label = { Text(label, fontSize = 9.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Lavender, selectedTextColor = Lavender, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Raised))
        }
    }
}
