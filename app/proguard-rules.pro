# ──────────────────────────────────────────────────────────────────────────────
# Project ProGuard / R8 rules
# Run a release build (./gradlew assembleRelease) and test every flow before
# changing any keep rule. R8 is aggressive — minor reflection use breaks silently.
# ──────────────────────────────────────────────────────────────────────────────

# ── App models (Firebase deserialization uses no-arg constructors + reflection) ─
-keep class com.randomchat.shnapp.model.** { *; }
-keepclassmembers class com.randomchat.shnapp.model.** {
    <init>(...);
    *;
}
# Preserve enum names — Firebase serializes by name
-keepclassmembers enum com.randomchat.shnapp.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
# Firebase Firestore annotations
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.Exclude <fields>;
}

# ── Firebase Crashlytics — keep symbol info for readable stack traces ─────────
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ── Google Play Billing ───────────────────────────────────────────────────────
-keep class com.android.billingclient.** { *; }
-keep interface com.android.billingclient.api.** { *; }

# ── AdMob ─────────────────────────────────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ── ML Kit Text Recognition ───────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.flow.**

# ── Jetpack Compose ───────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
# Compose runtime metadata
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── Navigation Compose ────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ── DataStore (Preferences) ───────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class androidx.datastore.** { *; }

# ── Coil image loader ─────────────────────────────────────────────────────────
-dontwarn coil3.**
-dontwarn coil.**

# ── Suppress harmless warnings ────────────────────────────────────────────────
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ── Keep our utility object members reachable via reflection ─────────────────
-keep class com.randomchat.shnapp.utils.Telemetry { *; }
-keep class com.randomchat.shnapp.utils.PiiDetector { *; }
