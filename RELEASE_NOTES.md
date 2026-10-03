# Release notes

## 1.12

- **New name:** the app is now called "Studio Remote for Unsloth". It is an unofficial client for Unsloth Studio.
- **English by default, plus Turkish:** the app is now in English. Pick another language in the new **Settings → Appearance → Display language** (Auto detect, English, Türkçe), laid out like the Studio web settings. Open Settings with the gear icon in the side menu.
- **Safer sign-in storage:** the session token is encrypted with a key held in the Android Keystore. Tokens saved by older versions are encrypted automatically on first start.
- **No backups:** app data is no longer included in Android cloud backups or device transfers.
- **Two builds:** the GitHub build still offers "Run in background" (battery-optimisation exemption). The Play build leaves out this permission.
- **Android 15+:** when the system's time limit for the background service is reached, the app now stops the service cleanly instead of crashing. The reply keeps being written on Studio and appears when you reopen the chat.
- **Studio updates:** responses from newer Studio versions are read more leniently. If Studio's API has changed, the app shows a "Studio version may not be compatible" message instead of an error code.
- **Release signing:** 1.12 is signed with a new release key instead of the debug key.
- **New package name:** `io.github.monkeyadluffy.studioremote`.

> **Note:** because of the new signing key and package name, 1.12 installs as a separate app and does not update 1.11 or older. Uninstall the old app, then sign in again. Your chats are stored in Studio and are not lost.
