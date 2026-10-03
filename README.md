# Studio Remote for Unsloth

An unofficial Android client for [Unsloth Studio](https://unsloth.ai). Use the Studio running on your own PC from your phone, with a native app instead of the browser.

> **Unofficial.** This project is not affiliated with, endorsed by, or sponsored by Unsloth. "Unsloth" is a trademark of its owners.

[TODO: add 3–4 screenshots here (chat, model hub, research run)]

## What it does

Studio Remote connects to a Studio server that **you** run (on your PC, home server, or over Tailscale) and gives you:

- **Chat** with the model loaded in Studio. Threads and projects are shared with the Studio web UI, so your history is the same on both.
- **Model management:** browse and download GGUF models from Hugging Face, see downloads in progress, load and unload models, delete cached models.
- **Research runs** and **RAG:** start research runs and upload documents for retrieval.
- **Media:** image generation, video generation and text-to-speech through Studio.
- **Voice:** speech-to-text (Studio's STT, or Android's speech recognizer for dictation).
- **Tool confirmation:** approve or deny tool calls from the phone.
- **Background generation:** long generations keep running with a notification while the app is in the background.
- **Languages:** English and Turkish, chosen in Settings → Appearance (gear icon in the side menu).

## Requirements

- Android 10 (API 29) or newer
- Unsloth Studio running and reachable from your phone. Tested with Studio **2026.9.14**.
- A Studio account (the login you use in the Studio web UI)

## Setup

1. **On your PC**, start Studio so it listens on your network:
   - `unsloth studio -H 0.0.0.0 -p 8888`, or
   - in Studio: Settings → API → Remote & LAN → LAN access → Start
   (menu names can change between Studio versions)
2. Allow port 8888 in your firewall and give the PC a fixed local IP.
3. **On your phone**, install the APK (see below), open the app and enter the server address, for example `http://192.168.1.20:8888`, then sign in.

### Using it away from home

Use [Tailscale](https://tailscale.com) on both devices and enter the PC's Tailscale address (`http://100.x.y.z:8888`, or an `https://…ts.net` address if you use `tailscale serve`). This avoids opening ports on your router.

## Install

**[⬇ Download the latest APK](https://github.com/monkeyaDluffy/studio-remote-for-unsloth/releases/latest/download/StudioRemote-for-Unsloth.apk)**, open it on your phone and install it (you may need to allow installs from your browser or file manager). Older versions are on the [Releases](https://github.com/monkeyaDluffy/studio-remote-for-unsloth/releases) page.

Verify the download: the APK should be signed with this certificate.

- SHA-256 fingerprint: `913fe2cb3bbaea75fcdccb80e5e6d3a536cd175433a2fab968cc8b2a290d1d24`

## Security notes

- On a home network the app talks to Studio over plain **HTTP**. Only use it on networks you trust, or use Tailscale.
- Studio can run tools (web search, code, terminal) with your user account. Anyone holding your Studio session can use them. Keep the server off the public internet and consider starting Studio with tools disabled when it is exposed to a network.
- The sign-in token is stored on the device only. See [PRIVACY.md](PRIVACY.md).

## Compatibility warning

Studio Remote uses Studio's **internal** HTTP API, which is not a stable public API. Studio is in beta and updates often, so a Studio update can break parts of this app. If something stops working after a Studio update, please open an issue with your Studio version.

## Build from source

Open the project in Android Studio (or use the command line, JDK 17+ and the Android SDK are required) and pick a build variant:

- `github`: the APK published on the Releases page. Asks for the battery-optimisation exemption.
- `play`: the Google Play build. Leaves out `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

```
./gradlew assembleGithubRelease   # app/build/outputs/apk/github/release/
./gradlew assemblePlayRelease
./gradlew bundlePlayRelease       # .aab for Play
```

To sign release builds, copy `keystore.properties.example` to `keystore.properties` and fill it in. Without that file the release APK is built unsigned and you can sign it yourself with `apksigner`. Never commit `keystore.properties` or the `.jks` file.

## Support

Translations are welcome: copy `app/src/main/res/values/strings.xml` to `values-<language>/strings.xml`, translate it and add the language to `AppLanguage.kt` and `localeFilters` in `app/build.gradle.kts`.

Bugs and feature requests: open an issue. When reporting, include your Android version, Studio version and what you did.

## License

Licensed under the [Apache License 2.0](LICENSE).
