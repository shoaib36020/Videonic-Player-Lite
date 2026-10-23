package com.videonic.lite.player

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.videonic.lite.MainActivity

/**
 * Decoder choice is a real MediaCodec preference, not a simulated player setting.
 * Media3/Android still choose an available decoder; hardware/software preference is not a guarantee.
 */
@OptIn(UnstableApi::class)
class VideonicPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_SET_DECODER = "com.videonic.lite.player.SET_DECODER"
        const val EXTRA_MODE = "decoder_mode"
        const val EXTRA_FALLBACK = "decoder_fallback"
    }

    private var session: MediaSession? = null
    private var playback: ExoPlayer? = null
    private val prefs by lazy { getSharedPreferences("videonic", MODE_PRIVATE) }

    private fun makePlayer(mode: String, fallback: Boolean): ExoPlayer {
        val selector: MediaCodecSelector = when (mode) {
            "software" -> MediaCodecSelector.PREFER_SOFTWARE
            "hardware" -> MediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
                    .sortedWith(compareByDescending<MediaCodecInfo> { it.hardwareAccelerated })
            }
            else -> MediaCodecSelector.DEFAULT
        }
        val renderers = DefaultRenderersFactory(this)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(fallback)
        return ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
            )
            playWhenReady = false
            addAnalyticsListener(object : AnalyticsListener {
                override fun onMediaItemTransition(
                    eventTime: AnalyticsListener.EventTime,
                    mediaItem: MediaItem?,
                    reason: Int
                ) {
                    prefs.edit().remove("active_video_decoder").apply()
                }

                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long
                ) {
                    prefs.edit().putString("active_video_decoder", decoderName).apply()
                }
            })
        }
    }

    /** Recreate the renderer safely, keeping the SAME session and the current playlist/position. */
    private fun switchDecoder(mode: String, fallback: Boolean): Boolean {
        val active = session ?: return false
        val old = playback ?: return false
        val replacement = runCatching { makePlayer(mode, fallback) }.getOrNull() ?: return false
        try {
            val count = old.mediaItemCount
            val items = (0 until count).map { old.getMediaItemAt(it) }
            val index = old.currentMediaItemIndex.coerceAtLeast(0)
            val position = old.currentPosition.coerceAtLeast(0L)
            val shouldPlay = old.playWhenReady
            replacement.playbackParameters = old.playbackParameters
            replacement.trackSelectionParameters = old.trackSelectionParameters
            replacement.repeatMode = old.repeatMode
            replacement.shuffleModeEnabled = old.shuffleModeEnabled
            replacement.volume = old.volume
            if (items.isNotEmpty()) replacement.setMediaItems(items, index.coerceAtMost(items.lastIndex), position)
            replacement.playWhenReady = shouldPlay
            // MediaSession.setPlayer keeps controllers connected to this session.
            active.setPlayer(replacement)
            playback = replacement
            prefs.edit().putString(EXTRA_MODE, mode).putBoolean(EXTRA_FALLBACK, fallback)
                .remove("active_video_decoder").apply()
            runCatching { old.release() }
            if (items.isNotEmpty()) replacement.prepare()
            return true
        } catch (_: Exception) {
            replacement.release()
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        val mode = prefs.getString(EXTRA_MODE, "auto") ?: "auto"
        val fallback = prefs.getBoolean(EXTRA_FALLBACK, true)
        val player = makePlayer(mode, fallback)
        playback = player
        val openPlayer = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CURRENT_VIDEO
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openPlayer, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon().add(SessionCommand(ACTION_SET_DECODER, Bundle.EMPTY)).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands).build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction != ACTION_SET_DECODER) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
                val mode = args.getString(EXTRA_MODE, "auto")
                if (mode !in listOf("auto", "hardware", "software")) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
                val ok = switchDecoder(mode, args.getBoolean(EXTRA_FALLBACK, true))
                return Futures.immediateFuture(
                    SessionResult(if (ok) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_UNKNOWN)
                )
            }
        }
        session = MediaSession.Builder(this, player).setCallback(callback)
            .setSessionActivity(pending).build()
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val headphones = prefs.getBoolean("background_audio_enabled", false)
        if (!headphones && !session.player.isPlaying) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)?.cancelAll()
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        playback?.release()
        playback = null
        super.onDestroy()
    }
}
