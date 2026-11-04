package app.macro17

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView

/** Pré-visualização do vídeo, no formato do base (activity_small_video_player): título 22sp, vídeo de 350dp, play/tempo/barra vermelha. */
class VideoPlayerActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private fun fmt(ms: Int) = "%02d:%02d".format(ms / 60000, ms / 1000 % 60)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val uri = intent.data ?: return finish()
        val video = VideoView(this)
        val play = ImageButton(this).apply { setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0); contentDescription = "Reproduzir" }
        val cur = TextView(this).apply { text = "00:00"; textSize = 12f; setTextColor(0xFF555555.toInt()) }
        val total = TextView(this).apply { text = "00:00"; textSize = 12f; setTextColor(0xFF555555.toInt()) }
        val bar = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Color.RED); thumbTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) { if (user) video.seekTo(p) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        val tick = object : Runnable { override fun run() { bar.progress = video.currentPosition; cur.text = fmt(video.currentPosition); if (video.isPlaying) h.postDelayed(this, 250) } }
        play.setOnClickListener {
            if (video.isPlaying) { video.pause(); play.setImageResource(android.R.drawable.ic_media_play) }
            else { video.start(); play.setImageResource(android.R.drawable.ic_media_pause); h.post(tick) }
        }
        video.setOnPreparedListener { bar.max = it.duration; total.text = fmt(it.duration) }
        video.setOnCompletionListener { play.setImageResource(android.R.drawable.ic_media_play); bar.progress = bar.max }
        video.setVideoURI(uri)
        val close = ImageButton(this).apply { setImageResource(android.R.drawable.ic_menu_close_clear_cancel); setBackgroundColor(0); contentDescription = "Fechar"; setColorFilter(0xFF616161.toInt()); setOnClickListener { finish() } }
        val head = RelativeLayout(this).apply {
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(TextView(this@VideoPlayerActivity).apply { text = "Pré-visualização do vídeo"; textSize = 22f; setTextColor(0xFF333333.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD) },
                RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.addRule(RelativeLayout.CENTER_IN_PARENT) })
            addView(close, RelativeLayout.LayoutParams(dp(32), dp(32)).also { it.addRule(RelativeLayout.ALIGN_PARENT_END); it.addRule(RelativeLayout.CENTER_VERTICAL) })
        }
        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(4))
            addView(play, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(cur, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also { it.marginStart = dp(8) })
            addView(bar, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).also { it.setMargins(dp(8), 0, dp(8), 0) })
            addView(total)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(video, LinearLayout.LayoutParams(MATCH_PARENT, dp(350)))
            addView(controls, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
    }

    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
