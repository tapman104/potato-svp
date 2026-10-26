# MPV JNI — native library calls these by exact name/signature
-keep class is.xyz.mpv.** { *; }

# Explicitly keep JNI callback overloads that R8 strips as "unreachable"
-keepclassmembers class is.xyz.mpv.MPVLib {
    public static void eventProperty(java.lang.String, boolean);
    public static void eventProperty(java.lang.String);
}

# Prevent R8 from optimizing/inlining these JNI entry points
-keepclasseswithmembernames class is.xyz.mpv.MPVLib {
    public static void eventProperty(java.lang.String, boolean);
    public static void eventProperty(java.lang.String);
}

# Suppress R8 missing class warnings from mpv-android-lib compiled with newer Kotlin
-dontwarn kotlin.coroutines.jvm.internal.SpillingKt
