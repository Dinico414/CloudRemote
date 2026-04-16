# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep the Device data class to prevent Firestore and Gson deserialization issues
-keep class com.xenonware.cloudremote.data.Device { *; }
-keepclassmembers class com.xenonware.cloudremote.data.Device { *; }

# Gson rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }

# WorkManager and Room rules for Glance
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class androidx.work.OverwritingInputMerger {
    public <init>();
}
-keep class androidx.work.ArrayCreatingInputMerger {
    public <init>();
}
-keep class * extends androidx.work.InputMerger {
    public <init>();
}
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.impl.WorkDatabase_Impl
