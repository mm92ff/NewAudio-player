-- Scheduled CPU time for the application main and RenderThread threads inside
-- the measurement window. This measures running time, not wall-clock duration.
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
), selected_threads AS (
  SELECT
    t.utid,
    CASE WHEN t.tid = p.pid THEN 'MainThread' ELSE 'RenderThread' END AS role,
    COALESCE(t.name, '<unnamed>') AS thread_name,
    p.name AS process_name
  FROM thread t
  JOIN process p ON t.upid = p.upid
  WHERE (p.name = 'com.example.newaudio'
      OR p.name GLOB 'com.example.newaudio:*')
    AND (t.tid = p.pid OR t.name GLOB 'RenderThread*')
), running AS (
  SELECT
    st.role,
    st.thread_name,
    st.process_name,
    MAX(0, MIN(s.ts + s.dur, b.end_ts) - MAX(s.ts, b.start_ts)) AS overlap_ns,
    b.end_ts - b.start_ts AS window_ns
  FROM sched s
  JOIN selected_threads st ON s.utid = st.utid
  CROSS JOIN bounds b
  WHERE s.dur > 0
    AND s.ts < b.end_ts
    AND s.ts + s.dur > b.start_ts
)
SELECT
  role,
  thread_name,
  process_name,
  ROUND(SUM(overlap_ns) / 1000000.0, 3) AS scheduled_cpu_ms,
  ROUND(MAX(window_ns) / 1000000.0, 3) AS measurement_window_ms,
  ROUND(100.0 * SUM(overlap_ns) / MAX(window_ns), 2) AS cpu_utilization_percent
FROM running
GROUP BY role, thread_name, process_name
ORDER BY CASE role WHEN 'MainThread' THEN 0 ELSE 1 END,
  scheduled_cpu_ms DESC;
