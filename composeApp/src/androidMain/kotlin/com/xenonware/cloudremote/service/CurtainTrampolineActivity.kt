package com.xenonware.cloudremote.service

import android.app.Activity
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
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Extend into the cutout/status bar area
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        if (SwipeableCurtainManager.isCurtainVisible) {
            SwipeableCurtainManager.hideCurtain()
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