# ====================================================================
# ProGuard / R8 Configuration: Antigravity Remote Control Android App
# ====================================================================

# 1. Android General & Architecture Components
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# Keep Parcelable and Serializable implementations
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 2. JavaScript Interface Bridge (CRITICAL for Web Notification Bridge)
# Methods annotated with @JavascriptInterface are invoked via WebKit reflection from JS.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.antigravity.remotecontrol.nativebridge.NotificationBridge { *; }
-keep class com.antigravity.remotecontrol.nativebridge.INotificationBridge { *; }

# 3. AndroidX Security Crypto & Keystore (CRITICAL for EncryptedSharedPreferences)
# Tink crypto engines, keysets, and AndroidKeyStore access must not be stripped or renamed.
-keep class androidx.security.crypto.** { *; }
-keep interface androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.antigravity.remotecontrol.security.** { *; }

# 4. AndroidX WebKit & Browser Custom Tabs
-keep class androidx.webkit.** { *; }
-keep class androidx.browser.** { *; }
-dontwarn androidx.webkit.**
-dontwarn androidx.browser.**
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public *;
}

# 5. Kotlin Coroutines & Dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 6. Jetpack Compose & Material 3
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# 7. Kotlin Metadata & Serialization
-keep class kotlin.Metadata { *; }
-dontnote kotlinx.serialization.SerializationKt
