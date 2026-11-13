# Offline Android Study Tracker (Kotlin 2.0 + Jetpack Compose + Room SQLite)

## Architecture Summary (All 4 Stages Complete)
- **Zero-Internet Privacy**: `AndroidManifest.xml` requests zero network permissions (`INTERNET` is completely omitted).
- **Tech Stack**: Kotlin 2.0.21, Jetpack Compose (Material 3 True `#000000` AMOLED theme for 120Hz displays), Room Database 2.6.1 (7 entities + relational projections), Foreground Service (`specialUse` subtype with lock-screen Pause/Resume/Save actions and `SystemClock.elapsedRealtime()` drift-free timing), `BootRecoveryReceiver`, and Android Storage Access Framework (`.json` full database backup/restore + UTF-8 BOM `.csv` export for Microsoft Excel).
- **Target APIs**: Android 8.0 (API 26) through Android 15+ (API 35).

## How to Generate the Installable `.apk`

### Option 1: Build Locally on Windows 11 (Android Studio)
1. Extract `StudyTracker_Android_Project.zip` to a folder on your PC.
2. Open **Android Studio Ladybug (or newer)** and select **Open** -> choose the extracted `StudyTracker_Android_Project` folder.
3. Let Gradle sync finishes, then click **Build > Build Bundle(s) / APK(s) > Build APK(s)** (or run `./gradlew assembleDebug` in the terminal).
4. Copy `app/build/outputs/apk/debug/app-debug.apk` to your phone or tablet and tap to install.

### Option 2: Cloud Build via GitHub Actions (No Local Android SDK Needed)
1. Upload this project folder to a free GitHub repository.
2. Open the **Actions** tab in GitHub — the included `.github/workflows/build-apk.yml` workflow automatically compiles both `app-debug.apk` and `app-release.apk` and provides a direct download link under **Artifacts**.
