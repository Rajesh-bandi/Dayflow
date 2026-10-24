package com.dayflow.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dayflow.DayflowApplication
import com.dayflow.data.TaskRepository

/** Single access point to the app-level repository from Compose. */
@Composable
fun rememberRepository(): TaskRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as DayflowApplication).repository }
}

internal fun repositoryFrom(context: Context): TaskRepository =
    (context.applicationContext as DayflowApplication).repository
