# AirPen Studio

A full S Pen customisation app for Samsung Galaxy phones and tablets: **air gestures** (8-way flicks, diagonals, and shapes), **air mouse from across the room**, and **air typing**.

**1.3.2 adds air-mouse glow trails (28 styles), button hover glow, and hold-to-peek previews** on gestures and setup rows. 1.3.1 stroke training and 1.3.0 heading accuracy stay. Side button required. Uninstall any older build first.

**1.2.0** added the One Euro IMU filter, radial dead zone, velocity-weighted heading, and cardinal bias.

**1.1.1 was a rollback to the 1.0.8 S Pen engine.** 1.1.0 auto-armed every motion event and hogged the BLE pen so hardware S Pen stopped working. Uninstall 1.1.0 first.

## Install on S22 Ultra (download APK)

On your phone, open:

**https://github.com/guccichine/AirPenStudio/releases**

Download **AirPenStudio.apk** from **1.3.2**, **uninstall any older AirPen Studio first**, then follow [INSTALL.md](INSTALL.md) (Accessibility + Appear on top + disable Samsung Air actions for other apps).

If Releases is empty, GitHub Actions is still building — wait a few minutes and refresh.

Samsung and S Pen are trademarks of Samsung Electronics. This project is not affiliated with Samsung.

## What you can do

- **Gestures:** up, down, left, right, four diagonals, circle (CW/CCW), square, triangle, zigzag, check, X, plus, heart, infinity, arrow, caret, star, spiral, pigtail, brackets.
- **Flick to scroll (exactly one page):** hold the side button, flick **up** or **down**. Each flick moves **one full page** in that direction and stops — no extra fling. Works in Chrome, feeds, Settings, any app.
- **Button:** click, double-click, triple-click, long-press — each remappable.
- **Air mouse:** S Pen IMU moves an on-screen cursor (up to ~10 m). Click, double-click, long-press, drag, scroll, precision mode. Mouse tab picks a glow trail style (comet, laser, aurora, rainbow, lightning, and more).
- **Air type:** write letters in the air (now with $1 + point-cloud matching, extra templates, next-letter boost, and a handwriting trainer). Flicks: ← backspace, → space, ↓ enter, ↑ shift. Or point at a floating QWERTY.
- **More gesture actions:** YouTube, Maps, Chrome, WhatsApp, share, zoom, jump to top/bottom, pull-to-refresh, D-pad, Wi-Fi/Bluetooth settings, notes, gallery, and more. Search them in the Gestures tab.
- **New shapes:** wave, diamond, hook.
- **Camera tracking:** fallback mouse for S Pens that dropped BLE (S25 Ultra and later). Track a bright tip in the camera.
- **Profiles:** System, Reading, Media, Typing, plus your own. Per-app profile switching.
- **Macros, HUD, haptics, invert axes, dead zone, sensitivity, export/import JSON.**
- **Practice pad** on the Home screen: draw a gesture, tap the air-gesture button you meant, and AirPen stores that stroke as a template. Recognition prefers your samples.
- **Accuracy controls** in More → settings: cardinal bias, motion smoothing, settle trim, heading offset, adaptive dead zone.
- **Hold-to-peek:** hold a gesture row or a setup permission row to preview the clip and the page it opens. Buttons glow gold when pressed.

## Supported hardware

BLE air-motion S Pen (Note10 / Note20, S20–S24 Ultra, Tab S6 and many Tab S pens).

S25 Ultra / S26 Ultra / Fold S Pens without a gyroscope: use **Camera** mode.

## Build

Needs JDK 17+ and Android SDK 34.
