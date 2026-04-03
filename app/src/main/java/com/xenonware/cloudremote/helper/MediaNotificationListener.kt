package com.xenonware.cloudremote.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.scale

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_MEDIA_UPDATE = "com.xenonware.cloudremote.MEDIA_UPDATE"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM_ART = "extra_album_art"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_CUSTOM_ACTION_1_TITLE = "extra_custom_action_1_title"
        const val EXTRA_CUSTOM_ACTION_1_ACTION = "extra_custom_action_1_action"
        const val EXTRA_CUSTOM_ACTION_2_TITLE = "extra_custom_action_2_title"
        const val EXTRA_CUSTOM_ACTION_2_ACTION = "extra_custom_action_2_action"
        private const val MAX_ALBUM_ART_SIZE = 192
    }

    private var activeMediaController: MediaController? = null
    private var lastSentIntent: Intent? = null
    private var lastBitmapRef: Bitmap? = null
    private var lastEncodedArt: String = ""
    private lateinit var mediaSessionManager: MediaSessionManager

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleControllersChanged(controllers)
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            updateMediaInfo(activeMediaController?.metadata, state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            updateMediaInfo(metadata, activeMediaController?.playbackState)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val componentName = ComponentName(this, this.javaClass)
        mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
        findActiveMediaController()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.notification?.category == "transport") {
            findActiveMediaController()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.notification?.category == "transport") {
            findActiveMediaController()
        }
    }

    private fun handleControllersChanged(controllers: List<MediaController>?) {
        if (!controllers.isNullOrEmpty()) {
            val newController = controllers[0]
            if (newController != activeMediaController) {
                activeMediaController?.unregisterCallback(mediaControllerCallback)
                activeMediaController = newController
                activeMediaController?.registerCallback(mediaControllerCallback)
                updateMediaInfo(activeMediaController?.metadata, activeMediaController?.playbackState)
            } else {
                updateMediaInfo(activeMediaController?.metadata, activeMediaController?.playbackState)
            }
        } else {
            activeMediaController?.unregisterCallback(mediaControllerCallback)
            activeMediaController = null
            updateMediaInfo(null, null)
        }
    }

    private fun findActiveMediaController() {
        val componentName = ComponentName(this, this.javaClass)
        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            handleControllersChanged(controllers)
        } catch (_: SecurityException) {
        }
    }

    private fun updateMediaInfo(metadata: MediaMetadata?, playbackState: PlaybackState?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            
        val albumArtString = if (albumArtBitmap != null) {
            if (albumArtBitmap == lastBitmapRef) {
                lastEncodedArt
            } else {
                lastBitmapRef = albumArtBitmap
                lastEncodedArt = encodeBitmap(albumArtBitmap)
                lastEncodedArt
            }
        } else {
            lastBitmapRef = null
            lastEncodedArt = ""
            ""
        }

        val customActions = playbackState?.customActions ?: emptyList()
        val customAction1 = customActions.getOrNull(0)
        val customAction2 = customActions.getOrNull(1)

        val newIntent = Intent(ACTION_MEDIA_UPDATE).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_ALBUM_ART, albumArtString)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_CUSTOM_ACTION_1_TITLE, customAction1?.name?.toString() ?: "")
            putExtra(EXTRA_CUSTOM_ACTION_1_ACTION, customAction1?.action ?: "")
            putExtra(EXTRA_CUSTOM_ACTION_2_TITLE, customAction2?.name?.toString() ?: "")
            putExtra(EXTRA_CUSTOM_ACTION_2_ACTION, customAction2?.action ?: "")
        }

        if (isSameAsLast(newIntent)) return
        
        lastSentIntent = newIntent
        LocalBroadcastManager.getInstance(this).sendBroadcast(newIntent)
    }

    private fun isSameAsLast(newIntent: Intent): Boolean {
        val last = lastSentIntent ?: return false
        return newIntent.getStringExtra(EXTRA_TITLE) == last.getStringExtra(EXTRA_TITLE) &&
                newIntent.getStringExtra(EXTRA_ARTIST) == last.getStringExtra(EXTRA_ARTIST) &&
                newIntent.getStringExtra(EXTRA_ALBUM_ART) == last.getStringExtra(EXTRA_ALBUM_ART) &&
                newIntent.getBooleanExtra(EXTRA_IS_PLAYING, false) == last.getBooleanExtra(EXTRA_IS_PLAYING, false) &&
                newIntent.getStringExtra(EXTRA_CUSTOM_ACTION_1_TITLE) == last.getStringExtra(EXTRA_CUSTOM_ACTION_1_TITLE) &&
                newIntent.getStringExtra(EXTRA_CUSTOM_ACTION_1_ACTION) == last.getStringExtra(EXTRA_CUSTOM_ACTION_1_ACTION) &&
                newIntent.getStringExtra(EXTRA_CUSTOM_ACTION_2_TITLE) == last.getStringExtra(EXTRA_CUSTOM_ACTION_2_TITLE) &&
                newIntent.getStringExtra(EXTRA_CUSTOM_ACTION_2_ACTION) == last.getStringExtra(EXTRA_CUSTOM_ACTION_2_ACTION)
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val resizedBitmap = resizeBitmap(bitmap)
        val outputStream = ByteArrayOutputStream()
        
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        
        val quality = if (isNetworkBad()) 30 else 90
        resizedBitmap.compress(format, quality, outputStream)

        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        var width = bitmap.width
        var height = bitmap.height
        if (width <= MAX_ALBUM_ART_SIZE && height <= MAX_ALBUM_ART_SIZE) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        if (ratio > 1) {
            width = MAX_ALBUM_ART_SIZE
            height = (width / ratio).roundToInt()
        } else {
            height = MAX_ALBUM_ART_SIZE
            width = (height * ratio).roundToInt()
        }
        return bitmap.scale(width, height)
    }

    private fun isNetworkBad(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        
        val isMetered = cm.isActiveNetworkMetered
        val bandwidth = caps.linkDownstreamBandwidthKbps
        
        return isMetered || (bandwidth > 0 && bandwidth < 800)
    }
}
