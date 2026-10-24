package com.dayflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.util.Log
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat

/** Keeps the Bluetooth media-button session reachable while Lumi is backgrounded or the screen is locked. */
class LumiVoiceControlService : Service() {
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var voicePhase: String = "IDLE"
    private var voiceUiAttached = false
    private var lastHandledDownTime = Long.MIN_VALUE
    private var lastHandledElapsedMs = Long.MIN_VALUE
    private var lastHandledKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN

    /** The name the user gave the assistant, so its notification text matches the rest of the app. */
    private fun agentName(): String =
        (applicationContext as? DayflowApplication)?.preferences?.state?.value?.agentName
            ?: com.dayflow.data.DEFAULT_AGENT_NAME

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "service.created pid=${android.os.Process.myPid()}")
        createNotificationChannel()
        val session = MediaSession(this, "LumiEarbudControls")
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = mediaButtonIntent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                val accepted = event?.let { isVoiceToggleKey(it.keyCode) } == true
                Log.i(TAG, "media_button.callback key=${event?.keyCode} action=${event?.action} accepted=$accepted downTime=${event?.downTime} lastDown=$lastHandledDownTime sessionActive=${mediaSession?.isActive}")
                if (event != null && accepted) return handleMediaKey(event)
                return super.onMediaButtonEvent(mediaButtonIntent)
            }

            override fun onPlay() { Log.i(TAG, "media_session.onPlay active=${mediaSession?.isActive}"); toggleVoice("media session play") }
            override fun onPause() { Log.i(TAG, "media_session.onPause active=${mediaSession?.isActive}"); toggleVoice("media session pause") }
        })
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setMediaButtonReceiver(PendingIntent.getService(
            this, 2,
            Intent(this, LumiVoiceControlService::class.java).setAction(ACTION_MEDIA_BUTTON),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        ))
        mediaSession = session
        session.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "${agentName()} voice control")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Press your earbud button to talk")
            .build())
        session.isActive = true
        publishPlaybackState(PlaybackState.STATE_PAUSED, "ready")
        Log.i(TAG, "media_session.activated active=${session.isActive} receiver=true")
        registerHeadsetDiagnostics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "service.start action=${intent?.action ?: "restore"} startId=$startId flags=$flags sessionActive=${mediaSession?.isActive}")
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "service.stop requested")
            releaseWakeLock()
            mediaSession?.isActive = false
            Log.i(TAG, "media_session.deactivated reason=explicit-stop")
            mediaSession?.release()
            mediaSession = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RELEASE_WAKE) releaseWakeLock()
        if (intent?.action == ACTION_VOICE_UI_ATTACHED) {
            voiceUiAttached = true
            voicePhase = "IDLE" // A newly composed voice screen has no live recognizer to resume.
            publishPlaybackState(PlaybackState.STATE_PAUSED, "voice-ui-attached")
            Log.i(TAG, "voice_ui.attached sessionActive=${mediaSession?.isActive}")
        }
        if (intent?.action == ACTION_VOICE_UI_DETACHED) {
            voiceUiAttached = false
            voicePhase = "IDLE"
            publishPlaybackState(PlaybackState.STATE_PAUSED, "voice-ui-detached")
            Log.i(TAG, "voice_ui.detached sessionActive=${mediaSession?.isActive}")
        }
        if (intent?.action == ACTION_SET_VOICE_PHASE) {
            val phase = intent.getStringExtra(EXTRA_VOICE_PHASE).orEmpty()
            if (phase in setOf("IDLE", "RECORDING", "PROCESSING", "RESPONDING")) voicePhase = phase
            when (phase) {
                "RESPONDING" -> publishPlaybackState(PlaybackState.STATE_PLAYING, "tts-start")
                "IDLE", "RECORDING", "PROCESSING" -> publishPlaybackState(PlaybackState.STATE_PAUSED, phase.lowercase())
            }
            // A request that is being thought about or spoken must survive the screen going off,
            // so hold the CPU for the whole think-and-speak window, not just the button press.
            if (phase == "PROCESSING" || phase == "RESPONDING") keepCpuAwakeBriefly()
            Log.i(TAG, "voice_phase.ack phase=$voicePhase sessionActive=${mediaSession?.isActive}")
        }
        if (intent?.action == ACTION_MEDIA_BUTTON) {
            val event = intent.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
            Log.i(TAG, "Media button receiver invoked: key=${event?.keyCode}, action=${event?.action}")
            if (event != null) handleMediaKey(event)
        }
        if (intent?.action == ACTION_NOTIFICATION_TOGGLE) {
            toggleVoice("notification control")
        }
        val notification = buildNotification()
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
        return START_STICKY
    }

    private fun isVoiceToggleKey(keyCode: Int): Boolean = keyCode == 79 || keyCode == 85 || keyCode == 126 || keyCode == 127

    private fun toggleVoice(source: String) {
        if (!voiceUiAttached) {
            Log.w(TAG, "voice_toggle.ignored source=$source reason=voice-ui-not-attached")
            return
        }
        val nextPhase = when (voicePhase) {
            "IDLE" -> "RECORDING"
            "RECORDING" -> "PROCESSING"
            "RESPONDING" -> "IDLE" // A press during spoken output interrupts it and returns to ready.
            else -> {
                Log.i(TAG, "voice_toggle.ignored source=$source phase=$voicePhase")
                return
            }
        }
        voicePhase = nextPhase
        if (nextPhase == "IDLE" || nextPhase == "RECORDING" || nextPhase == "PROCESSING") {
            publishPlaybackState(PlaybackState.STATE_PAUSED, "voice-$nextPhase")
        }
        Log.i(TAG, "voice_toggle.dispatch source=$source phase=$nextPhase sessionActive=${mediaSession?.isActive} wakeHeld=${wakeLock?.isHeld == true}")
        keepCpuAwakeBriefly()
        sendBroadcast(Intent(ACTION_TOGGLE_VOICE).setPackage(packageName).putExtra(EXTRA_VOICE_PHASE, nextPhase))
    }

    private fun publishPlaybackState(state: Int, reason: String) {
        val actions = when (state) {
            PlaybackState.STATE_PLAYING -> PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PAUSE
            else -> PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE
        }
        mediaSession?.setPlaybackState(PlaybackState.Builder().setActions(actions).setState(state, 0L, if (state == PlaybackState.STATE_PLAYING) 1f else 0f).build())
        Log.i(TAG, "media_session.state state=$state reason=$reason active=${mediaSession?.isActive} actions=$actions")
    }

    private fun registerHeadsetDiagnostics() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        runCatching {
            registerReceiver(headsetReceiver, filter)
            headsetReceiverRegistered = true
        }.onFailure { Log.w(TAG, "headset_receiver.register failed", it) }
    }

    private val headsetReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            Log.i(TAG, "headset.connection action=${intent?.action} state=${intent?.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)} sessionActive=${mediaSession?.isActive}")
        }
    }
    private var headsetReceiverRegistered = false

    private fun handleMediaKey(event: android.view.KeyEvent): Boolean {
        val accepted = isVoiceToggleKey(event.keyCode)
        if (!accepted) return false
        if (event.action != android.view.KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0) {
            Log.i(TAG, "media_button.repeat key=${event.keyCode} repeatCount=${event.repeatCount}; ignored")
            return true
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val sameRealDown = event.downTime != 0L && event.downTime == lastHandledDownTime
        // Some Bluetooth stacks construct KeyEvents with downTime=0 for every press.
        // Use a short debounce only for that broken timestamp case.
        val syntheticTimestampBurst = event.downTime == 0L && event.keyCode == lastHandledKeyCode &&
            lastHandledElapsedMs != Long.MIN_VALUE && now - lastHandledElapsedMs < MEDIA_KEY_DEBOUNCE_MS
        if (sameRealDown || syntheticTimestampBurst) {
            Log.w(TAG, "media_button.duplicate key=${event.keyCode} downTime=${event.downTime} elapsedMs=${now - lastHandledElapsedMs}; ignored")
            return true
        }
        lastHandledDownTime = event.downTime
        lastHandledElapsedMs = now
        lastHandledKeyCode = event.keyCode
        Log.i(TAG, "media_button.accepted key=${event.keyCode} downTime=${event.downTime} elapsedMs=$now")
        toggleVoice("key ${event.keyCode}")
        return true
    }

    private fun buildNotification(): Notification {
        val openLumi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_LUMI_VOICE)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LumiVoiceControlService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getService(
            this, 3, Intent(this, LumiVoiceControlService::class.java).setAction(ACTION_NOTIFICATION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.dayflow_logo)
            .setContentTitle("${agentName()} earbud controls are on")
            .setContentText("Press your earbud button to talk, then press again to send.")
            .setContentIntent(openLumi)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_btn_speak_now, "Talk", talk).build())
            .addAction(Notification.Action.Builder(0, "Stop", stop).build())
            .setStyle(Notification.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "${agentName()} earbud controls", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when ${agentName()} can be started from your earbud button."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun keepCpuAwakeBriefly() {
        val lock = wakeLock ?: (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:LumiVoiceRequest")
            .also { wakeLock = it }
        if (!lock.isHeld) lock.acquire(300_000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        Log.w(TAG, "service.destroy sessionActive=${mediaSession?.isActive}")
        if (headsetReceiverRegistered) runCatching { unregisterReceiver(headsetReceiver) }
        releaseWakeLock()
        mediaSession?.isActive = false
        Log.i(TAG, "media_session.deactivated reason=service-destroy")
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TOGGLE_VOICE = "com.dayflow.action.TOGGLE_EARBUD_VOICE"
        private const val TAG = "LumiEarbud"
        private const val ACTION_MEDIA_BUTTON = "com.dayflow.action.EARBUD_MEDIA_BUTTON"
        const val ACTION_RELEASE_WAKE = "com.dayflow.action.RELEASE_VOICE_WAKE"
        const val ACTION_SET_VOICE_PHASE = "com.dayflow.action.SET_VOICE_PHASE"
        const val ACTION_VOICE_UI_ATTACHED = "com.dayflow.action.VOICE_UI_ATTACHED"
        const val ACTION_VOICE_UI_DETACHED = "com.dayflow.action.VOICE_UI_DETACHED"
        const val EXTRA_VOICE_PHASE = "voice_phase"
        const val ACTION_STOP = "com.dayflow.action.STOP_EARBUD_CONTROLS"
        private const val ACTION_NOTIFICATION_TOGGLE = "com.dayflow.action.NOTIFICATION_TOGGLE_VOICE"
        private const val CHANNEL_ID = "lumi_earbud_controls"
        private const val NOTIFICATION_ID = 2407
        private const val MEDIA_KEY_DEBOUNCE_MS = 300L
    }
}
