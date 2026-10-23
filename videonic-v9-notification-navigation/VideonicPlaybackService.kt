package com.videonic.lite.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import com.videonic.lite.MainActivity
import android.os.Build
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class VideonicPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val player = ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            playWhenReady = false
        }
        val openPlayer=Intent(this,MainActivity::class.java).apply {
            action=MainActivity.ACTION_OPEN_CURRENT_VIDEO
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending=PendingIntent.getActivity(this,0,openPlayer,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session=MediaSession.Builder(this,player).setSessionActivity(pending).build()
    }
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val headphones=getSharedPreferences("videonic",MODE_PRIVATE)
            .getBoolean("background_audio_enabled",false)
        // Media3 normally leaves a paused media-session notification behind. Never
        // retain that notification for ordinary video playback without headphone mode.
        if(!headphones && !session.player.isPlaying) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)?.cancelAll()
            return
        }
        // Playing media still follows Android foreground-service notification rules.
        super.onUpdateNotification(session,startInForegroundRequired)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { session?.run { player.release(); release() }; session = null; super.onDestroy() }
}
