# NewAudio benchmark media fixtures

These files are generated locally for deterministic performance tests. They
contain synthetic silence, tones, colors and text only; no third-party media is
embedded.

- `audio-template.wav`: 20 seconds, mono PCM silence.
- `audio-cover-{small,medium,large}.mp3`: approximately 20.036-second synthetic
  audio (the manifest allows 2 ms container/encoder tolerance) with
  embedded 96x96, 256x256 and 512x512 generated cover art.
- `video-template.mp4`: 20 seconds, 320x180 synthetic H.264/AAC video.
- `artwork-small.png`, `artwork.png`, `artwork-large.png`: generated benchmark
  artwork at 96x96, 256x256 and 512x512.

The benchmark-only setup receiver copies these templates to app-private files
with the deterministic names described by `fixture-manifest.json`. This source
set is absent from debug and release APKs.

The expanded benchmark manifest creates 30 audio and 48 video records. Video
previews deliberately include both artwork-backed entries and one entry whose
`thumbnailUri` is absent, forcing the `VideoFrameDecoder` path. Setup reports
either `COLD_EMPTY_IMAGE_CACHE` or `WARM_PRELOADED_IMAGE_CACHE` after safely
controlling Coil's app-private cache.
