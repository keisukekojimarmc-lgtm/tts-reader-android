package com.example.minutesreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.minutesreader.MainActivity
import com.example.minutesreader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 読み上げ中だけ動くフォアグラウンドサービス。
 * - 画面オフ・ロック中も読み上げを続ける（通知 + WakeLock）
 * - 通知・ロック画面・イヤホンのボタンから 再生/一時停止・前後の文 を操作
 * - 電話や他アプリの音声が始まったら一時停止、イヤホンが抜けたら一時停止
 */
class ReaderService : Service() {

    private lateinit var speaker: Speaker
    private lateinit var session: MediaSession
    private lateinit var nm: NotificationManager
    private lateinit var audio: AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var started = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var pausedByFocus = false
    private var noisyRegistered = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) speaker.pause()
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                pausedByFocus = false
                speaker.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (speaker.state.value.playing) {
                    pausedByFocus = true
                    speaker.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                if (pausedByFocus) {
                    pausedByFocus = false
                    speaker.play()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        speaker = Speaker.get(this)
        nm = getSystemService(NotificationManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "読み上げ", NotificationManager.IMPORTANCE_LOW).apply {
                description = "議事録の読み上げ中に表示します"
                setShowBadge(false)
            },
        )
        session = MediaSession(this, "MinutesReader").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = speaker.play()
                override fun onPause() = speaker.pause()
                override fun onSkipToNext() = speaker.next()
                override fun onSkipToPrevious() = speaker.prev()
                override fun onStop() = speaker.close()
            })
            isActive = true
        }
        scope.launch { speaker.state.collect { render(it) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> speaker.toggle()
            ACT_PREV -> speaker.prev()
            ACT_NEXT -> speaker.next()
            ACT_CLOSE -> speaker.close()
        }
        val st = speaker.state.value
        if (!started) {
            // startForegroundService() から 5 秒以内に必ず呼ぶ必要がある
            val n = buildNotification(st)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
            started = true
        }
        if (!st.active) shutdown() else render(st)
        return START_NOT_STICKY
    }

    private fun render(st: PlayerState) {
        if (!started) return
        if (!st.active) {
            shutdown()
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, st.title.ifBlank { "議事録" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, speaker.currentText())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "議事録ビューア")
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (st.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    st.rate,
                )
                .build(),
        )
        if (st.playing) {
            acquireWakeLock()
            requestFocus()
            registerNoisy()
        } else {
            releaseWakeLock()
        }
        nm.notify(NOTIF_ID, buildNotification(st))
    }

    private fun buildNotification(st: PlayerState): Notification {
        fun action(icon: Int, title: String, act: String, req: Int) = Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(
                this, req, Intent(this, ReaderService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = if (st.total > 0) "${st.index + 1} / ${st.total}" else ""
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reader)
            .setContentTitle(st.title.ifBlank { "議事録の読み上げ" })
            .setContentText(speaker.currentText())
            .setSubText(progress)
            .setContentIntent(open)
            .setOngoing(st.playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(action(R.drawable.ic_prev, "前の文", ACT_PREV, 1))
            .addAction(
                if (st.playing) action(R.drawable.ic_pause, "一時停止", ACT_TOGGLE, 2)
                else action(R.drawable.ic_play, "再生", ACT_TOGGLE, 2),
            )
            .addAction(action(R.drawable.ic_next, "次の文", ACT_NEXT, 3))
            .addAction(action(R.drawable.ic_close, "終了", ACT_CLOSE, 4))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MinutesReader:speech")
            .apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun requestFocus() {
        if (hasFocus) return
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        hasFocus = audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
        noisyRegistered = true
    }

    private fun shutdown() {
        releaseWakeLock()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        hasFocus = false
        pausedByFocus = false
        if (noisyRegistered) {
            runCatching { unregisterReceiver(noisy) }
            noisyRegistered = false
        }
        if (started) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            started = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        if (noisyRegistered) runCatching { unregisterReceiver(noisy) }
        session.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL = "reader"
        const val NOTIF_ID = 1
        const val ACT_TOGGLE = "toggle"
        const val ACT_PREV = "prev"
        const val ACT_NEXT = "next"
        const val ACT_CLOSE = "close"
    }
}
