package com.xenonware.cloudremote.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.xenonware.cloudremote.helper.SwipeableCurtainManager

class CurtainTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        instance = this
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        if (instance == this) {
            instance = null
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()

        if (isLocked) {
            if (SwipeableCurtainManager.isCurtainVisible) {
                SwipeableCurtainManager.hideCurtain(this)
            } else {
                val intent = Intent(this, CurtainTrampolineActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        } else {
            if (SwipeableCurtainManager.isCurtainVisible) {
                SwipeableCurtainManager.hideCurtain(this)
            } else {
                SwipeableCurtainManager.showCurtain(applicationContext)
            }

            // Collapse the quick settings shade (deprecated and restricted in Android 12+)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeIntent)
            }
        }
    }

    fun updateTileState() {
        val tile = qsTile ?: return
        if (isCurtainActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Curtain On"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Curtain Off"
        }
        tile.updateTile()
    }

    companion object {
        var isCurtainActive = false
        @SuppressLint("StaticFieldLeak")
        var instance: CurtainTileService? = null

        fun requestTileUpdate(context: Context) {
            try {
                instance?.updateTileState()
                requestListeningState(
                    context,
                    ComponentName(context, CurtainTileService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}