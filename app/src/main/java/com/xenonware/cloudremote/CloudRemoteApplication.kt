package com.xenonware.cloudremote

import android.app.Application
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudRemoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        if (BuildConfig.DEBUG) {
            setupCrashHandler()
        }
    }

    private fun setupCrashHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val fileName = "CloudRemote_Crash_$timestamp.txt"
                
                // Try to use the public Downloads directory first
                val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val success = writeLogToFile(publicDownloadsDir, fileName, thread, throwable)

                // If public directory fails, fallback to app-specific external directory
                if (!success) {
                    val appDownloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    writeLogToFile(appDownloadsDir, fileName, thread, throwable)
                }

            } catch (e: Exception) {
                // Cannot log to file, fallback to default behavior
                Log.e("CrashHandler", "Failed to write crash log", e)
            } finally {
                // Call the default handler to let the app crash normally
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun writeLogToFile(dir: File?, fileName: String, thread: Thread, throwable: Throwable): Boolean {
        if (dir == null) return false
        
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val logFile = File(dir, fileName)
            FileWriter(logFile, true).use { writer ->
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                
                writer.append("--- Crash Log ---\n")
                writer.append("Date: $timestamp\n")
                writer.append("Thread: ${thread.name}\n")
                writer.append("Exception: ${throwable.javaClass.name}\n")
                writer.append("Message: ${throwable.message}\n")
                writer.append("Stacktrace:\n")
                writer.append(Log.getStackTraceString(throwable))
                writer.append("\n-----------------\n")
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
