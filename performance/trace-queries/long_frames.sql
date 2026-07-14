-- Individual late/janky NewAudio frames inside the selected measurement window.
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
    actual.ts,
    actual.dur,
    expected.dur AS expected_dur,
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
)
SELECT
  ROW_NUMBER() OVER (ORDER BY f.ts) AS frame_index,
  ROUND((f.ts - b.start_ts) / 1000000.0, 3) AS start_ms,
  ROUND(f.dur / 1000000.0, 3) AS duration_ms,
  ROUND(f.expected_dur / 1000000.0, 3) AS expected_duration_ms,
  ROUND(f.overrun_ns / 1000000.0, 3) AS overrun_ms,
  f.expected_frame_missing,
  COALESCE(f.jank_type, 'None') AS jank_type,
  f.on_time_finish
FROM frames f
CROSS JOIN bounds b
WHERE f.expected_frame_missing = 1
   OR f.overrun_ns > 0
   OR f.on_time_finish = 0
   OR (f.jank_type IS NOT NULL AND f.jank_type != 'None')
ORDER BY f.dur DESC, f.ts
LIMIT 200;
