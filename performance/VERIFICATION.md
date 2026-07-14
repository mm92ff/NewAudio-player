# Compose-Tracing- und Macrobenchmark-Verifikation

Stand: 2026-07-14

## Umgesetzter Umfang

- release-naher, nicht debuggable App-Buildtyp `benchmark` und separates
  selbstinstrumentierendes `com.android.test`-Modul
- deterministische lokale Audio-/Video-/Playlist-/Marker-Fixtures mit
  signaturgeschütztem Benchmark-only Setup und reproduzierbarem Zustandsdigest
- stabile Semantics-Tags, fachliche Readiness-Signale und
  `ReportDrawnWhen` für Startup
- Startup-, Navigation-, Browser-, Playlist-, Audio- und Video-Journeys
- strikt getrennte Metrik- und Full-Compose-Trace-Modi
- sechs Trace-Processor-Abfragen, strukturale Checks sowie JSON-, CSV- und
  Markdown-Berichte
- Median-/MAD-Baseline-Policy mit Wiederholungsregel und deaktiviertem Hard
  Gate bis zur Kalibrierung
- saubere Git-Provenienz, Clean-by-default-Runner, strikt getrennte
  Testklassen und erhaltene Fehlerartefakte
- Normalizer mit stabilen BR-04-Varianten und expliziter Metrik-Policy sowie
  Aggregator für unabhängige Primär- und Wiederholungsbatches
- fail-closed Baseline-Kompatibilität für Gerät, Display, Decodepfad,
  Animationen, Cache, Fixtures und Provenienz
- prozessweit gemeinsamer Preview-ImageLoader mit explizitem Cold-/Warm-
  Zustandswechsel und verteilten Artwork-/VideoFrameDecoder-Fixtures
- versionierte Runner-, Kandidaten-, Aggregat- und Trace-Report-Schemas
- Release-Isolationsprüfung sowie vollständige PR-, Security-, Emulator- und
  wöchentliche Performance-CI-Definitionen

## Nachaudit vom 14. Juli 2026

Der Plan wurde erneut gegen Implementierung, Hostverträge und reale API-35-
Läufe geprüft. Dabei gefundene Restlücken wurden geschlossen:

- unbekannte Methoden, widersprüchliche Cacheangaben, sensible zusätzliche
  Gradle-Argumente und unzulässige Iterationszahlen werden fail-closed verworfen
- `-AllowDirty` kann nie baselinefähig werden; ungetrackte Inhalte und ihr
  Vorhandensein sind Bestandteil der Provenienz
- Serienkeys enthalten Einheit und alle fachlichen Dimensionen; Aggregat und
  Repeat sind exakt mit den Top-Level-Run-IDs verknüpft und enthalten
  Batch-Median, Gesamtmedian, MAD sowie den Akku-Min/Max-Bereich
- physische Vergleiche verlangen stabile Geräte-Rollen-ID, Stromquelle,
  Thermalstatus, Refresh-Rate und einen vollständig passenden Akkubereich
- Startup-Readiness wartet auf gelayouteten Root und ersten interaktiven Inhalt
- Fixture-Cleanup akzeptiert nur erwartete direkte private Unterverzeichnisse;
  Video-Zeitstempel besitzen einen deterministischen Offset
- Fehlerläufe kopieren ausschließlich seit Laufbeginn neu entstandene Ausgaben,
  erhalten den ursprünglichen Exitcode und verwenden Schema 3
- Trace-Identität und UTC-Zeit werden nach JSON-Roundtrip validiert; alle sechs
  SQL-Abfragen besitzen einen ausführbaren semantischen Test
- der Trace-Runner unterscheidet `reporting`, `failed` und `succeeded`; ein
  kurzer Trace-only Settle verhindert offene letzte Journey-Slices
- Debug und Release werden beide auf Benchmark-/Perfetto-Inhalte geprüft;
  CI erzeugt eine aufgelöste Gradle-SBOM, bewahrt den Grype-JSON-Report auf und
  scannt auch erzeugte Performance-Artefakte mit Gitleaks

## Lokal bestandene Nachweise

Die folgenden Prüfungen wurden auf dem Arbeitsbaum ausgeführt:

- `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` und
  `:app:assembleRelease`
- `:app:assembleBenchmark` und `:benchmark:assembleBenchmark` einschließlich
  aktualisierter Dependency Locks und Compose-Compiler-Reports
- `:app:connectedDebugAndroidTest`: 9/9 auf API 35
- `FixtureContractTest`: drei frische Seed-Zyklen mit identischem Digest und
  exakten Fixture-Zahlen
- Metrik-Smokes mit einer Iteration: NV-01, AU-02, VI-04 und VI-05 sowie
  `ST-01` (`metrics-20260713-190712101`)
- BR-04 Vier-Spalten Cold mit realer Vor-/Zurückbewegung, Frame-Samples und
  gemischtem Decoderpfad (`metrics-20260713-193604721`)
- BR-04 Vier-Spalten Warm mit explizit vorgeladenem, prozessweit gemeinsamem
  Image-Cache (`metrics-20260713-195843940`)
- kritische Audio-Postconditions auf API 35: AU-01
  (`metrics-20260713-205505536`), AU-03
  (`metrics-20260713-222131314`), AU-04
  (`metrics-20260713-210028687`), AU-05
  (`metrics-20260713-210232117`), AU-06
  (`metrics-20260713-205748132`), AU-08
  (`metrics-20260713-211145793`) und AU-09
  (`metrics-20260713-211518795`)
- kritische Videozustände einschließlich Inline-Wiedergabe, Fullscreen,
  Marker-On und statischem SurfaceView-Idle ohne erzwungene Frame-Metrik:
  VI-01 (`metrics-20260713-214128957`), VI-06 Marker-On
  (`metrics-20260713-215440522`) und VI-02
  (`metrics-20260713-221715150`); die übrigen Videojourneys waren im
  gemeinsamen Klassenlauf grün
- absichtlich roter Diagnose-Probe mit erhaltenem Fehlerexitcode sowie
  Screenshot, Window-Hierarchie, Logcat, `failure.json` und Run-Manifest
  (`metrics-20260713-222342218`)
- Full-Compose-Trace BR-02: drei reale Trace-Iterationen; jede enthält das
  eigene `NewAudio:BR02`-Fenster, Compose-Slices, FrameTimeline sowie Main- und
  RenderThread-Daten und besteht alle sechs SQL-Abfragen
- Full-Compose-Trace NV-01 mit drei realen Iterationen, per-Trace-Manifesten,
  Cache-/Decoder-/Journey-Identität und sechs bestandenen SQL-Berichten
  (`compose-trace-20260713-222424580`)
- alle Host-Selbsttests, PowerShell-Parser, Workflow-YAML-Parser,
  Fixture-Driftprüfung und Runner-Vertragstests
- neue Host-Selbsttests für Normalizer, Serien-Aggregator, Comparator und
  Trace-Report sowie der Repository-Hygiene-Gate
- Release-APK- und Dependency-Graph-Isolation: keine Benchmark-Fixtures,
  Receiver, Runtime-Tracing- oder Perfetto-Komponenten in Debug/Release
- erneuter Gesamtbuild einschließlich `:app:testBenchmarkUnitTest` und
  `cyclonedxBom`; lokale SBOM mit 577 Komponenten und 578 Dependency-Knoten
- erneute Debug-Instrumentation 9/9 und `FixtureContractTest` 3/3 auf
  `emulator-5554` (API 35)
- verschärfte ST-01-Readiness (`metrics-20260713-231101927`) und VI-04-
  Fullscreen/Inline-Rückkehr (`metrics-20260713-231239222`) jeweils grün
- erneuter roter Fehler-Probe mit erhaltenem Exitcode 1 und exakt vier
  aktuellen, SHA-256-verifizierten Diagnoseartefakten
- finaler ST-01-Full-Compose-Trace mit drei geschlossenen Messfenstern und je
  sechs grünen SQL-Berichten (`compose-trace-20260713-232506031`)

Die AVD-Metriken und Full-Tracing-Zeiten sind ausschließlich Struktur- und
Journey-Nachweise. Sie sind keine Performance-Baseline.

Die neu ergänzte CI-Erzeugung der CycloneDX-SBOM ist auch lokal real ausgeführt.
Grype-/Gitleaks-Action, Dependency Review und die vollständige API-35-
Workflowmatrix wurden lokal syntaktisch beziehungsweise durch Hostverträge
geprüft; ihre GitHub-Action-Ausführung ist erst durch den nächsten Workflowlauf
nachgewiesen.

Die kritische R3-Geräteauswahl, der Warm-Gallery-Smoke, der absichtlich rote
Fehlerartefakt-Probe und ein realer Trace mit dem neuen per-Trace-Manifest sind
damit lokal nachgewiesen. Der vollständige CI-Matrixlauf, drei unabhängige
Serien je Journey und das zweite Anzeigeprofil bleiben CI-/Labor-Nachweise und
werden nicht aus den gezielten AVD-Smokes abgeleitet.

## Bewusst ausstehende externe Messung

Vor Aktivierung von Warnbudgets oder eines harten Performance-Gates müssen auf
einem fest benannten physischen Referenzgerät mindestens drei vollständige
Metrikserien je Hauptjourney laufen. Gerät, Fingerprint, Displaymodus,
CompilationMode, Fixture-SHA, Decodepfad, Akku und thermischer Zustand müssen
identisch beziehungsweise dokumentiert sein. Erst daraus werden relative,
absolute und MAD-basierte Schwellen in `baselines/reference-device.json`
kalibriert.

Eine Baseline-Profile-Entscheidung bleibt gemäß Plan ein nachgelagertes A/B-
Experiment und wurde nicht vorweggenommen.
