# Antigravity Remote Control

A native Android client that renders and interacts with the Antigravity Remote Control web
interface. It is built to keep working on hostile networks — corporate and campus setups that
run SSL-intercepting proxies (Netspark and similar) — while still supporting Google Sign-In,
file uploads, and web push notifications.

## Why a native wrapper

A plain browser tab breaks in three specific ways on these networks and workflows:

- **Intercepted TLS** — the proxy re-signs certificates, so the connection fails validation.
- **Google OAuth** — Google returns `403 disallowed_useragent` to any WebView-shaped
  User-Agent, and the sign-in popup is cross-origin.
- **Native capabilities** — file pickers and W3C notifications have no default WebView wiring.

This app solves each one explicitly rather than working around it in the web app.

## Features

**Security & storage**
- Target URL stored in `EncryptedSharedPreferences` (AES-256 GCM) with a hardware-backed
  `MasterKey` from AndroidKeyStore, plus self-healing recovery on Keystore invalidation.
- `UrlValidator` accepts hostnames, IPv4, IPv6, `localhost`, the emulator alias `10.0.2.2`,
  and custom ports.
- `network_security_config.xml` declares system, user, and bundled raw trust anchors, and
  permits cleartext traffic for LAN targets.

**Web engine**
- User-Agent sanitizer strips `; wv` and `Version/4.0` so Google OAuth stops rejecting the client.
- `onReceivedSslError` proceeds through custom and self-signed proxy certificates.
- `onCreateWindow` / `onCloseWindow` host cross-origin auth popups in native modal dialogs.
- Third-party cookies enabled across parent and child WebViews, flushed to disk after auth.

**Native bridges**
- `onShowFileChooser` wired to `ActivityResultLauncher`, honoring `acceptTypes` and multi-select,
  and always calling `onReceiveValue(null)` on cancel to avoid deadlocking the WebView thread.
- `@JavascriptInterface` notification bridge with an injected `window.Notification` polyfill,
  mapping web notifications onto Android channels, including the Android 13+
  `POST_NOTIFICATIONS` runtime permission flow.

## Requirements

| | |
|---|---|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 |
| Compile SDK | 35 |
| Language | Kotlin 2.3, Java 17 toolchain |
| Build | Gradle wrapper + AGP |

## Building

Point Gradle at your SDK by creating `local.properties` in the project root (it is gitignored):

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

Then build and test:

```bash
./gradlew assembleDebug
```

```bash
./gradlew test
```

The APK lands in `app/build/outputs/apk/debug/`.

## First run

The app gates startup behind a configuration dialog: enter the URL of your Antigravity Remote
Control server (`https://host`, `http://192.168.1.x:8080`, `http://10.0.2.2:3000` from an
emulator). The value is validated, normalized, and stored encrypted. Change it later from the
settings menu.

## Layout

```
app/src/main/java/com/antigravity/remotecontrol/
├── MainActivity.kt            # host activity, permission + activity-result plumbing
├── security/                  # SecurePreferencesManager, UrlValidator
├── web/                       # WebEngineManager, WebView/WebChrome clients, auth popup
├── ui/                        # UrlConfigDialog
└── nativebridge/              # file chooser, notification, UI bridges

app/src/test/                  # unit tests + tiered E2E suites
scripts/                       # standards checks, fuzz/stress harnesses, E2E runner
```

## Design notes

Two behaviors are deliberate trade-offs worth knowing before you deploy this:

- **SSL errors are bypassed unconditionally.** That is the point — it lets the app work behind
  an intercepting proxy — but it also means the app does not detect a genuine MITM. It is
  intended for a server you control, on a network you have accepted.
- **Cleartext HTTP is permitted** so LAN and development targets work.

The tiered test suites under `app/src/test/` are the executable spec for the behaviors above —
start there for the details of what each layer guarantees.
