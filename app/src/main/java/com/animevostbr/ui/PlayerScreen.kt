package com.animevostbr.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.animevostbr.playback.GesturePlayerView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.animevostbr.MainActivity
import com.animevostbr.core.Quality
import com.animevostbr.core.Repo
import com.animevostbr.playback.PlaybackRules
import com.animevostbr.playback.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val view = LocalView.current

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = (view.context as? Activity)?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, view) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            insets?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Leaving the app (Home, another app, a closed picture-in-picture window) pauses the episode.
    // A picture-in-picture window only pauses the activity, so playback goes on there.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.player.pause()
                vm.saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = view.context as? MainActivity
    val inPip = UiState.inPip
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) { activity?.setPlayerPip(active = true, playing = playing) }
    DisposableEffect(Unit) { onDispose { activity?.setPlayerPip(active = false, playing = false) } }

    // Position for the next-episode countdown and the "skip opening" button
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vm) {
        while (isActive) {
            positionMs = vm.player.currentPosition
            durationMs = vm.player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    val countdown = PlaybackRules.countdown(positionMs, durationMs, vm.hasNext)
    val offerSkip = PlaybackRules.offerSkipIntro(positionMs, durationMs)

    var qualityMenu by remember { mutableStateOf(false) }

    // The top bar shows and hides together with the player controller
    var controlsVisible by remember { mutableStateOf(true) }

    var seekHint by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var seekHintTick by remember { mutableStateOf(0) }
    LaunchedEffect(seekHintTick) {
        if (seekHint != null) {
            delay(700)
            seekHint = null
        }
    }

    // Android TV remote: the player surface holds focus and keys are handled in handleTvKey.
    // PlayerView buttons never take focus on TV: focus cannot get back from an AndroidView to Compose.
    val isTv = UiState.tvMode
    var playerView by remember { mutableStateOf<GesturePlayerView?>(null) }
    val surfaceFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var topBarFocused by remember { mutableStateOf(false) }
    var wantTopBarFocus by remember { mutableStateOf(false) }

    // Picture mode: pinch on phones, top bar button everywhere; kept between episodes and sessions
    var resizeMode by remember { mutableStateOf(PictureMode.of(Repo.prefs.videoResizeMode)) }
    fun applyMode(mode: PictureMode) {
        if (mode != resizeMode) {
            resizeMode = mode
            Repo.prefs.videoResizeMode = mode.resizeMode
        }
        vm.showNotice(mode.hint)
    }
    LaunchedEffect(resizeMode, playerView) { playerView?.resizeMode = resizeMode.resizeMode }
    // the small window shows the picture only
    LaunchedEffect(inPip, playerView) {
        playerView?.useController = !inPip
        if (inPip) playerView?.hideController()
    }
    // after the quality menu closes, the remote controls the video again
    var qualityMenuUsed by remember { mutableStateOf(false) }
    LaunchedEffect(qualityMenu) {
        if (qualityMenu) qualityMenuUsed = true
        else if (qualityMenuUsed && isTv) runCatching { surfaceFocus.requestFocus() }
    }
    val wideBar = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(Unit) { if (isTv) runCatching { surfaceFocus.requestFocus() } }
    // Up shows the bar; focus it only once it is on screen
    LaunchedEffect(wantTopBarFocus, controlsVisible) {
        if (wantTopBarFocus && controlsVisible) {
            runCatching { topBarFocus.requestFocus() }
            wantTopBarFocus = false
        }
    }
    LaunchedEffect(vm.error) {
        if (!isTv) return@LaunchedEffect
        runCatching { if (vm.error != null) retryFocus.requestFocus() else surfaceFocus.requestFocus() }
    }
    // Back on the remote first leaves the bar or hides the controls, then exits
    BackHandler(enabled = isTv && (topBarFocused || controlsVisible) && vm.error == null) {
        if (topBarFocused) surfaceFocus.requestFocus() else playerView?.hideController()
    }

    fun handleTvKey(event: KeyEvent): Boolean {
        val pv = playerView ?: return false
        if (topBarFocused) {
            // arrows move within the bar; Down returns to the video
            if (event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown) {
                surfaceFocus.requestFocus()
                return true
            }
            return false
        }
        val down = event.type == KeyEventType.KeyDown
        val repeat = event.nativeKeyEvent.repeatCount
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                if (down && repeat == 0) {
                    vm.player.playWhenReady = !vm.player.playWhenReady
                    pv.showController()
                }
            }
            Key.MediaPlay -> if (down) { vm.player.play(); pv.showController() }
            Key.MediaPause -> if (down) { vm.player.pause(); pv.showController() }
            Key.DirectionLeft, Key.DirectionRight, Key.MediaRewind, Key.MediaFastForward -> {
                // when held, step on every third repeat, otherwise it seeks too fast
                if (down && repeat % 3 == 0) {
                    val forward = event.key == Key.DirectionRight || event.key == Key.MediaFastForward
                    pv.seekStep(if (forward) 1 else -1)
                    pv.showController()
                }
            }
            Key.DirectionUp -> if (down) {
                pv.showController()
                wantTopBarFocus = true
            }
            Key.DirectionDown -> if (down) pv.showController()
            Key.MediaNext, Key.ChannelUp -> if (down && repeat == 0) vm.next()
            Key.MediaPrevious, Key.ChannelDown -> if (down && repeat == 0) vm.prev()
            else -> return false
        }
        return true
    }

    DisposableEffect(vm) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    vm.saveProgress()
                    vm.next()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        playing = vm.player.isPlaying
        vm.player.addListener(listener)
        onDispose { vm.player.removeListener(listener) }
    }

    LaunchedEffect(vm) {
        while (isActive) {
            delay(5000)
            vm.saveProgress()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { isTv && handleTvKey(it) }
    ) {
        // Focusable player surface that receives remote keys
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(surfaceFocus)
                .focusable()
        )
        AndroidView(
            factory = { ctx ->
                GesturePlayerView(
                    ctx,
                    onSeekStep = { dir, totalMs ->
                        seekHint = dir to totalMs
                        seekHintTick++
                    },
                    onPinch = { zoomIn -> applyMode(if (zoomIn) PictureMode.ZOOM else PictureMode.FIT) }
                ).apply {
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == android.view.View.VISIBLE
                    })
                    player = vm.player
                    if (isTv) {
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                    playerView = this
                    // the listener reports changes only — read the initial state
                    post { controlsVisible = isControllerFullyVisible }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (vm.loadingLink) {
            CircularProgressIndicator(color = AccentColor, modifier = Modifier.align(Alignment.Center))
        }

        if (offerSkip && !wideBar && controlsVisible && !inPip) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { vm.skipIntro() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.FastForward, contentDescription = null, tint = Color.White)
                Text("Пропустить 90 с", color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (countdown != null && !inPip) {
            Text(
                text = "Следующая серия через $countdown",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { vm.next() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        if (!inPip) vm.notice?.let { message ->
            LaunchedEffect(message) {
                delay(3000)
                vm.clearNotice()
            }
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        seekHint?.let { (dir, totalMs) ->
            SeekHint(
                forward = dir > 0,
                seconds = totalMs / 1000,
                modifier = Modifier.align(if (dir > 0) Alignment.CenterEnd else Alignment.CenterStart)
            )
        }

        AnimatedVisibility(
            visible = !inPip && (controlsVisible || topBarFocused || qualityMenu || vm.error != null),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp)
                    .onFocusChanged { topBarFocused = it.hasFocus }
            ) {
                IconButton(
                    modifier = Modifier
                        .focusRequester(topBarFocus)
                        .focusBorder(CircleShape),
                    onClick = onBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Text(
                    text = vm.currentLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(modifier = Modifier.focusBorder(), onClick = { if (vm.hasPrev) vm.prev() }, enabled = vm.hasPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Предыдущая серия", tint = Color.White)
                }
                TextButton(modifier = Modifier.focusBorder(), onClick = { applyMode(resizeMode.next()) }) {
                    Icon(Icons.Filled.AspectRatio, contentDescription = "Формат: ${resizeMode.label}", tint = Color.White)
                    // icon only in portrait: the bar has no room for the label next to the title
                    if (wideBar) Text(resizeMode.label, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                }
                // in portrait the bar has no room: the button sits above the seek bar instead
                if (offerSkip && wideBar) {
                    TextButton(modifier = Modifier.focusBorder(), onClick = { vm.skipIntro() }) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Пропустить заставку: вперёд на 90 секунд", tint = Color.White)
                        Text("90 с", color = Color.White, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Box {
                    TextButton(modifier = Modifier.focusBorder(), onClick = { qualityMenu = true }) {
                        // the word "Качество" only where the bar has room for it
                        Text(if (wideBar) "Качество: ${vm.quality.label}" else vm.quality.label, color = Color.White)
                    }
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                        for (q in Quality.entries) {
                            DropdownMenuItem(
                                text = { Text(if (q == vm.quality) "${q.label}  ✓" else q.label) },
                                modifier = Modifier.focusBorder(),
                                onClick = {
                                    qualityMenu = false
                                    if (q != vm.quality) vm.selectQuality(q)
                                }
                            )
                        }
                    }
                }
                TextButton(modifier = Modifier.focusBorder(), onClick = { if (vm.hasNext) vm.next() }, enabled = vm.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Следующая серия", tint = Color.White)
                }
            }
        }

        vm.error?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        TextButton(modifier = Modifier.focusBorder(), onClick = onBack) { Text("Вернуться", color = AccentColor) }
                        TextButton(
                            modifier = Modifier
                                .focusRequester(retryFocus)
                                .focusBorder(),
                            onClick = { vm.playEpisode(vm.currentEp) }
                        ) {
                            Text("Повторить", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** How the video fills the screen when its aspect ratio differs from the display. */
private enum class PictureMode(val resizeMode: Int, val label: String, val hint: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Исходный", "Исходный формат"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Заполнить", "Заполнить экран — края кадра обрезаны"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Растянуть", "Растянуть на весь экран");

    fun next(): PictureMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(resizeMode: Int): PictureMode = entries.firstOrNull { it.resizeMode == resizeMode } ?: FIT
    }
}

/** YouTube-style translucent bubble at the screen edge. */
@Composable
private fun SeekHint(forward: Boolean, seconds: Long, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .size(110.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
    ) {
        Text(
            text = if (forward) "$seconds с ››" else "‹‹ $seconds с",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
