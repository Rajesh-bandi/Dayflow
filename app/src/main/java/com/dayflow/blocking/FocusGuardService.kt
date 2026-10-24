package com.dayflow.blocking

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.util.Log
import com.dayflow.MainActivity
import com.dayflow.DayflowApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Optional redirect to Home for selected launchable apps while a saved focus session is running. */
class FocusGuardService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    @Volatile private var focusActive = false
    @Volatile private var currentSession: com.dayflow.data.local.FocusSessionEntity? = null
    private var lastRedirectPackage: String? = null
    private var lastRedirectAt = 0L
    private var lastSeenPackage: String? = null
    private var returnAttemptedPackage: String? = null
    private var returnAttemptedAt = 0L

    private companion object { const val TAG = "DayflowFocus" }

    private val accessibilityButtonCallback = object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
            val intent = Intent(this@FocusGuardService, MainActivity::class.java)
                .setAction(MainActivity.ACTION_LUMI_VOICE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "service connected")
        getAccessibilityButtonController().registerAccessibilityButtonCallback(accessibilityButtonCallback)
        val app = application as DayflowApplication
        FocusGuard.publish { it.copy(connected = true, blockedPackages = BlockedAppPreferences(this).selected.value.size) }
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serviceScope
        serviceScope.launch {
            app.focusSessions.observeCurrent().collect { session ->
                currentSession = session
                val wasActive = focusActive
                val now = System.currentTimeMillis()
                val running = session?.status == com.dayflow.data.FocusStatus.RUNNING.name
                val isExpired = running && session != null && now >= session.expectedEndAtMillis
                if (isExpired) {
                    app.focusSessions.reconcile(now)
                }
                focusActive = running && !isExpired
                if (!focusActive) {
                    lastRedirectPackage = null
                    lastRedirectAt = 0L
                    // Permission to enter an app lasts only as long as the session that granted it.
                    if (wasActive) FocusGuard.clearPermits()
                }
                FocusGuard.publish { it.copy(connected = true, enforcing = focusActive) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        val blocked = BlockedAppPreferences(this)
        val now = System.currentTimeMillis()
        val session = currentSession
        if (session != null && session.status == com.dayflow.data.FocusStatus.RUNNING.name && now >= session.expectedEndAtMillis) {
            focusActive = false
            currentSession = null
            lastRedirectPackage = null
            FocusGuard.clearPermits()
            FocusGuard.publish { it.copy(connected = true, enforcing = false, lastEventAtMillis = now) }
            val app = application as DayflowApplication
            scope?.launch { app.focusSessions.reconcile(now) }
            return
        }
        // Report the event that just arrived so the settings screen can show the guard is listening,
        // which distinguishes "service not running" from "no blocked app opened yet".
        if (packageName != lastSeenPackage) {
            lastSeenPackage = packageName
            Log.i(TAG, "window event from $packageName (session running=$focusActive)")
        }
        FocusGuard.publish { it.copy(connected = true, enforcing = focusActive, lastEventAtMillis = now) }
        // If the return to Dayflow was refused, Android background-launch limits are in play, so fall back
        // to Home rather than leaving the user inside an app the session blocks.
        if (returnAttemptedPackage == packageName && now - returnAttemptedAt in 1..2_000L) {
            Log.w(TAG, "return to Dayflow was refused, falling back to Home")
            returnAttemptedPackage = null
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        if (!focusActive || !blocked.isBlocked(packageName)) return
        // An app the user opened deliberately from the focus screen is allowed to stay open.
        if (FocusGuard.isPermitted(packageName)) return
        if (lastRedirectPackage == packageName && now - lastRedirectAt < 2_000L) return
        lastRedirectPackage = packageName
        lastRedirectAt = now
        FocusGuard.publish { it.copy(lastRedirectPackage = packageName, lastEventAtMillis = now) }
        // Return the user into the running session instead of the launcher, so focus is one tap away.
        returnAttemptedPackage = packageName
        returnAttemptedAt = now
        val returned = runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_FOCUS_RETURN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }.isSuccess
        if (!returned) {
            returnAttemptedPackage = null
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "service unbound")
        focusActive = false
        FocusGuard.publish { it.copy(connected = false, enforcing = false) }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "service destroyed")
        scope?.cancel(); scope = null
        focusActive = false
        FocusGuard.publish { it.copy(connected = false, enforcing = false) }
        super.onDestroy()
    }
}
