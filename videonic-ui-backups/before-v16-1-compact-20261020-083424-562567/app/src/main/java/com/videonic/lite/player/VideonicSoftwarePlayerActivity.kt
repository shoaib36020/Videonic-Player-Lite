package com.videonic.lite.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import android.content.res.AssetFileDescriptor
import com.videonic.lite.data.LocalLibrary
import java.util.Locale

/**
 * Opt-in native video software decoder for HEVC Main10 and files unavailable to Media3.
 * This is a separate, safely isolated player. It does not replace or mutate the existing
 * Media3 service/session. Software 10-bit performance remains device-dependent.
 * Dependency: org.videolan.android:libvlc-all:3.6.5 (LGPL-2.1).
 */
class VideonicSoftwarePlayerActivity : Activity() {
    companion object {
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_START_MS = "position_ms"
        const val EXTRA_VIDEO_ID = "video_id"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var engine: LibVLC? = null
    private var vlc: MediaPlayer? = null
    private var afd: AssetFileDescriptor? = null
    private var started = false
    private var videoSurface: SurfaceView? = null
    private var progressTracking = false
    private var controlsVisible = true
    private var seekBar: SeekBar? = null
    private var elapsedView: TextView? = null
    private var durationView: TextView? = null
    private var playButton: TextView? = null
    private var controls: LinearLayout? = null
    private var titleBar: LinearLayout? = null
    private var videoId: Long = -1
    private var videoDuration: Long = 0L
    private var lastPosition: Long = 0L
    private var startMs = 0L
    private var pendingSeek = false
    private var dragging = false
    private var errorView: TextView? = null
    private val blue = Color.rgb(37, 99, 235)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, sizeSp: Float=15f, bold: Boolean=false): TextView =
        TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = sizeSp
            gravity = Gravity.CENTER
            if(bold) typeface = Typeface.DEFAULT_BOLD
        }
    private fun clock(ms: Long): String {
        val seconds=ms.coerceAtLeast(0L)/1000L
        return if(seconds>=3600L)
            String.format(Locale.US,"%d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60)
        else String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        videoId = intent.getLongExtra(EXTRA_VIDEO_ID,-1L)
        startMs = intent.getLongExtra(EXTRA_START_MS,0L).coerceAtLeast(0L)
        val uri = intent.data
        if(uri == null) { Toast.makeText(this,"No video selected",Toast.LENGTH_SHORT).show();finish();return }

        val root=FrameLayout(this).apply {setBackgroundColor(Color.BLACK)}
        setContentView(root)
        val surface=SurfaceView(this)
        videoSurface=surface
        root.addView(surface,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        surface.setOnClickListener { controlsVisible=!controlsVisible;setControlsVisible() }

        val top=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(8),dp(6),dp(12),dp(6))
            setBackgroundColor(0xB9000000.toInt())
        }
        titleBar=top
        val back=text("‹",34f).apply {setOnClickListener{finish()} }
        top.addView(back,LinearLayout.LayoutParams(dp(54),dp(50)))
        val title=text(intent.getStringExtra(EXTRA_TITLE) ?: "Video",18f,true).apply {
            gravity=Gravity.CENTER_VERTICAL
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        top.addView(title,LinearLayout.LayoutParams(0,dp(50),1f))
        val sw=text("HW/SW",11f).apply {setTextColor(0xFFAAC7FE.toInt())}
        top.addView(sw,LinearLayout.LayoutParams(dp(64),dp(50)))
        root.addView(top,FrameLayout.LayoutParams(-1,dp(66),Gravity.TOP))

        val bottom=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(14),dp(16),dp(22))
            setBackgroundColor(0xD5000000.toInt())
        }
        controls=bottom
        val timing=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        elapsedView=text("00:00",13f,true)
        durationView=text("00:00",13f,true)
        timing.addView(elapsedView,LinearLayout.LayoutParams(dp(56),dp(36)))
        val seek=SeekBar(this).apply {
            max=1000
            progressTintList=android.content.res.ColorStateList.valueOf(blue)
            thumbTintList=android.content.res.ColorStateList.valueOf(blue)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) { dragging=true }
                override fun onStopTrackingTouch(bar: SeekBar?) {
                    dragging=false
                    val p=vlc
                    if(p!=null && p.isSeekable && videoDuration>0L) {
                        val target=(((bar?.progress ?: 0)/1000.0)*videoDuration).toLong()
                        p.time=target
                        lastPosition=target
                    } else Toast.makeText(this@VideonicSoftwarePlayerActivity,
                        "This video does not support seeking",Toast.LENGTH_SHORT).show()
                }
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if(fromUser && videoDuration>0L) elapsedView?.text=clock((progress/1000.0*videoDuration).toLong())
                }
            })
        }
        seekBar=seek
        timing.addView(seek,LinearLayout.LayoutParams(0,dp(36),1f))
        timing.addView(durationView,LinearLayout.LayoutParams(dp(56),dp(36)))
        bottom.addView(timing,LinearLayout.LayoutParams(-1,dp(44)))

        val actions=LinearLayout(this).apply {gravity=Gravity.CENTER}
        fun action(caption:String,onClick:()->Unit):TextView {
            return text(caption,18f,true).apply {
                setOnClickListener{onClick()}
                background=android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF26232D.toInt());cornerRadius=dp(36).toFloat()
                }
            }
        }
        val back10=action("↶ 10") {seekBy(-10_000L)}
        actions.addView(back10,LinearLayout.LayoutParams(0,dp(56),1f).apply {marginEnd=dp(10)})
        val play=action("Ⅱ") {
            vlc?.let { if(it.isPlaying) it.pause() else it.play() }
            updatePlayButton()
        }.apply {
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(blue);cornerRadius=dp(40).toFloat()
            }
            textSize=26f
        }
        playButton=play
        actions.addView(play,LinearLayout.LayoutParams(dp(74),dp(70)))
        val forward10=action("10 ↷") {seekBy(10_000L)}
        actions.addView(forward10,LinearLayout.LayoutParams(0,dp(56),1f).apply {marginStart=dp(10)})
        bottom.addView(actions,LinearLayout.LayoutParams(-1,dp(78)))
        val hint=text("Software playback · HEVC 10-bit support depends on device speed",11f)
        hint.setTextColor(0xFFADB9CE.toInt())
        bottom.addView(hint,LinearLayout.LayoutParams(-1,dp(28)))
        root.addView(bottom,FrameLayout.LayoutParams(-1,dp(174),Gravity.BOTTOM))

        val error=text("",14f).apply {
            visibility=View.GONE
            setPadding(dp(18),dp(8),dp(18),dp(8))
            setBackgroundColor(0xD5000000.toInt())
        }
        errorView=error
        root.addView(error,FrameLayout.LayoutParams(-1,dp(85),Gravity.CENTER))

        try {
            val lib=LibVLC(this,arrayListOf("--avcodec-hw=none"))
            engine=lib
            val player=MediaPlayer(lib)
            vlc=player
            player.setEventListener { event ->
                when(event.type) {
                    MediaPlayer.Event.Playing -> runOnUiThread {
                        if(!pendingSeek && startMs>0L) {
                            pendingSeek=true
                            handler.postDelayed({if(vlc?.isSeekable==true) vlc?.time=startMs},200L)
                        }
                        updatePlayButton()
                    }
                    MediaPlayer.Event.EncounteredError -> runOnUiThread {
                        errorView?.text="Software decoder could not play this file. Its format or resolution may exceed the device's capabilities."
                        errorView?.visibility=View.VISIBLE
                    }
                    MediaPlayer.Event.EndReached -> runOnUiThread {updatePlayButton()}
                }
            }
            surface.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v:View) {startPlayback(uri)}
                override fun onViewDetachedFromWindow(v:View) {}
            })
            if(surface.isAttachedToWindow) surface.post{startPlayback(uri)}
        } catch(e:Throwable) {
            errorView?.text="Software playback could not initialize: ${e.localizedMessage ?: "Decoder unavailable"}"
            errorView?.visibility=View.VISIBLE
        }
        handler.post(tick)
    }

    private fun startPlayback(uri:Uri) {
        if(started) return
        val player=vlc ?: return
        val lib=engine ?: return
        started=true
        try {
            player.vlcVout.setVideoView(videoSurface ?: return)
            player.vlcVout.attachViews()
            val media=if(uri.scheme=="content") {
                afd=contentResolver.openAssetFileDescriptor(uri,"r")
                afd?.let{Media(lib,it)} ?: Media(lib,uri)
            } else Media(lib,uri)
            player.setMedia(media)
            media.release()
            player.play()
        } catch(e:Throwable) {
            errorView?.text="Can't read this video: ${e.localizedMessage ?: "File unavailable"}"
            errorView?.visibility=View.VISIBLE
        }
    }

    private val tick=object:Runnable {
        override fun run() {
            vlc?.let {player ->
                runCatching {
                    val now=player.time.coerceAtLeast(0L)
                    val length=player.length.coerceAtLeast(0L)
                    if(length>0L) videoDuration=length
                    if(now>0L) lastPosition=now
                    if(!dragging) {
                        elapsedView?.text=clock(now)
                        durationView?.text=clock(videoDuration)
                        if(videoDuration>0L) seekBar?.progress=(now*1000L/videoDuration).toInt().coerceIn(0,1000)
                    }
                    updatePlayButton()
                }
            }
            handler.postDelayed(this,500L)
        }
    }

    private fun setControlsVisible() {
        controls?.visibility=if(controlsVisible) View.VISIBLE else View.INVISIBLE
        titleBar?.visibility=if(controlsVisible) View.VISIBLE else View.INVISIBLE
    }
    private fun updatePlayButton() {playButton?.text=if(vlc?.isPlaying==true) "Ⅱ" else "▶"}
    private fun seekBy(change:Long) {
        vlc?.let {
            if(it.isSeekable) it.time=(it.time+change).coerceIn(0L,videoDuration.coerceAtLeast(it.time))
            else Toast.makeText(this,"This video does not support seeking",Toast.LENGTH_SHORT).show()
        }
    }
    override fun onPause() {
        super.onPause()
        vlc?.pause()
        saveRecent()
    }
    private fun saveRecent() {
        if(videoId>0L && lastPosition>0L) runCatching {LocalLibrary(this).savePosition(videoId,lastPosition)}
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        saveRecent()
        runCatching {vlc?.stop();vlc?.vlcVout?.detachViews();vlc?.release()}
        vlc=null
        runCatching {afd?.close()};afd=null
        runCatching {engine?.release()};engine=null
        super.onDestroy()
    }
}
