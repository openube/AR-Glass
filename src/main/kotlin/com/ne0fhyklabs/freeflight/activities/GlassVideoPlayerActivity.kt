package com.ne0fhyklabs.freeflight.activities

import android.support.v4.app.FragmentActivity
import android.os.Bundle
import android.view.WindowManager
import com.ne0fhyklabs.freeflight.R
import kotlin.properties.Delegates
import android.widget.VideoView
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.net.Uri
import com.google.android.glass.touchpad.GestureDetector
import android.view.MotionEvent
import com.google.android.glass.touchpad.Gesture
import android.content.Context
import android.media.AudioManager
import com.google.android.glass.media.Sounds
import android.widget.ImageView
import android.view.View
import com.google.glass.widget.SliderView
import android.widget.TextView
import android.os.Handler

/**
 * Used to play the videos recorder by the AR Drone on glass.
 */
public class GlassVideoPlayerActivity : FragmentActivity() {

    class object  {
        val TAG = javaClass<GlassVideoPlayerActivity>().getSimpleName()
        val PACKAGE_NAME = javaClass<GlassVideoPlayerActivity>().getPackage()?.getName()
        val EXTRA_VIDEO_URI = "$PACKAGE_NAME.EXTRA_VIDEO_URI"

        val INFO_VISIBILITY_DURATION = 2000L //milliseconds
        val INFO_FADEOUT_DURATION = 300L // milliseconds
    }

    /**
     * Handle to the video player.
     */
    private val mVideoView: VideoView by Delegates.lazy {
        val videoView = findViewById(R.id.glass_video_player) as VideoView
        videoView.setOnCompletionListener { finish() }
        videoView.setOnErrorListener { mp, what, extra ->
            Log.e(TAG, "Unable to play video (error type: $what, error code: $extra)")
            finish()
            true
        }
        videoView.setOnPreparedListener { playVideo() }

        videoView
    }

    /**
     * Handle to the video player pause icon.
     */
    private val mPauseImg: ImageView by Delegates.lazy {
        findViewById(R.id.glass_video_pause) as ImageView
    }

    /**
     * Handle to the video player progress bar
     */
    private val mVideoProgress: SliderView by Delegates.lazy {
        findViewById(R.id.glass_video_progress) as SliderView
    }

    /**
     * Handle to the video player time display.
     */
    private val mVideoTimer: TextView by Delegates.lazy {
        findViewById(R.id.glass_video_time) as TextView
    }

    /**
     * Handle to glass gesture detector.
     */
    private val mGlassDetector: GestureDetector by Delegates.lazy {
        val gestureDetector = GestureDetector(getApplicationContext())
        gestureDetector.setBaseListener {
            when(it) {
                Gesture.TAP -> {
                    togglePlayPause()
                    true
                }

                else -> false
            }
        }
        gestureDetector.setScrollListener { displacement, delta, velocity ->
            showVideoInfo()
            val updatedPosition = mVideoView.getCurrentPosition() + (delta.toInt() * 10)
            if (updatedPosition >= 0 && updatedPosition <= mVideoView.getDuration()) {
                mVideoView.seekTo(updatedPosition)
                enableInfoUpdate(mVideoView.isPlaying())
            }
            true
        }

        gestureDetector
    }

    /**
     * This callback updates the video time.
     */
    private val mVideoTimeUpdater: Runnable = object : Runnable {
        override fun run() {
            mCallbacksRunner.removeCallbacks(mVideoTimeUpdater)

            mVideoTimer.setText(" ${mVideoView.getCurrentPosition() / 1000} / ${mVideoView
                    .getDuration() / 1000}s ")

            mCallbacksRunner.postDelayed(mVideoTimeUpdater, 500)
        }
    }

    private val mInfoVisibilityUpdater: Runnable = object : Runnable {
        override fun run() {
            mVideoInfoContainer.animate()?.alpha(0f)?.setDuration(INFO_FADEOUT_DURATION)
        }
    }

    /**
     * This view contains the video progress bar, and time view.
     */
    private val mVideoInfoContainer: View by Delegates.lazy { findViewById(R.id.glass_video_info)!! }

    /**
     * This is used to run the callbacks used in this activity.
     */
    private val mCallbacksRunner = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_glass_video_player)
        val intent = getIntent()
        handleIntent(intent!!)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent!!)
    }

    override fun onPause() {
        super.onPause()
        stopVideo()
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.playSoundEffect(Sounds.DISMISSED);
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        return mGlassDetector.onMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    private fun handleIntent(intent: Intent) {
        //Retrieve the video uri
        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (videoUri == null) {
            Log.e(TAG, "Intent is missing video uri argument.")
            Toast.makeText(getApplicationContext()!!, "Invalid video!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            mVideoView.setVideoURI(Uri.parse(videoUri))
        }
    }

    private fun togglePlayPause(): Unit = if (mVideoView.isPlaying()) pauseVideo() else playVideo()

    private fun pauseVideo() {
        showVideoInfo()
        enableInfoUpdate(false)
        mVideoView.pause()
        mPauseImg.setVisibility(View.VISIBLE)
    }

    private fun playVideo() {
        mPauseImg.setVisibility(View.INVISIBLE)
        mVideoView.start()
        enableInfoUpdate(true)
        showVideoInfo()
    }

    private fun stopVideo() {
        mPauseImg.setVisibility(View.INVISIBLE)
        enableInfoUpdate(false)
        mVideoView.stopPlayback()
    }

    private fun showVideoInfo() {
        mCallbacksRunner.removeCallbacks(mInfoVisibilityUpdater)

        mVideoInfoContainer.setAlpha(1f)
        mCallbacksRunner.postDelayed(mInfoVisibilityUpdater, INFO_VISIBILITY_DURATION)
    }

    private fun enableInfoUpdate(enable: Boolean) {
        mVideoTimeUpdater.run();

        val videoDuration = mVideoView.getDuration()
        val videoPosition = mVideoView.getCurrentPosition()
        val progressDuration = (videoDuration - videoPosition).toLong()
        if (progressDuration >= 0 ) {
            mVideoProgress.stopProgress(false)
            val progressStart = videoPosition.toFloat() / videoDuration.toFloat()
            mVideoProgress.setManualProgress(progressStart)
            if (enable) {
                mVideoProgress.startProgress(progressDuration)
            } else {
                mCallbacksRunner.removeCallbacks(mVideoTimeUpdater)
            }
        }

    }
}