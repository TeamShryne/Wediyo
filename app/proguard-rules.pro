# Wediyo ProGuard rules
-keep class com.teamshryne.wediyo.** { *; }
# UniFFI / JNA — Kotlin bindings use JNA even on Android
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-dontwarn com.sun.jna.win32.**
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
# Keep UniFFI generated
-keep class uniffi.wediyo_engine.** { *; }
# Rust JNI symbols
-keepclasseswithmembernames class * {
    native <methods>;
}
# R8 full mode workaround for JNA AWT (not on Android)
-dontnote com.sun.jna.**
-dontnote java.awt.**
