# AHMAD NetGuard

Android app to see and control devices on your home WiFi — device list,
custom naming, block/unblock. Built to eventually support multiple router
brands (Huawei first).

## What's real vs. placeholder right now

**Real / working structure:**
- Full Android project (Kotlin), opens directly in Android Studio
- Device list screen with cards (name, IP, MAC, signal, meta info)
- Block/Unblock button per device
- Custom device renaming, saved locally on your phone (`DeviceNameStore.kt`)
- `RouterAdapter` interface — the plug-in point for adding more router brands later
  without rewriting the UI

**Placeholder — needs YOUR router's real details filled in:**
- `HuaweiRouterAdapter.kt` — the login URL, form fields, and device-list HTML
  parsing are TEMPLATES. Every router firmware version has different exact
  endpoints, so this can't be guessed correctly — it must be captured from
  your specific router.

## Next step — capture your router's real API (do this once)

1. On your phone, connect to your home WiFi.
2. Open Chrome, go to `http://192.168.100.1`, log in like normal.
3. Go to the **Devices** page.
4. Chrome menu (⋮) → **Share** → if there's no direct "view source" option,
   instead open Chrome on a laptop/PC connected to the same WiFi, go to the
   same page, right-click → **View Page Source**, and save that as a `.html`
   file.
5. Send me that file (or just the relevant HTML around the device list table).

Once I see the real HTML/login form, I'll fill in the exact selectors and
this app will actually work against your router.

## Not implemented — and why

- **"Friend's phone shares hotspot forward" detection** — this needs
  packet-level TTL inspection, which stock Huawei firmware doesn't expose
  through its web admin panel. It would require flashing the router to
  OpenWrt custom firmware (advanced, and can brick incompatible routers).
  The `Device.possibleHotspotShare` field exists in the data model so the UI
  is ready for it, but it's not wired to anything real yet.
- **Multi-brand support** — only Huawei is scaffolded. Each additional brand
  (TP-Link, Xiaomi, etc.) needs its own adapter, built the same way: capture
  that router's real login/device-list pages, then implement `RouterAdapter`
  for it.

## How to open this project

1. Install **Android Studio** (free, from developer.android.com).
2. Open Android Studio → **Open** → select this `AhmadNetGuard` folder.
3. Let Gradle sync (needs internet the first time, to download dependencies).
4. Connect your Android phone via USB (enable Developer Options + USB
   debugging), or use an emulator.
5. Click Run ▶.
