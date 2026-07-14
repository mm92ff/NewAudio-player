from __future__ import annotations

import math
import sqlite3
import sys
from pathlib import Path


class Percentile:
    def __init__(self) -> None:
        self.values: list[float] = []
        self.percent = 50.0

    def step(self, value: float | None, percent: float) -> None:
        self.percent = float(percent)
        if value is not None:
            self.values.append(float(value))

    def finalize(self) -> float | None:
        if not self.values:
            return None
        values = sorted(self.values)
        rank = self.percent / 100.0 * (len(values) - 1)
        lower = math.floor(rank)
        upper = math.ceil(rank)
        if lower == upper:
            return values[lower]
        return values[lower] + (rank - lower) * (values[upper] - values[lower])


def main(query_root: Path) -> None:
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.create_aggregate("PERCENTILE", 2, Percentile)
    db.executescript(
        """
        CREATE TABLE slice(id INTEGER, ts INTEGER, dur INTEGER, name TEXT, track_id INTEGER);
        CREATE TABLE trace_bounds(start_ts INTEGER, end_ts INTEGER);
        CREATE TABLE process(upid INTEGER, pid INTEGER, name TEXT);
        CREATE TABLE thread(utid INTEGER, upid INTEGER, tid INTEGER, name TEXT);
        CREATE TABLE thread_track(id INTEGER, utid INTEGER);
        CREATE TABLE sched(ts INTEGER, dur INTEGER, utid INTEGER);
        CREATE TABLE actual_frame_timeline_slice(
          upid INTEGER, ts INTEGER, dur INTEGER, surface_frame_token INTEGER,
          display_frame_token INTEGER, jank_type TEXT, on_time_finish INTEGER
        );
        CREATE TABLE expected_frame_timeline_slice(
          upid INTEGER, ts INTEGER, dur INTEGER, surface_frame_token INTEGER,
          display_frame_token INTEGER
        );

        INSERT INTO trace_bounds VALUES(0, 100000000);
        INSERT INTO process VALUES(1, 10, 'com.example.newaudio');
        INSERT INTO thread VALUES(1, 1, 10, 'main'), (2, 1, 11, 'RenderThread');
        INSERT INTO thread_track VALUES(1, 1), (2, 2);
        INSERT INTO slice VALUES
          (1, 1000000, 80000000, 'NewAudio:NV01', 1),
          (2, 2000000, 25000000, 'Compose:recompose', 1),
          (3, 3000000, 18000000, 'VideoGalleryGrid', 2),
          (4, 36000000, 5000000, 'Compose:layout', 1);
        INSERT INTO sched VALUES(1000000, 20000000, 1), (3000000, 10000000, 2);
        INSERT INTO actual_frame_timeline_slice VALUES
          (1, 5000000, 20000000, 1, 1, 'App Deadline Missed', 0),
          (1, 35000000, 12000000, 2, 2, 'None', 1);
        INSERT INTO expected_frame_timeline_slice VALUES
          (1, 5000000, 16000000, 1, 1);
        """
    )

    results: dict[str, list[sqlite3.Row]] = {}
    for name in (
        "discover_compose_slices",
        "compose_hotspots",
        "frame_summary",
        "long_frames",
        "frame_slice_correlation",
        "main_thread_summary",
    ):
        sql = (query_root / f"{name}.sql").read_text(encoding="utf-8")
        results[name] = list(db.execute(sql))

    discovery = results["discover_compose_slices"]
    assert any(row["slice_kind"] == "measurement_window" for row in discovery)
    assert any(row["slice_kind"] == "compose" for row in discovery)
    assert results["compose_hotspots"], "compose hotspot query returned no rows"

    summary = results["frame_summary"][0]
    assert summary["frame_count"] == 2
    assert summary["expected_frame_missing_count"] == 1
    assert summary["overrun_frame_count"] == 1

    long_frames = results["long_frames"]
    missing = next(row for row in long_frames if row["expected_frame_missing"] == 1)
    assert missing["expected_duration_ms"] is None
    assert missing["overrun_ms"] is None, "missing expected frame produced synthetic overrun"

    correlation = results["frame_slice_correlation"]
    missing_correlations = [row for row in correlation if row["expected_frame_missing"] == 1]
    assert missing_correlations
    assert all(row["frame_overrun_ms"] is None for row in missing_correlations)
    assert {row["role"] for row in results["main_thread_summary"]} == {"MainThread", "RenderThread"}
    print("Trace SQL semantic self-test passed.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: trace-sql-selftest.py <query-root>")
    main(Path(sys.argv[1]).resolve())
