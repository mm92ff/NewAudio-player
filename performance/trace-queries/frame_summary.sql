-- FrameTimeline summary for NewAudio. Frame duration comes from the actual
-- timeline; expected duration is matched by the app surface/display tokens.
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
), frames AS (
  SELECT
    actual.ts,
    actual.dur / 1000000.0 AS duration_ms,
    CASE WHEN expected.dur IS NULL THEN NULL
      ELSE MAX(0, actual.dur - expected.dur) / 1000000.0 END AS overrun_ms,
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
  COUNT(*) AS frame_count,
  ROUND(PERCENTILE(duration_ms, 50), 3) AS frame_duration_p50_ms,
  ROUND(PERCENTILE(duration_ms, 90), 3) AS frame_duration_p90_ms,
  ROUND(PERCENTILE(duration_ms, 95), 3) AS frame_duration_p95_ms,
  ROUND(PERCENTILE(duration_ms, 99), 3) AS frame_duration_p99_ms,
  ROUND(MAX(duration_ms), 3) AS maximum_frame_duration_ms,
  ROUND(PERCENTILE(overrun_ms, 50), 3) AS frame_overrun_p50_ms,
  ROUND(PERCENTILE(overrun_ms, 90), 3) AS frame_overrun_p90_ms,
  ROUND(PERCENTILE(overrun_ms, 95), 3) AS frame_overrun_p95_ms,
  ROUND(PERCENTILE(overrun_ms, 99), 3) AS frame_overrun_p99_ms,
  SUM(CASE
    WHEN on_time_finish = 0
      OR (jank_type IS NOT NULL AND jank_type != 'None')
    THEN 1 ELSE 0 END) AS janky_frame_count,
  SUM(CASE WHEN overrun_ms > 0 THEN 1 ELSE 0 END) AS overrun_frame_count
  ,SUM(expected_frame_missing) AS expected_frame_missing_count
FROM frames;
