# AirPen Studio

A full S Pen customisation app for Samsung Galaxy phones and tablets: **air gestures**, **air mouse**, and **pointer hover**.

**1.4.0** goes back to the 1.1.1 S Pen feel and strips the extra modes that made Cycle get stuck.

- Live modes only: **GESTURE → MOUSE → POINTER** (Cycle walks those three and wraps)
- Live shapes only: circle CW/CCW, square, check, cross, wave — plus the 8 flicks and button clicks
- S Pen hover lab on Home: `View.setOnHoverListener`, `MotionEvent` hover enter/move/exit, stylus side button / S Pen Remote
- Video box muted preview on hover, hold side button to expand, glowing button outlines, destination hover cards
- Glow trail still available in Mouse / Pointer

Side button is still required for air gestures. Uninstall any older build first.

## Install on S22 Ultra (download APK)

On your phone, open:

**https://github.com/guccichine/AirPenStudio/releases**

Download **AirPenStudio.apk** from **1.4.0**, **uninstall any older AirPen Studio first**, then follow [INSTALL.md](INSTALL.md) (Accessibility + Appear on top + disable Samsung Air actions for other apps).

If Releases is empty, GitHub Actions is still building — wait a few minutes and refresh.

Samsung and S Pen are trademarks of Samsung Electronics. This project is not affiliated with Samsung.

## What you can do

- **Gestures:** up, down, left, right, four diagonals, circle, square, check, X, wave.
- **Flick to scroll (exactly one page):** hold the side button, flick **up** or **down**.
- **Button:** click, double-click, triple-click, long-press — each remappable.
- **Air mouse / Pointer:** S Pen IMU moves an on-screen cursor. Pointer keeps the glow trail visible while you hover.
- **Hover lab (in-app):** hover the S Pen tip over a video to autoplay a muted preview; hold the side button to expand; buttons get a gold outline; tabs show a destination card.
- **Practice pad** on Home: draw a live gesture, tap the chip, AirPen stores your stroke.
- **Accuracy controls** in More → settings: cardinal bias, motion smoothing, settle trim, heading offset, adaptive dead zone.

## Supported hardware

BLE air-motion S Pen (Note10 / Note20, S20–S24 Ultra, Tab S6 and many Tab S pens).

## Build

Needs JDK 17+ and Android SDK 34.
