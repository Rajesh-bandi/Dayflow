package com.dayflow.notifications

import android.app.NotificationChannel
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.dayflow.R

/**
 * Dayflow's own notification chime.
 *
 * The system default notification sound is easy to miss and impossible to tell apart from every other
 * app, so the app ships a short ascending three-note chime and uses it for its own channels. On Android 8
 * a channel's sound is fixed when the channel is first created, which is why the channel ids below carry
 * a version: bumping the id is the only way an installed app can pick up a new sound.
 */
internal object NotificationSound {

    /** A short vibration in the same rhythm as the chime, for silent-mode users. */
    val VIBRATION = longArrayOf(0, 90, 70, 140)

    fun uri(context: Context): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.dayflow_chime}")

    fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun channel(context: Context, id: String, name: String, description: String, importance: Int): NotificationChannel =
        NotificationChannel(id, name, importance).apply {
            this.description = description
            setSound(this@NotificationSound.uri(context), audioAttributes())
            enableVibration(true)
            vibrationPattern = VIBRATION
        }
}
