# Private DNS Switcher

An Android app that, on every Wi-Fi connect/disconnect event, checks whether
a given IP address responds as a DNS server, and switches the system's
Private DNS mode based on the result.

## How it works

1. The foreground service `WifiMonitorService` listens for Wi-Fi events via
   `ConnectivityManager.NetworkCallback` (both `onAvailable` and `onLost`).
2. On each event, after a configurable delay (to let the network settle),
   a real DNS query (raw UDP packet on port 53) is sent to the configured IP,
   resolving a configurable domain name.
3. If a valid DNS response is received, `private_dns_mode` is set to
   `opportunistic` (this is the "Automatic" mode in the Android UI).
4. If there is no response (2 second timeout), `private_dns_mode` is set to
   `hostname`, and `private_dns_specifier` is set to the configured hostname.

The check is performed the same way on both Wi-Fi connect and disconnect -
in both cases the app simply checks whether the given IP is reachable over
the current active network.

## Required step: WRITE_SECURE_SETTINGS permission

Android does not allow regular apps to modify protected system settings
(`Settings.Global`) without a special permission, which cannot be obtained
through a normal runtime permission dialog. It must be granted once via ADB
(no root required):

```bash
adb shell pm grant com.alexloi.privatednsswitcher android.permission.WRITE_SECURE_SETTINGS
```

This needs to be done once after installing the app (and again after
reinstalling/updating, if the permission is revoked).

The app also has a "Permission instructions" button showing this same command.

## Building

1. Open the project folder in Android Studio (Giraffe/Koala or newer).
2. Wait for Gradle sync to finish.
3. Build/run on a device (Run ▸ app).

Or from the command line:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.alexloi.privatednsswitcher android.permission.WRITE_SECURE_SETTINGS
```

## Usage

1. Open the app.
2. In "IP address to probe", enter the address whose reachability means
   "I'm on the trusted network" (default `10.10.1.1`).
3. In "Hostname for Private DNS", enter the DNS provider hostname for strict
   mode (DoT), used when the IP is unreachable, e.g. `dns.google` or
   `1dot1dot1dot1.cloudflare-dns.com`.
4. In "Delay before probing", set how many seconds to wait after a Wi-Fi
   event before sending the probe (default 2).
5. In "Domain name to resolve", set which domain the probe query asks for
   (default `google.com`) - only used to build the query, the actual
   answer's content isn't checked, just whether the server responds.
6. Tap "Save".
7. Tap "Start monitoring" - a persistent notification will appear, showing
   the result of the last probe.

## Limitations and notes

- The service is a foreground service, so it will show a persistent
  notification (an Android requirement for long-running background work).
  It's worth excluding the app from battery optimization so the system
  doesn't kill the service over time.
- `10.10.1.1` is typically a private address, so the probe is meaningful
  when the phone is actually on that local network (otherwise the packet
  simply won't arrive, which correctly counts as "unreachable").
- If `WRITE_SECURE_SETTINGS` hasn't been granted, the notification and the
  main screen will show that writing to system settings failed.
