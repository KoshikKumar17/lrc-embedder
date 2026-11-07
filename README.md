# LRC Embedder

Material 3 app that fetches lyrics from **LRCLib**, **Netease**, **QQ Music** and **Deezer** (plus **Spotify** as an optional fallback) and writes them
into your music files (MP3, M4A/MP4 AAC, FLAC, OGG, Opus, WAV ...) with TagLib.
If a format can't hold tags, a `.lrc` file is saved next to the song instead.

## Thanks & Credits 

> [SongSync](https://github.com/Lambada10/SongSync) for inspiration

## Optional: Spotify lyrics

Spotify lyrics come from the [spotify81 RapidAPI](https://rapidapi.com/airaudoeduardo/api/spotify81). It is only queried when
the other providers find no believable match, to save the free monthly request quota. Without a key it is skipped.

- **Build-time key (works for every user):** add `RAPIDAPI_KEY=your_key` to `local.properties`, or set a
  `RAPIDAPI_KEY` GitHub Actions secret for the release workflow.
- **Per-user key:** Settings -> "Spotify lyrics (RapidAPI key)". A key entered here overrides the built-in one.
- 
