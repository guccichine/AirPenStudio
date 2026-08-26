# Install on Galaxy S22 Ultra

The S22 Ultra has a BLE S Pen with air motion. This is the right phone for AirPen Studio.

## Fastest path (rollback to 1.0.8)

**1.1.0 broke S Pen** (auto-arm kept the BLE session hogged). This 1.1.1 build is the 1.0.8 engine again.

1. **Uninstall AirPen Studio** completely (Settings → Apps → AirPen Studio → Uninstall). This also frees the S Pen.
2. If the hardware S Pen is still dead: pop it in and out, then reboot.
3. On the phone, open: **https://github.com/guccichine/AirPenStudio/releases**
4. Under **AirPen Studio 1.1.1**, tap **AirPenStudio.apk** and download it.
   - If 1.1.1 is still building, use **1.0.8** instead: https://github.com/guccichine/AirPenStudio/releases/download/v1.0.8/AirPenStudio.apk
5. Open the downloaded file (Files or Chrome). Allow install from that app if Samsung asks.
6. Open **AirPen Studio**.

If the Releases page is empty, GitHub is still building. Wait a couple of minutes and refresh.

## Required setup on the phone

1. **Settings → Accessibility → Installed apps → AirPen Studio** → On.
2. **Settings → Apps → AirPen Studio → Appear on top** → On.
3. **Settings → Notifications** → allow AirPen.
4. **Settings → Advanced features → S Pen → Air actions** → turn Air actions **off** for Camera and other apps so they don't steal the side button.
5. Pull the S Pen out. In AirPen, Home should say **CONNECTED**.

## Use it

- **Gesture:** hold the side button, **flick up / down to scroll exactly one page**, or draw left/right/diagonal / a circle/square/triangle, then release.
- **Mouse:** wave the pen; the cursor follows. Click the side button to tap.
- **Type:** hold the button and write a letter in the air. If it guesses wrong, Home → **Recognize as letter**, draw it, tap the right letter so it learns.

You can also draw on the **Practice pad** on Home with your finger to test recognition.
