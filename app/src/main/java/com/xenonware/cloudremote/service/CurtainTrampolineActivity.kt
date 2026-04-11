package com.xenonware.cloudremote.service

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import com.xenonware.cloudremote.helper.SwipeableCurtainManager

class CurtainTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Allow this activity to show over the lockscreen to collapse the shade
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        
        if (SwipeableCurtainManager.isCurtainVisible) {
            SwipeableCurtainManager.hideCurtain(this)
        } else {
            SwipeableCurtainManager.showCurtain(this)
        }
        
        finish()
        overridePendingTransition(0, 0)
    }
}