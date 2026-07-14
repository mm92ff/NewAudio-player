-- Aggregate Compose activity inside explicit NewAudio:* measurement windows.
-- If the trace lacks such a window the query falls back to trace bounds so an
-- investigator still gets data; summarize-trace.ps1 reports this as a failure.
WITH newaudio_measurement AS (
  SELECT MIN(ts) AS start_ts, MAX(ts + dur) AS end_ts
  FROM slice
  WHERE name GLOB 'NewAudio:*' AND dur > 0
), benchmark_measurement AS (
  SELECT MIN(ts) AS start_ts, MAX(ts + dur) AS end_ts
  FROM slice
  WHERE lower(name) LIKE '%measureblock%' AND dur > 0
), all_trace_bounds AS (
  SELECT start_ts, end_ts FROM trace_bounds
), bounds AS (
  SELECT
    COALESCE(newaudio_measurement.start_ts, benchmark_measurement.start_ts,
      all_trace_bounds.start_ts) AS start_ts,
    COALESCE(newaudio_measurement.end_ts, benchmark_measurement.end_ts,
      all_trace_bounds.end_ts) AS end_ts
  FROM newaudio_measurement
  CROSS JOIN benchmark_measurement
  CROSS JOIN all_trace_bounds
), app_slices AS (
  SELECT
    s.name,
    s.dur,
    t.name AS thread_name
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t ON tt.utid = t.utid
  JOIN process p ON t.upid = p.upid
  CROSS JOIN bounds b
  WHERE (p.name = 'com.example.newaudio'
      OR p.name GLOB 'com.example.newaudio:*')
    AND s.ts < b.end_ts
    AND s.ts + s.dur > b.start_ts
    AND s.dur > 0
    AND (
      lower(s.name) LIKE '%compose%'
      OR lower(s.name) LIKE '%recompose%'
      OR lower(s.name) LIKE '%recomposition%'
      OR lower(s.name) LIKE '%snapshot%'
      OR s.name GLOB '*MainAppScreen*'
      OR s.name GLOB '*FileBrowserScreen*'
      OR s.name GLOB '*FileBrowserList*'
      OR s.name GLOB '*VideoGalleryGrid*'
      OR s.name GLOB '*MiniPlayer*'
      OR s.name GLOB '*MiniPlayerProgressBarHost*'
      OR s.name GLOB '*FullScreenPlayer*'
      OR s.name GLOB '*VideoFullscreenOverlay*'
      OR s.name GLOB '*PlayerAlbumArt*'
      OR s.name GLOB '*SongDetails*'
      OR s.name GLOB '*PlayerControls*'
      OR s.name GLOB '*PlayerSeekBarHost*'
      OR s.name GLOB '*VideoMarker*'
    )
)
SELECT
  name AS slice_name,
  COALESCE(thread_name, '<unknown>') AS thread_name,
  COUNT(*) AS occurrence_count,
  ROUND(SUM(dur) / 1000000.0, 3) AS total_duration_ms,
  ROUND(AVG(dur) / 1000000.0, 3) AS average_duration_ms,
  ROUND(PERCENTILE(dur, 50) / 1000000.0, 3) AS p50_duration_ms,
  ROUND(PERCENTILE(dur, 90) / 1000000.0, 3) AS p90_duration_ms,
  ROUND(PERCENTILE(dur, 95) / 1000000.0, 3) AS p95_duration_ms,
  ROUND(PERCENTILE(dur, 99) / 1000000.0, 3) AS p99_duration_ms,
  ROUND(MAX(dur) / 1000000.0, 3) AS maximum_duration_ms
FROM app_slices
GROUP BY name, thread_name
ORDER BY total_duration_ms DESC, maximum_duration_ms DESC, occurrence_count DESC
LIMIT 200;
