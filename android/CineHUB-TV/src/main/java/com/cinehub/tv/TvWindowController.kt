package com.cinehub.tv

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Primeira camada nativa Kotlin do CineHUB TV.
 *
 * Centraliza o comportamento imersivo da janela em Android TV/TV Box.
 * A interface existente continua funcionando durante a migração gradual
 * das telas para componentes nativos.
 */
object TvWindowController {
    @JvmStatic
    fun enterImmersiveMode(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
        )

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    @JvmStatic
    fun showSystemBars(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).show(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.navigationBars()
        )
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
