# Install on Galaxy S22 Ultra

The S22 Ultra has a BLE S Pen with air motion. This is the right phone for AirPen Studio.

## Fastest path (1.3.1 trainer)

**1.3.1** learns the way you actually draw: draw a gesture, tap the air-gesture it should be, and AirPen matches your samples. Side button is still required (1.1.0 auto-arm is gone). 1.3 accuracy (ballistic heading, bent-stroke gate) is still in this build.

1. **Uninstall AirPen Studio** completely (Settings → Apps → AirPen Studio → Uninstall). This also frees the S Pen.
2. If the hardware S Pen is still dead: pop it in and out, then reboot.
3. On the phone, open: **https://github.com/guccichine/AirPenStudio/releases**
4. Under **AirPen Studio 1.3.1**, tap **AirPenStudio.apk** and download it.
   - If 1.3.1 is still building, use **1.3.0**: https://github.com/guccichine/AirPenStudio/releases/download/v1.3.0/AirPenStudio.apk
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

- **Train a gesture:** Home practice pad → draw it with a finger → tap the matching air-gesture chip. Draw it again — it should now match yours. Up to 8 samples per gesture.
- **Gesture:** hold the side button, **flick up / down to scroll exactly one page**, or draw left/right/diagonal / a circle/square/triangle, then release.
- **Mouse:** wave the pen; the cursor follows. Click the side button to tap.
- **Type:** hold the button and write a letter in the air. If it guesses wrong, Home → **Train letters instead**, draw it, tap the right letter so it learns.
