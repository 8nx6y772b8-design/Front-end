# Tuner

An IPTV front end for Fire TV and Android TV, in the style of TiviMate. You give it your M3U playlist; it gives you a remote-friendly channel browser and a fast live player.

**Status:** v0.1. Playlist loading, channel groups, logos and a full-screen player with channel zapping. The TV guide (EPG) is next.

## Install on a Fire TV Stick

1. On the Fire Stick: **Settings → My Fire TV → Developer options**.
   - If you can't see Developer options, open **About**, select the device name 7 times, then go back.
2. Install the free **Downloader** app (by AFTVnews) from the Amazon Appstore.
3. In Developer options → **Install unknown apps**, turn **Downloader** on.
4. Open Downloader and enter:
   `https://github.com/<owner>/<repo>/releases/download/latest/tuner.apk`
   (works when the repo is public).
5. Install it. Tuner appears under **Your Apps & Channels**.

Updates install over the top, because every build is signed with the same key in `app/debug.keystore`.

## Remote controls

| Where | Button | Action |
|---|---|---|
| Channel list | Up/Down | Move; moving through groups switches the channel list |
| Channel list | Centre | Play channel |
| Player | Up / Down | Previous / next channel in the group |
| Player | Centre, Left, Right | Show / hide the channel banner |
| Player | Play/Pause | Pause / resume |
| Player | Back | Back to the list, on the channel you were watching |

## Building

Every push to `main` builds on GitHub Actions and publishes `tuner.apk` to the `latest` release.
To build locally, open the folder in Android Studio and run the `app` configuration.

Stack: Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer (HLS + MPEG-TS), Coil for logos.

## Roadmap

- [ ] EPG: XMLTV download, streaming parser, Room database, now/next on the banner
- [ ] TV guide grid (the TiviMate-style timeline)
- [ ] Favourites and recently watched
- [ ] Xtream Codes login (server + username + password instead of a long URL)
- [ ] Enter the playlist from your phone (QR code / local web page) instead of typing on the TV
- [ ] Catch-up, number-key channel entry, auto-play last channel on start
