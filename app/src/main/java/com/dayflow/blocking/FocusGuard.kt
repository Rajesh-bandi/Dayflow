package com.dayflow.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live state of the optional attention guard, surfaced so the user can see whether it is really running. */
data class FocusGuardStatus(
    val connected: Boolean = false,
    val enforcing: Boolean = false,
    val blockedPackages: Int = 0,
    val lastRedirectPackage: String? = null,
    val lastEventAtMillis: Long = 0L
)

object FocusGuard {
    private val mutable = MutableStateFlow(FocusGuardStatus())
    val status: StateFlow<FocusGuardStatus> = mutable

    /**
     * Apps the user deliberately opened from the focus screen during this session. Opening one is a
     * decision, so the guard lets it stay open; a session change clears the whole set again.
     */
    private val permitted = LinkedHashSet<String>()

    fun permit(packageName: String) { synchronized(permitted) { permitted.add(packageName) } }

    fun isPermitted(packageName: String): Boolean = synchronized(permitted) { packageName in permitted }

    fun permittedPackages(): Set<String> = synchronized(permitted) { permitted.toSet() }

    fun clearPermits() = synchronized(permitted) { permitted.clear() }

    fun publish(update: (FocusGuardStatus) -> FocusGuardStatus) {
        mutable.value = update(mutable.value)
    }

    /**
     * Asks Android which accessibility services are actually enabled rather than string-matching the
     * settings blob. The stored value and the component name format differ between OEM builds, which
     * made a correctly enabled guard look disabled.
     */
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return runCatching {
            manager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK).any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                service.packageName == context.packageName && service.name == FocusGuardService::class.java.name
            }
        }.getOrDefault(false)
    }

    /**
     * True when this install really declares the guard service. When it is false Android has nothing to
     * list under Accessibility, so no amount of switching will work and the app must say so instead of
     * telling the user to hunt through settings for an entry that is not there.
     */
    fun declared(context: Context): Boolean = runCatching {
        context.packageManager
            .queryIntentServices(Intent("android.accessibilityservice.AccessibilityService"), PackageManager.GET_META_DATA)
            .any { it.serviceInfo?.packageName == context.packageName && it.serviceInfo?.name == FocusGuardService::class.java.name }
    }.getOrDefault(false)

    /** The component name Android expects when linking straight to this service's own toggle page. */
    fun componentName(context: Context): String = "${context.packageName}/${FocusGuardService::class.java.name}"

    /** AccessibilityManager.FEEDBACK_ALL_MASK; not exposed in every SDK stub, and the value is fixed. */
    private const val FEEDBACK_ALL_MASK = -1
}
