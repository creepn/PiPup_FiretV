package nl.rogro82.pipup

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

// TODO: convert dimensions from px to dp

@SuppressLint("ViewConstructor")
sealed class PopupView(context: Context, val popup: PopupProps) : LinearLayout(context) {

    open fun create() {
        inflate(context, R.layout.popup,this)

        layoutParams = LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            orientation = VERTICAL
            minimumWidth = 240
        }

        setPadding(20,20,20,20)

        val title = findViewById<TextView>(R.id.popup_title)
        val message = findViewById<TextView>(R.id.popup_message)
        val frame = findViewById<FrameLayout>(R.id.popup_frame)

        if(popup.media == null) {
            removeView(frame)
        }

        if(popup.title.isNullOrEmpty()) {
            removeView(title)
        } else {
            title.text = popup.title
            title.textSize = popup.titleSize
            title.setTextColor(Color.parseColor(popup.titleColor))
        }

        if(popup.message.isNullOrEmpty()) {
            removeView(message)
        } else {
            message.text = popup.message
            message.textSize = popup.messageSize
            message.setTextColor(Color.parseColor(popup.messageColor))
        }

        // chime (local patch): "chime" plays the bundled ding-dong
        if (popup.sound == "chime") {
            try {
                val mp = android.media.MediaPlayer()
                mp.setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                val afd = context.resources.openRawResourceFd(R.raw.chime)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.setOnCompletionListener { it.release() }
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
            } catch (e: Throwable) { Log.e("PopupView", "chime failed", e) }
        }

        // rounded corners (local patch)
        val radius = popup.cornerRadius
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(popup.backgroundColor))
            cornerRadius = radius
            if (!popup.borderColor.isNullOrEmpty() && popup.borderWidth > 0) setStroke(popup.borderWidth, Color.parseColor(popup.borderColor))
        }
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        if (radius > 0f) {
            frame?.apply {
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, view.paddingTop, view.width, view.height - view.paddingBottom, radius * 0.6f)
                    }
                }
                clipToOutline = true
            }
        }
    }

    open fun destroy() {}

    private class Default(context: Context, popup: PopupProps) : PopupView(context, popup) {
        init { create() }
    }

    private class Video(context: Context, popup: PopupProps, val media: PopupProps.Media.Video): PopupView(context, popup) {
        private lateinit var mVideoView: VideoView

        init { create() }

        override fun create() {
            super.create()

            visibility = View.INVISIBLE

            val frame = findViewById<FrameLayout>(R.id.popup_frame)

            mVideoView = VideoView(context).apply {
                setVideoURI(Uri.parse(media.uri))
                setOnPreparedListener {
                    it.setOnVideoSizeChangedListener { _, _, _ ->

                        // resize video and show popup view

                        layoutParams = FrameLayout.LayoutParams(media.width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                            gravity = Gravity.CENTER
                        }

                        this@Video.visibility = View.VISIBLE
                    }
                }

                start()
            }

            frame.addView(mVideoView, FrameLayout.LayoutParams(1, 1))
        }

        override fun destroy() {
            try {
                if(mVideoView.isPlaying) {
                    mVideoView.stopPlayback()
                }
            } catch(e: Throwable) {}
        }
    }

    private class Image(context: Context, popup: PopupProps, val media: PopupProps.Media.Image): PopupView(context, popup) {
        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)

            try {
                val imageView = ImageView(context)

                val layoutParams =
                    FrameLayout.LayoutParams(media.width, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER
                    }

                frame.addView(imageView, layoutParams)

                Glide.with(context)
                    .load(Uri.parse(media.uri))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .into(imageView)

            } catch(e: Throwable) {
                removeView(frame)
            }
        }
    }

    private class Bitmap(context: Context, popup: PopupProps, val media: PopupProps.Media.Bitmap): PopupView(context, popup) {
        var mImageView: ImageView? = null

        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)
            mImageView = ImageView(context).apply {
                setImageBitmap(media.image)
            }

            val scaledHeight = ((media.width.toFloat() / media.image.width) * media.image.height).toInt()
            val layoutParams =
                FrameLayout.LayoutParams(media.width, scaledHeight).apply {
                    gravity = Gravity.CENTER
                }

            frame.addView(mImageView, layoutParams)
        }

        override fun destroy() {
            try {
                mImageView?.setImageDrawable(null)
                media.image.recycle()
            } catch(e: Throwable) {}
        }
    }

    private class Web(context: Context, popup: PopupProps, val media: PopupProps.Media.Web): PopupView(context, popup) {
        init { create() }

        override fun create() {
            super.create()

            val frame = findViewById<FrameLayout>(R.id.popup_frame)
            webView = WebView(context).apply {
                with(settings) {
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl(media.uri)
            }

            val layoutParams = FrameLayout.LayoutParams(
                media.width,
                media.height
            ).apply {
                gravity = Gravity.CENTER
            }

            frame.addView(webView, layoutParams)
        }

        private lateinit var webView: WebView

        override fun destroy() {
            try {
                if (::webView.isInitialized) webView.apply { stopLoading(); loadUrl("about:blank"); removeAllViews(); destroy() }
            } catch(e: Throwable) {}
        }
    }

    companion object {
        const val LOG_TAG = "PopupView"

        fun build(context: Context, popup: PopupProps): PopupView
        {
            return when (popup.media) {
                is PopupProps.Media.Web -> Web(context, popup, popup.media)
                is PopupProps.Media.Video -> Video(context, popup, popup.media)
                is PopupProps.Media.Image -> Image(context, popup, popup.media)
                is PopupProps.Media.Bitmap -> Bitmap(context, popup, popup.media)
                else -> Default(context, popup)
            }
        }
    }
}