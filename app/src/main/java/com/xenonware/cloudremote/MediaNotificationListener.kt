package com.xenonware.cloudremote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream

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
    }

    private var activeMediaController: MediaController? = null
    private var lastAlbumArt: String? = null
    private lateinit var mediaSessionManager: MediaSessionManager

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
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
        findActiveMediaController()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.notification?.category == "transport") {
            findActiveMediaController()
        }
    }

    private fun findActiveMediaController() {
        val componentName = ComponentName(this, this.javaClass)
        val controllers = mediaSessionManager.getActiveSessions(componentName)
        if (controllers.isNotEmpty()) {
            val newController = controllers[0]
            if (newController != activeMediaController) {
                activeMediaController?.unregisterCallback(mediaControllerCallback)
                activeMediaController = newController
                activeMediaController?.registerCallback(mediaControllerCallback)
                updateMediaInfo(activeMediaController?.metadata, activeMediaController?.playbackState)
            }
        }
    }

    private fun updateMediaInfo(metadata: MediaMetadata?, playbackState: PlaybackState?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

        val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        val albumArtString = albumArtBitmap?.let { encodeBitmap(it) } ?: ""

        if (albumArtString != lastAlbumArt) {
            lastAlbumArt = albumArtString
        }

        val customActions = playbackState?.customActions ?: emptyList()
        val customAction1 = customActions.getOrNull(0)
        val customAction2 = customActions.getOrNull(1)

        val intent = Intent(ACTION_MEDIA_UPDATE).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_ARTIST, artist)
            putExtra(EXTRA_ALBUM_ART, lastAlbumArt)
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_CUSTOM_ACTION_1_TITLE, customAction1?.name.toString())
            putExtra(EXTRA_CUSTOM_ACTION_1_ACTION, customAction1?.action)
            putExtra(EXTRA_CUSTOM_ACTION_2_TITLE, customAction2?.name.toString())
            putExtra(EXTRA_CUSTOM_ACTION_2_ACTION, customAction2?.action)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream) // Compress to 30% quality
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}