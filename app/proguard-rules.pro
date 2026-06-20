# =====================================================================
# Phase 6 · Step 6.5 — R8/ProGuard keep rules for the :app module.
# =====================================================================
#
# Pairs with `getDefaultProguardFile("proguard-android-optimize.txt")` so
# R8 can both minify (rename + strip unused) and optimze (inlining,
# class merging, identifier propagation). The rules below keep the surface
# that reflection-driven libraries need AT RUNTIME, otherwise proguard
# will silently rename / strip our public entry points and we would
# spend hours debugging null pointers at the crash site instead of
# tripping `./gradlew assembleRelease` at build time.
#
# Rule categories:
#   1. Attributes (preserve annotations, signatures, inner classes).
#   2. Our application code (manifest-referenced + reflection targets).
#   3. Sentry (defense-in-depth on top of the SDK's consumer rules).
#   4. Room (we use KSP-generated code; explicit keep = belt + suspenders).
#   5. Gson (we mirror the SDK's consumer rules; explicit keeps are needed
#      because @SerializedName on our own data classes is a contract).
#   6. Kotlin (metadata + companions + default args).
#   7. Media3 / ExoPlayer (built-in consumer rules cover most, but
#      keep the public Surface entry points + PlaybackException sig).
#   8. OkHttp + Okio (silent field strip breaks TLS).
#   9. Firebase Messaging (Service reflection on the manifest entry).
#  10. Glide (annotation-processed extensions; keep the GeneratedAppGlideModule).
#  11. Coroutines debug agent (debug-only).
#  12. DataStore Preferences (Kotlin serialization for proto — we use the
#      plain Preferences API but the runtime still tries reflective paths
#      on API edge cases; keep its core Marker types).
#  13. Lottie (Moshi-based JSON reflection on animation models).
#  14. Material / AppCompat / Navigation reflective instantiation.
# =====================================================================

# ---------------------------------------------------------------------
# 1. Attributes every reflection-driven consumer needs.
# ---------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------
# 2. Our application code.
#    AGP's manifest-merger emits keep rules for every <activity>,
#    <service>, <provider>, <receiver> it sees, so explicit keeps are
#    belt + suspenders. They are harmless if duplicated.
# ---------------------------------------------------------------------

# Application class — also referenced dynamically via Class.forName
# from a few libraries that probe for an Application instance.
-keep public class com.sportstream.app.SportStreamApp { *; }

# Activities registered in AndroidManifest.xml.
-keep public class com.sportstream.app.ui.activities.SplashActivity { *; }
-keep public class com.sportstream.app.ui.activities.MainActivity { *; }
-keep public class com.sportstream.app.ui.activities.PlayerActivity { *; }
-keep public class com.sportstream.app.ui.update.UpdateActivity { *; }
-keep public class com.sportstream.app.ui.crash.CrashActivity { *; }

# Services.
-keep public class com.sportstream.app.ui.player.FloatingPlayerService { *; }
-keep public class com.sportstream.app.services.SportStreamMessagingService { *; }
-keep public class com.sportstream.app.services.UpdateDownloadReceiver { *; }

# Our chained UncaughtExceptionHandler — keeps the class + Reader for the
# CrashActivity UI to surface the dump.
-keep public class com.sportstream.app.data.crash.CrashHandler { *; }
-keep public class com.sportstream.app.data.crash.CrashHandler$Companion { *; }

# Kotlinx coroutines internal ServiceLoader files (DebugProbesKt + the
# Marker.ensureCoroutineContext probes that some libs do at startup).
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }

# ---------------------------------------------------------------------
# 3. Sentry — explicit overrides of the SDK's consumer rules.
# ---------------------------------------------------------------------
-keep class io.sentry.android.core.SentryAndroid { *; }
-keep class io.sentry.Sentry { *; }
-keep class io.sentry.SentryOptions { *; }
-keep class io.sentry.protocol.** { *; }
-keep class io.sentry.event.Event { *; }
-dontwarn io.sentry.**
# Note: `io.sentry.android.ndk.**` is deliberately NOT kept here because
# libs.versions.toml pulls `io.sentry:sentry-android` (no NDK module). If a
# future hardening step adds the NDK module for native crash symbolication,
# re-introduce `-keep class io.sentry.android.ndk.** { *; }` there.

# ---------------------------------------------------------------------
# 4. Room — even though KSP-generated impls are kept automatically,
#    keep our @Entity/@Dao/@Database contracts.
# ---------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep @androidx.room.Database class *
-dontwarn androidx.room.paging.**

# Our entities + DAOs (AppDatabase v1+2).
-keep class com.sportstream.app.data.local.AppDatabase { *; }
-keep class com.sportstream.app.data.local.AppDatabase_Impl { *; }
-keep class com.sportstream.app.data.local.FavoriteEntity { *; }
-keep class com.sportstream.app.data.local.PlaylistEntity { *; }
-keep class com.sportstream.app.data.local.NoticeEntity { *; }
-keep class com.sportstream.app.data.local.Converters { *; }
-keep interface com.sportstream.app.data.local.FavoriteDao { *; }
-keep interface com.sportstream.app.data.local.PlaylistDao { *; }
-keep interface com.sportstream.app.data.local.NoticeDao { *; }
-keep class com.sportstream.app.data.local.FavoriteDao_Impl { *; }
-keep class com.sportstream.app.data.local.PlaylistDao_Impl { *; }
-keep class com.sportstream.app.data.local.NoticeDao_Impl { *; }

# ---------------------------------------------------------------------
# 5. Gson — our data models use @SerializedName.
# ---------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.**

# Our data models — Gson proxy generation requires no stripping.
-keep class com.sportstream.app.data.models.** { *; }
-keepclassmembers class com.sportstream.app.data.models.** {
    <fields>;
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------------------------------------------------------------------
# 6. Kotlin — keep metadata so Kotlin reflection libs can introspect.
# ---------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
# Note: `kotlin.reflect.**` is deliberately NOT kept here because we don't
# add `kotlin-reflect` as a dependency. If a future step pulls it in,
# scope the keep to `kotlin.reflect.jvm.internal.**` rather than the whole
# tree (saves ~3 MB).
-keepclassmembers class **$Companion { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers,allowobfuscation class * {
    @kotlinx.coroutines.* <methods>;
}

# ---------------------------------------------------------------------
# 7. Media3 / ExoPlayer — entry points used reflectively.
# ---------------------------------------------------------------------
-keep class androidx.media3.exoplayer.ExoPlayer { *; }
-keep class androidx.media3.exoplayer.source.** { *; }
-keep class androidx.media3.common.PlaybackException { *; }
-keep class androidx.media3.ui.PlayerView { *; }
-dontwarn androidx.media3.**

# ---------------------------------------------------------------------
# 8. OkHttp + Okio — keep public surface so TLS / interceptor chain works.
# ---------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---------------------------------------------------------------------
# 9. Firebase Messaging — Service reflection.
# ---------------------------------------------------------------------
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }
-keep class com.google.firebase.messaging.RemoteMessage { *; }
-keep class com.google.firebase.messaging.RemoteMessage$** { *; }
-dontwarn com.google.firebase.**

# ---------------------------------------------------------------------
# 10. Glide — keep the GeneratedAppGlideModule (annotation-processed).
# ---------------------------------------------------------------------
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ---------------------------------------------------------------------
# 11. Coroutines debug agent — debug-only, no-op in release.
# ---------------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**

# ---------------------------------------------------------------------
# 12. DataStore Preferences.
# ---------------------------------------------------------------------
-keep class androidx.datastore.preferences.protobuf.** { *; }
-dontwarn androidx.datastore.preferences.protobuf.**

# ---------------------------------------------------------------------
# 13. Lottie — Moshi annotation-driven generation.
# ---------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ---------------------------------------------------------------------
# 14. Material / AppCompat / Navigation reflective instantiation.
# ---------------------------------------------------------------------
-keep public class com.google.android.material.** { *; }
-keep public class androidx.appcompat.widget.** { *; }
-keep public class androidx.navigation.fragment.NavHostFragment { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.appcompat.**
-dontwarn androidx.navigation.**
