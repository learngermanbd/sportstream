# Release Signing & Secrets

## Layout

* `signing.properties` &mdash; lives at **repo root** (next to `settings.gradle.kts`), **GITIGNORED**. Consumes by both `:app` and `:admin` via `rootProject.file("signing.properties")`.
* `*.keystore` / `*.jks` &mdash; also gitignored; conventional name `sportstream-release.jks` placed at `~/keystores/` or any out-of-repo path the properties file references.

## `signing.properties` schema

```properties
RELEASE_STORE_FILE=/absolute/path/to/sportstream-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

## Generating a fresh release keystore

```bash
keytool -genkeypair \
  -alias sportstream-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 25000 \
  -keystore ~/keystores/sportstream-release.jks \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=SportStream, OU=Mobile, O=learngermanbd, L=BD, S=, C=BD"
```

Back up the keystore to the password manager / off-disk cold storage. **If this keystore is lost, signed APKs cannot be re-uploaded to Google Play under the same identity.**

## Build behavior

* When `signing.properties` is present, `:app:assembleRelease` / `:admin:assembleRelease` sign with v1 + v2 + v3 schemes (compatible with API 24+).
* When `signing.properties` is missing, the build falls back to the **debug** signing config so the release task still produces an installable APK for pipeline testing &mdash; this APK will NOT pass Play Store integrity checks.

## Why per-module signing config?

Both `:app` and `:admin` declare the same `signingConfigs.release` block because each module produces a separate, independently-installable APK. Sharing the keystore is correct &mdash; it's the same SportStream release identity &mdash; but each APK gets its own signer config so a future split (e.g. a third-party licensing partner) can swap signing for one app without the other.

## Operator checklist before a Play upload

1. `keytool -list -v -keystore ~/keystores/sportstream-release.jks` &mdash; confirm alias + validity dates.
2. `./gradlew :app:assembleRelease -PenableR8.fullMode=true` &mdash; re-build with R8 full mode for tighter obfuscation.
3. Decompile with `jadx` and grep for non-obfuscated sensitive class names.
4. Test the signed APK on a real device before upload (Play Integrity won't catch a mis-aligned signature on install).
