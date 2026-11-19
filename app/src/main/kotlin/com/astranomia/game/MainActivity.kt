package com.astranomia.game

import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.astranomia.game.view.AstranomiaGameView

class MainActivity : AppCompatActivity() {

    private var gameView: AstranomiaGameView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety handler: prevent background thread crashes from terminating the app
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("Astranomia", "Uncaught exception on thread: ${thread.name}", throwable)
            if (thread == Looper.getMainLooper().thread) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemUI()

            gameView = AstranomiaGameView(this)
            setContentView(gameView)
        } catch (t: Throwable) {
            Log.e("MainActivity", "Error in onCreate", t)
        }
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        } catch (t: Throwable) {
            Log.e("MainActivity", "hideSystemUI error", t)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            hideSystemUI()
            gameView?.onResume()
        } catch (t: Throwable) {
            Log.e("MainActivity", "onResume error", t)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            gameView?.onPause()
        } catch (t: Throwable) {
            Log.e("MainActivity", "onPause error", t)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            gameView?.onDestroy()
        } catch (t: Throwable) {
            Log.e("MainActivity", "onDestroy error", t)
        }
    }
}
