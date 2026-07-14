# Fixture license

The benchmark media is generated from synthetic silence, test patterns, and
flat colors in `app/src/benchmark/assets/fixtures`. It is dedicated to the
public domain under CC0-1.0.

No user media, network resource, logo, photograph, or third-party recording is
included. The fixture manifest in this directory expands the runtime filenames,
playlists, markers, metadata, and exact source-template SHA-256 values used by
the benchmark-only target receiver. Re-running
`benchmark/tools/generate-fixtures.ps1` verifies the source hashes and refreshes
that derived manifest; no second binary media copy is packaged in the test APK.
