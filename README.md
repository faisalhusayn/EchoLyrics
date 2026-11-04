# EchoLyrics

EchoLyrics is a lightweight Android app for an Amazon Echo Show 5. It displays time-synced lyrics for the Spotify track currently playing on the Echo Show. Audio playback remains handled by Spotify Connect, Alexa, or the Spotify phone app.

The app uses plain Android Views, Kotlin, OkHttp, and kotlinx.serialization. It does not use Google Play Services or AndroidX.

## Device Target

- Echo Show 5 / Fire OS reporting Android API 25
- Landscape, approximately 960x480
- `minSdk 25`
- `targetSdk 28`
- Touchscreen-only controls
- Keeps the display on while EchoLyrics is in the foreground
- Designed to stay below 60 MB PSS on the target device

## Spotify Developer App

1. Open <https://developer.spotify.com/dashboard>.
2. Create an app.
3. Use any app name, such as `EchoLyrics`.
4. Select Web API if Spotify asks which APIs are used.
5. Add this exact redirect URI:

```text
echolyrics://callback
```

6. Copy the app's Client ID. Do not use or commit the Client Secret.

The required scopes are:

```text
user-read-currently-playing
user-read-playback-state
user-modify-playback-state
user-library-read
user-library-modify
```

The third scope is required for Previous, Play/Pause, Next, and seeking. Existing tokens from an older EchoLyrics version may need to be reauthorized.

## Local Configuration

Create `local.properties` in the project root. This file is local-only and should not be committed:

```properties
sdk.dir=C:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk
spotifyClientId=your_spotify_client_id
```

The client ID is injected into `BuildConfig` during the build and is not hardcoded in source code.

## Required Build Tools

Install:

- JDK 17
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0 or newer
- Android SDK Platform-Tools

Set `ANDROID_HOME` or provide `sdk.dir` in `local.properties`.

## Build

From the project directory:

```powershell
cd C:\Users\YOUR_NAME\Desktop\echo
.\gradlew.bat assembleDebug
```

The APK is generated at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

On macOS/Linux, use:

```bash
./gradlew assembleDebug
```

## Install and Launch

Enable ADB/debugging on the Echo Show and connect it to the computer. Verify the device:

```powershell
adb devices
```

Install and launch:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.echolyrics/.MainActivity
```

The explicit `am start` command is preferred on Fire OS. Some Fire OS builds print a misleading warning when launched with `monkey` even though the activity is correctly installed.

To stop the app:

```powershell
adb shell am force-stop com.echolyrics
```

To remove it:

```powershell
adb uninstall com.echolyrics
```

## First Login

1. Launch EchoLyrics.
2. Sign in to Spotify in the in-app WebView.
3. Approve the requested permissions.
4. Leave EchoLyrics open.
5. Start a song through Spotify Connect, Alexa, or the Spotify phone app.

Playback must be routed to the Echo Show. EchoLyrics does not play audio itself.

## v3 Features

- Adaptive polling: approximately 3 seconds while playing, 12 seconds while paused/idle, 1 second near track end, and exponential network backoff.
- Queue-based next-track lyric prefetch with the same capped disk cache.
- Like/save, shuffle, repeat, playback, and seek controls. Save requires the library scopes and playback controls require Premium.
- In-memory debug log containing up to 100 non-sensitive events. Long-press the main background or open it from Settings.

All v3 options are on the separate scrollable Settings page. The main overlay is reserved for playback, seeking, Settings, and Close.

If the app repeatedly asks for consent, make sure the redirect URI is exactly `echolyrics://callback`, then clear the old app data and log in again:

```powershell
adb shell pm clear com.echolyrics
adb shell am start -n com.echolyrics/.MainActivity
```

## Login Fallback

If typing on the Echo Show is inconvenient, use the PC PKCE helper:

```powershell
python tools\pkce_refresh_token.py
```

The helper needs a temporary redirect URI registered in the Spotify dashboard:

```text
http://127.0.0.1:8765/callback
```

After the helper prints a refresh token, push it to the Echo Show:

```powershell
adb shell am start -n com.echolyrics/.MainActivity --es refresh_token "TOKEN"
```

Remove the temporary HTTP redirect URI from the Spotify dashboard afterward.

## Main Screen

- Full-screen dark lyrics display
- Spotify track title, artist, and album-art thumbnail
- Dimmed album art background when enabled
- Full scrollable synced lyric list
- Current lyric line highlighted and automatically centered
- Plain lyrics auto-scroll proportionally to playback progress
- Touch scrolling temporarily overrides plain lyric auto-scroll
- Touch anywhere to open the playback overlay

## Playback Overlay

- `|<`: previous track
- `>` or `||`: play or pause
- `>|`: next track
- Seek slider: seek through the current track
- `Settings`: opens the separate Settings page
- `Close`: closes the overlay and returns focus to lyrics

Playback controls use Spotify Web API endpoints and require Spotify Premium with an active playback device. API errors are handled without crashing:

- 403: Premium required
- 404: no active device
- 401: reauthorization required
- 429: retry delay from `Retry-After`

## Settings Page

Settings are on a separate scrollable page so the main lyrics view remains clean.

### Display

- Show or hide lyrics
- Show or hide album art and its background
- Cycle font size
- Toggle night mode

### Sync

- Track offset minus 100 ms
- Track offset plus 100 ms
- Global offset minus 100 ms
- Global offset plus 100 ms

Track offsets are stored by Spotify track ID. The global offset is used as the default for tracks without a saved track-specific adjustment.

### Behavior

- Auto-launch monitor on/off

When enabled, the auto-launch monitor brings EchoLyrics forward when playback transitions from stopped to playing. It does not repeatedly take over the screen while a user is deliberately browsing elsewhere.

### Account and Cache

- Log out
- Clear cached lyrics

## Lyrics Sources and Cache

Lyrics are requested in this order:

1. LRCLIB, including synced LRC lyrics and plain lyrics
2. lyrics.ovh as a free fallback for plain lyrics

Synced lyrics are parsed from LRC timestamps. Lyrics are cached in memory and on disk by Spotify track ID. The disk cache is capped at approximately 1 MB, and the oldest files are removed when the cap is exceeded.

Cached lyrics can be displayed without network access. If no cached or remote lyrics exist, the app reports `No lyrics found` internally and keeps the main view usable.

## Reliability

- Spotify polling is approximately every three seconds
- Network failures use exponential backoff up to 30 seconds
- Spotify 401 responses trigger token refresh and one retry
- Spotify token expiry is refreshed automatically
- Spotify 429 responses respect `Retry-After`
- Spotify 204 responses are treated as no active playback
- LRCLIB 404 responses fall through to search and then lyrics.ovh
- Tokens are stored in private SharedPreferences
- The app has no audio service and does not play music

## Memory and Performance

Album artwork is downscaled before display and the previous bitmap is recycled when replaced. The app avoids large UI libraries and uses lightweight programmatic Views. A device measurement during development showed approximately 25-40 MB PSS depending on WebView and artwork state, under the 60 MB target.

## Troubleshooting

### `INSTALL_FAILED_OLDER_SDK`

The APK now uses `minSdk 25`, matching the API level reported by the Echo Show. Rebuild the latest APK before installing.

### `Client_id: Not present`

Check that `local.properties` contains `spotifyClientId`, then rebuild and reinstall:

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### App keeps stopping

Capture the actual app crash, excluding unrelated Fire OS services:

```powershell
adb logcat -c
adb shell am force-stop com.echolyrics
adb shell am start -n com.echolyrics/.MainActivity
adb logcat -d -s AndroidRuntime:E com.echolyrics:E *:S
```

### Memory usage

```powershell
adb shell dumpsys meminfo com.echolyrics
```

### No lyrics

The track may not have coverage in LRCLIB or lyrics.ovh. Try `Refresh lyrics` from Settings. Plain lyrics do not have line-level timing.

## Known Limits

- Spotify playback state can be up to one polling interval behind.
- Network latency and Fire OS scheduling can affect sync accuracy.
- Spotify playback controls require Premium and an active device.
- Lyrics coverage varies by source.
- Fire OS API 25 has an older WebView and limited modern Android APIs, so the app intentionally uses legacy-compatible Views and APIs.
- The queue endpoint may be unavailable for some account/device states; prefetch is skipped safely in that case.
- Auto-launch is a lightweight polling monitor and is subject to Fire OS background process rules.
