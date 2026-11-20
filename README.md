# Universal Stream TV

A native Android TV app (Kotlin + Jetpack Compose) for opening any website, short links included, and watching its videos full screen, using only the TV remote.

- **Package:** `com.universalstream.tv` · **minSdk** 26 (Android 8.0) · **target/compileSdk** 35
- **Targets:** Xiaomi Mi TV Stick, Xiaomi Android TVs (e.g. 65"), Google TV and other Android TV boxes

> **Build status — please read.** This project was written in an environment that could not reach
> Google's Maven repository, so **Gradle was never run and no APK was produced yet.** The pure-Kotlin
> URL parser and HTML-redirect parser were compiled and checked (35/35 checks passed); everything
> Android-specific still needs its first build. No physical-device testing has been done.
> Build with Android Studio or the included GitHub Actions workflow (below) and report any compile error.

---

## Features

| Area | What it does |
|---|---|
| Home | Large URL box (TV keyboard, Bluetooth/USB keyboard, paste, clear), Continue, Favorites, Recently visited |
| URL handling | `example.com`, `www.…`, `http://`, `https://`, `host:port`; HTTPS added automatically; `javascript:`, `file:`, `intent:` etc. rejected |
| Short links | OkHttp resolver: 301/302/303/307/308, multi-hop, HEAD→GET fallback, timeouts, loop detection, max 10 hops, query + fragment kept, every hop validated; meta-refresh/JS detection; WebView fallback for client-side redirects |
| Preview | Original URL → destination, redirect count, HTTPS-downgrade / HTTP warnings, Open / Cancel; on failure, "Open original link" |
| Browser | Single reused WebView, JS/DOM storage/cookies, HTML5 video, fullscreen, JS dialogs, permission consent, SSL errors never bypassed |
| Remote | Pointer mode (D-pad moves pointer, OK clicks, edges scroll) or Direct mode; PLAY/PAUSE, FF, REW; MENU or **hold BACK** toggles toolbar |
| Toolbar | Back, Forward, Reload/Stop, Home, URL (OK edits), Favorite, Copy URL, Pointer on/off, External browser; overlay that auto-hides |
| Data | Room (history, favorites), DataStore (settings) |
| Settings | Home page (+ open at startup), dark mode, clear history/cache/cookies, JavaScript, text zoom, user agent (Default/Desktop/Mobile/Custom), toolbar on load, navigation mode, About (device + WebView info) |

### BACK key priority (browser)
1. Exit fullscreen video → 2. close toolbar/overlay → 3. WebView history back → 4. Home screen.
On Home, BACK needs a second press within 2 s to exit, so the app never closes by accident.

### Remote control map
| Key | Action |
|---|---|
| D-pad | Pointer mode: move pointer (accelerates when held; push an edge to scroll). Direct mode: sent to the site |
| OK | Pointer mode: click under the pointer. Elsewhere: select |
| BACK (short) | See priority above |
| BACK (hold ~0.7 s) / MENU / INFO | Show or hide the toolbar |
| PLAY/PAUSE, PLAY, PAUSE, STOP | Control the page's video |
| FAST FORWARD / REWIND | Seek ±10 s |
| Card + MENU or hold OK | Card options (open, favorite, delete from history) |

---

## Architecture

MVVM with a small manual dependency container (no DI framework, to keep startup and memory low).

```
app/src/main/java/com/universalstream/tv/
├── UniversalStreamApp.kt          Application; creates AppContainer
├── AppContainer.kt                Repositories, OkHttp, resolver, device profile
├── MainActivity.kt                Hosts Compose, owns BrowserController, routes remote keys
├── core/
│   ├── UrlUtils.kt                URL parsing/validation, ShortenerRegistry (pure Kotlin)
│   ├── UserAgents.kt              UA modes + provider shared by WebView and resolver
│   ├── DeviceProfile.kt           Android/screen/RAM/WebView detection, low-performance profile
│   └── NetworkMonitor.kt          Connectivity → auto-reload after reconnect
├── resolver/
│   ├── ShortUrlResolver.kt        OkHttp manual redirect engine
│   ├── HtmlRedirectParser.kt      Meta refresh / simple JS redirect detection
│   ├── WebViewRedirectResolver.kt Short-lived off-screen WebView fallback
│   └── UrlResolutionService.kt    Orchestration + preview decision + error messages
├── data/
│   ├── BrowserData.kt             Room entities, DAOs, database, BrowserRepository
│   └── SettingsRepository.kt      DataStore settings
├── browser/
│   ├── BrowserController.kt       The single WebView + WebViewClient/WebChromeClient policy
│   ├── FullscreenManager.kt       onShowCustomView/onHideCustomView host
│   ├── CursorController.kt        Pointer mode (touch + mouse-wheel event synthesis)
│   ├── MediaScripts.kt            Read-only media status/command scripts
│   ├── RemoteKeyHandler.kt        BACK/MENU/media/D-pad routing
│   └── BrowserModels.kt           UI models + BrowserListener
└── ui/
    ├── AppRoot.kt, AppViewModel.kt
    ├── theme/Theme.kt             Dark/light schemes, TV type scale, safe margins
    ├── components/TvComponents.kt Focusable surface, buttons, rows, dialogs
    ├── home/                      HomeScreen, UrlInputField, HomeViewModel
    ├── browser/                   BrowserScreen, BrowserViewModel
    └── settings/                  SettingsScreen, SettingsViewModel
```

### Key decisions
- **One WebView for the app's lifetime.** Compose embeds it with `AndroidView`; leaving the browser only detaches and pauses it. It is recreated only after a renderer crash, or when memory runs low while the page is not on screen, and then reloads the last URL. The Activity handles keyboard/navigation config changes itself (`configChanges`), so plugging in a Bluetooth keyboard does not destroy the WebView or stop the video.
- **Fullscreen video outside Compose.** The view from `onShowCustomView` is added to the window's decor view, above everything, and removed again by BACK or `onHideCustomView`.
- **Pointer mode is the default** because most streaming sites are not built for D-pad focus. It is switchable per session (toolbar) or permanently (Settings → Remote navigation → Direct keys). In fullscreen, keys always go straight to the player.
- **Media keys:** a small `evaluateJavascript` call controls the main `<video>` element. If none is reachable (a cross-origin iframe player), the key is forwarded as Arrow/Space, which most web players understand. No `addJavascriptInterface` is used.
- **Audio focus:** Chromium WebView requests and releases audio focus for HTML5 media itself. The app pauses media when it goes to the background, keeps the screen on only while a video is fullscreen or playing, and releases the flag otherwise.
- **Preview rule:** the confirmation dialog appears for known short links, redirects to a *different* site, and any plain-HTTP destination. Same-site redirects (e.g. `http`→`https`, `example.com`→`www.example.com`) open directly.
- **WebView fallback resolver** creates a temporary, image-blocked WebView only when HTTP resolution stops on a shortener page, and destroys it within 15 s.
- **Low-memory profile** (≤ ~1.7 GB RAM or `isLowRamDevice`, e.g. Mi TV Stick 1 GB): no focus animation, shorter history, less frequent media polling, eager WebView release. Rendering resolution is never forced; WebView renders at the window size the system provides.

### Security
`allowFileAccess`/`allowContentAccess`/file-URL access off · only http/https main-frame navigation · `intent://` never launched (only an http(s) `browser_fallback_url`) · SSL errors always `cancel()` with an error screen · camera/mic/geolocation denied, protected-media (DRM) ID only after consent · Safe Browsing enabled · no JavaScript interfaces · mixed content in compatibility mode · console logs only in debug builds, truncated · credentials in URLs are stripped · system CAs only (no user CAs) · HTTP allowed but flagged with warnings.

**Not bypassed, by design:** DRM, CAPTCHAs, sign-in, paywalls, anti-bot checks, geo-blocks, certificate errors. The app shows the original site and plays video with that site's own player; it never extracts or downloads streams.

---

## Build

### Option A — Android Studio (recommended)
1. Install Android Studio (Ladybug 2024.2 or newer) with Android SDK Platform 35.
2. **File → Open** → select the `UniversalStreamTV` folder. Let Gradle sync (it downloads Gradle 8.14.3, AGP 8.7.3 and dependencies).
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

### Option B — command line
```bash
cd UniversalStreamTV
./gradlew testDebugUnitTest    # unit tests (URL parser, HTML parser, redirect engine)
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # minified release APK (signed with the debug key for side-loading)
```
Requires JDK 17+ and `ANDROID_HOME` (or `local.properties` with `sdk.dir=...`).

### Option C — GitHub Actions (no Android Studio needed)
Push the project to a GitHub repository. `.github/workflows/build-apk.yml` runs the tests and builds the APK; download it from the workflow run's **Artifacts** (`UniversalStreamTV-debug-apk`).

### APK output location
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## Install on Xiaomi Mi TV Stick
**Method 1 — ADB over Wi-Fi (most reliable)**
1. On the stick: **Settings → Device Preferences → About → Build**, press OK 7 times to enable Developer options.
2. **Settings → Device Preferences → Developer options →** turn on **USB debugging** (on some firmware also **Network debugging**).
3. Find the stick's IP: **Settings → Network & Internet →** your Wi-Fi network.
4. On your computer (same network):
   ```bash
   adb connect 192.168.x.x:5555
   adb install -r app-debug.apk
   ```
   Accept the "Allow USB debugging?" prompt on the TV the first time.

**Method 2 — Send Files to TV / file manager**
1. Install a file manager (e.g. *Send Files to TV*, *X-plore* or *FX File Explorer*) on the stick from the Play Store.
2. **Settings → Device Preferences → Security & restrictions → Unknown sources** → allow that app.
3. Send or copy the APK to the stick, open it in the file manager and choose **Install**.

The app then appears in **Apps** on the home screen, with its TV banner.

## Install on a Xiaomi 65" Android TV (Mi TV / Xiaomi TV A/P/Q series)
- **Android TV / Google TV models:** same steps as the stick (Developer options under **Settings → Device Preferences → About → Build**, or **Settings → System → About → Android TV OS build** on Google TV). Then use ADB, or a USB drive: copy the APK to the drive, open it with the TV's file manager, and allow **Unknown sources** for that file manager when asked.
- **PatchWall launcher:** the app is listed in **Apps** (Leanback launcher category). You can pin it to the home row from there.

---

## Testing checklist (to be performed — not yet done)
Physical-device testing has **not** been carried out. Recommended pass on Mi TV Stick, the 65" TV, a 1080p and a 4K device, with the stock remote and a Bluetooth keyboard:

- [ ] App appears in the launcher with banner; launches to Home with the URL box focused
- [ ] `example.com` (HTTPS added), `http://` site (warning shown), paste, clear
- [ ] `bit.ly`/`tinyurl.com`/`t.co`/`is.gd`/`cutt.ly` links resolve; preview shows original + destination
- [ ] Multi-hop redirect, redirect loop error, offline error with "Open original link"
- [ ] Streaming site: pointer navigation, scrolling at edges, click to play
- [ ] Fullscreen enter/exit with BACK; PLAY/PAUSE, FF, REW; screen stays on during long playback
- [ ] Toolbar via MENU and via holding BACK; Back/Forward/Reload/Home/Copy/External
- [ ] History and favorites persist after restart; delete/clear works
- [ ] Unplug network during playback → error screen → auto-reload when back online
- [ ] 1–2 hour playback on the Mi TV Stick; check memory with `adb shell dumpsys meminfo com.universalstream.tv`

When something fails, note whether it is an **app bug** or a **site/device limitation**:
- **DRM (Widevine):** many premium services refuse WebView playback, or limit it to SD on L3 devices. That is decided by the service, not this app.
- **Codecs:** HEVC/AV1/Dolby support depends on the TV chip; HLS/DASH work only when the site's own player supports them on WebView.
- **WebView version:** old *Android System WebView* builds break modern players. Settings → About shows the version; update it from the Play Store.
- **Cross-origin iframe players** may ignore remote media keys; use pointer mode on the player's own controls.
- **Remotes without MENU** (some Xiaomi remotes): hold BACK to open the toolbar.

## Possible next steps
- Move UI text to `strings.xml` for Indonesian/English localisation.
- Add a release keystore and signing config before sharing the APK.
- Optional per-site "Desktop mode" toggle in the toolbar.
