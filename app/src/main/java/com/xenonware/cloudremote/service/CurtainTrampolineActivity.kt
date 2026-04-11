package com.xenonware.cloudremote.service

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.xenonware.cloudremote.helper.SwipeableCurtainManager

class CurtainTrampolineActivity : Activity() {
    
    companion object {
        private var instance: CurtainTrampolineActivity? = null
        
        fun finishInstance() {
            instance?.finish()
            instance = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        
        // Ensure this activity shows over the lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or 
                           WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                           WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        
        // Extend into the cutout/status bar area
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (SwipeableCurtainManager.isCurtainVisible) {
            SwipeableCurtainManager.hideCurtain(this)
            finish()
        } else {
            // Show the curtain. By passing this activity context, 
            // the manager can use its windowing properties.
            SwipeableCurtainManager.showCurtain(this)
        }
        
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}