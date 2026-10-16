# Pocket Ledger

An offline Android money tracker for recording income and expenses, understanding monthly spending, and managing categories.

**Current version: 0.5.6** · **Android 6.0 or newer**

## Download and install

Get the APK from the [latest release](https://github.com/gatofile/Pocket-Ledger/releases/latest).

1. Download `Pocket-Ledger-Android-0.5.6.apk` on your Android phone.
2. Open the downloaded file and follow Android's installation instructions. If asked, allow your browser or Files app to install this APK.
3. Open Pocket Ledger. No account or internet connection is needed to use it.

Installing an update signed with the same key preserves existing data. Export a backup from Settings before updating.

## Features

- **Main:** monthly spending, recent activity, and a quick button to add a transaction.
- **Transactions:** income, expenses, monthly net balance, a comparison chart, and a dated activity list. Swipe a row to edit or delete it.
- **Categories:** a monthly circular chart, amounts and percentages by category, category transaction lists, and all-time totals.
- **Custom categories:** save a reusable category or use a one-time label for income or spending.
- **Dates and times:** each transaction records both; custom pickers include **Today** and **Now** shortcuts.
- **Appearance:** light, green dark, gray dark, and AMOLED black themes, a custom accent color, and four logo styles.
- **Localization:** English, Portuguese, Spanish, French, German, Italian, and Japanese, plus a choice of currencies.
- **Backups:** export and import transactions, categories, and settings as JSON. Backups are compatible with the included web app.
- **Data controls:** reset appearance or delete activity with an explicit confirmation.

Opening the app plays a short animation and selects the current month. Browsing older months keeps charts and category details aligned with the selected month.

## Privacy and offline use

Transactions, categories, and preferences are stored locally on the device. The Android app does not request internet access or sync data to a server. Use **Settings → Export data** to keep a backup or move data to another device.

Deleting the app can remove its local data. Keep exported backups somewhere safe.

## Build the Android app

### Requirements

- JDK 17 or newer (set `JAVA_HOME`).
- Android SDK platform 36 and Build Tools 36.0.0 (set `ANDROID_HOME` to the SDK directory).
- Internet access for the first build to download Gradle and build dependencies.

The Gradle wrapper is included; a separate Gradle installation is not needed.

On Windows, run this from the repository folder in PowerShell:

```powershell
.\build-apk.ps1
```

The script saves `Pocket-Ledger-Android-<version>.apk` in the repository folder.

On other systems:

```sh
chmod +x gradlew
./gradlew assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Signing

By default, a fresh checkout uses Android's local debug signing key. You can point `POCKET_LEDGER_DEBUG_KEYSTORE` to an existing compatible debug keystore when rebuilding an update. Signing keys are excluded from the repository.

The published APK is signed for personal testing. A build made with a different signing key cannot replace it without uninstalling the existing app. Export your data before uninstalling. Publishing to Google Play requires a release signing setup.

## Project layout

- `app/src/main/java/com/pocketledger/`: native Android app.
- `app/src/main/res/`: Android strings, icons, themes, and translations.
- `app/src/main/assets/www/`: the earlier installable web app, kept as a reference; it is excluded from the native APK build.
- `CHANGELOG.md`: recent changes.

See the [release history](https://github.com/gatofile/Pocket-Ledger/releases) for APK downloads and version notes.
