# AirPen Studio

A full S Pen customisation app for Samsung Galaxy phones and tablets: **air gestures** (8-way flicks, diagonals, and shapes) and **air mouse from across the room**.

## Install on S22 Ultra (download APK)

On your phone, open:

**https://github.com/guccichine/AirPenStudio/releases**

Download **AirPenStudio.apk**, install it, then follow [INSTALL.md](INSTALL.md) (Accessibility + Appear on top + disable Samsung Air actions for other apps).

If Releases is empty, GitHub Actions is still building — wait a few minutes and refresh.

Samsung and S Pen are trademarks of Samsung Electronics. This project is not affiliated with Samsung.

## What you can do

- **Gestures:** up, down, left, right, four diagonals, circle (CW/CCW), square, triangle, zigzag, check, X, plus, heart, infinity, arrow, caret, star, spiral, pigtail, brackets, wave, diamond, hook, **L, U, lightning, semicircle, question**.
- **Flick to scroll (exactly one page):** hold the side button, flick **up** or **down**. Each flick moves **one full page** in that direction and stops — no extra fling. Works in Chrome, feeds, Settings, any app.
- **Pickup:** motion auto-arms even if the side-button event is missed. Short, fast flicks count. Tune sensitivity, dead zone, min length, and invert axes in More → settings.
- **Button:** click, double-click, triple-click, long-press — each remappable. Triple-click stops AirPen.
- **Air mouse:** S Pen IMU moves an on-screen cursor (up to ~10 m). Click, double-click, long-press, drag, scroll, precision mode.
- **STOP:** big Stop on Home, a floating STOP overlay over other apps, and a Stop action on the persistent notification. Cuts the S Pen session, air mouse, and background service immediately.
- **More gesture actions:** tabs, half-page scroll, find, bookmark, YouTube, Maps, Chrome, WhatsApp, Telegram, Netflix, Drive, Translate, share, zoom, jump to top/bottom, pull-to-refresh, D-pad, Wi-Fi/Bluetooth, notes, gallery, timer, wallet, and more. Search them in the Gestures tab.
- **Camera tracking:** fallback mouse for S Pens that dropped BLE (S25 Ultra and later). Track a bright tip in the camera.
- **Profiles:** System, Reading, Media, Gaming, Night, plus your own. Per-app profile switching.
- **Macros, HUD, haptics, invert axes, dead zone, sensitivity, export/import JSON.**
- **Practice pad** on the Home screen so you can train the recogniser with a finger or the S Pen on glass.

Air typing (air-write IME / letter recognition) has been removed.

## Supported hardware

BLE air-motion S Pen (Note10 / Note20, S20–S24 Ultra, Tab S6 and many Tab S pens).

S25 Ultra / S26 Ultra / Fold S Pens without a gyroscope: use **Camera** mode.

## Build

Needs JDK 17+ and Android SDK 34.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
cd AirPenStudio
chmod +x gradlew
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
