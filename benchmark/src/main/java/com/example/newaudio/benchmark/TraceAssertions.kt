package com.example.newaudio.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceMetric
import androidx.benchmark.traceprocessor.TraceProcessor
import androidx.test.platform.app.InstrumentationRegistry

internal object TraceAssertions {
    fun isFullTracingEnabled(): Boolean =
        InstrumentationRegistry.getArguments()
            .getString("androidx.benchmark.fullTracing.enable")
            .toBoolean()
}

/**
 * A paused player or a static Compose overlay above a SurfaceView video may correctly
 * produce no app RenderThread frames. FrameTimingMetric rejects an empty frame sample,
 * so these idle journeys use explicit trace-window/count metrics and treat a zero frame
 * count as a valid, informative result.
 */
@OptIn(ExperimentalMetricApi::class)
internal class IdleWindowMetric(
    private val expectedJourney: TraceJourney
) : TraceMetric() {
    override fun getMeasurements(
        captureInfo: Metric.CaptureInfo,
        traceSession: TraceProcessor.Session
    ): List<Metric.Measurement> {
        val packageName = captureInfo.targetPackageName.replace("'", "''")
        val sectionName = expectedJourney.sectionName.replace("'", "''")
        val windowCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice
            WHERE name = '$sectionName' AND dur > 0
            """.trimIndent()
        )
        check(windowCount == 1L) {
            "Expected exactly one $sectionName measurement window, found $windowCount"
        }
        val frameCount = traceSession.scalar(
            """
            WITH measurement_window AS (
              SELECT ts, ts + dur AS end_ts
              FROM slice
              WHERE name = '$sectionName' AND dur > 0
              LIMIT 1
            )
            SELECT COUNT(*) AS value
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            CROSS JOIN measurement_window w
            WHERE p.name = '$packageName'
              AND (s.name GLOB 'Choreographer#doFrame*' OR s.name GLOB 'DrawFrame*')
              AND s.ts < w.end_ts
              AND s.ts + s.dur > w.ts
            """.trimIndent()
        )
        return listOf(
            Metric.Measurement("idleWindowCount", windowCount.toDouble()),
            Metric.Measurement("idleFrameCount", frameCount.toDouble())
        )
    }

    private fun TraceProcessor.Session.scalar(sql: String): Long =
        query(sql).firstOrNull()?.long("value") ?: 0L
}

@OptIn(ExperimentalMetricApi::class)
internal class RequiredTraceSlicesMetric(
    private val expectedJourney: TraceJourney,
    private val requireComposeSlices: Boolean = TraceAssertions.isFullTracingEnabled(),
    private val requireFrames: Boolean = true
) : TraceMetric() {
    override fun getMeasurements(
        captureInfo: Metric.CaptureInfo,
        traceSession: TraceProcessor.Session
    ): List<Metric.Measurement> {
        val packageName = captureInfo.targetPackageName.replace("'", "''")
        val sectionName = expectedJourney.sectionName.replace("'", "''")
        val measurementWindowCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice
            WHERE name = '$sectionName' AND dur > 0
            """.trimIndent()
        )
        val mainCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '$packageName' AND t.tid = p.pid
            """.trimIndent()
        )
        val renderCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '$packageName' AND t.name GLOB 'RenderThread*'
            """.trimIndent()
        )
        val frameCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '$packageName'
              AND (s.name GLOB 'Choreographer#doFrame*' OR s.name GLOB 'DrawFrame*')
            """.trimIndent()
        )
        val composeCount = traceSession.scalar(
            """
            SELECT COUNT(*) AS value
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '$packageName'
              AND (
                lower(s.name) GLOB '*compose*'
                OR s.name GLOB '*MainAppScreen*'
                OR s.name GLOB '*FileBrowserScreen*'
                OR s.name GLOB '*MiniPlayer*'
                OR s.name GLOB '*FullScreenPlayer*'
                OR s.name GLOB '*VideoFullscreenOverlay*'
              )
            """.trimIndent()
        )

        check(measurementWindowCount > 0) {
            "Trace has no ${expectedJourney.sectionName} measurement window"
        }
        check(mainCount > 0) { "Trace has no target main-thread slices" }
        check(renderCount > 0) { "Trace has no target RenderThread slices" }
        if (requireFrames) {
            check(frameCount > 0) { "Trace has no target frame slices" }
        }
        if (requireComposeSlices) {
            check(composeCount > 0) {
                "Full tracing was enabled, but no Compose/composable slices were found"
            }
        }

        return listOf(
            Metric.Measurement("newaudioMeasurementWindowCount", measurementWindowCount.toDouble()),
            Metric.Measurement("newaudioMainThreadSliceCount", mainCount.toDouble()),
            Metric.Measurement("newaudioRenderThreadSliceCount", renderCount.toDouble()),
            Metric.Measurement("newaudioFrameSliceCount", frameCount.toDouble()),
            Metric.Measurement("newaudioComposeSliceCount", composeCount.toDouble())
        )
    }

    private fun TraceProcessor.Session.scalar(sql: String): Long =
        query(sql).firstOrNull()?.long("value") ?: 0L
}
