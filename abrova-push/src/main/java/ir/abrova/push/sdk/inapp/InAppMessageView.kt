package ir.abrova.push.sdk.inapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.load
import ir.abrova.push.sdk.AbrovaPushLogger
import java.util.Locale
import kotlin.math.abs

/**
 * The built-in in-app message display, drawn like the Console's preview (Push › In-app):
 * - modal: centred card over a dimmed app, image 2:1, buttons stacked;
 * - banner: slim bar at the top or the bottom (the message's position), small square image, buttons in the bar;
 * - fullscreen: the whole screen, image on the upper part, buttons at the bottom;
 * - card: at the bottom without dimming, image 3:1, buttons side by side.
 * Close button on the top corner of the panel (left for right-to-left text). The message's own colours win.
 */
class InAppMessageView private constructor(
    private val activity: Activity,
    private val message: InAppMessage,
    private val content: InAppMessageContent,
    private val onButtonClick: (InAppButton) -> Unit,
    private val onDismiss: () -> Unit,
    /** The view left the screen without being dismissed (its activity was closed or replaced). */
    private val onGone: () -> Unit = {}
) {
    private var rootView: FrameLayout? = null
    private var containerView: View? = null
    private var dismissed = false

    private val bg = parseColor(content.backgroundColor, Color.WHITE)
    private val fg = parseColor(content.textColor, Color.parseColor("#18181B"))
    private val rtl = isRtl(content)

    companion object {
        private const val TAG = "AbrovaPush.InAppView"
        private const val ANIMATION_DURATION = 220L
        private val PRIMARY = Color.parseColor("#1C72C8")
        private val DESTRUCTIVE = Color.parseColor("#DC2626")

        /** Image width : height, as in the Console. */
        internal const val MODAL_IMAGE_RATIO = 2f
        internal const val FULLSCREEN_IMAGE_RATIO = 2f
        internal const val CARD_IMAGE_RATIO = 3f

        /**
         * Show an in-app message
         */
        fun show(
            activity: Activity,
            message: InAppMessage,
            content: InAppMessageContent,
            onButtonClick: (InAppButton) -> Unit,
            onDismiss: () -> Unit,
            onGone: () -> Unit = {}
        ): InAppMessageView {
            return InAppMessageView(activity, message, content, onButtonClick, onDismiss, onGone).also {
                it.show()
            }
        }

        /** Right-to-left when the phone's language is, or the text itself is (a Farsi app's own words). */
        internal fun isRtl(content: InAppMessageContent, locale: Locale = Locale.getDefault()): Boolean {
            if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) return true
            return (content.title + content.body).any { it in '֐'..'ࣿ' }
        }
    }

    private fun show() {
        when (message.type) {
            InAppMessageType.MODAL -> showModal()
            InAppMessageType.BANNER -> showBanner()
            InAppMessageType.FULLSCREEN -> showFullscreen()
            InAppMessageType.CARD -> showCard()
        }
    }

    /**
     * Dismiss the message
     */
    fun dismiss() {
        if (dismissed) return
        dismissed = true
        animateOut {
            rootView?.let { root ->
                (activity.window.decorView as? ViewGroup)?.removeView(root)
            }
            rootView = null
            containerView = null
            onDismiss()
        }
    }

    // ==================== Modal ====================

    private fun showModal() {
        val root = createRootOverlay(dim = true)
        val card = panel(radiusDp = 16, elevationDp = 12)
        val column = vertical()
        content.imageUrl?.let { column.addView(image(it, MODAL_IMAGE_RATIO)) }
        column.addView(textBlock(titleSp = 16f, bodySp = 13.5f))
        column.addView(buttons(row = false) ?: spacer(12))
        card.addView(withClose(column))

        root.addView(card, FrameLayout.LayoutParams(
            minOf(activity.resources.displayMetrics.widthPixels - dp(36), dp(420)),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        present(root, card, Slide.SCALE)
    }

    // ==================== Banner ====================

    private fun showBanner() {
        val bottom = content.position == "bottom"
        val root = createRootOverlay(dim = false, clickThrough = true)
        val banner = panel(radiusDp = 14, elevationDp = 8)

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(6), dp(10))
        }
        content.imageUrl?.let { url ->
            bar.addView(CardView(activity).apply {
                radius = dp(8).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(Color.TRANSPARENT)
                addView(loadInto(ImageView(activity), url))
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
        }
        bar.addView(vertical().apply {
            addView(text(content.title, 14f, bold = true, maxLines = 1))
            if (content.body.isNotEmpty()) addView(text(content.body, 12f, alpha = 0.8f, maxLines = 2))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.buttons.forEach { b ->
            bar.addView(compactButton(b), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) })
        }
        bar.addView(plainClose())
        banner.addView(bar)

        val insets = systemBars()
        root.addView(banner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            (if (bottom) Gravity.BOTTOM else Gravity.TOP) or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = dp(8)
            rightMargin = dp(8)
            if (bottom) bottomMargin = insets.bottom + dp(12) else topMargin = insets.top + dp(8)
        })
        setupSwipeToDismiss(banner, up = !bottom)
        present(root, banner, if (bottom) Slide.UP else Slide.DOWN)
    }

    // ==================== Fullscreen ====================

    private fun showFullscreen() {
        val root = createRootOverlay(dim = false)
        val insets = systemBars()
        val screen = FrameLayout(activity).apply { setBackgroundColor(bg) }

        val page = vertical()
        content.imageUrl?.let { page.addView(image(it, FULLSCREEN_IMAGE_RATIO)) }
            ?: page.addView(spacer(insets.top / activity.resources.displayMetrics.density.toInt() + 40))
        page.addView(textBlock(titleSp = 22f, bodySp = 15f, topDp = 18))
        val scroll = ScrollView(activity).apply { isFillViewport = true; addView(page) }

        val column = vertical()
        column.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        buttons(row = false)?.let { b ->
            b.setPadding(b.paddingLeft, b.paddingTop, b.paddingRight, b.paddingBottom + insets.bottom + dp(8))
            column.addView(b)
        } ?: column.addView(spacer(insets.bottom / activity.resources.displayMetrics.density.toInt() + 16))
        screen.addView(column)
        screen.addView(roundClose(), closeParams(topPx = insets.top + dp(10)))

        root.addView(screen, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        present(root, screen, Slide.FADE)
    }

    // ==================== Card ====================

    private fun showCard() {
        val root = createRootOverlay(dim = false, clickThrough = true)
        val card = panel(radiusDp = 16, elevationDp = 12)
        val column = vertical()
        content.imageUrl?.let { column.addView(image(it, CARD_IMAGE_RATIO)) }
        column.addView(textBlock(titleSp = 15f, bodySp = 13f))
        column.addView(buttons(row = true) ?: spacer(12))
        card.addView(withClose(column))

        root.addView(card, FrameLayout.LayoutParams(
            minOf(activity.resources.displayMetrics.widthPixels - dp(20), dp(480)),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = systemBars().bottom + dp(16) })
        setupSwipeToDismiss(card, up = false)
        present(root, card, Slide.UP)
    }

    // ==================== Building blocks ====================

    private fun createRootOverlay(dim: Boolean, clickThrough: Boolean = false): FrameLayout =
        FrameLayout(activity).apply {
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            if (dim) {
                setBackgroundColor(Color.argb(115, 0, 0, 0))
                setOnClickListener { dismiss() }
            } else if (!clickThrough) {
                isClickable = true
            }
        }

    private fun panel(radiusDp: Int, elevationDp: Int): CardView = CardView(activity).apply {
        radius = dp(radiusDp).toFloat()
        cardElevation = dp(elevationDp).toFloat()
        setCardBackgroundColor(bg)
        isClickable = true // taps on the panel don't reach the backdrop
    }

    private fun vertical(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    private fun spacer(heightDp: Int): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
    }

    private fun text(value: String, sp: Float, bold: Boolean = false, alpha: Float = 1f, maxLines: Int? = null): TextView =
        TextView(activity).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(fg)
            this.alpha = alpha
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines?.let { this.maxLines = it; ellipsize = TextUtils.TruncateAt.END }
        }

    /** Title and body, as the Console's preview: bold title, body slightly faded. */
    private fun textBlock(titleSp: Float, bodySp: Float, topDp: Int = 12): LinearLayout = vertical().apply {
        setPadding(dp(14), dp(topDp), dp(14), dp(4))
        if (content.title.isNotEmpty()) addView(text(content.title, titleSp, bold = true))
        if (content.body.isNotEmpty()) addView(text(content.body, bodySp, alpha = 0.85f).apply {
            setLineSpacing(0f, 1.25f)
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(4)
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun image(url: String, ratio: Float): ImageView = loadInto(RatioImageView(activity, ratio), url)

    private fun loadInto(view: ImageView, url: String): ImageView = view.apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Color.argb(20, 0, 0, 0))
        load(url) {
            crossfade(true)
            listener(
                onSuccess = { _, _ -> setBackgroundColor(Color.TRANSPARENT) },
                onError = { _, result -> AbrovaPushLogger.e(TAG, "Failed to load image: $url, ${result.throwable.message}") }
            )
        }
    }

    /** Buttons as in the Console: stacked (modal, full screen) or side by side (card). Null without buttons. */
    private fun buttons(row: Boolean): LinearLayout? {
        if (content.buttons.isEmpty()) return null
        return LinearLayout(activity).apply {
            orientation = if (row) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(14))
            content.buttons.forEachIndexed { i, b ->
                val lp = if (row) {
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = dp(6) }
                } else {
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (i > 0) topMargin = dp(6) }
                }
                addView(button(b), lp)
            }
        }
    }

    private fun button(button: InAppButton): TextView = TextView(activity).apply {
        text = button.text
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(typeface, Typeface.BOLD)
        val boxed = button.style == InAppButtonStyle.PRIMARY || button.style == InAppButtonStyle.SECONDARY
        minHeight = dp(if (boxed) 40 else 32)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        when (button.style) {
            InAppButtonStyle.PRIMARY -> {
                setTextColor(Color.WHITE)
                background = rounded(PRIMARY, null)
            }
            InAppButtonStyle.SECONDARY -> {
                setTextColor(fg)
                alpha = 0.85f
                background = rounded(Color.TRANSPARENT, fg)
            }
            InAppButtonStyle.TEXT -> {
                setTextColor(fg)
                alpha = 0.8f
                setTypeface(typeface, Typeface.NORMAL)
            }
            InAppButtonStyle.DESTRUCTIVE -> {
                setTextColor(DESTRUCTIVE)
                setTypeface(typeface, Typeface.NORMAL)
            }
        }
        setOnClickListener { onButtonClick(button) }
    }

    /** A banner's button: small, in the bar. */
    private fun compactButton(button: InAppButton): TextView = button(button).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        minHeight = dp(30)
        minimumHeight = dp(30)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun rounded(fill: Int, stroke: Int?): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(9).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    /** The panel's content with the round close button on its top corner (left in right-to-left). */
    private fun withClose(content: View): FrameLayout = FrameLayout(activity).apply {
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        addView(roundClose(), closeParams(topPx = dp(8)))
    }

    private fun closeParams(topPx: Int) = FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply {
        topMargin = topPx
        marginEnd = dp(8)
    }

    private fun roundClose(): TextView = TextView(activity).apply {
        text = "✕"
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(90, 0, 0, 0))
        }
        contentDescription = activity.getString(android.R.string.cancel)
        setOnClickListener { dismiss() }
    }

    private fun plainClose(): TextView = TextView(activity).apply {
        text = "✕"
        gravity = Gravity.CENTER
        setTextColor(fg)
        alpha = 0.7f
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginStart = dp(2) }
        contentDescription = activity.getString(android.R.string.cancel)
        setOnClickListener { dismiss() }
    }

    private data class Bars(val top: Int, val bottom: Int)

    /** Status bar and navigation bar heights, so nothing sits under them. */
    private fun systemBars(): Bars {
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
        return Bars(insets?.top ?: dp(24), insets?.bottom ?: 0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeToDismiss(view: View, up: Boolean) {
        var startY = 0f
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    false // let taps reach the buttons
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = event.rawY - startY
                    if ((up && delta < 0) || (!up && delta > 0)) v.translationY = delta
                    abs(delta) > dp(8)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (abs(v.translationY) > dp(60)) {
                        dismiss()
                    } else if (v.translationY != 0f) {
                        ObjectAnimator.ofFloat(v, "translationY", v.translationY, 0f).apply {
                            duration = 200
                            interpolator = DecelerateInterpolator()
                            start()
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }

    private enum class Slide { FADE, SCALE, UP, DOWN }

    private fun present(root: FrameLayout, panel: View, slide: Slide) {
        rootView = root
        containerView = panel
        // The activity went away under the message (Back on the app, another screen): the message is gone too,
        // so the next one can show.
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (dismissed) return
                dismissed = true
                rootView = null
                containerView = null
                onGone()
            }
        })
        // Modal and full screen block the app: Back closes the message, not the screen behind it.
        if (message.type == InAppMessageType.MODAL || message.type == InAppMessageType.FULLSCREEN) {
            root.isFocusableInTouchMode = true
            root.setOnKeyListener { _, keyCode, event ->
                if (keyCode != android.view.KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == android.view.KeyEvent.ACTION_UP) dismiss()
                true
            }
            root.post { root.requestFocus() }
        }
        (activity.window.decorView as? ViewGroup)?.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        panel.alpha = 0f
        when (slide) {
            Slide.SCALE -> { panel.scaleX = 0.92f; panel.scaleY = 0.92f }
            Slide.UP -> panel.translationY = dp(40).toFloat()
            Slide.DOWN -> panel.translationY = -dp(40).toFloat()
            Slide.FADE -> {}
        }
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(ANIMATION_DURATION)
            .setInterpolator(DecelerateInterpolator())
            .start()
        if (root.background != null) {
            root.background.alpha = 0
            ObjectAnimator.ofInt(root.background, "alpha", 0, 255).setDuration(ANIMATION_DURATION).start()
        }
    }

    private fun animateOut(onComplete: () -> Unit) {
        val view = containerView ?: run {
            onComplete()
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onComplete()
                }
            })
            .start()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), activity.resources.displayMetrics
    ).toInt()

    private fun parseColor(colorString: String?, default: Int): Int = try {
        if (colorString.isNullOrEmpty()) default else Color.parseColor(colorString)
    } catch (_: Exception) {
        default
    }

    /** An image as wide as its parent, with height = width / [ratio]. */
    private class RatioImageView(context: Context, private val ratio: Float) : ImageView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((width / ratio).toInt(), MeasureSpec.EXACTLY)
            )
        }
    }
}
