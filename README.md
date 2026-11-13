# Tuner

An IPTV front end for Fire TV and Android TV, in the style of TiviMate. You give it your M3U playlist; it gives you a remote-friendly channel browser and a fast live player.

**Status:** v0.2. New design with live preview, M3U or Xtream login, TV guide, and recording to a network drive (record now, timed, from the guide) with playback.

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
| Player | Menu ≡ | Record now / record this programme / schedule |
| Guide | OK | Watch (on now) or record (later) |
| Recordings | Menu ≡ | Play / delete / cancel |

## Building

Every push to `main` builds on GitHub Actions and publishes `tuner.apk` to the `latest` release.
To build locally, open the folder in Android Studio and run the `app` configuration.

Stack: Kotlin, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer (HLS + MPEG-TS), Coil for logos.

## Roadmap

- [x] EPG: XMLTV download, streaming parser, SQLite, now/next on the banner
- [x] TV guide grid (timeline) with record-from-guide
- [x] Xtream Codes login (server + username + password)
- [x] Recording to a network drive (SMB): record now, timed, from the guide, playback
- [ ] Favourites and recently watched
- [ ] Enter the playlist from your phone (QR code / local web page) instead of typing on the TV
- [ ] Catch-up, number-key channel entry, auto-play last channel on start
