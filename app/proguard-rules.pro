# =========================================================================
# Phase 6 · Step 6.5 / Phase 7 · Step 7.2 — ProGuard / R8 rules
# =========================================================================
#
# Keep rules for reflection-driven libraries and security classes.
# Most libraries ship their own consumer ProGuard rules; these are the
# project-specific additions that R8 cannot infer automatically.
# =========================================================================

# ── Gson ─────────────────────────────────────────────────────────────────
# @SerializedName fields are accessed via reflection.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# ── OkHttp platform ─────────────────────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Room (KSP-generated code is already kept, but DAOs may use reflection)
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ── Sentry ───────────────────────────────────────────────────────────────
# Keep Sentry event classes that are serialized via reflection.
-keep class io.sentry.SentryEvent { *; }
-keep class io.sentry.protocol.** { *; }

# ── Phase 7 · Step 7.2 — Security package ────────────────────────────────
# EncryptedConstants is accessed directly (no reflection) so R8 will keep
# it automatically.  However, the field names serve as the encryption key
# lookup table — if R8 renames them the app crashes at runtime.  We keep
# the class and its fields but allow method-level obfuscation.
-keep class com.sportstream.app.security.EncryptedConstants { *; }
-keep class com.sportstream.app.security.EncryptedConstants$Entry { *; }

# RuntimeStringProvider's ENTRIES keys are string literals, not reflection.
# KeystoreManager, StringEncryptor, SecurityModule — direct calls only.
# No additional keep rules needed for those classes.

# ── Data models (Phase 2) ────────────────────────────────────────────────
# Keep all data classes that are deserialized from JSON via org.json.
-keep class com.sportstream.app.data.models.** { *; }

# ── Crash handler ────────────────────────────────────────────────────────
# CrashActivity is launched via Intent from the UncaughtExceptionHandler.
-keep class com.sportstream.app.data.crash.CrashActivity { *; }
