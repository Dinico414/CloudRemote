package com.xenonware.cloudremote.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onClick() {
        super.onClick()
        val handler = Handler(Looper.getMainLooper())
        
        if (isCurtainActive) {
            handler.post {
                SwipeableCurtainManager.hideCurtain(applicationContext)
            }
        } else {
            handler.post {
                SwipeableCurtainManager.showCurtain(applicationContext)
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
                TileService.requestListeningState(
                    context,
                    ComponentName(context, CurtainTileService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}