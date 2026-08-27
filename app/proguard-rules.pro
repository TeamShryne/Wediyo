# Wediyo ProGuard rules
-keep class com.teamshryne.wediyo.** { *; }
# UniFFI / JNA
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
# Rust JNI symbols
-keepclasseswithmembernames class * {
    native <methods>;
}
