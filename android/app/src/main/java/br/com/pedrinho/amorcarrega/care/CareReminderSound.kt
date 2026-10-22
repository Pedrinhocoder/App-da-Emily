package br.com.pedrinho.amorcarrega.care

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Direct TYPE_ALARM playback, like the battery, with an independent, bounded lifetime. */
object CareReminderSound {
    const val URI_PREFIX = "direct_sound_uri:"
    const val PLAY_MS = 5000L
    private const val LIFETIME_MS = 7000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var active: Deferred<Unit>? = null

    fun allowedUri(context: Context, channelId: String): Uri? {
        val prefs = CareAlarms.preferences(context)
        if (!prefs.getBoolean("sound", true)) return null
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return null
        val channel = manager.getNotificationChannel(channelId) ?: return null
        // If the user explicitly enables channel audio later, Android becomes the only
        // sound source for that channel. Never layer a player over a sounding notification.
        if (channel.sound != null) return null
        if (channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return null
        if (Build.VERSION.SDK_INT >= 28 && channel.group != null &&
            manager.getNotificationChannelGroup(channel.group)?.isBlocked == true) return null
        if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE) return null
        // No volume/DND override. USAGE_ALARM remains subject to the device's alarm policy.
        return prefs.getString(URI_PREFIX + channelId,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString())
            ?.takeIf { it.isNotEmpty() }?.let(Uri::parse)
    }

    suspend fun play(context: Context, channelId: String) {
        val uri = allowedUri(context, channelId) ?: return
        val job = mutex.withLock {
            active?.takeIf { it.isActive } ?: scope.async {
                playOnce(context.applicationContext, uri)
            }.also { active = it }
        }
        // Every receiver awaits the shared clip: Android keeps its goAsync result alive.
        job.await()
    }

    private suspend fun playOnce(context: Context, uri: Uri) {
        val handler = Handler(Looper.getMainLooper())
        val wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AmorCuida:medicationSound")
        var player: MediaPlayer? = null
        var stop: Runnable? = null
        try {
            wake.acquire(LIFETIME_MS + 1000)
            val audio = MediaPlayer().also { player = it }
            audio.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            audio.isLooping = false
            audio.setDataSource(context, uri)
            withTimeoutOrNull(LIFETIME_MS) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val finished = AtomicBoolean(false)
                    fun finish() {
                        if (finished.compareAndSet(false, true)) continuation.resume(Unit)
                    }
                    val timeout = Runnable { finish() }.also { stop = it }
                    continuation.invokeOnCancellation {
                        finished.set(true)
                        handler.removeCallbacks(timeout)
                    }
                    audio.setOnPreparedListener {
                        if (!finished.get()) {
                            try {
                                it.start()
                                handler.postDelayed(timeout, PLAY_MS)
                            } catch (error: Exception) {
                                Log.e("CareReminderSound", "Falha ao iniciar áudio do medicamento", error)
                                finish()
                            }
                        }
                    }
                    audio.setOnCompletionListener { finish() }
                    audio.setOnErrorListener { _, what, extra ->
                        Log.e("CareReminderSound", "Erro de áudio do medicamento: $what/$extra")
                        finish(); true
                    }
                    audio.prepareAsync()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("CareReminderSound", "Falha ao reproduzir alarme do medicamento", error)
        } finally {
            stop?.let(handler::removeCallbacks)
            player?.let { audio ->
                runCatching { audio.setOnPreparedListener(null); audio.setOnCompletionListener(null); audio.setOnErrorListener(null) }
                runCatching { audio.stop() }
                runCatching { audio.release() }
            }
            if (wake.isHeld) wake.release()
        }
    }
}
