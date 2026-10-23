package com.videonic.lite.player

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaParserExtractorAdapter
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.videonic.lite.MainActivity

/**
 * V15 safe decoder transition: prepare first, swap on READY, rollback on error.
 * Decoder choice is a real MediaCodec preference, not a simulated player setting.
 * Media3/Android still choose an available decoder; hardware/software preference is not a guarantee.
 */
@OptIn(UnstableApi::class)
class VideonicPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_SET_DECODER = "com.videonic.lite.player.SET_DECODER"
        const val ACTION_TRY_ALTERNATE_SEEKER = "com.videonic.lite.player.TRY_ALTERNATE_SEEKER"
        const val ACTION_RESTORE_DEFAULT_SEEKER = "com.videonic.lite.player.RESTORE_DEFAULT_SEEKER"
        const val EXTRA_MODE = "decoder_mode"
        const val EXTRA_FALLBACK = "decoder_fallback"
    }

    private var session: MediaSession? = null
    private var playback: ExoPlayer? = null
    private val prefs by lazy { getSharedPreferences("videonic", MODE_PRIVATE) }
    private val mainHandler=Handler(Looper.getMainLooper())
    private var isDecoderSwitching=false
    // Optional, session-local alternative container parser; never install an extra native codec.
    private var usePlatformParser=false

    private fun makePlayer(mode: String, fallback: Boolean, platformParser:Boolean=false): ExoPlayer {
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
        // Enables approximate seeking only in extractors that support CBR byte seeks,
        // including some non-indexed transport streams. It cannot repair a damaged
        // MP4/MKV missing necessary seek metadata. No new native codec dependency.
        val extractors=DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSources:MediaSource.Factory = if(platformParser && Build.VERSION.SDK_INT>=30) {
            // Android platform MediaParser sometimes finds seek information where
            // Media3's bundled extractor cannot. It is an optional *internal* parser,
            // not an external player or a software video decoder.
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this),MediaParserExtractorAdapter.FACTORY)
        } else DefaultMediaSourceFactory(this,extractors)
        return ExoPlayer.Builder(this, renderers, mediaSources).build().apply {
            // Prefer a close existing keyframe in sparse-GOP files. This makes seeking
            // faster/less prone to stalling, but it cannot make an unindexed stream seekable.
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
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

    /**
     * This is a two-phase decoder change, not a blind player swap.
     * Keep the MediaSession/controller. Release the old decoder resources with stop(),
     * prepare the candidate while its playWhenReady is false, swap only after STATE_READY,
     * and restore the original player/position if initialization fails or times out.
     * The old implementation released a working player before testing the replacement.
     */
    private fun switchDecoder(mode: String, fallback: Boolean, parserOverride:Boolean?=null): ListenableFuture<SessionResult> {
        val response=SettableFuture.create<SessionResult>()
        val active=session
        val old=playback
        if(active==null || old==null || isDecoderSwitching) {
            response.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            return response
        }
        val currentMode=prefs.getString(EXTRA_MODE,"auto") ?: "auto"
        val currentFallback=prefs.getBoolean(EXTRA_FALLBACK,true)
        val nextParser=parserOverride ?: usePlatformParser
        if(currentMode==mode && currentFallback==fallback && nextParser==usePlatformParser) {
            response.set(SessionResult(SessionResult.RESULT_SUCCESS))
            return response
        }
        val items=(0 until old.mediaItemCount).map { old.getMediaItemAt(it) }
        val index=old.currentMediaItemIndex.coerceIn(0, (items.size-1).coerceAtLeast(0))
        val position=old.currentPosition.coerceAtLeast(0L)
        val shouldPlay=old.playWhenReady
        val speed=old.playbackParameters
        val tracks=old.trackSelectionParameters
        val repeat=old.repeatMode
        val shuffle=old.shuffleModeEnabled
        val volume=old.volume
        val candidate=runCatching { makePlayer(mode,fallback,nextParser) }.getOrElse {
            response.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            return response
        }
        isDecoderSwitching=true
        var settled=false
        var timer:Runnable?=null
        fun finish(ok:Boolean) {
            if(settled)return
            settled=true
            timer?.let { mainHandler.removeCallbacks(it) }
            isDecoderSwitching=false
            if(ok) {
                try {
                    active.setPlayer(candidate)
                    playback=candidate
                    usePlatformParser=nextParser
                    prefs.edit().putString(EXTRA_MODE,mode).putBoolean(EXTRA_FALLBACK,fallback)
                        .remove("active_video_decoder").apply()
                    candidate.playWhenReady=shouldPlay
                    runCatching { old.release() }
                    response.set(SessionResult(SessionResult.RESULT_SUCCESS))
                } catch (_:Exception) {
                    runCatching { active.setPlayer(old) }
                    playback=old
                    runCatching {candidate.release()}
                    runCatching { old.seekTo(index,position);old.prepare();old.playWhenReady=shouldPlay }
                    response.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            } else {
                // A failed replacement must NEVER strand the video on a stopped session.
                runCatching {candidate.release()}
                playback=old
                runCatching {
                    old.seekTo(index,position)
                    if(items.isNotEmpty()) old.prepare()
                    old.playWhenReady=shouldPlay
                }
                response.set(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
            }
        }
        try {
            // Free the single hardware decoder slot on lower-end devices while keeping
            // old MediaItems so it can be prepared again for a safe rollback.
            old.playWhenReady=false
            old.stop()
            candidate.playbackParameters=speed
            candidate.trackSelectionParameters=tracks
            candidate.repeatMode=repeat
            candidate.shuffleModeEnabled=shuffle
            candidate.volume=volume
            if(items.isNotEmpty()) {
                candidate.setMediaItems(items,index,position)
                candidate.addListener(object:Player.Listener {
                    override fun onPlaybackStateChanged(state:Int) {
                        if(state==Player.STATE_READY) {
                            // Do not replace a working session with another non-seekable
                            // one when the user explicitly requested a seek recovery.
                            val canSeek=candidate.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                            finish(parserOverride==null || !nextParser || canSeek)
                        }
                    }
                    override fun onPlayerError(error:PlaybackException) { finish(false) }
                })
                timer=Runnable {finish(false)}
                mainHandler.postDelayed(timer!!,15000L)
                candidate.prepare()
            } else finish(true)
        } catch (_:Exception) {
            finish(false)
        }
        return response
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
                    .buildUpon()
                    .add(SessionCommand(ACTION_SET_DECODER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_TRY_ALTERNATE_SEEKER, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_RESTORE_DEFAULT_SEEKER, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(commands).build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_TRY_ALTERNATE_SEEKER) {
                    if(Build.VERSION.SDK_INT<30) return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    return switchDecoder(prefs.getString(EXTRA_MODE,"auto")?:"auto",prefs.getBoolean(EXTRA_FALLBACK,true),true)
                }
                if (customCommand.customAction == ACTION_RESTORE_DEFAULT_SEEKER) {
                    return switchDecoder(prefs.getString(EXTRA_MODE,"auto")?:"auto",prefs.getBoolean(EXTRA_FALLBACK,true),false)
                }
                if (customCommand.customAction != ACTION_SET_DECODER) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
                val mode = args.getString(EXTRA_MODE, "auto")
                if (mode !in listOf("auto", "hardware", "software")) {
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                }
                return switchDecoder(mode, args.getBoolean(EXTRA_FALLBACK, true))
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
