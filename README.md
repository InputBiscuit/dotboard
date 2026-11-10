# Dotboard

An open-source Android home-screen widget that looks like a UK station dot-matrix departure board,
with live departures from any station.

<!-- Screenshots: add images to fastlane/metadata/android/en-US/images/phoneScreenshots/ and link them here. -->
*Screenshots coming soon.*

## Features

- A dot-matrix LED board drawn dot by dot, in amber, yellow-green or white
- Live departures: time, destination, platform, and "Exp 22:14", "Delayed" or "Cancelled", the way real
  boards show them
- Scrolling calling points, alternating next trains and a ticking clock with seconds
- Several boards on one home screen, each with its own station and an optional "calling at" filter
- Resizable: bigger boards show more trains, and it adapts to both screens of a foldable
- Battery-friendly: the scrolling text, alternating rows and clock all run in your launcher, so Dotboard only
  wakes up to refresh departures (every 15, 30 or 60 minutes, and to drop a train once it has left). Tap a
  board to refresh it
- Live times straight away: no sign-up or API key needed (or use your own key if you prefer)
- Built-in sample boards for trying it out
- No ads, no tracking, no analytics, no Google Play Services. Free and open source (GPL-3.0)

Requires Android 12 or newer.

## Install

### GitHub Releases

Download the latest `dotboard-vX.Y.Z.apk` from the [Releases](../../releases) page and open it on your
phone. You may need to allow installing apps from your browser or file manager.

### Obtainium (automatic updates)

[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates apps straight from GitHub Releases:

1. Install Obtainium.
2. Tap **Add app** and paste this repository's URL.
3. Tap **Add**, then **Install**. Obtainium will notify you when a new release is out.

(Obtainium needs the repository to be public, or a GitHub token added in its settings.)

## Live data and privacy

Dotboard uses the **Live Departure Board** feed from the [Rail Data Marketplace](https://raildata.org.uk/).

- **By default** the app fetches boards through Dotboard's proxy, a small Cloudflare Worker (source in
  [`proxy/`](proxy/)) that holds an API key, so you don't need one. The proxy sees which station (and
  "calling at" station) you ask for and your IP address. It uses the IP only briefly to rate-limit abuse, caches
  each board for about 45 seconds so people watching the same station share one request, and keeps no logs.
- **With your own key** the app talks to the Rail Data Marketplace directly and skips the proxy.

If the proxy (or the feed) can't be reached, boards keep showing the last departures fetched, marked
"Updated hh:mm".

### Using your own API key (optional)

1. Register at [raildata.org.uk](https://raildata.org.uk/) and confirm your email address.
2. Search the data catalogue for **Live Departure Board** and subscribe to it (free). Approval can take up to
   a few working days.
3. Once approved, open your subscription and copy its **Consumer key**.
4. In Dotboard, open **Settings**, paste the key and tap **Save key**.

Your key is stored encrypted on your phone (Android Keystore) and is only sent to `api1.raildata.org.uk`.

## Using it

- **Add a board:** long-press your home screen → **Widgets** → **Dotboard** → **Departure board**, pick a
  station (and optionally a station it should call at), then tap **Add board**. Or use **Add a board** in
  the app.
- **Change a board's station:** long-press it and choose the settings/reconfigure option.
- **Refresh now:** tap the board.
- **Bigger or smaller:** resize the widget; the board redraws to fit.

## Building

Requirements: JDK 17 or newer and the Android SDK (platform 37, build-tools 37).

```sh
echo "sdk.dir=/path/to/Android/sdk" > local.properties   # not committed
./gradlew assembleDebug    # app/build/outputs/apk/debug/
./gradlew :core:test       # unit tests (pure Kotlin: API client, renderer, data)
```

`./gradlew assembleRelease` builds an unsigned, minified release APK. Signed releases are built by GitHub
Actions from a version tag (`.github/workflows/release.yml`).

### Project layout

- `core/`: pure Kotlin/JVM. Rail Data Marketplace client, departure models, sample data, board cache, and the
  dot-matrix renderer (layout, bitmap fonts, round-dot rasteriser, dot mask). Unit-tested on the JVM.
- `app/`: the Android app and widget (Compose screens, RemoteViews widget, WorkManager refresh).
- `proxy/`: the Cloudflare Worker that proxies board requests (`npm test`; deploy with `npx wrangler deploy` and
  set the key with `npx wrangler secret put RDM_API_KEY`).
- `tools/fontgen/`: turns the Dot Matrix TTFs into the bitmap fonts in `core/src/main/resources`.
- `tools/icon/`: generates the app and store icons.
- `fastlane/metadata/`: store listing for F-Droid.

## Credits

- Live data: Powered by National Rail Enquiries, via the Rail Data Marketplace.
- Typeface: [Dot Matrix](https://github.com/DanielHartUK/Dot-Matrix-Typeface) by Daniel Hart, under the
  [SIL Open Font License 1.1](tools/fontgen/fonts/OFL.txt). Dotboard converts it to bitmap fonts with
  `tools/fontgen/FontGen.java` (`java -Djava.awt.headless=true tools/fontgen/FontGen.java`).
- Station names and CRS codes: [`davwheat/uk-railway-stations`](https://github.com/davwheat/uk-railway-stations)
  by David Wheatley, derived from [Trainline EU's stations dataset](https://github.com/trainline-eu/stations)
  and [their sources](https://github.com/trainline-eu/stations#licence). Licensed under the
  [Open Database License (ODbL) 1.0](licenses/stations-ODbL-1.0.txt).
  Dotboard's modified copy (trimmed to CRS code and name) is at
  [`app/src/main/assets/stations.csv`](app/src/main/assets/stations.csv) and is also licensed under ODbL 1.0.

Dotboard is an independent project and isn't affiliated with or endorsed by National Rail Enquiries,
Rail Delivery Group or any train operator.

## Licence

Copyright (C) 2026 Dotboard contributors.

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, version 3 of the License.
See [LICENSE](LICENSE).

The bundled station data is licensed separately under ODbL 1.0, and the Dot Matrix typeface (and the bitmap
fonts made from it) under the SIL Open Font License 1.1.
