# Changelog

## 0.5.8

- Check GitHub for updates when the app opens, with an optional download and Android installation flow.
- Added App updates as the first Settings section, including manual checks, download progress, and an up-to-date status.
- Verify downloaded APK size, GitHub checksum when available, package identity, version, and signing certificate before installation.
- Added translations for the update controls and kept offline tracking available when GitHub cannot be reached.

## 0.5.7

- Added an optional Donate button in Settings that opens https://ko-fi.com/gatofile in the phone's browser.
- Added a matching heart icon and translated the support section into every supported language.
- The money tracker continues to work offline; opening Ko-fi uses the browser's internet connection.

## 0.5.6

- Added a smooth opening animation for the app and logo. Android's animation setting is respected.
- The selected month resets to the phone's current month when opening the app or returning from the background.
- Returning from a backup file picker preserves the selected month.
- Added a translated **Now** button to the time picker. It fills the current hour and minute while keeping the selected date.

## 0.5.5

- Appearance changes that need the app to close ask for confirmation and explain that Pocket Ledger must be opened again manually.
- Removed automatic relaunch after those settings changes.
