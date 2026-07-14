-- Correlate late/janky frames with overlapping Compose/app slices. The first
-- rows per frame are the slices with the greatest temporal overlap.
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
    COALESCE(n.start_ts, m.start_ts, t.start_ts) AS start_ts,
    COALESCE(n.end_ts, m.end_ts, t.end_ts) AS end_ts
  FROM newaudio_measurement n
  CROSS JOIN benchmark_measurement m
  CROSS JOIN all_trace_bounds t
), frames AS (
  SELECT
    actual.upid,
    actual.ts,
    actual.dur,
    CASE WHEN expected.dur IS NULL THEN NULL
      ELSE MAX(0, actual.dur - expected.dur) END AS overrun_ns,
    CASE WHEN expected.dur IS NULL THEN 1 ELSE 0 END AS expected_frame_missing,
    actual.jank_type,
    actual.on_time_finish
  FROM actual_frame_timeline_slice actual
  JOIN process p ON actual.upid = p.upid
  LEFT JOIN expected_frame_timeline_slice expected
    ON actual.upid = expected.upid
   AND actual.surface_frame_token = expected.surface_frame_token
   AND actual.display_frame_token = expected.display_frame_token
  CROSS JOIN bounds b
  WHERE (p.name = 'com.example.newaudio'
      OR p.name GLOB 'com.example.newaudio:*')
    AND actual.surface_frame_token != 0
    AND actual.ts < b.end_ts
    AND actual.ts + actual.dur > b.start_ts
    AND actual.dur > 0
), long_frames AS (
  SELECT * FROM frames
  WHERE expected_frame_missing = 1
     OR overrun_ns > 0
     OR on_time_finish = 0
     OR (jank_type IS NOT NULL AND jank_type != 'None')
), app_slices AS (
  SELECT s.ts, s.dur, s.name, t.name AS thread_name, t.upid
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t ON tt.utid = t.utid
  JOIN process p ON t.upid = p.upid
  WHERE (p.name = 'com.example.newaudio'
      OR p.name GLOB 'com.example.newaudio:*')
    AND s.dur > 0
    AND (
      lower(s.name) LIKE '%compose%'
      OR lower(s.name) LIKE '%recompose%'
      OR lower(s.name) LIKE '%snapshot%'
      OR s.name GLOB '*MainAppScreen*'
      OR s.name GLOB '*FileBrowserScreen*'
      OR s.name GLOB '*FileBrowserList*'
      OR s.name GLOB '*VideoGalleryGrid*'
      OR s.name GLOB '*MiniPlayer*'
      OR s.name GLOB '*FullScreenPlayer*'
      OR s.name GLOB '*VideoFullscreenOverlay*'
      OR s.name GLOB '*PlayerAlbumArt*'
      OR s.name GLOB '*SongDetails*'
      OR s.name GLOB '*PlayerControls*'
      OR s.name GLOB '*PlayerSeekBarHost*'
      OR s.name GLOB '*VideoMarker*'
    )
), overlaps AS (
  SELECT
    f.ts AS frame_ts,
    f.dur AS frame_dur,
    f.overrun_ns,
    f.expected_frame_missing,
    f.jank_type,
    s.name AS slice_name,
    s.thread_name,
    s.dur AS slice_dur,
    MIN(f.ts + f.dur, s.ts + s.dur) - MAX(f.ts, s.ts) AS overlap_ns
  FROM long_frames f
  JOIN app_slices s
    ON s.upid = f.upid
   AND s.ts < f.ts + f.dur
   AND s.ts + s.dur > f.ts
), ranked AS (
  SELECT *, ROW_NUMBER() OVER (
    PARTITION BY frame_ts
    ORDER BY overlap_ns DESC, slice_dur DESC, slice_name
  ) AS overlap_rank
  FROM overlaps
)
SELECT
  ROUND((r.frame_ts - b.start_ts) / 1000000.0, 3) AS frame_start_ms,
  ROUND(r.frame_dur / 1000000.0, 3) AS frame_duration_ms,
  ROUND(r.overrun_ns / 1000000.0, 3) AS frame_overrun_ms,
  r.expected_frame_missing,
  COALESCE(r.jank_type, 'None') AS jank_type,
  r.overlap_rank,
  r.slice_name,
  COALESCE(r.thread_name, '<unknown>') AS thread_name,
  ROUND(r.overlap_ns / 1000000.0, 3) AS overlap_ms,
  ROUND(r.slice_dur / 1000000.0, 3) AS slice_duration_ms
FROM ranked r
CROSS JOIN bounds b
WHERE r.overlap_rank <= 8
ORDER BY r.frame_ts, r.overlap_rank
LIMIT 400;
