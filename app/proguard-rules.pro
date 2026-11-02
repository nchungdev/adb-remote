# Keep OkHttp & Kotlin Coroutines metadata
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WebRTC JNI & native callbacks intact
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Strip verbose/debug/info logs from release builds to prevent logcat scraping
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Repackage obfuscated classes into a flat package to hinder decompilation navigation
-repackageclasses 'o'
-allowaccessmodification
