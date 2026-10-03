# Privacy Policy – Studio Remote for Unsloth

_Last updated: 3 October 2026_

Studio Remote for Unsloth ("the app") is an unofficial client for Unsloth Studio. This policy explains what data the app handles.

## Summary

The app talks only to the Studio server **you** configure. It has no developer-operated backend. The developer does not receive your chats, files, credentials or usage data. The app contains no analytics, advertising or crash-reporting SDK.

## Data the app handles

| Data | Why | Where it goes |
|---|---|---|
| Server address, account sign-in and session token | To connect to your Studio server | Stored on your device; sent only to your Studio server |
| Chat messages, files you upload, generated text/images/video/audio | To use Studio's features | Sent to and from your Studio server |
| Voice recordings | Dictation and speech-to-text (microphone permission) | Dictation uses Android's speech recognizer (handled by your device's speech service under its own policy). Speech-to-text through Studio sends audio to your Studio server |
| Model search queries | Browsing models in the model hub | Sent from your device to huggingface.co (the Hugging Face API), under Hugging Face's own privacy policy |

The developer cannot access any of this. Whoever operates your Studio server (usually you) controls the data stored there.

## Permissions

- **Internet / Wi-Fi state:** connect to your server and check network availability.
- **Microphone:** voice input, only when you use it.
- **Notifications:** show progress of long-running generations.
- **Foreground service and wake lock:** keep a generation running while the app is in the background.
- **Ignore battery optimizations** (GitHub build only): lets long generations keep running. You can deny it.

## Storage and security

The sign-in token is stored on your device only, is encrypted with a key held in the Android Keystore, and is excluded from Android backups and device-to-device transfers. Your Studio password is not stored.

The app can connect over plain HTTP so that it can reach servers on your local network. Use a trusted network or a VPN such as Tailscale.

## Children

The app is not directed to children under 13.

## Deleting your data

Clear the app's storage in Android settings, or uninstall it, to remove everything stored on your device. Data on your Studio server is managed in Studio.

## Changes

If this policy changes, the new version will be published at this address with an updated date.

## Contact

Questions about this policy: ttaaeeq@gmail.com
