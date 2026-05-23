# Google Play Release Checklist

## Project State

- The Android launcher icon is generated from the iOS source icon: `mobile/iosApp/AtomGoIOS/Assets.xcassets/AppIcon.appiconset/Comp 1_00000.png`.
- Android adaptive and legacy launcher icons live under `mobile/androidApp/src/main/res/mipmap-*`.
- The Play Console 512 px icon asset is `mobile/androidApp/play-store/icon-512.png`.
- `targetSdk = 35`, which matches Google Play's current Android 15 / API 35 submission requirement for new apps and updates.
- Release builds disable cleartext traffic through the `usesCleartextTraffic` manifest placeholder.
- App backups are disabled because the app can store remembered login/password values.

## Build A Release Bundle

Unsigned local release bundle:

```bash
./gradlew :mobile:androidApp:bundleRelease -PatomgoEnv=prod
```

This artifact is for validation only. Play Console upload requires a signed bundle.

Signed release bundle with an upload key:

```bash
ATOMGO_RELEASE_STORE_FILE=/absolute/path/upload-keystore.jks \
ATOMGO_RELEASE_STORE_PASSWORD='...' \
ATOMGO_RELEASE_KEY_ALIAS='...' \
ATOMGO_RELEASE_KEY_PASSWORD='...' \
./gradlew :mobile:androidApp:bundleRelease -PatomgoEnv=prod
```

Output:

```text
mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab
```

Do not commit keystores or passwords. Use Play App Signing in Play Console and keep this local key as the upload key.

## Play Console Items Still Required

- Create the app in Play Console and upload the `.aab`.
- Enroll in Play App Signing.
- Fill app name, short description, full description, category, contact email, privacy policy, data safety, content rating, and target audience.
- Upload screenshots and any required store graphics.
- Increase `versionCode` before every later production update.

## Official References

- Target API level: https://developer.android.com/google/play/requirements/target-sdk
- Android App Bundle and Play App Signing: https://support.google.com/googleplay/android-developer/answer/9844279
- Play app setup and versionCode rules: https://support.google.com/googleplay/android-developer/answer/9859152
