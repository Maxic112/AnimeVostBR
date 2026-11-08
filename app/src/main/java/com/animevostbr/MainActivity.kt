package com.animevostbr

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import com.animevostbr.core.Repo
import com.animevostbr.ui.AppNav
import com.animevostbr.ui.AppTheme
import com.animevostbr.ui.UiState

class MainActivity : ComponentActivity() {

    /** Title to open from a new-episode notification. */
    private val openTitleId = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        UiState.tvMode = isTv
        UiState.themeMode = Repo.prefs.themeMode

        // Notification permission (Android 13+); not requested on TV
        if (!isTv && Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        // After recreation the intent has already been handled — do not reopen the title
        if (savedInstanceState == null) openTitleId.value = titleIdFrom(intent)

        setContent {
            AppTheme {
                AppNav(
                    isTv = isTv,
                    openTitleId = openTitleId.value,
                    onTitleOpened = { openTitleId.value = null }
                )
            }
        }
    }

    // launchMode=singleTask: notification intents arrive here while the app is running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        titleIdFrom(intent)?.let { openTitleId.value = it }
    }

    /** The player is open and playing on a phone: leaving the app shrinks it to a small window. */
    private var pipWanted = false

    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .apply { if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(pipWanted) }
        .build()

    /** Called by the player screen when it opens, closes, starts or stops playing. */
    fun setPlayerPip(active: Boolean, playing: Boolean) {
        val supported = !UiState.tvMode && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        pipWanted = supported && active && playing
        if (supported) runCatching { setPictureInPictureParams(pipParams()) }
    }

    // Android 12+ enters picture-in-picture by itself (setAutoEnterEnabled)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipWanted && Build.VERSION.SDK_INT < 31) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        UiState.inPip = isInPictureInPictureMode
    }

    private fun titleIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_TITLE_ID, -1)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_TITLE_ID = "title_id"
    }
}
