package com.example.burnprotector

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.util.Random

object AppSettingsKeys {
    const val KEY_AUTO_LAUNCH_ENABLED = "auto_launch_enabled"
    const val KEY_AUTO_LAUNCH_PACKAGE = "auto_launch_package"
    const val KEY_AUTO_LAUNCH_NAME = "auto_launch_name"
    const val KEY_WHITELIST_ENABLED = "whitelist_enabled"
    const val KEY_WHITELIST_PACKAGES = "whitelist_packages"
}

enum class AppPickerMode {
    AUTO_LAUNCH,
    WHITELIST
}

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences(
                    OverlayConstants.PREFS_NAME,
                    Context.MODE_PRIVATE
                )
            }

            var currentTheme by remember {
                mutableStateOf(prefs.getString("app_theme_mode", "SYSTEM") ?: "SYSTEM")
            }

            val isSysDark = isSystemInDarkTheme()
            val colorScheme = when (currentTheme) {
                "LIGHT" -> lightColorScheme(
                    background = Color(0xFFF7F9FC),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE9EDF5),
                    onBackground = Color(0xFF191C20),
                    onSurface = Color(0xFF191C20),
                    onSurfaceVariant = Color(0xFF555B63),
                    primary = Color(0xFF2563EB)
                )

                "GREY" -> darkColorScheme(
                    background = Color(0xFF232528),
                    surface = Color(0xFF2C2F34),
                    surfaceVariant = Color(0xFF383C42),
                    onBackground = Color(0xFFF0F2F5),
                    onSurface = Color(0xFFF0F2F5),
                    onSurfaceVariant = Color(0xFF9EA3AC),
                    primary = Color(0xFF60A5FA)
                )

                "DARK" -> darkColorScheme(
                    background = Color(0xFF0C0D0E),
                    surface = Color(0xFF16181A),
                    surfaceVariant = Color(0xFF212428),
                    onBackground = Color(0xFFEDEDED),
                    onSurface = Color(0xFFEDEDED),
                    onSurfaceVariant = Color(0xFF9EA3AC),
                    primary = Color(0xFF38BDF8)
                )

                else -> if (isSysDark) darkColorScheme() else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    AppNavigation(
                        contentPadding = innerPadding,
                        currentTheme = currentTheme,
                        onThemeChange = { newTheme ->
                            currentTheme = newTheme
                            prefs.edit().putString("app_theme_mode", newTheme).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GearIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier = modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerR = size.minDimension * 0.47f
        val innerR = size.minDimension * 0.19f
        val notchDistance = size.minDimension * 0.44f
        val notchR = size.minDimension * 0.105f

        val gearBody = Path().apply {
            addOval(
                Rect(
                    center.x - outerR,
                    center.y - outerR,
                    center.x + outerR,
                    center.y + outerR
                )
            )
        }

        val cutouts = Path().apply {
            for (i in 0 until 8) {
                val angle = (i * 45f) * (Math.PI / 180f).toFloat()
                val nx = center.x + notchDistance * kotlin.math.cos(angle)
                val ny = center.y + notchDistance * kotlin.math.sin(angle)
                addOval(Rect(nx - notchR, ny - notchR, nx + notchR, ny + notchR))
            }
            addOval(
                Rect(
                    center.x - innerR,
                    center.y - innerR,
                    center.x + innerR,
                    center.y + innerR
                )
            )
        }

        val finalGearPath = Path()
        finalGearPath.op(gearBody, cutouts, PathOperation.Difference)

        drawPath(finalGearPath, color = color)
    }
}

@Composable
fun BackArrowIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val stroke = 2.8.dp.toPx()
        drawLine(
            color,
            Offset(size.width * 0.2f, size.height * 0.5f),
            Offset(size.width * 0.8f, size.height * 0.5f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.2f, size.height * 0.5f),
            Offset(size.width * 0.48f, size.height * 0.22f),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color,
            Offset(size.width * 0.2f, size.height * 0.5f),
            Offset(size.width * 0.48f, size.height * 0.78f),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
fun SearchIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.2.dp.toPx()
        val r = size.minDimension * 0.35f
        val cx = size.width * 0.42f
        val cy = size.height * 0.42f

        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        val rad = (45f * Math.PI / 180f).toFloat()
        val startX = cx + r * kotlin.math.cos(rad)
        val startY = cy + r * kotlin.math.sin(rad)
        drawLine(
            color,
            Offset(startX, startY),
            Offset(size.width * 0.88f, size.height * 0.88f),
            stroke,
            StrokeCap.Round
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun AppIconImage(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawableToBitmap(drawable) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
    )
}

@Composable
fun NoiseCanvas(modifier: Modifier = Modifier, frequency: Float = 59f) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frequency) {
        val interval = (1000f / frequency).toLong().coerceIn(7L, 200L)
        while (true) {
            delay(interval)
            tick++
        }
    }
    val noiseBitmap = remember { Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888) }
    val pixels = remember { IntArray(96 * 96) }
    val random = remember { Random() }

    Canvas(modifier = modifier) {
        val currentTick = tick
        for (i in pixels.indices) {
            val v = random.nextInt(256)
            pixels[i] = android.graphics.Color.rgb(v, v, v)
        }
        noiseBitmap.setPixels(pixels, 0, 96, 0, 0, 96, 96)
        val shader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        val paint = Paint().apply {
            asFrameworkPaint().shader = shader
        }
        drawIntoCanvas { canvas ->
            canvas.drawRect(Rect(0f, 0f, size.width, size.height), paint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
    )

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        colors = sliderColors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = sliderColors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp
            )
        }
    )
}

@Composable
fun AppNavigation(
    contentPadding: PaddingValues,
    currentTheme: String,
    onThemeChange: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs =
        remember { context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE) }
    val isSysDark = isSystemInDarkTheme()

    var isServiceRunning by remember { mutableStateOf(OverlayConstants.isRunning) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isAppPickerOpen by remember { mutableStateOf(false) }
    var appPickerMode by remember { mutableStateOf(AppPickerMode.AUTO_LAUNCH) }
    var isPixelCleanerRunning by remember { mutableStateOf(false) }
    var wasServiceRunningBeforeCleaner by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    var previewActiveEdge by remember { mutableStateOf<String?>(null) }
    var previewPercent by remember { mutableFloatStateOf(0f) }
    var previewBarStyle by remember { mutableStateOf("BLACK") }
    var previewAnimFreq by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_ANIM_FREQ,
                OverlayConstants.DEFAULT_FREQ
            )
        )
    }
    var previewBarAlpha by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_BAR_ALPHA,
                OverlayConstants.DEFAULT_ALPHA
            )
        )
    }

    var systemBarsMode by remember {
        mutableStateOf(
            prefs.getString(
                OverlayConstants.KEY_SYSTEM_BARS_MODE,
                OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
            ) ?: OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
        )
    }

    BackHandler {
        if (isPixelCleanerRunning) {
            isPixelCleanerRunning = false
            if (wasServiceRunningBeforeCleaner) {
                startOverlay(context, coroutineScope, launchApp = false) { isServiceRunning = it }
            }
        } else if (isAppPickerOpen) {
            isAppPickerOpen = false
        } else if (isSettingsOpen) {
            isSettingsOpen = false
        } else {
            showExitDialog = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rgb_preview")
    val animatedHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue_preview"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !isSettingsOpen && !isAppPickerOpen && !isPixelCleanerRunning,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
        ) {
            MainHomeScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                isRunning = isServiceRunning,
                onOpenSettings = { isSettingsOpen = true },
                onStart = {
                    startOverlay(context, coroutineScope, launchApp = true) {
                        isServiceRunning = it
                    }
                },
                onStop = {
                    stopAndExit(context) { isServiceRunning = it }
                }
            )
        }

        AnimatedVisibility(
            visible = isSettingsOpen && !isAppPickerOpen && !isPixelCleanerRunning,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            SettingsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                onBack = { isSettingsOpen = false },
                currentTheme = currentTheme,
                onThemeChange = onThemeChange,
                systemBarsMode = systemBarsMode,
                onSystemBarsModeChange = { newMode ->
                    systemBarsMode = newMode
                },
                onOpenAppPicker = { mode ->
                    appPickerMode = mode
                    isAppPickerOpen = true
                },
                onAdjustingBar = { edge, percent, style, freq, alpha ->
                    val isRunning = OverlayConstants.isRunning
                    val isTopHidden = OverlayConstants.isTopBarHidden
                    val isBottomHidden = OverlayConstants.isBottomBarHidden
                    val isLeftHidden = OverlayConstants.isLeftBarHidden
                    val isRightHidden = OverlayConstants.isRightBarHidden

                    val showPreview = if (!isRunning) {
                        true
                    } else {
                        when (edge) {
                            "TOP" -> isTopHidden
                            "BOTTOM" -> isBottomHidden
                            "LEFT" -> isLeftHidden
                            "RIGHT" -> isRightHidden
                            else -> true
                        }
                    }

                    if (showPreview) {
                        previewActiveEdge = edge
                        previewPercent = percent
                        previewBarStyle = style
                        previewAnimFreq = freq
                        previewBarAlpha = alpha
                    } else {
                        previewActiveEdge = null
                    }
                },
                onLaunchCleaner = {
                    wasServiceRunningBeforeCleaner = OverlayConstants.isRunning
                    if (wasServiceRunningBeforeCleaner) {
                        NormalOverlayService.hideBars()
                        OverlayAccessibilityService.hideBars()
                    }
                    isPixelCleanerRunning = true
                }
            )
        }

        AnimatedVisibility(
            visible = isAppPickerOpen && !isPixelCleanerRunning,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            AppPickerScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                mode = appPickerMode,
                onBack = { isAppPickerOpen = false }
            )
        }

        if (isPixelCleanerRunning) {
            val barStyle = prefs.getString(OverlayConstants.KEY_BAR_STYLE, "BLACK") ?: "BLACK"
            val animFreq =
                prefs.getFloat(OverlayConstants.KEY_ANIM_FREQ, OverlayConstants.DEFAULT_FREQ)

            PixelCleanerFullScreen(
                style = barStyle,
                frequency = animFreq,
                systemBarsMode = systemBarsMode,
                onClose = {
                    isPixelCleanerRunning = false
                    if (wasServiceRunningBeforeCleaner) {
                        startOverlay(
                            context,
                            coroutineScope,
                            launchApp = false
                        ) { isServiceRunning = it }
                    }
                }
            )
        }

        if (!isPixelCleanerRunning && previewActiveEdge != null && previewPercent > 0f) {
            val topInset = contentPadding.calculateTopPadding()
            val bottomInset = contentPadding.calculateBottomPadding()
            val totalScreenHeight =
                LocalConfiguration.current.screenHeightDp.dp + topInset + bottomInset
            val totalScreenWidth = LocalConfiguration.current.screenWidthDp.dp

            val coverStatusBar = systemBarsMode == "STATUS_BAR" || systemBarsMode == "BOTH"
            val coverNavBar = systemBarsMode == "NAV_BAR" || systemBarsMode == "BOTH"

            val curTopPercent = prefs.getFloat(
                OverlayConstants.KEY_TOP_PERCENT,
                OverlayConstants.DEFAULT_TOP_PERCENT
            )
            val curBottomPercent = prefs.getFloat(
                OverlayConstants.KEY_BOTTOM_PERCENT,
                OverlayConstants.DEFAULT_BOTTOM_PERCENT
            )

            val topOffsetCalc =
                (if (coverStatusBar) 0.dp else topInset) + (totalScreenHeight * curTopPercent)
            val bottomOffsetCalc =
                (if (coverNavBar) 0.dp else bottomInset) + (totalScreenHeight * curBottomPercent)

            val barModifier = when (previewActiveEdge) {
                "TOP" -> Modifier
                    .fillMaxWidth()
                    .then(if (coverStatusBar) Modifier else Modifier.padding(top = topInset))
                    .height(totalScreenHeight * previewPercent)
                    .align(Alignment.TopCenter)

                "BOTTOM" -> Modifier
                    .fillMaxWidth()
                    .then(if (coverNavBar) Modifier else Modifier.padding(bottom = bottomInset))
                    .height(totalScreenHeight * previewPercent)
                    .align(Alignment.BottomCenter)

                "LEFT" -> Modifier
                    .padding(top = topOffsetCalc, bottom = bottomOffsetCalc)
                    .fillMaxHeight()
                    .width(totalScreenWidth * previewPercent)
                    .align(Alignment.CenterStart)

                "RIGHT" -> Modifier
                    .padding(top = topOffsetCalc, bottom = bottomOffsetCalc)
                    .fillMaxHeight()
                    .width(totalScreenWidth * previewPercent)
                    .align(Alignment.CenterEnd)

                else -> Modifier
            }

            Box(
                modifier = barModifier.graphicsLayer { alpha = previewBarAlpha }
            ) {
                when (previewBarStyle) {
                    "WHITE" -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )

                    "RGB" -> {
                        val rgbColor = Color(
                            android.graphics.Color.HSVToColor(
                                floatArrayOf(
                                    animatedHue,
                                    0.85f,
                                    0.95f
                                )
                            )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(rgbColor)
                        )
                    }

                    "NOISE" -> {
                        NoiseCanvas(modifier = Modifier.fillMaxSize(), frequency = previewAnimFreq)
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        val dialogBgColor = when (currentTheme) {
            "DARK" -> Color(0xFF141517)
            "GREY" -> Color(0xFF26282D)
            "LIGHT" -> Color(0xFFFFFFFF)
            else -> if (isSysDark) Color(0xFF141517) else Color(0xFFFFFFFF)
        }

        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = dialogBgColor,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Выйти из приложения?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Служба будет остановлена, а защитные полосы исчезнут.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { stopAndExit(context) { isServiceRunning = it } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Выйти", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(
                        "Отмена",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(22.dp)
        )
    }
}

@Composable
fun PixelCleanerFullScreen(
    style: String,
    frequency: Float,
    systemBarsMode: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(systemBarsMode) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            when (systemBarsMode) {
                "BOTH" -> insetsController.hide(WindowInsetsCompat.Type.systemBars())
                "STATUS_BAR" -> {
                    insetsController.hide(WindowInsetsCompat.Type.statusBars())
                    insetsController.show(WindowInsetsCompat.Type.navigationBars())
                }

                "NAV_BAR" -> {
                    insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                    insetsController.show(WindowInsetsCompat.Type.statusBars())
                }

                else -> {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var showHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3500)
        showHint = false
    }

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(frequency) {
        val interval = (1000f / frequency).toLong().coerceIn(7L, 200L)
        while (true) {
            delay(interval)
            tick++
        }
    }

    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val rgbCycleColors = remember {
        listOf(
            Color.Red,
            Color.Green,
            Color.Blue,
            Color.White,
            Color(0xFF00FFFF),
            Color(0xFFFF00FF),
            Color(0xFFFFFF00),
            Color.Black
        )
    }

    val currentColor = when (style) {
        "RGB" -> rgbCycleColors[(tick % rgbCycleColors.size).toInt()]
        "WHITE" -> Color.White
        "BLACK" -> Color.Black
        else -> Color.Transparent
    }

    val coverStatusBar = systemBarsMode == "STATUS_BAR" || systemBarsMode == "BOTH"
    val coverNavBar = systemBarsMode == "NAV_BAR" || systemBarsMode == "BOTH"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 600L) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                lastTapTime = now

                if (tapCount >= 3) {
                    onClose()
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!coverStatusBar) Modifier.statusBarsPadding() else Modifier)
                .then(if (!coverNavBar) Modifier.navigationBarsPadding() else Modifier)
        ) {
            if (style == "NOISE") {
                NoiseCanvas(
                    modifier = Modifier.fillMaxSize(),
                    frequency = frequency
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(currentColor)
                )
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(600)),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                Text(
                    text = "Быстро коснитесь экрана 3 раза в любом месте для выхода",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
fun MainHomeScreen(
    modifier: Modifier = Modifier,
    isRunning: Boolean,
    onOpenSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFFFFB300) else Color(0xFF00C853),
        animationSpec = tween(350),
        label = "btnColor"
    )
    val buttonTextColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF1E1E1E) else Color.White,
        animationSpec = tween(350),
        label = "btnTextColor"
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Burn Protector",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                GearIcon()
            }
        }

        Text(
            text = "УПРАВЛЕНИЕ",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "Защитные полосы",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Защита экрана от выгорания статических элементов",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = buttonTextColor
                        )
                    ) {
                        AnimatedContent(
                            targetState = isRunning,
                            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
                            label = "btnText"
                        ) { active ->
                            Text(
                                text = if (active) "Активно" else "Запуск",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "Стоп", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Как это работает:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Полосы защищают края экрана от выгорания\n• При клике на полосу она временно скроется на заданное время\n• В белом списке полосы включаются только в заданных приложениях\n• Кнопка «Стоп» убирает полосы, останавливает службу и закрывает приложение\n• Есть отдельная «Анти-Залипание» функция в настройках",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            val context = LocalContext.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️",
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Служба недоступна (Android 13+)?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Если в Спец. возможностях переключатель серый (недоступен):\n\n" +


                            "1. Настройки телефона → приложения → Burn Protector\n" +
                            "2. Нажмите ⋮ (3 точки) в верхнем правом углу\n" +
                            "3. Выберите «Разрешить ограниченные настройки»\n" +
                            "4. Подтвердите паролем (если есть) и повторно включите службу\n\n" +


                            "P.S. Заодно можете включить «всегда сверху» (поверх других окон)",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Открыть «О приложении»",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusChip(
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isGranted) {
        Color(0xFF132A1C)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }

    val contentColor = if (isGranted) {
        Color(0xFF4ADE80)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val borderColor = if (isGranted) {
        Color(0xFF22C55E).copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = !isGranted, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isGranted) "✓" else "↗",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    systemBarsMode: String,
    onSystemBarsModeChange: (String) -> Unit,
    onOpenAppPicker: (AppPickerMode) -> Unit,
    onAdjustingBar: (edge: String?, percent: Float, style: String, freq: Float, alpha: Float) -> Unit,
    onLaunchCleaner: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs =
        remember { context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE) }

    var topPercent by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_TOP_PERCENT,
                OverlayConstants.DEFAULT_TOP_PERCENT
            )
        )
    }
    var bottomPercent by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_BOTTOM_PERCENT,
                OverlayConstants.DEFAULT_BOTTOM_PERCENT
            )
        )
    }
    var sideBarsEnabled by remember {
        mutableStateOf(prefs.getBoolean(OverlayConstants.KEY_SIDE_BARS_ENABLED, false))
    }
    var leftPercent by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_LEFT_PERCENT,
                OverlayConstants.DEFAULT_LEFT_PERCENT
            )
        )
    }
    var rightPercent by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_RIGHT_PERCENT,
                OverlayConstants.DEFAULT_RIGHT_PERCENT
            )
        )
    }

    var barStyle by remember {
        mutableStateOf(prefs.getString(OverlayConstants.KEY_BAR_STYLE, "BLACK") ?: "BLACK")
    }
    var barAlpha by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_BAR_ALPHA,
                OverlayConstants.DEFAULT_ALPHA
            )
        )
    }
    var hideDuration by remember {
        mutableIntStateOf(
            prefs.getInt(
                OverlayConstants.KEY_HIDE_DURATION_SEC,
                OverlayConstants.DEFAULT_HIDE_DURATION
            )
        )
    }
    var showManualInputDialog by remember { mutableStateOf(false) }

    var animFrequency by remember {
        mutableFloatStateOf(
            prefs.getFloat(
                OverlayConstants.KEY_ANIM_FREQ,
                OverlayConstants.DEFAULT_FREQ
            )
        )
    }
    var currentSystemBarsMode by remember {
        mutableStateOf(systemBarsMode)
    }
    var executionMode by remember {
        mutableStateOf(
            prefs.getString(OverlayConstants.KEY_EXECUTION_MODE, "NON_ROOT") ?: "NON_ROOT"
        )
    }

    var autoLaunchEnabled by remember {
        mutableStateOf(prefs.getBoolean(AppSettingsKeys.KEY_AUTO_LAUNCH_ENABLED, false))
    }
    var autoLaunchAppName by remember {
        mutableStateOf(prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_NAME, "") ?: "")
    }
    var autoLaunchAppPkg by remember {
        mutableStateOf(prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE, "") ?: "")
    }

    var whitelistEnabled by remember {
        mutableStateOf(prefs.getBoolean(AppSettingsKeys.KEY_WHITELIST_ENABLED, false))
    }
    var whitelistPackages by remember {
        mutableStateOf(
            prefs.getStringSet(AppSettingsKeys.KEY_WHITELIST_PACKAGES, emptySet())?.toSet()
                ?: emptySet()
        )
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                AppSettingsKeys.KEY_WHITELIST_PACKAGES -> {
                    whitelistPackages =
                        prefs.getStringSet(AppSettingsKeys.KEY_WHITELIST_PACKAGES, emptySet())
                            ?.toSet() ?: emptySet()
                }

                AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE -> {
                    autoLaunchAppPkg =
                        prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE, "") ?: ""
                }

                AppSettingsKeys.KEY_AUTO_LAUNCH_NAME -> {
                    autoLaunchAppName =
                        prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_NAME, "") ?: ""
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(
                context
            ) else true
        )
    }
    var hasAccessibilityPermission by remember {
        mutableStateOf(
            isAccessibilityServiceEnabled(
                context,
                OverlayAccessibilityService::class.java
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
                hasAccessibilityPermission =
                    isAccessibilityServiceEnabled(context, OverlayAccessibilityService::class.java)
                whitelistPackages =
                    prefs.getStringSet(AppSettingsKeys.KEY_WHITELIST_PACKAGES, emptySet())?.toSet()
                        ?: emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var hideSystemNotif by remember {
        mutableStateOf(prefs.getBoolean(OverlayConstants.KEY_HIDE_SYSTEM_NOTIF, false))
    }

    fun saveConfig() {
        prefs.edit()
            .putFloat(OverlayConstants.KEY_TOP_PERCENT, topPercent)
            .putFloat(OverlayConstants.KEY_BOTTOM_PERCENT, bottomPercent)
            .putBoolean(OverlayConstants.KEY_SIDE_BARS_ENABLED, sideBarsEnabled)
            .putFloat(OverlayConstants.KEY_LEFT_PERCENT, leftPercent)
            .putFloat(OverlayConstants.KEY_RIGHT_PERCENT, rightPercent)
            .putString(OverlayConstants.KEY_BAR_STYLE, barStyle)
            .putFloat(OverlayConstants.KEY_BAR_ALPHA, barAlpha)
            .putInt(OverlayConstants.KEY_HIDE_DURATION_SEC, hideDuration)
            .putFloat(OverlayConstants.KEY_ANIM_FREQ, animFrequency)
            .putString(OverlayConstants.KEY_SYSTEM_BARS_MODE, currentSystemBarsMode)
            .putString(OverlayConstants.KEY_EXECUTION_MODE, executionMode)
            .putBoolean(AppSettingsKeys.KEY_AUTO_LAUNCH_ENABLED, autoLaunchEnabled)
            .putString(AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE, autoLaunchAppPkg)
            .putString(AppSettingsKeys.KEY_AUTO_LAUNCH_NAME, autoLaunchAppName)
            .putBoolean(AppSettingsKeys.KEY_WHITELIST_ENABLED, whitelistEnabled)
            .putBoolean(OverlayConstants.KEY_HIDE_SYSTEM_NOTIF, hideSystemNotif)
            .apply()

        if (OverlayConstants.isRunning) {
            NormalOverlayService.updateIfRunning()
            OverlayAccessibilityService.updateIfRunning()
        }
    }

    var autoLaunchAppIcon by remember(autoLaunchAppPkg) {
        mutableStateOf<Drawable?>(null)
    }
    LaunchedEffect(autoLaunchAppPkg) {
        if (autoLaunchAppPkg.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val icon = context.packageManager.getApplicationIcon(autoLaunchAppPkg)
                    autoLaunchAppIcon = icon
                } catch (_: Exception) {
                    autoLaunchAppIcon = null
                }
            }
        } else {
            autoLaunchAppIcon = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rgb_mini")
    val animatedHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue_mini"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                BackArrowIcon()
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "Настройки",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PermissionStatusChip(
                    label = "Окна",
                    isGranted = hasOverlayPermission,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }
                )

                PermissionStatusChip(
                    label = "Служба",
                    isGranted = hasAccessibilityPermission,
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.4f
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Раздельный размер полос",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val previewH = 190.dp
                    val previewW = 95.dp

                    val coverTopMini =
                        currentSystemBarsMode == "STATUS_BAR" || currentSystemBarsMode == "BOTH"
                    val coverBottomMini =
                        currentSystemBarsMode == "NAV_BAR" || currentSystemBarsMode == "BOTH"
                    val miniTopOffset = if (coverTopMini) 0.dp else 4.dp
                    val miniBottomOffset = if (coverBottomMini) 0.dp else 4.dp

                    val topMiniHeight = previewH * topPercent
                    val bottomMiniHeight = previewH * bottomPercent
                    val sideTopMiniOffset = miniTopOffset + topMiniHeight
                    val sideBottomMiniOffset = miniBottomOffset + bottomMiniHeight

                    Box(
                        modifier = Modifier
                            .size(width = previewW, height = previewH)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                2.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                RoundedCornerShape(16.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        val topBarMod = Modifier
                            .fillMaxWidth()
                            .padding(top = miniTopOffset)
                            .height(topMiniHeight)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = barAlpha }

                        when (barStyle) {
                            "WHITE" -> Box(modifier = topBarMod.background(Color.White))
                            "RGB" -> {
                                val rgbColor = Color(
                                    android.graphics.Color.HSVToColor(
                                        floatArrayOf(
                                            animatedHue,
                                            0.85f,
                                            0.95f
                                        )
                                    )
                                )
                                Box(modifier = topBarMod.background(rgbColor))
                            }

                            "NOISE" -> NoiseCanvas(modifier = topBarMod, frequency = animFrequency)
                            else -> Box(modifier = topBarMod.background(Color.Black))
                        }

                        val bottomBarMod = Modifier
                            .fillMaxWidth()
                            .padding(bottom = miniBottomOffset)
                            .height(bottomMiniHeight)
                            .align(Alignment.BottomCenter)
                            .graphicsLayer { alpha = barAlpha }

                        when (barStyle) {
                            "WHITE" -> Box(modifier = bottomBarMod.background(Color.White))
                            "RGB" -> {
                                val rgbColor = Color(
                                    android.graphics.Color.HSVToColor(
                                        floatArrayOf(
                                            animatedHue,
                                            0.85f,
                                            0.95f
                                        )
                                    )
                                )
                                Box(modifier = bottomBarMod.background(rgbColor))
                            }

                            "NOISE" -> NoiseCanvas(
                                modifier = bottomBarMod,
                                frequency = animFrequency
                            )

                            else -> Box(modifier = bottomBarMod.background(Color.Black))
                        }

                        if (sideBarsEnabled) {
                            val leftBarMod = Modifier
                                .padding(top = sideTopMiniOffset, bottom = sideBottomMiniOffset)
                                .fillMaxHeight()
                                .width(previewW * leftPercent)
                                .align(Alignment.CenterStart)
                                .graphicsLayer { alpha = barAlpha }

                            when (barStyle) {
                                "WHITE" -> Box(modifier = leftBarMod.background(Color.White))
                                "RGB" -> {
                                    val rgbColor = Color(
                                        android.graphics.Color.HSVToColor(
                                            floatArrayOf(
                                                animatedHue,
                                                0.85f,
                                                0.95f
                                            )
                                        )
                                    )
                                    Box(modifier = leftBarMod.background(rgbColor))
                                }

                                "NOISE" -> NoiseCanvas(
                                    modifier = leftBarMod,
                                    frequency = animFrequency
                                )

                                else -> Box(modifier = leftBarMod.background(Color.Black))
                            }

                            val rightBarMod = Modifier
                                .padding(top = sideTopMiniOffset, bottom = sideBottomMiniOffset)
                                .fillMaxHeight()
                                .width(previewW * rightPercent)
                                .align(Alignment.CenterEnd)
                                .graphicsLayer { alpha = barAlpha }

                            when (barStyle) {
                                "WHITE" -> Box(modifier = rightBarMod.background(Color.White))
                                "RGB" -> {
                                    val rgbColor = Color(
                                        android.graphics.Color.HSVToColor(
                                            floatArrayOf(
                                                animatedHue,
                                                0.85f,
                                                0.95f
                                            )
                                        )
                                    )
                                    Box(modifier = rightBarMod.background(rgbColor))
                                }

                                "NOISE" -> NoiseCanvas(
                                    modifier = rightBarMod,
                                    frequency = animFrequency
                                )

                                else -> Box(modifier = rightBarMod.background(Color.Black))
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Верхняя полоса: ${(topPercent * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        CleanSlider(
                            value = topPercent,
                            onValueChange = {
                                topPercent = it
                                onAdjustingBar("TOP", it, barStyle, animFrequency, barAlpha)
                                saveConfig()
                            },
                            onValueChangeFinished = {
                                onAdjustingBar(null, 0f, barStyle, animFrequency, barAlpha)
                            },
                            valueRange = 0.02f..0.50f
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Нижняя полоса: ${(bottomPercent * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        CleanSlider(
                            value = bottomPercent,
                            onValueChange = {
                                bottomPercent = it
                                onAdjustingBar("BOTTOM", it, barStyle, animFrequency, barAlpha)
                                saveConfig()
                            },
                            onValueChangeFinished = {
                                onAdjustingBar(null, 0f, barStyle, animFrequency, barAlpha)
                            },
                            valueRange = 0.02f..0.50f
                        )
                    }

                    AnimatedVisibility(
                        visible = sideBarsEnabled,
                        enter = expandVertically(animationSpec = tween(280)) + fadeIn(
                            animationSpec = tween(
                                200
                            )
                        ),
                        exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(
                            animationSpec = tween(
                                180
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                thickness = 1.dp
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Левая полоса: ${(leftPercent * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                CleanSlider(
                                    value = leftPercent,
                                    onValueChange = {
                                        leftPercent = it
                                        onAdjustingBar(
                                            "LEFT",
                                            it,
                                            barStyle,
                                            animFrequency,
                                            barAlpha
                                        )
                                        saveConfig()
                                    },
                                    onValueChangeFinished = {
                                        onAdjustingBar(null, 0f, barStyle, animFrequency, barAlpha)
                                    },
                                    valueRange = 0.01f..0.50f
                                )
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Правая полоса: ${(rightPercent * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                CleanSlider(
                                    value = rightPercent,
                                    onValueChange = {
                                        rightPercent = it
                                        onAdjustingBar(
                                            "RIGHT",
                                            it,
                                            barStyle,
                                            animFrequency,
                                            barAlpha
                                        )
                                        saveConfig()
                                    },
                                    onValueChangeFinished = {
                                        onAdjustingBar(null, 0f, barStyle, animFrequency, barAlpha)
                                    },
                                    valueRange = 0.01f..0.50f
                                )
                            }
                        }
                    }
                }
            }


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.4f
                    )
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    autoLaunchEnabled = !autoLaunchEnabled
                                    saveConfig()
                                }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = "Автозапуск",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Автоматически открывать выбранное приложение после запуска службы",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = autoLaunchEnabled,
                                onCheckedChange = {
                                    autoLaunchEnabled = it
                                    saveConfig()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        AnimatedVisibility(
                            visible = autoLaunchEnabled,
                            enter = expandVertically(animationSpec = tween(260)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 6.dp, bottom = 16.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenAppPicker(AppPickerMode.AUTO_LAUNCH) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (autoLaunchAppIcon != null) {
                                                AppIconImage(
                                                    drawable = autoLaunchAppIcon!!,
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(text = "📱", fontSize = 18.sp)
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = if (autoLaunchAppName.isNotEmpty()) autoLaunchAppName else "Приложение не выбрано",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (autoLaunchAppPkg.isNotEmpty()) autoLaunchAppPkg else "Нажмите для выбора приложения",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (autoLaunchAppPkg.isEmpty()) "Выбрать" else "Изменить",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        thickness = 1.dp
                    )


                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    whitelistEnabled = !whitelistEnabled
                                    saveConfig()
                                    if (whitelistEnabled) {
                                        if (executionMode == "ROOT") {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                checkAndGrantRoot(context)
                                                withContext(Dispatchers.Main) {
                                                    refreshServiceRunningState(
                                                        context,
                                                        coroutineScope
                                                    )
                                                }
                                            }
                                        } else if (!isAccessibilityServiceEnabled(
                                                context,
                                                OverlayAccessibilityService::class.java
                                            )
                                        ) {
                                            Toast.makeText(
                                                context,
                                                "Для работы белого списка включите службу «Burn Protector» в Спец. возможностях",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        } else {
                                            refreshServiceRunningState(context, coroutineScope)
                                        }
                                    } else {
                                        refreshServiceRunningState(context, coroutineScope)
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = "Белый список",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Полосы будут активны только в выбранных приложениях",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = whitelistEnabled,
                                onCheckedChange = {
                                    whitelistEnabled = it
                                    saveConfig()
                                    if (it) {
                                        if (executionMode == "ROOT") {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                checkAndGrantRoot(context)
                                                withContext(Dispatchers.Main) {
                                                    refreshServiceRunningState(
                                                        context,
                                                        coroutineScope
                                                    )
                                                }
                                            }
                                        } else if (!isAccessibilityServiceEnabled(
                                                context,
                                                OverlayAccessibilityService::class.java
                                            )
                                        ) {
                                            Toast.makeText(
                                                context,
                                                "Для работы белого списка включите службу «Burn Protector» в Спец. возможностях",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        } else {
                                            refreshServiceRunningState(context, coroutineScope)
                                        }
                                    } else {
                                        refreshServiceRunningState(context, coroutineScope)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        AnimatedVisibility(
                            visible = whitelistEnabled,
                            enter = expandVertically(animationSpec = tween(260)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp)
                                    .padding(top = 6.dp, bottom = 16.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenAppPicker(AppPickerMode.WHITELIST) },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 13.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (whitelistPackages.isEmpty()) "Приложения не выбраны" else "Выбрано приложений: ${whitelistPackages.size}",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Нажмите для настройки белого списка",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(
                                            text = "Настроить",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            SettingsDropdownCard(
                title = "Стиль полос",
                selectedValue = when (barStyle) {
                    "WHITE" -> "Белые"
                    "NOISE" -> "Шум"
                    "RGB" -> "RGB"
                    else -> "Чёрные"
                },
                options = listOf(
                    "Чёрные" to "BLACK",
                    "Белые" to "WHITE",
                    "Шум" to "NOISE",
                    "RGB" to "RGB"
                ),
                onSelect = { id ->
                    barStyle = id
                    saveConfig()
                },
                extraContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )


                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Прозрачность",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(barAlpha * 100).toInt()}%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Регулировка прозрачности защитных полос на экране",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CleanSlider(
                                    value = barAlpha,
                                    onValueChange = {
                                        barAlpha = it
                                        saveConfig()
                                    },
                                    valueRange = 0.15f..1.0f
                                )
                            }

                            AnimatedVisibility(
                                visible = barStyle == "NOISE" || barStyle == "RGB",
                                enter = expandVertically(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                    expandFrom = Alignment.Top,
                                    clip = true
                                ) + fadeIn(animationSpec = tween(200)),
                                exit = shrinkVertically(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    shrinkTowards = Alignment.Top,
                                    clip = true
                                ) + fadeOut(animationSpec = tween(180))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        thickness = 1.dp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Частота анимации",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${animFrequency.toInt()} Гц",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    CleanSlider(
                                        value = animFrequency,
                                        onValueChange = {
                                            animFrequency = it
                                            saveConfig()
                                        },
                                        valueRange = 5f..144f
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )


                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Пауза при касании",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Время скрытия полосы при клике по ней",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showManualInputDialog = true }
                                ) {
                                    Text(
                                        text = "$hideDuration сек",
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(3, 5, 10, 15)
                                presets.forEach { sec ->
                                    val isSelected = hideDuration == sec
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.7f
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                hideDuration = sec
                                                saveConfig()
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$sec с",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            val sliderMax = maxOf(30f, hideDuration.toFloat())
                            CleanSlider(
                                value = hideDuration.toFloat().coerceIn(1f, sliderMax),
                                onValueChange = {
                                    hideDuration = it.toInt().coerceIn(1, 300)
                                    saveConfig()
                                },
                                valueRange = 1f..sliderMax
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )


                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    sideBarsEnabled = !sideBarsEnabled
                                    saveConfig()
                                }
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = "Боковые полосы",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Отображать защитные полосы слева и справа",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = sideBarsEnabled,
                                onCheckedChange = {
                                    sideBarsEnabled = it
                                    saveConfig()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            )


            SettingsDropdownCard(
                title = "Перекрытие системных панелей",
                selectedValue = when (currentSystemBarsMode) {
                    "BOTH" -> "Обе панели"
                    "NAV_BAR" -> "Только кнопки"
                    "STATUS_BAR" -> "Только уведомления"
                    else -> "Не перекрывать"
                },
                options = listOf(
                    "Не перекрывать" to "NONE",
                    "Обе панели" to "BOTH",
                    "Только кнопки" to "NAV_BAR",
                    "Только уведомления" to "STATUS_BAR"
                ),
                onSelect = { id ->
                    currentSystemBarsMode = id
                    onSystemBarsModeChange(id)
                    saveConfig()

                    if (id != "NONE") {
                        if (executionMode == "ROOT") {
                            coroutineScope.launch(Dispatchers.IO) {
                                checkAndGrantRoot(context)
                                withContext(Dispatchers.Main) {
                                    refreshServiceRunningState(context, coroutineScope)
                                }
                            }
                        } else {
                            if (!isAccessibilityServiceEnabled(
                                    context,
                                    OverlayAccessibilityService::class.java
                                )
                            ) {
                                Toast.makeText(
                                    context,
                                    "Для перекрытия системных панелей включите службу «Burn Protector» в Спец. возможностях",
                                    Toast.LENGTH_LONG
                                ).show()
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } else {
                                refreshServiceRunningState(context, coroutineScope)
                            }
                        }
                    } else {
                        refreshServiceRunningState(context, coroutineScope)
                    }
                }
            )


            SettingsDropdownCard(
                title = "Тема интерфейса",
                selectedValue = when (currentTheme) {
                    "LIGHT" -> "Светлая"
                    "GREY" -> "Серая"
                    "DARK" -> "Тёмная"
                    else -> "Системная"
                },
                options = listOf(
                    "Системная" to "SYSTEM",
                    "Светлая" to "LIGHT",
                    "Серая" to "GREY",
                    "Тёмная" to "DARK"
                ),
                onSelect = { id ->
                    onThemeChange(id)
                }
            )


            SettingsDropdownCard(
                title = "Режим работы",
                selectedValue = if (executionMode == "ROOT") "Root" else "Non-root",
                options = listOf(
                    "Non-root" to "NON_ROOT",
                    "Root" to "ROOT"
                ),
                onSelect = { id ->
                    executionMode = id
                    saveConfig()

                    if (id == "ROOT") {
                        Toast.makeText(context, "Запрос Root прав...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch(Dispatchers.IO) {
                            val rootGranted = checkAndGrantRoot(context)
                            withContext(Dispatchers.Main) {
                                if (rootGranted) {
                                    Toast.makeText(
                                        context,
                                        "Root доступ получен",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshServiceRunningState(context, coroutineScope)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Не удалось получить Root доступ",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        refreshServiceRunningState(context, coroutineScope)
                    }
                }
            )


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                hideSystemNotif = !hideSystemNotif
                                saveConfig()
                                if (hideSystemNotif && OverlayConstants.isRunning) {
                                    SystemNotificationHelper.hideNotificationWithRoot(context)
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = "Скрыть системное уведомление",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Скрывать уведомление «поверх других приложений» от системы Android",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = hideSystemNotif,
                            onCheckedChange = {
                                hideSystemNotif = it
                                saveConfig()
                                if (it && OverlayConstants.isRunning) {
                                    SystemNotificationHelper.hideNotificationWithRoot(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = executionMode != "ROOT",
                        enter = expandVertically(animationSpec = tween(260)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Нужно отключить вручную (если доступно):",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                )
                                TextButton(
                                    onClick = {
                                        SystemNotificationHelper.openSystemNotificationSettings(
                                            context
                                        )
                                    }
                                ) {
                                    Text("Настройки", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }


            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Анти-залипание (Full Screen)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Полноэкранный цикл для устранения эффекта памяти OLED дисплея. Использует выбранный выше стиль (${
                            when (barStyle) {
                                "RGB" -> "RGB"
                                "NOISE" -> "Шум"
                                "WHITE" -> "Белый"
                                else -> "Чёрный"
                            }
                        }), частоту (${animFrequency.toInt()} Гц) и способ перекрытия панелей.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onLaunchCleaner,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Запустить",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showManualInputDialog) {
        var textValue by remember { mutableStateOf(hideDuration.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Время скрытия",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Укажите количество секунд от 1 до 300:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                textValue = input
                                val num = input.toIntOrNull()
                                isError = num == null || num !in 1..300
                            }
                        },
                        singleLine = true,
                        isError = isError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Text(
                            text = "Значение должно быть в диапазоне от 1 до 300 секунд",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sec = textValue.toIntOrNull()
                        if (sec != null && sec in 1..300) {
                            hideDuration = sec
                            saveConfig()
                            showManualInputDialog = false
                        } else {
                            isError = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Применить", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text(
                        "Отмена",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

@Composable
fun AppPickerScreen(
    modifier: Modifier = Modifier,
    mode: AppPickerMode,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val prefs =
        remember { context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE) }

    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val selectedSinglePkg = remember {
        mutableStateOf(prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE, "") ?: "")
    }

    val selectedWhitelist = remember {
        mutableStateListOf<String>().apply {
            addAll(
                prefs.getStringSet(AppSettingsKeys.KEY_WHITELIST_PACKAGES, emptySet()) ?: emptySet()
            )
        }
    }

    fun persistWhitelist() {
        prefs.edit()
            .putStringSet(AppSettingsKeys.KEY_WHITELIST_PACKAGES, selectedWhitelist.toSet())
            .apply()
        if (OverlayConstants.isRunning) {
            NormalOverlayService.updateIfRunning()
            OverlayAccessibilityService.updateIfRunning()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mode == AppPickerMode.WHITELIST) {
                persistWhitelist()
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedList = pm.queryIntentActivities(mainIntent, 0)
            val apps = resolvedList.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName) null
                else {
                    val label = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm)
                    AppItem(label, pkg, icon)
                }
            }.distinctBy { it.packageName }
                .sortedBy { it.name.lowercase() }

            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = remember(
        searchQuery,
        installedApps,
        selectedWhitelist.toList(),
        selectedSinglePkg.value,
        mode
    ) {
        val baseList = if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        baseList.sortedWith(
            compareByDescending<AppItem> { app ->
                if (mode == AppPickerMode.AUTO_LAUNCH) {
                    app.packageName == selectedSinglePkg.value
                } else {
                    selectedWhitelist.contains(app.packageName)
                }
            }.thenBy { it.name.lowercase() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable {
                        keyboardController?.hide()
                        if (mode == AppPickerMode.WHITELIST) {
                            persistWhitelist()
                        }
                        onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                BackArrowIcon()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (mode == AppPickerMode.AUTO_LAUNCH) "Автозапуск" else "Белый список",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (mode == AppPickerMode.AUTO_LAUNCH) "Выберите приложение для запуска" else "Выберите приложения для работы полос",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            placeholder = { Text("Поиск по названию или пакету", fontSize = 14.sp) },
            leadingIcon = { SearchIcon() },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { searchQuery = "" }
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        )

        if (mode == AppPickerMode.WHITELIST && !isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выбрано: ${selectedWhitelist.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val toAdd = filteredApps.map { it.packageName }
                                    .filter { it !in selectedWhitelist }
                                selectedWhitelist.addAll(toAdd)
                                persistWhitelist()
                            }
                    ) {
                        Text(
                            text = "Выбрать все",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (searchQuery.isNotBlank()) {
                                    val toRemove = filteredApps.map { it.packageName }.toSet()
                                    selectedWhitelist.removeAll(toRemove)
                                } else {
                                    selectedWhitelist.clear()
                                }
                                persistWhitelist()
                            }
                    ) {
                        Text(
                            text = "Снять все",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Приложения не найдены",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = if (mode == AppPickerMode.AUTO_LAUNCH) {
                        selectedSinglePkg.value == app.packageName
                    } else {
                        selectedWhitelist.contains(app.packageName)
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isChecked) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (isChecked) {
                            BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                if (mode == AppPickerMode.AUTO_LAUNCH) {
                                    selectedSinglePkg.value = app.packageName
                                    prefs.edit()
                                        .putString(
                                            AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE,
                                            app.packageName
                                        )
                                        .putString(AppSettingsKeys.KEY_AUTO_LAUNCH_NAME, app.name)
                                        .apply()
                                    keyboardController?.hide()
                                    onBack()
                                } else {
                                    if (isChecked) {
                                        selectedWhitelist.remove(app.packageName)
                                    } else {
                                        selectedWhitelist.add(app.packageName)
                                    }
                                    persistWhitelist()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            AppIconImage(
                                drawable = app.icon,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = app.packageName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (mode == AppPickerMode.AUTO_LAUNCH) {
                                RadioButton(
                                    selected = isChecked,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            } else {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mode == AppPickerMode.WHITELIST) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            persistWhitelist()
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Готово",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsDropdownCard(
    title: String,
    selectedValue: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded || menuExpanded) 180f else 0f,
        animationSpec = tween(250),
        label = "arrow"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                )

                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { menuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = selectedValue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "▼",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.rotate(arrowRotation)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        options.forEach { (label, id) ->
                            val isSelected = label == selectedValue
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onSelect(id)
                                    menuExpanded = false
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                    clip = true
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                    clip = true
                ) + fadeOut(animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        options.forEachIndexed { index, (label, id) ->
                            val isSelected = label == selectedValue
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelect(id)
                                        expanded = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.9f
                                    )
                                )
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (index < options.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }

            extraContent?.invoke(this)
        }
    }
}

fun checkAndGrantRoot(context: Context): Boolean {
    val pkg = context.packageName
    val serviceFull = ComponentName(context, OverlayAccessibilityService::class.java).flattenToString()
    val script = """
        pm grant $pkg android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null
        pm grant $pkg android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
        appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null
        appops set $pkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null
        CUR=${'$'}(settings get secure enabled_accessibility_services | tr -d '\r' | tr -d '\n')
        if [ "${'$'}CUR" = "null" ] || [ -z "${'$'}CUR" ]; then
            NEW="$serviceFull"
        else
            case ":${'$'}CUR:" in
                *":$serviceFull:"*) NEW="${'$'}CUR" ;;
                *) NEW="${'$'}CUR:$serviceFull" ;;
            esac
        fi
        settings put secure enabled_accessibility_services "${'$'}NEW"
        settings put secure accessibility_enabled 0
        settings put secure accessibility_enabled 1
    """.trimIndent()

    return try {
        val suProcess = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(suProcess.outputStream)
        os.writeBytes(script + "\n")
        os.writeBytes("exit\n")
        os.flush()
        val code = suProcess.waitFor()
        code == 0
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun refreshServiceRunningState(context: Context, scope: CoroutineScope) {
    if (!OverlayConstants.isRunning) return

    val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val systemBarsMode = prefs.getString(
        OverlayConstants.KEY_SYSTEM_BARS_MODE,
        OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
    ) ?: OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
    val executionMode =
        prefs.getString(OverlayConstants.KEY_EXECUTION_MODE, "NON_ROOT") ?: "NON_ROOT"
    val isWhitelistEnabled = prefs.getBoolean(AppSettingsKeys.KEY_WHITELIST_ENABLED, false)

    val needsAccessibility =
        (executionMode == "ROOT") || (systemBarsMode != "NONE") || isWhitelistEnabled

    if (needsAccessibility) {
        if (OverlayAccessibilityService.instance != null) {
            NormalOverlayService.hideBars()
            OverlayConstants.isRunning = true
            OverlayAccessibilityService.startOrUpdateBars()
        } else if (isAccessibilityServiceEnabled(
                context,
                OverlayAccessibilityService::class.java
            )
        ) {
            NormalOverlayService.hideBars()
            OverlayConstants.isRunning = true
            OverlayAccessibilityService.startOrUpdateBars()
        } else if (executionMode == "ROOT") {
            scope.launch(Dispatchers.IO) {
                checkAndGrantRoot(context)
                var retries = 0
                while (OverlayAccessibilityService.instance == null && retries < 25) {
                    delay(100)
                    retries++
                }
                withContext(Dispatchers.Main) {
                    NormalOverlayService.hideBars()
                    OverlayConstants.isRunning = true
                    OverlayAccessibilityService.startOrUpdateBars()
                }
            }
        } else {
            OverlayAccessibilityService.hideBars()
            OverlayConstants.isRunning = true
            startNormalService(context)
        }
    } else {
        if (OverlayAccessibilityService.instance != null) {
            OverlayAccessibilityService.startOrUpdateBars()
        } else {
            NormalOverlayService.startOrUpdateBars()
        }
    }
}

private fun startOverlay(
    context: Context,
    scope: CoroutineScope,
    launchApp: Boolean = true,
    onStateChange: (Boolean) -> Unit
) {
    val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val systemBarsMode = prefs.getString(
        OverlayConstants.KEY_SYSTEM_BARS_MODE,
        OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
    ) ?: OverlayConstants.DEFAULT_SYSTEM_BARS_MODE
    val executionMode =
        prefs.getString(OverlayConstants.KEY_EXECUTION_MODE, "NON_ROOT") ?: "NON_ROOT"
    val isWhitelistEnabled = prefs.getBoolean(AppSettingsKeys.KEY_WHITELIST_ENABLED, false)

    if (executionMode == "ROOT") {
        Toast.makeText(context, "Запуск через Root", Toast.LENGTH_SHORT).show()
        OverlayConstants.isRunning = true
        onStateChange(true)
        scope.launch(Dispatchers.IO) {
            checkAndGrantRoot(context)
            var retries = 0
            while (OverlayAccessibilityService.instance == null && retries < 35) {
                delay(100)
                retries++
            }

            withContext(Dispatchers.Main) {
                if (OverlayAccessibilityService.instance != null || isAccessibilityServiceEnabled(
                        context,
                        OverlayAccessibilityService::class.java
                    )
                ) {
                    NormalOverlayService.hideBars()
                    OverlayAccessibilityService.startOrUpdateBars()
                    Toast.makeText(context, "Служба запущена (Root)", Toast.LENGTH_SHORT).show()
                } else {
                    startNormalService(context)
                    Toast.makeText(context, "Служба запущена", Toast.LENGTH_SHORT).show()
                }
                if (launchApp) {
                    checkAndLaunchSelectedApp(context)
                }
                SystemNotificationHelper.hideNotificationWithRoot(context)
            }
        }
        return
    }

    val needsAccessibility = (systemBarsMode != "NONE") || isWhitelistEnabled

    if (needsAccessibility) {
        if (!isAccessibilityServiceEnabled(context, OverlayAccessibilityService::class.java)) {
            val message = if (isWhitelistEnabled) {
                "Для работы белого списка включите службу «Burn Protector» в Спец. возможностях"
            } else {
                "Включите службу «Burn Protector» в Настройках доступности"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        OverlayConstants.isRunning = true
        onStateChange(true)
        NormalOverlayService.hideBars()
        OverlayAccessibilityService.startOrUpdateBars()
        Toast.makeText(context, "Служба запущена", Toast.LENGTH_SHORT).show()
        if (launchApp) {
            checkAndLaunchSelectedApp(context)
        }
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "Включите разрешение «Всегда сверху»", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            return
        }
        OverlayConstants.isRunning = true
        onStateChange(true)
        OverlayAccessibilityService.hideBars()
        startNormalService(context)
        Toast.makeText(context, "Служба запущена", Toast.LENGTH_SHORT).show()
        if (launchApp) {
            checkAndLaunchSelectedApp(context)
        }
    }
}

private fun startNormalService(context: Context) {
    val intent = Intent(context, NormalOverlayService::class.java).apply {
        action = OverlayConstants.ACTION_UPDATE_CONFIG
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun isAccessibilityServiceEnabled(
    context: Context,
    service: Class<out AccessibilityService>
): Boolean {
    val expectedComponentName = ComponentName(context, service)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
        if (enabledComponent != null && enabledComponent == expectedComponentName) {
            return true
        }
    }
    return false
}

private fun stopAndExit(context: Context, onStateChange: (Boolean) -> Unit) {
    OverlayConstants.isRunning = false
    onStateChange(false)
    NormalOverlayService.hideBars()
    OverlayAccessibilityService.hideBars()
    Toast.makeText(context, "Служба остановлена", Toast.LENGTH_SHORT).show()
    (context as? Activity)?.finishAffinity()
}

object SystemNotificationHelper {
    fun hideNotificationWithRoot(context: Context) {
        val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(OverlayConstants.KEY_HIDE_SYSTEM_NOTIF, false)
        if (!isEnabled) return

        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            try {
                val pkg = context.packageName
                val script = """
                    cmd notification list 2>/dev/null | grep "$pkg" | while read -r line; do
                        clean_key=${'$'}(echo "${'$'}line" | sed 's/^[ \t]*
                        if [ -n "${'$'}clean_key" ]; then
                            cmd notification snooze --for 31536000000000 "${'$'}clean_key"
                        fi
                    done
                """.trimIndent()

                val suProcess = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(suProcess.outputStream)
                os.writeBytes(script + "\n")
                os.writeBytes("exit\n")
                os.flush()
                suProcess.waitFor()
            } catch (_: Exception) {
            }
        }
    }

    fun openSystemNotificationSettings(context: Context) {
        val intents = mutableListOf<Intent>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intents.add(Intent(Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS))
        }
        intents.add(Intent("android.settings.NOTIFICATION_SETTINGS"))
        intents.add(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
        intents.add(Intent(Settings.ACTION_SETTINGS))

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(
                    context,
                    "Система Android → Уведомления → Поверх других приложений",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (_: Exception) {
            }
        }
    }
}

private fun checkAndLaunchSelectedApp(context: Context) {
    val prefs = context.getSharedPreferences(OverlayConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val autoLaunch = prefs.getBoolean(AppSettingsKeys.KEY_AUTO_LAUNCH_ENABLED, false)
    if (!autoLaunch) return

    val selectedPkg = prefs.getString(AppSettingsKeys.KEY_AUTO_LAUNCH_PACKAGE, "") ?: ""
    if (selectedPkg.isBlank()) {
        return
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(selectedPkg)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    } else {
        Toast.makeText(context, "Приложение для автозапуска не найдено", Toast.LENGTH_SHORT).show()
    }
}