package com.dayflow.blocking

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BlockedAppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("focus_block_list", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(prefs.getStringSet("packages", emptySet()).orEmpty())
    val selected: StateFlow<Set<String>> = flow

    fun setBlocked(packageName: String, blocked: Boolean) {
        val updated = flow.value.toMutableSet().apply { if (blocked) add(packageName) else remove(packageName) }
        prefs.edit().putStringSet("packages", updated.toSet()).apply()
        flow.value = updated
    }

    fun setAllBlocked(packageNames: Set<String>, blocked: Boolean) {
        val updated = flow.value.toMutableSet().apply {
            if (blocked) addAll(packageNames) else removeAll(packageNames)
        }
        prefs.edit().putStringSet("packages", updated.toSet()).apply()
        flow.value = updated
    }

    fun isBlocked(packageName: String): Boolean = packageName in prefs.getStringSet("packages", emptySet()).orEmpty()
}
