package com.xenonware.cloudremote

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

class CurtainTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onClick() {
        super.onClick()
        if (isCurtainActive) {
            val closeIntent = Intent(SwipeableCurtainActivity.ACTION_CLOSE_CURTAIN)
            sendBroadcast(closeIntent)
        } else {
            val intent = Intent(this, SwipeableCurtainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        }
        isCurtainActive = !isCurtainActive
        updateTile()
    }

    private fun updateTile() {
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
    }
}
