package com.animevostbr

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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

    private fun titleIdFrom(intent: Intent?): Int? =
        intent?.getIntExtra(EXTRA_TITLE_ID, -1)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_TITLE_ID = "title_id"
    }
}
