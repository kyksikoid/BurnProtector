package com.example.burnprotector

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.Random

object OverlayConstants {
    const val PREFS_NAME = "burn_protector_prefs"
    const val KEY_HIDE_SYSTEM_NOTIF = "key_hide_system_notif"
    const val KEY_TOP_PERCENT = "key_top_percent"
    const val KEY_BOTTOM_PERCENT = "key_bottom_percent"
    const val KEY_BAR_STYLE = "key_bar_style"
    const val KEY_BAR_ALPHA = "key_bar_alpha"
    const val KEY_HIDE_DURATION_SEC = "key_hide_duration_sec"
    const val KEY_ANIM_FREQ = "key_anim_freq"
    const val KEY_SYSTEM_BARS_MODE = "key_system_bars_mode"
    const val KEY_EXECUTION_MODE = "key_execution_mode"

    const val KEY_SIDE_BARS_ENABLED = "key_side_bars_enabled"
    const val KEY_LEFT_PERCENT = "key_left_percent"
    const val KEY_RIGHT_PERCENT = "key_right_percent"

    const val KEY_WHITELIST_ENABLED = "whitelist_enabled"
    const val KEY_WHITELIST_PACKAGES = "whitelist_packages"

    const val ACTION_UPDATE_CONFIG = "action_update_config"
    const val ACTION_STOP = "action_stop"

    const val DEFAULT_TOP_PERCENT = 0.05f
    const val DEFAULT_BOTTOM_PERCENT = 0.04f
    const val DEFAULT_LEFT_PERCENT = 0.04f
    const val DEFAULT_RIGHT_PERCENT = 0.04f
    const val DEFAULT_ALPHA = 1.0f
    const val DEFAULT_HIDE_DURATION = 3
    const val DEFAULT_FREQ = 59f
    const val DEFAULT_SYSTEM_BARS_MODE = "NONE"

    var isRunning: Boolean = false
    var isTopBarHidden: Boolean = false
    var isBottomBarHidden: Boolean = false
    var isLeftBarHidden: Boolean = false
    var isRightBarHidden: Boolean = false
}

private enum class BarEdge {
    TOP, BOTTOM, LEFT, RIGHT
}

class OverlayBarManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val windowLayoutType: Int
) {
    private var topBarView: OverlayBarView? = null
    private var topParams: WindowManager.LayoutParams? = null
    private val topHandler = Handler(Looper.getMainLooper())

    private var bottomBarView: OverlayBarView? = null
    private var bottomParams: WindowManager.LayoutParams? = null
    private val bottomHandler = Handler(Looper.getMainLooper())

    private var leftBarView: OverlayBarView? = null
    private var leftParams: WindowManager.LayoutParams? = null
    private val leftHandler = Handler(Looper.getMainLooper())

    private var rightBarView: OverlayBarView? = null
    private var rightParams: WindowManager.LayoutParams? = null
    private val rightHandler = Handler(Looper.getMainLooper())

    private val animHandler = Handler(Looper.getMainLooper())
    private var currentHue = 0f

    private val animRunnable = object : Runnable {
        override fun run() {
            val prefs =
                context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val style = prefs.getString(OverlayConstants.KEY_BAR_STYLE, "BLACK") ?: "BLACK"
            val freq = prefs.getFloat(OverlayConstants.KEY_ANIM_FREQ, OverlayConstants.DEFAULT_FREQ)
                .coerceIn(5f, 144f)
            val intervalMs = (1000f / freq).toLong().coerceIn(7L, 200L)

            if (style == "RGB") {
                currentHue = (currentHue + 3f) % 360f
                val color = Color.HSVToColor(floatArrayOf(currentHue, 0.85f, 0.95f))
                topBarView?.updateColor(color)
                bottomBarView?.updateColor(color)
                leftBarView?.updateColor(color)
                rightBarView?.updateColor(color)
                animHandler.postDelayed(this, intervalMs)
            } else if (style == "NOISE") {
                topBarView?.updateNoise()
                bottomBarView?.updateNoise()
                leftBarView?.updateNoise()
                rightBarView?.updateNoise()
                animHandler.postDelayed(this, intervalMs)
            }
        }
    }

    private fun getScreenHeightPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.height()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.y
        }
    }

    private fun getScreenWidthPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds.width()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
            point.x
        }
    }

    private fun getStatusBarHeightPx(): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    private fun getNavigationBarHeightPx(): Int {
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    fun createOrUpdateBars() {
        val screenHeight = getScreenHeightPx()
        val screenWidth = getScreenWidthPx()
        val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)

        val topPercent =
            prefs.getFloat(OverlayConstants.KEY_TOP_PERCENT, OverlayConstants.DEFAULT_TOP_PERCENT)
        val bottomPercent = prefs.getFloat(
            OverlayConstants.KEY_BOTTOM_PERCENT,
            OverlayConstants.DEFAULT_BOTTOM_PERCENT
        )
        val systemBarsMode = prefs.getString(
            OverlayConstants.KEY_SYSTEM_BARS_MODE,
            OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
        ) ?: OverlayConstants.DEFAULT_SYSTEM_BARS_MODE

        val sideBarsEnabled = prefs.getBoolean(OverlayConstants.KEY_SIDE_BARS_ENABLED, false)
        val leftPercent =
            prefs.getFloat(OverlayConstants.KEY_LEFT_PERCENT, OverlayConstants.DEFAULT_LEFT_PERCENT)
        val rightPercent = prefs.getFloat(
            OverlayConstants.KEY_RIGHT_PERCENT,
            OverlayConstants.DEFAULT_RIGHT_PERCENT
        )

        val coverStatusBar = systemBarsMode == "STATUS_BAR" || systemBarsMode == "BOTH"
        val coverNavBar = systemBarsMode == "NAV_BAR" || systemBarsMode == "BOTH"

        val statusBarHeight = getStatusBarHeightPx()
        val navBarHeight = getNavigationBarHeightPx()

        val topHeightPx = (screenHeight * topPercent).toInt().coerceAtLeast(1)
        val bottomHeightPx = (screenHeight * bottomPercent).toInt().coerceAtLeast(1)

        val topY = if (coverStatusBar) 0 else statusBarHeight
        val bottomY = if (coverNavBar) 0 else navBarHeight


        val sideTopY = topY + topHeightPx
        val sideBottomFromTop = screenHeight - bottomY - bottomHeightPx
        val sideHeightPx = (sideBottomFromTop - sideTopY).coerceAtLeast(0)

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS


        if (topBarView == null) {
            topParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                topHeightPx,
                windowLayoutType,
                baseFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                x = 0
                y = topY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    fitInsetsSides = 0
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            topBarView = OverlayBarView(context).apply {
                fitsSystemWindows = false
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        temporarilyHide(BarEdge.TOP)
                        true
                    } else false
                }
            }
            try {
                windowManager.addView(topBarView, topParams)
            } catch (_: Exception) {
            }
        } else {
            topParams?.let { params ->
                params.height = topHeightPx
                params.y = topY
                try {
                    windowManager.updateViewLayout(topBarView, params)
                } catch (_: Exception) {
                }
            }
        }


        if (bottomBarView == null) {
            bottomParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                bottomHeightPx,
                windowLayoutType,
                baseFlags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM
                x = 0
                y = bottomY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    fitInsetsTypes = 0
                    fitInsetsSides = 0
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            bottomBarView = OverlayBarView(context).apply {
                fitsSystemWindows = false
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        temporarilyHide(BarEdge.BOTTOM)
                        true
                    } else false
                }
            }
            try {
                windowManager.addView(bottomBarView, bottomParams)
            } catch (_: Exception) {
            }
        } else {
            bottomParams?.let { params ->
                params.height = bottomHeightPx
                params.y = bottomY
                try {
                    windowManager.updateViewLayout(bottomBarView, params)
                } catch (_: Exception) {
                }
            }
        }


        if (sideBarsEnabled && sideHeightPx > 0) {
            val leftWidthPx = (screenWidth * leftPercent).toInt().coerceAtLeast(1)
            val rightWidthPx = (screenWidth * rightPercent).toInt().coerceAtLeast(1)


            if (leftBarView == null) {
                leftParams = WindowManager.LayoutParams(
                    leftWidthPx,
                    sideHeightPx,
                    windowLayoutType,
                    baseFlags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = sideTopY
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        fitInsetsTypes = 0
                        fitInsetsSides = 0
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                leftBarView = OverlayBarView(context).apply {
                    fitsSystemWindows = false
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            temporarilyHide(BarEdge.LEFT)
                            true
                        } else false
                    }
                }
                try {
                    windowManager.addView(leftBarView, leftParams)
                } catch (_: Exception) {
                }
            } else {
                leftParams?.let { params ->
                    params.width = leftWidthPx
                    params.height = sideHeightPx
                    params.y = sideTopY
                    try {
                        windowManager.updateViewLayout(leftBarView, params)
                    } catch (_: Exception) {
                    }
                }
            }


            if (rightBarView == null) {
                rightParams = WindowManager.LayoutParams(
                    rightWidthPx,
                    sideHeightPx,
                    windowLayoutType,
                    baseFlags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = 0
                    y = sideTopY
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        fitInsetsTypes = 0
                        fitInsetsSides = 0
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }

                rightBarView = OverlayBarView(context).apply {
                    fitsSystemWindows = false
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            temporarilyHide(BarEdge.RIGHT)
                            true
                        } else false
                    }
                }
                try {
                    windowManager.addView(rightBarView, rightParams)
                } catch (_: Exception) {
                }
            } else {
                rightParams?.let { params ->
                    params.width = rightWidthPx
                    params.height = sideHeightPx
                    params.y = sideTopY
                    try {
                        windowManager.updateViewLayout(rightBarView, params)
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            removeSideBarsOnly()
        }

        applyStyleAndAnimation()
    }

    private fun applyStyleAndAnimation() {
        animHandler.removeCallbacksAndMessages(null)
        val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val style = prefs.getString(OverlayConstants.KEY_BAR_STYLE, "BLACK") ?: "BLACK"
        val alpha = prefs.getFloat(OverlayConstants.KEY_BAR_ALPHA, OverlayConstants.DEFAULT_ALPHA)
            .coerceIn(0.15f, 1.0f)

        listOf(topBarView, bottomBarView, leftBarView, rightBarView).forEach { view ->
            view?.alpha = alpha
            view?.currentStyle = style
        }

        when (style) {
            "WHITE" -> {
                listOf(topBarView, bottomBarView, leftBarView, rightBarView).forEach {
                    it?.updateColor(Color.WHITE)
                }
            }

            "NOISE" -> {
                listOf(topBarView, bottomBarView, leftBarView, rightBarView).forEach {
                    it?.updateNoise()
                }
                animHandler.post(animRunnable)
            }

            "RGB" -> {
                animHandler.post(animRunnable)
            }

            else -> {
                listOf(topBarView, bottomBarView, leftBarView, rightBarView).forEach {
                    it?.updateColor(Color.BLACK)
                }
            }
        }
    }

    private fun temporarilyHide(edge: BarEdge) {
        val view = when (edge) {
            BarEdge.TOP -> topBarView
            BarEdge.BOTTOM -> bottomBarView
            BarEdge.LEFT -> leftBarView
            BarEdge.RIGHT -> rightBarView
        }
        val params = when (edge) {
            BarEdge.TOP -> topParams
            BarEdge.BOTTOM -> bottomParams
            BarEdge.LEFT -> leftParams
            BarEdge.RIGHT -> rightParams
        }
        val handler = when (edge) {
            BarEdge.TOP -> topHandler
            BarEdge.BOTTOM -> bottomHandler
            BarEdge.LEFT -> leftHandler
            BarEdge.RIGHT -> rightHandler
        }

        if (view == null || params == null) return

        when (edge) {
            BarEdge.TOP -> OverlayConstants.isTopBarHidden = true
            BarEdge.BOTTOM -> OverlayConstants.isBottomBarHidden = true
            BarEdge.LEFT -> OverlayConstants.isLeftBarHidden = true
            BarEdge.RIGHT -> OverlayConstants.isRightBarHidden = true
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
        view.visibility = View.INVISIBLE

        val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val hideDurationSec = prefs.getInt(
            OverlayConstants.KEY_HIDE_DURATION_SEC,
            OverlayConstants.DEFAULT_HIDE_DURATION
        ).coerceIn(1, 300)

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            when (edge) {
                BarEdge.TOP -> OverlayConstants.isTopBarHidden = false
                BarEdge.BOTTOM -> OverlayConstants.isBottomBarHidden = false
                BarEdge.LEFT -> OverlayConstants.isLeftBarHidden = false
                BarEdge.RIGHT -> OverlayConstants.isRightBarHidden = false
            }
            if (view.isAttachedToWindow) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {
                }
                view.visibility = View.VISIBLE
            }
        }, hideDurationSec * 1000L)
    }

    private fun removeSideBarsOnly() {
        leftHandler.removeCallbacksAndMessages(null)
        rightHandler.removeCallbacksAndMessages(null)
        OverlayConstants.isLeftBarHidden = false
        OverlayConstants.isRightBarHidden = false

        leftBarView?.let {
            try {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        rightBarView?.let {
            try {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        leftBarView = null
        rightBarView = null
    }

    fun removeBars(stopService: Boolean = false) {
        animHandler.removeCallbacksAndMessages(null)
        topHandler.removeCallbacksAndMessages(null)
        bottomHandler.removeCallbacksAndMessages(null)
        leftHandler.removeCallbacksAndMessages(null)
        rightHandler.removeCallbacksAndMessages(null)

        if (stopService) {
            OverlayConstants.isRunning = false
        }
        OverlayConstants.isTopBarHidden = false
        OverlayConstants.isBottomBarHidden = false
        OverlayConstants.isLeftBarHidden = false
        OverlayConstants.isRightBarHidden = false

        listOf(topBarView, bottomBarView, leftBarView, rightBarView).forEach { v ->
            v?.let {
                try {
                    if (it.isAttachedToWindow) windowManager.removeView(it)
                } catch (_: Exception) {
                }
            }
        }
        topBarView = null
        bottomBarView = null
        leftBarView = null
        rightBarView = null
    }
}

class NormalOverlayService : Service() {

    companion object {
        var instance: NormalOverlayService? = null
            private set

        fun startOrUpdateBars() {
            instance?.checkAndApplyBars()
        }

        fun hideBars() {
            instance?.let {
                it.barManager?.removeBars(stopService = true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    it.stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    it.stopForeground(true)
                }
                it.stopSelf()
            }
            instance = null
        }

        fun updateIfRunning() {
            if (OverlayConstants.isRunning) {
                instance?.checkAndApplyBars()
            }
        }
    }

    private var barManager: OverlayBarManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        barManager = OverlayBarManager(this, wm, layoutType)
        createNotificationChannelAndStartForeground()
    }

    private fun createNotificationChannelAndStartForeground() {
        val channelId = "burn_protector_normal_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Burn Protector",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая защита экрана"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Burn Protector")
            .setContentText("Защитные полосы активны")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == OverlayConstants.ACTION_STOP) {
            hideBars()
            return START_NOT_STICKY
        }
        checkAndApplyBars()
        SystemNotificationHelper.hideNotificationWithRoot(this)
        return START_STICKY
    }

    private fun checkAndApplyBars() {
        val prefs = getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isWhitelistEnabled = prefs.getBoolean(OverlayConstants.KEY_WHITELIST_ENABLED, false)

        if (isWhitelistEnabled) {
            barManager?.removeBars(stopService = false)
        } else {
            barManager?.createOrUpdateBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barManager?.removeBars(stopService = true)
        instance = null
    }
}

class OverlayAccessibilityService : AccessibilityService() {

    companion object {
        var instance: OverlayAccessibilityService? = null
            private set

        var currentForegroundPackage: String = ""

        fun startOrUpdateBars() {
            OverlayConstants.isRunning = true
            instance?.checkWhitelistAndApply()
        }

        fun hideBars() {
            OverlayConstants.isRunning = false
            currentForegroundPackage = ""
            instance?.barManager?.removeBars(stopService = true)
        }

        fun updateIfRunning() {
            if (OverlayConstants.isRunning) {
                instance?.checkWhitelistAndApply()
            }
        }
    }

    private var barManager: OverlayBarManager? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !OverlayConstants.isRunning) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            val rootPkg = try {
                rootInActiveWindow?.packageName?.toString()
            } catch (_: Exception) {
                null
            }
            val eventPkg = event.packageName?.toString()

            val activePkg = when {
                !rootPkg.isNullOrEmpty() && !isIgnoredPackage(rootPkg) -> rootPkg
                !eventPkg.isNullOrEmpty() && !isIgnoredPackage(eventPkg) -> eventPkg
                else -> null
            }

            if (activePkg != null) {
                currentForegroundPackage = activePkg
                checkWhitelistAndApply()
            }
        }
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        return pkg == "com.android.systemui" ||
                pkg == "android" ||
                pkg == packageName ||
                pkg.contains("inputmethod")
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        barManager =
            OverlayBarManager(this, wm, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        if (OverlayConstants.isRunning) {
            checkWhitelistAndApply()
        }
    }

    fun checkWhitelistAndApply() {
        if (!OverlayConstants.isRunning) {
            barManager?.removeBars(stopService = false)
            return
        }

        val prefs = getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isWhitelistEnabled = prefs.getBoolean(OverlayConstants.KEY_WHITELIST_ENABLED, false)

        if (!isWhitelistEnabled) {
            barManager?.createOrUpdateBars()
            return
        }

        if (currentForegroundPackage.isEmpty() || isIgnoredPackage(currentForegroundPackage)) {
            val directPkg = try {
                rootInActiveWindow?.packageName?.toString()
            } catch (_: Exception) {
                null
            }
            if (!directPkg.isNullOrEmpty() && !isIgnoredPackage(directPkg)) {
                currentForegroundPackage = directPkg
            }
        }

        val whitelist =
            prefs.getStringSet(OverlayConstants.KEY_WHITELIST_PACKAGES, emptySet()) ?: emptySet()

        if (currentForegroundPackage.isNotEmpty() && whitelist.contains(currentForegroundPackage)) {
            barManager?.createOrUpdateBars()
        } else {
            barManager?.removeBars(stopService = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        barManager?.removeBars(stopService = true)
        instance = null
        currentForegroundPackage = ""
    }
}

class OverlayBarView(context: Context) : View(context) {
    var currentStyle: String = "BLACK"
    private var currentSolidColor: Int = Color.BLACK

    private val noiseBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(96 * 96)
    private val random = Random()
    private val noisePaint = Paint()

    fun updateNoise() {
        for (i in pixels.indices) {
            val v = random.nextInt(256)
            pixels[i] = Color.rgb(v, v, v)
        }
        noiseBitmap.setPixels(pixels, 0, 96, 0, 0, 96, 96)
        noisePaint.shader =
            BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        invalidate()
    }

    fun updateColor(color: Int) {
        currentSolidColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentStyle == "NOISE") {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), noisePaint)
        } else {
            canvas.drawColor(currentSolidColor)
        }
    }
}