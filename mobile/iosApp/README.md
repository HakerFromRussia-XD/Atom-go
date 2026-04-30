# iOS App (KMM)

This iOS app is connected to `:mobile:shared` with direct framework integration.

## Regenerate Xcode project

```bash
cd mobile/iosApp
xcodegen generate
```

## Build from terminal

```bash
xcodebuild \
  -project mobile/iosApp/AtomGoIOS.xcodeproj \
  -scheme AtomGoIOS \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```
