# LRC Embedder

Material 3 app that fetches lyrics from **LRCLib**, **Netease**, **QQ Music** and **Deezer** and writes them
into your music files (MP3, M4A/MP4 AAC, FLAC, OGG, Opus, WAV ...) with TagLib.
If a format can't hold tags, a `.lrc` file is saved next to the song instead.

## Matching

Song matching and scoring (messy file-name parsing, title/artist/duration confidence scoring, wrong-singer
veto, iTunes/Deezer last-resort rescue) is ported from
[MusicResync](https://github.com/DynamycSound/MusicResync), which is GPL-3.0 licensed, so this project
must be distributed under GPL-3.0 as well.
