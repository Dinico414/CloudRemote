package com.xenonware.cloudremote.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.unit.IntSize
import androidx.core.content.edit
import com.xenonware.cloudremote.viewmodel.ThemeSetting
import kotlin.math.max
import kotlin.math.min

class SharedPreferenceManager(private val context: Context) {

    private val prefsName = "AppPrefs"
    private val isUserLoggedInKey = "is_user_logged_in"
    private val themeKey = "app_theme"
    private val blackedOutModeKey = "blacked_out_mode_enabled"
    private val coverThemeEnabledKey = "cover_theme_enabled"
    private val coverDisplayDimension1Key = "cover_display_dimension_1"
    private val coverDisplayDimension2Key = "cover_display_dimension_2"
    private val languageTagKey = "app_language_tag"
    private val developerModeKey = "developer_mode_enabled"
    private val inputReceiverEnabledKey = "input_receiver_enabled"

    internal val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    var isUserLoggedIn: Boolean
        get() = sharedPreferences.getBoolean(isUserLoggedInKey, false)
        set(value) = sharedPreferences.edit { putBoolean(isUserLoggedInKey, value) }

    val localDeviceId: String
        @SuppressLint("HardwareIds")
        get() {
            var id = sharedPreferences.getString("local_device_id", null)
            if (id == null) {
                id = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID
                ) ?: "unknown_device"
                sharedPreferences.edit { putString("local_device_id", id) }
            }
            return id
        }

    var theme: Int
        get() = sharedPreferences.getInt(themeKey, ThemeSetting.SYSTEM.ordinal)
        set(value) = sharedPreferences.edit { putInt(themeKey, value) }

    val themeFlag: Array<Int> = arrayOf(
        AppCompatDelegate.MODE_NIGHT_NO,
        AppCompatDelegate.MODE_NIGHT_YES,
        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    var blackedOutModeEnabled: Boolean
        get() = sharedPreferences.getBoolean(blackedOutModeKey, false)
        set(value) = sharedPreferences.edit { putBoolean(blackedOutModeKey, value) }

    var coverThemeEnabled: Boolean
        get() = sharedPreferences.getBoolean(coverThemeEnabledKey, false)
        set(value) = sharedPreferences.edit { putBoolean(coverThemeEnabledKey, value) }

    var coverDisplaySize: IntSize
        get() {
            val dim1 = sharedPreferences.getInt(coverDisplayDimension1Key, 0)
            val dim2 = sharedPreferences.getInt(coverDisplayDimension2Key, 0)
            return IntSize(dim1, dim2)
        }
        set(value) {
            sharedPreferences.edit {
                putInt(coverDisplayDimension1Key, min(value.width, value.height))
                putInt(coverDisplayDimension2Key, max(value.width, value.height))
            }
        }

    var languageTag: String
        get() = sharedPreferences.getString(languageTagKey, "") ?: ""
        set(value) = sharedPreferences.edit { putString(languageTagKey, value) }

    var developerModeEnabled: Boolean
        get() = sharedPreferences.getBoolean(developerModeKey, false)
        set(value) = sharedPreferences.edit { putBoolean(developerModeKey, value) }

    var inputReceiverEnabled: Boolean
        get() = sharedPreferences.getBoolean(inputReceiverEnabledKey, true)
        set(value) = sharedPreferences.edit { putBoolean(inputReceiverEnabledKey, value) }

    fun isCoverThemeApplied(currentDisplaySize: IntSize): Boolean {
        if (!coverThemeEnabled) return false
        val storedDimension1 = sharedPreferences.getInt(coverDisplayDimension1Key, 0)
        val storedDimension2 = sharedPreferences.getInt(coverDisplayDimension2Key, 0)
        if (storedDimension1 == 0 || storedDimension2 == 0) return false
        val currentMatchesStoredOrder =
            (currentDisplaySize.width == storedDimension1 && currentDisplaySize.height == storedDimension2)
        val currentMatchesSwappedOrder =
            (currentDisplaySize.width == storedDimension2 && currentDisplaySize.height == storedDimension1)

        return currentMatchesStoredOrder || currentMatchesSwappedOrder
    }

    fun clearSettings() {
        sharedPreferences.edit {
            putBoolean(isUserLoggedInKey, false)
            putBoolean(blackedOutModeKey, false)
            putBoolean(coverThemeEnabledKey, false)
            putBoolean(developerModeKey, false)
            putBoolean(inputReceiverEnabledKey, true)
            putInt(themeKey, ThemeSetting.SYSTEM.ordinal)
            remove(coverDisplayDimension1Key)
            remove(coverDisplayDimension2Key)
            remove(languageTagKey)
        }
    }
}
