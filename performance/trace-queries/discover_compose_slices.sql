-- Discover the actual Compose slice names emitted by the pinned runtime and
-- the explicit NewAudio measurement windows. Keep this query broad: its output
-- is the input used to refine version-specific hotspot rules.
WITH app_slices AS (
  SELECT
    s.id,
    s.ts,
    s.dur,
    s.name,
    s.track_id,
    t.name AS thread_name,
    p.name AS process_name
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t ON tt.utid = t.utid
  LEFT JOIN process p ON t.upid = p.upid
  WHERE p.name = 'com.example.newaudio'
     OR p.name GLOB 'com.example.newaudio:*'
), window_slices AS (
  SELECT
    s.name,
    t.name AS thread_name,
    p.name AS process_name,
    s.dur
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t ON tt.utid = t.utid
  LEFT JOIN process p ON t.upid = p.upid
  WHERE s.name GLOB 'NewAudio:*'
     OR lower(s.name) LIKE '%measureblock%'
), classified AS (
  SELECT
    CASE
      WHEN lower(name) LIKE '%compose%'
        OR lower(name) LIKE '%recompose%'
        OR lower(name) LIKE '%recomposition%'
        OR lower(name) LIKE '%snapshot%'
        OR name GLOB '*MainAppScreen*'
        OR name GLOB '*FileBrowserScreen*'
        OR name GLOB '*FileBrowserList*'
        OR name GLOB '*VideoGalleryGrid*'
        OR name GLOB '*MiniPlayer*'
        OR name GLOB '*MiniPlayerProgressBarHost*'
        OR name GLOB '*FullScreenPlayer*'
        OR name GLOB '*VideoFullscreenOverlay*'
        OR name GLOB '*PlayerAlbumArt*'
        OR name GLOB '*SongDetails*'
        OR name GLOB '*PlayerControls*'
        OR name GLOB '*PlayerSeekBarHost*'
        OR name GLOB '*VideoMarker*'
      THEN 'compose'
      ELSE NULL
    END AS slice_kind,
    name,
    thread_name,
    process_name,
    dur
  FROM app_slices
  UNION ALL
  SELECT
    CASE
      WHEN name GLOB 'NewAudio:*' THEN 'measurement_window'
      ELSE 'benchmark_measure_block'
    END AS slice_kind,
    name,
    thread_name,
    process_name,
    dur
  FROM window_slices
)
SELECT
  slice_kind,
  name AS slice_name,
  COALESCE(thread_name, '<unknown>') AS thread_name,
  COALESCE(process_name, '<unknown>') AS process_name,
  COUNT(*) AS occurrence_count,
  ROUND(SUM(dur) / 1000000.0, 3) AS total_duration_ms,
  ROUND(AVG(dur) / 1000000.0, 3) AS average_duration_ms,
  ROUND(MAX(dur) / 1000000.0, 3) AS maximum_duration_ms
FROM classified
WHERE slice_kind IS NOT NULL
  AND dur > 0
GROUP BY slice_kind, name, thread_name, process_name
ORDER BY
  CASE slice_kind WHEN 'measurement_window' THEN 0 ELSE 1 END,
  occurrence_count DESC,
  total_duration_ms DESC,
  slice_name
LIMIT 1000;
