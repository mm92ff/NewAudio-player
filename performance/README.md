# NewAudio Performance-Messstrecke

Dieses Verzeichnis enthält die reproduzierbaren Host-Werkzeuge für die
Macrobenchmark- und Compose-Tracing-Messstrecke. Zahlen aus dem Metrikmodus und
Traces aus dem Diagnosemodus werden bewusst getrennt gehalten.

## Voraussetzungen

- Windows PowerShell 5.1 oder PowerShell 7
- Android SDK mit `adb` (über `PATH`, `ANDROID_HOME` oder `ANDROID_SDK_ROOT`)
- genau ein gestartetes, entsperrtes Testgerät; bei mehreren Geräten muss
  `ANDROID_SERIAL` das Ziel eindeutig auswählen
- gebautes `:app:benchmark`- und `:benchmark`-Modul
- für Trace-Berichte: Perfetto `trace_processor_shell.exe` oder der offizielle
  `trace_processor`-Wrapper im `PATH`; alternativ den Pfad mit
  `-TraceProcessorPath` angeben

Die Skripte akzeptieren ausschließlich Ausgaben unter
`performance/results/`. Dort erzeugte Rohtraces, JSON-/CSV-Dateien und Berichte
sind Laufartefakte und gehören nicht in Git.

Jeder Runner erfasst Commit, Dirty-Status sowie kanonische SHA-256-Werte von
Status, Diff und Inhalt aller ungetrackten Dateien. Standardmäßig wird ein
schmutziger Arbeitsbaum vor Gradle abgewiesen. `-AllowDirty` erlaubt nur einen
Diagnose-/Entwicklerlauf; dessen `baselineEligible` bleibt auch bei einem
ansonsten sauberen Arbeitsbaum `false`. Auch ein explizites `-Iterations` macht
einen Metriklauf nicht baselinefähig. Potenziell geheime Werte in zusätzlichen
Gradle-Argumenten werden abgewiesen, weil die vollständigen Argumente Teil des
Run-Manifests sind.

Erfolg und Fehler erzeugen ein versioniertes `run-manifest.json`. Darin stehen
unter anderem Runner-Pfad und -SHA-256, die vollständig aufgelösten
Gradle-Argumente, Testselektor, Ergebnisverzeichnis sowie die Geräte- und
Fixture-Provenienz. Der Trace-Runner ergänzt pro Trace Journey-ID, Testmethode,
Iteration, Aufnahmezeit, Cachezustand und Decoderpfad.
Physische Geräte benötigen zusätzlich eine stabile, nicht-serielle Rollen-ID
über `-DeviceRoleId` (zum Beispiel `lab-pixel-8`). Emulator-Smokes erhalten
standardmäßig `emulator-smoke`. Refresh-Rate, Akku, Stromquelle und thermischer
Status werden automatisch erfasst; die Refresh-Rate fällt bei fehlender
Systemeinstellung auf den aktiven Displaymodus zurück.

Vor einem Lauf können die Voraussetzungen ohne Build geprüft werden:

```powershell
.\performance\scripts\run-benchmarks.ps1 -CheckPrerequisites
.\performance\scripts\run-compose-trace.ps1 -CheckPrerequisites `
  -TraceProcessorPath C:\tools\perfetto\trace_processor_shell.exe
```

Mit `-DryRun` wird der exakte Gradle-Aufruf angezeigt, ohne ein Gerät oder den
Arbeitsbaum zu verändern.

## Metrikmodus

Der Metrikmodus aktiviert **kein** Full Composition Tracing. Nur seine Werte
dürfen in eine Startup-/Frame-Baseline einfließen.

```powershell
.\performance\scripts\run-benchmarks.ps1 `
  -TestClass com.example.newaudio.benchmark.StartupBenchmark
```

Weitere vorgesehene Klassen sind `NavigationBenchmark`,
`BrowserRenderingBenchmark`, `AudioPlaybackBenchmark` und
`VideoPlaybackBenchmark`. `-TestClass` kann auch eine einzelne Testmethode im
JUnit-Format `Klasse#Methode` adressieren. Zusätzliche Gradle-Argumente werden
als einzelne Werte über `-AdditionalGradleArguments` übergeben. Das Skript
weist Full-Tracing-Argumente im Metrikmodus ausdrücklich zurück.
Andere Testklassen werden nicht akzeptiert. Klasse, Iterationszahl, Shard und
Tracing-Schalter sind runner-eigene Argumente und können nicht über
`-AdditionalGradleArguments` überschrieben werden.

Die vollständige `BrowserRenderingBenchmark`-Klasse kann für lange CI-Läufe
auf frische Emulatoren verteilt werden. `-MetricShard` akzeptiert dafür
`lists`, `gallery-cold`, `gallery-warm` oder `folders-playlists` und ist nicht
mit einem einzelnen `Klasse#Methode`-Selektor kombinierbar:

```powershell
.\performance\scripts\run-benchmarks.ps1 `
  -TestClass com.example.newaudio.benchmark.BrowserRenderingBenchmark `
  -MetricShard gallery-cold
```

Vier erfolgreiche Shard-Kandidaten werden mit
`merge-metric-shards.ps1` zu genau einem logischen, unabhängigen Browser-Batch
zusammengeführt. Das Skript verlangt vier disjunkte Kandidaten und alle 13
versionierten Browser-Journeys; unvollständige oder überlappende Shards werden
abgewiesen.

Für einen reinen Journey-Smoke kann die Iterationszahl explizit reduziert
werden:

```powershell
.\performance\scripts\run-benchmarks.ps1 `
  -TestClass com.example.newaudio.benchmark.VideoPlaybackBenchmark#vi04FullscreenInlineTransition `
  -Iterations 1
```

`-Iterations` ist nur für Smoke-Läufe gedacht. Ohne Override gelten die im
Benchmark festgelegten Seriengrößen (Startup 10, Metrik 5, Diagnose-Trace 3).
Reduzierte Läufe sind nicht baselinefähig.

## Diagnosemodus mit Full Compose Tracing

Der Diagnosemodus aktiviert
`androidx.benchmark.fullTracing.enable=true` ausschließlich für
`TraceCaptureTest`, kopiert die neu erzeugten `.perfetto-trace`-Dateien in ein
eigenes Run-Verzeichnis und erstellt standardmäßig für jede Trace-Datei einen
Kurzbericht:

```powershell
.\performance\scripts\run-compose-trace.ps1 `
  -TraceProcessorPath C:\tools\perfetto\trace_processor_shell.exe
```

Die Trace-Laufzeiten sind wegen des Tracing-Overheads nicht mit den
Metrikwerten vergleichbar. Wenn zunächst nur Rohtraces benötigt werden, kann
`-SkipSummary` verwendet werden.
Dieser Runner akzeptiert ausschließlich `TraceCaptureTest` (optional mit
`#Methode`). Gradle-Ausgabe und `run-failure.json` bleiben bei einem Fehler im
Run-Verzeichnis erhalten; neue Traces werden soweit möglich ebenfalls gerettet.

Macrobenchmark legt die ursprünglichen Dateien unter
`benchmark/build/outputs/connected_android_test_additional_output/` ab. Die
Skripte kopieren nur Artefakte, die während des aktuellen Laufs entstanden
sind; vorhandene Dateien werden weder gelöscht noch überschrieben.

## Vorhandene Trace zusammenfassen

```powershell
.\performance\scripts\summarize-trace.ps1 `
  -TracePath C:\traces\AU-02.perfetto-trace `
  -Journey AU-02 `
  -CompilationMode Partial `
  -TraceProcessorPath C:\tools\perfetto\trace_processor_shell.exe
```

Der Befehl erzeugt pro SQL-Abfrage CSV und JSON sowie `summary.md` und
`metadata.json`. Standardmäßig ist der Lauf strukturell fehlgeschlagen, wenn

- keine Compose-Slices,
- keine FrameTimeline-Daten oder
- keine Main-/RenderThread-Daten der Ziel-App

gefunden werden. Für die gezielte Untersuchung unvollständiger oder älterer
Traces gibt es die expliziten Schalter `-AllowMissingComposeSlices`,
`-AllowMissingFrames` und `-AllowMissingAppThreads`. Solche Berichte sind nicht
baselinefähig.

Als Analysefenster wird zuerst `NewAudio:*`, danach der Macrobenchmark-
`measureBlock` und zuletzt klar als `whole_trace_fallback` gekennzeichnet die
gesamte Trace verwendet. Die Fallback-Daten werden zur Diagnose geschrieben;
ein fehlendes eigenes `NewAudio:*`-Fenster bleibt jedoch absichtlich ein
struktureller Testfehler und ist nie baselinefähig.
Die Journey-ID wird vorrangig aus diesem Fenster abgeleitet und stabil als
beispielsweise `BR-04-GRID-3-COLD` geschrieben. Bei runner-erzeugten Berichten
sind Commit, Modus, Testklasse, CompilationMode, Geräteumgebung sowie die
per-Trace-Identität aus `run-metadata.json` autoritativ. Widerspricht die
Journey im Tracefenster dem Manifest, schlägt der Bericht fehl.
Während der Reporter läuft, trägt `run-manifest.json` den Zustand `reporting`.
Erst nach allen sechs Berichten pro Trace wird er `succeeded`; Reporterfehler
werden als `failed` mit Fehlerzeit und Ursache festgehalten.

## SQL-Abfragen

- `discover_compose_slices.sql`: tatsächliche Compose-Namen und
  `NewAudio:*`-Messfenster der gepinnten Version entdecken
- `compose_hotspots.sql`: häufigste und teuerste Compose-Slices im Messfenster
- `frame_summary.sql`: Frame-Dauer, Overrun und Jank P50/P90/P95/P99
- `long_frames.sql`: einzelne verspätete beziehungsweise janky Frames
- `frame_slice_correlation.sql`: zeitlicher Overlap langer Frames mit den
  relevantesten Compose-/App-Slices
- `main_thread_summary.sql`: CPU-Laufzeit von MainThread und RenderThread im
  Messfenster

Die Abfragen sind auf `com.example.newaudio` begrenzt. Das Messfenster verwendet
die Priorität `NewAudio:*` → Macrobenchmark-`measureBlock` → gesamte Trace. Der
gewählte Fallback steht ausdrücklich im Bericht.
Fehlt die erwartete FrameTimeline-Zeile, wird kein synthetischer Overrun
berechnet. Stattdessen liefern die Abfragen `expected_frame_missing`
beziehungsweise `expected_frame_missing_count`; der Overrun bleibt leer.

Der SQL-Vertrag setzt die Perfetto-Tabellen `slice`, `trace_bounds`, `process`,
`thread`, `thread_track`, `sched`, `actual_frame_timeline_slice` und
`expected_frame_timeline_slice` voraus. Zeitstempel und Dauern sind darin
Nanosekunden; Berichtsspalten mit dem Suffix `_ms` werden explizit in
Millisekunden umgerechnet. Leere Ergebnislisten sind nur für optionale
Hotspot-/Korrelationszeilen erlaubt. Frame-, Messfenster- und App-Thread-
Pflichtdaten bleiben fail-closed. `trace-sql-selftest.py` führt alle sechs
Abfragen gegen eine kleine SQLite-Vertragsdatenbank tatsächlich aus.

## Baseline-Metadaten

`baselines/reference-device.json` ist ein auszufüllendes Template und enthält
absichtlich keine erfundenen Grenzwerte. Eine belastbare Baseline benötigt
mindestens drei vollständige Serien auf demselben physischen Gerät. Festzuhalten
sind insbesondere:

- Git-Commit, App-/Benchmark-Variante und CompilationMode
- Fixture-Manifest und SHA-256
- Gerät, Build-Fingerprint, API, Displaymodus und thermischer Zustand
- Host, Android SDK/Build Tools und deaktivierte Animationen
- Median und Streuung je Journey
- getrennte absolute und relative Warnschwellen

Emulatorwerte dürfen nur als Struktur-/Journey-Smoke dokumentiert werden.
Full-Tracing-Läufe dürfen nie in `metricSeries` oder Performance-Gates
einfließen.

Das Vergleichsskript erwartet eine aggregierte Kandidatendatei mit
`mode: "metrics"`, `fullComposeTracing: false`, den vom Lauf erzeugten
Umgebungsdaten und Serien einschließlich `unit`, `type`, `direction`,
`gateEligible`, `batches` und `independentSeriesCount`.
`run-benchmarks.ps1` erzeugt diese Datei automatisch als
`candidate-series.json`; Sample-Metriken werden dafür pro Iteration auf
P50/P90/P95/P99 verdichtet. Eine solche Datei ist genau **ein** unabhängiger
Run-Batch; Iterationen erhöhen die Serienzahl nicht. `frameCount` ist nur eine
informative Count-Metrik und wird nie als Millisekunden-Regression gewertet.

Mindestens drei getrennte vollständige Läufe werden aggregiert;
Wiederholungsläufe bleiben separat:

```powershell
.\performance\scripts\aggregate-series.ps1 `
  -CandidatePath .\run1\candidate-series.json,.\run2\candidate-series.json,.\run3\candidate-series.json `
  -RepeatCandidatePath .\repeat1\candidate-series.json,.\repeat2\candidate-series.json,.\repeat3\candidate-series.json `
  -OutputPath .\performance\results\candidate-aggregate.json
```

Der Aggregator akzeptiert nur eindeutige Run-IDs, baselinefähige Läufe und
identische Pflichtumgebungen. Er speichert pro Batch den Median, anschließend
Median und MAD über die unabhängigen Batches sowie den Akku-Min/Max-Bereich
aller Primär- und Wiederholungsläufe. Er kalibriert keine Baseline automatisch.
Nach einer Kalibrierung wird sie so geprüft:

```powershell
.\performance\scripts\compare-baseline.ps1 `
  -BaselinePath .\performance\baselines\reference-device.json `
  -CandidatePath .\performance\results\candidate-aggregate.json `
  -OutputPath .\performance\results\baseline-comparison.json
```

Die Policy kombiniert relative und absolute Schwelle mit Median Absolute
Deviation, verlangt bei Regressionen einen Wiederholungslauf und lässt einen
einzelnen Ausreißer nicht automatisch scheitern. Ihre Logik kann ohne Gerät
mit `compare-baseline.ps1 -SelfTest` geprüft werden.
Der Vergleich ist fail-closed für App/Variante/CompilationMode, Fixture und
Cache, physisches Gerät/Fingerprint/API/ABI/Hardware, Decoder-Policy,
Auflösung/Dichte/Refresh-Rate, alle Animationsskalen und saubere
Git-Provenienz. Geräte-Rollen-ID, Stromquelle und thermischer Status müssen
ebenfalls übereinstimmen; der gesamte Kandidaten-Akkubereich muss innerhalb
des kalibrierten Baselinebereichs liegen. Das Minimum zählt ausschließlich
unabhängige Batches, und jede Metrikserie muss exakt auf die Top-Level-Run-IDs
verweisen.

## Build- und Release-Isolation

Compose `runtime-tracing` 1.7.3 ist ausschließlich im App-Buildtyp
`benchmark` aktiv. Es bringt den Perfetto-SDK-Client 1.0.0 transitiv in genau
diese Variante; Handshake und Native Binary sind deshalb im Testmodul auf
dieselbe atomare Version 1.0.0 festgelegt. Debug und Release enthalten weder
Runtime Tracing noch Perfetto, Fixture-Receiver oder Benchmark-Steuerung.

```powershell
.\performance\scripts\verify-release-isolation.ps1
```

Die Release-CI baut Debug-, Release- und beide Benchmark-APKs, prüft die
APK- und Dependency-Isolation von Debug und Release sowie alle Host-Werkzeuge,
Fixture-Drift und Runner-Verträge. Sie scannt die vollständige Git-Historie mit
Gitleaks, erzeugt über das Gradle-CycloneDX-Plugin eine SBOM der aufgelösten
direkten und transitiven Dependencies und prüft diese mit Grype ab Schweregrad
`high`. Der JSON-Report wird zusammen mit der SBOM aufbewahrt und enthält
Scanner-/Datenbank-Metadaten. Ausnahmen sind nicht stillschweigend erlaubt: Sie
benötigen eine reviewte `.grype.yaml` oder ein VEX-Dokument mit CVE/GHSA,
betroffener Komponente, Begründung, Verantwortlichem und Ablaufdatum. Pull
Requests führen zusätzlich den Dependency Review aus.

Ein separater wöchentlicher/manueller API-35-Workflow führt jede Metrikklasse
mit einer Iteration in Default- und Large-Font-Profil aus. Zusätzlich erzeugt
er erst nach grünem Smoke vollständige Large-Font-Defaults, drei unabhängige
vollständige Default-Serien, aggregiert diese und erfasst die vollständige
`TraceCaptureTest`-Matrix mit drei Iterationen pro Journey. Ein abschließender
Gitleaks-Job scannt alle heruntergeladenen Performance-Artefakte. Die langen
Browser-Vollserien laufen in vier frischen Emulator-Shards und werden vor der
Aggregation pro Serie wieder zu einem Kandidaten zusammengesetzt. Diese
Emulatorläufe sind ausschließlich Struktur- und Journey-Gates.

Ein absichtlich roter Diagnosepfad prüft die Fehlerartefaktkette. Er ist
standardmäßig übersprungen und wird nur explizit aktiviert:

```powershell
.\performance\scripts\run-benchmarks.ps1 `
  -TestClass com.example.newaudio.benchmark.NavigationBenchmark#diagnosticFailureArtifactProbe `
  -AdditionalGradleArguments -Pnewaudio.benchmark.failureProbe=true `
  -AllowDirty
```

Der erwartete rote Lauf muss auf dem Host Screenshot, Window-Hierarchie,
PID-gefiltertes Logcat, Fehler-JSON und `run-manifest.json` erhalten.

Bekannte absichtliche Aktualisierungen werden getrennt interpretiert: Audio-
Position etwa mit 1 Hz, Video-Fullscreen-Timeline etwa mit 4 Hz, optionale
Repeat-Rotation und Marquee. Sie sind kein pauschaler Regressionsbeweis;
entscheidend ist, ob statische Elternbereiche im Trace unnötig mitarbeiten.

Compose-Compiler-Reports und -Metriken lassen sich mit
`-Pnewaudio.composeCompilerReports=true` aktivieren. Die Release-CI bewahrt
`app/build/reports/compose-compiler/` als Diagnoseartefakt auf; diese Daten sind
kein Laufzeit-Performance-Gate.

## Reproduzierbarkeit und Aufbewahrung

Für einen Vergleich müssen Gerät, System-Image, Displaymodus, Fixture-Version,
CompilationMode, Cache-Zustand und Benchmark-Klasse gleich bleiben. Rohtraces
sollten nur bis zur Befundklärung oder bis zum Ablauf der CI-Artefakt-Retention
aufbewahrt werden. Kleine Berichte und ausgefüllte, geprüfte Baseline-Metadaten
können versioniert werden; Laufartefakte unter `performance/results/` nicht.

Der zuletzt lokal verifizierte Umfang und die noch extern auszuführenden
physischen Messserien stehen in `VERIFICATION.md`.
