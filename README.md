# Private DNS Switcher

An Android app that watches network changes (Wi-Fi, mobile data, Ethernet,
VPN) and switches the system's Private DNS mode based on one of four
selectable detection modes.

## Modes

1. **DNS query to server (IP)** — sends a raw DNS query (UDP, port 53)
   directly to a configured IP address for a configured domain. A valid
   response means the network is trusted.
2. **System domain resolve** — forces Private DNS to automatic, then
   resolves a configured domain using the normal system resolver. A
   successful resolve means the local network's own DNS knows this domain,
   so the network is trusted.
3. **Ping IP address** — pings a configured IP address. Reachable means the
   network is trusted.
4. **Wi-Fi network name** — compares the currently connected Wi-Fi SSID
   against a list of trusted SSIDs.

In all four modes: trusted network -> `private_dns_mode` is set to
`opportunistic` (the "Automatic" mode in the Android UI). Not trusted ->
`private_dns_mode` is set to `hostname`, with `private_dns_specifier` set
to the configured hostname.

## How it works

The foreground service `NetworkMonitorService` registers a default network
callback (`ConnectivityManager.registerDefaultNetworkCallback`), which
fires on Wi-Fi connect/disconnect, mobile data connect/disconnect, Ethernet
connect/disconnect, VPN connect/disconnect, and switches between any of
these. On each event, after a configurable delay (to let the network
settle), `ModeEvaluator` runs whichever mode is currently selected and
applies the result via `PrivateDnsManager`.

## Required step: WRITE_SECURE_SETTINGS permission

Android does not allow regular apps to modify protected system settings
(`Settings.Global`) without a special permission, which cannot be obtained
through a normal runtime permission dialog. It must be granted once via ADB
(no root required):

```bash
adb shell pm grant com.alexloi.pdnsswitcher android.permission.WRITE_SECURE_SETTINGS
```

This needs to be done once after installing the app (and again after
reinstalling/updating, if the permission is revoked).

The app also has a "Permission instructions" button showing this same command.

## Required step for Mode 4 (Wi-Fi network name)

Since Android 8.1, reading the real connected Wi-Fi SSID requires the
`ACCESS_FINE_LOCATION` runtime permission and device location services to
be turned on. The app has a "Grant location permission" button that appears
when Mode 4 is selected.

## Building

1. Open the project folder in Android Studio (Giraffe/Koala or newer).
2. Wait for Gradle sync to finish.
3. Build/run on a device (Run ▸ app).

Or from the command line:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.alexloi.pdnsswitcher android.permission.WRITE_SECURE_SETTINGS
```

## Usage

1. Open the app.
2. Pick a detection mode.
3. Fill in the fields relevant to that mode (IP, domain, or trusted SSIDs).
4. Set the hostname to use for Private DNS when the network is judged not
   trusted, e.g. `dns.google` or `1dot1dot1dot1.cloudflare-dns.com`.
5. Set the delay (in seconds) to wait after a network event before
   checking.
6. Tap "Save".
7. Tap "Start monitoring" — a persistent notification will appear, showing
   the result of the last check.

## Project structure

- `com.alexloi.pdnsswitcher` — UI, service, prefs, mode selection, and the
  code that writes the Private DNS setting.
- `com.alexloi.pdnsswitcher.probe` — the four detection mechanisms
  (`DnsProbe`, `SystemResolveProbe`, `PingProbe`, `WifiSsidChecker`), each
  independent of the others and of the service.

## Limitations and notes

- The service is a foreground service, so it will show a persistent
  notification (an Android requirement for long-running background work).
  It's worth excluding the app from battery optimization so the system
  doesn't kill the service over time.
- If `WRITE_SECURE_SETTINGS` hasn't been granted, the notification and the
  main screen will show that writing to system settings failed.
