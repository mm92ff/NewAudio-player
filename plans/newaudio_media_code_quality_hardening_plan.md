# NewAudio: Media-Code-Quality- und Robustheits-Hardening

**Stand:** 14. Juli 2026
**Status:** umgesetzt; automatische Media-/App-Gates grün, manuelle Gerätegates offen
**Ausgangspunkt:** Media-Repository-Refactoring ist funktional gebaut und automatisch verifiziert
**Priorität:** hoch für Gateway-Lifecycle und State-Konsistenz, mittel für Struktur und Dokumentation
**Risikoklasse:** 6/10 bei sequenzieller Umsetzung, 9/10 als gemeinsamer Big-Bang

## 0. Umsetzungsprotokoll

Umgesetzt am 14. Juli 2026:

- einheitliche Media-Type-Priorität und isoliertes, lokalisiertes
  `PlaybackErrorMapper`;
- generationssicherer Controller-Disconnect, Single-Flight-Reconnect,
  getrennte Acquire-/Operationsfehlergrenze und expliziter Release-Pfad;
- `PlaybackTransitionCoordinator` mit unveränderlichem Gesamt-Snapshot,
  Consume-on-success für Sessions und beobachtbarem Best-Effort-Rollback;
- Delete-Invarianten, Aktivpfad-Fallback, `playWhenReady`, vollständige
  Vor-Commit-Kompensation und sichtbare Rollbackfehler;
- extrahierte `PlaybackSnapshotWriter`, `PlaybackPreferenceWriter` und
  `PlaybackPositionTracker` mit Scope-Ownership und monotoner Zeitquelle;
- KDoc an den wichtigen Verträgen sowie dauerhafte Dokumentation unter
  `docs/media-playback-architecture.md`;
- Regressionstests für Typkonflikte, Error-Mapping, Disconnect/Generation,
  Transitionfehler, synchrone Listener-Callbacks, Pfadgrenzen,
  Partial-Delete, Rollbackfehler, Persistenzreihenfolge und Ticker-Lifecycle.

Automatisch verifiziert:

- vollständige JVM-Suite: grün;
- `lintDebug`: grün im isolierten Ein-Worker-Lauf;
- `assembleDebug`, `assembleRelease`: grün;
- `compileDebugAndroidTestKotlin`: grün;
- App-Instrumentierung auf AVD `newaudio`: 21/21 grün;
- `git diff --check`: keine Whitespace-Fehler (nur bestehende
  Windows-Line-Ending-Hinweise).

Offen beziehungsweise bewusst nicht als erfolgreich markiert:

- manueller Musik-/Video-/Delete-/Reconnect-Smoke mit kontrollierten Medien;
- P30-Release-Smoke;
- das separate Benchmark-Modul: der Root-Task startete zusätzlich 67
  Benchmark-/Tracing-Fixtures, von denen 66 bereits am vorhandenen
  Fixture-Kommando `RESET` scheiterten. Die 21 App-Instrumentationstests waren
  davor vollständig grün. Der Benchmark-Fixture-Befund gehört zum späteren
  Compose-Tracing-/Benchmark-Schritt und nicht zum Media-Hardening.

## 1. Ziel

Dieser Plan schließt die beim Qualitätsaudit gefundenen Restpunkte des
Media-Refactorings. Er soll nicht erneut die gesamte Architektur umbauen,
sondern die vorhandene Aufteilung robuster, konsistenter, leichter lesbar und
dauerhaft verständlich machen.

Die Ziele sind:

- getrennte und wiederverbindbare Media3-Controller-Lifecycles;
- eine präzise Fehlergrenze zwischen temporärer Nichtverfügbarkeit,
  Konfigurationsfehlern, Cancellation und Programmierfehlern;
- konsistente Queue-, Session- und Playback-State-Übergänge auch bei
  Playerfehlern;
- ein vollständiger, beobachtbarer Best-Effort-Rollback für Delete- und
  Playlistoperationen;
- identische Audio-/Video-Klassifikation in Listener, Mapper und
  State-Synchronisierung;
- ein kleinerer, lifecycle-klarer `PlayerListenerDelegate`;
- KDoc an den wichtigen öffentlichen und nebenläufigen Verträgen;
- Tests, die nicht nur Erfolgsfälle, sondern auch partielle Mutation,
  Disconnect, Retry, Race und Rollbackfehler absichern.

## 2. Bestätigte Ausgangslage

### 2.1 Automatische Baseline

- `testDebugUnitTest`: **376 Tests in 61 Suites**, 0 Fehler, 0 übersprungen;
- `lintDebug`: erfolgreich;
- `assembleDebug`: erfolgreich;
- `assembleRelease`: erfolgreich;
- `compileDebugAndroidTestKotlin`: erfolgreich;
- echter Factory-/Service-Verbindungstest auf AVD `newaudio`: erfolgreich;
- aktuelle Debug-APK auf dem AVD `newaudio` installiert.

Diese Baseline ist vor der ersten Produktionsänderung erneut zu bestätigen.

### 2.2 Qualitative Ausgangslage

Der Audit bewertet den aktuellen Kern pragmatisch mit:

| Kriterium | Bewertung |
|---|---:|
| Lesbarkeit und Kotlin-Stil | 7,5/10 |
| Struktur und Verantwortlichkeiten | 8/10 |
| Code-nahe Dokumentation | 4,5/10 |
| Tests als ausführbare Dokumentation | 7/10 |

Stärken der vorhandenen Implementierung:

- klare Dateien und sprechende Komponentennamen;
- Constructor Injection statt versteckter Abhängigkeiten;
- eigener Owner für Playback-State und Queue-State;
- reine Delete-Entscheidung ohne Android-/Player-Abhängigkeit;
- gut lesbare, verhaltensorientierte Testnamen;
- konsistente Formatierung und geringe Verschachtelung;
- bereits vorhandene Single-Flight-, Retry-, Cancellation- und
  Delete-Rollback-Basis.

### 2.3 Arbeitsbaum-Risiko

Der Worktree enthält weiterhin unabhängige Playlist-, Settings-, Gradient-
und Media-Änderungen. Außerdem wird `plans/` aktuell durch `.gitignore`
ignoriert.

Vor Sprint 1 müssen daher:

1. die Media-Dateien eindeutig vom restlichen Worktree isoliert werden;
2. unabhängige Änderungen separat committed oder sicher geparkt werden;
3. entschieden werden, ob dieser Plan nur lokal bleibt oder gezielt
   versioniert werden soll;
4. pro Sprint ein thematisch sauberer Commit möglich sein.

## 3. Geltungsbereich

### 3.1 Primäre Produktionsdateien

```text
app/src/main/java/com/example/newaudio/
├── domain/repository/IMediaRepository.kt
├── data/repository/MediaRepositoryImpl.kt
├── data/audio/PlayerListenerDelegate.kt
└── data/media/
    ├── controller/
    │   ├── MediaControllerFactory.kt
    │   ├── MediaControllerGateway.kt
    │   └── PlayerListenerDelegateFactory.kt
    ├── deletion/
    │   ├── DeletedMediaDecisionCalculator.kt
    │   └── DeletedMediaReconciler.kt
    ├── library/MediaLibraryRepository.kt
    ├── mapping/
    │   ├── Media3ItemMapper.kt
    │   └── Media3PlaybackStateSynchronizer.kt
    └── playback/
        ├── PlaybackQueueState.kt
        ├── PlaybackSessionCoordinator.kt
        └── PlaybackStateStore.kt
```

### 3.2 Zugehörige Tests

- sämtliche Tests unter `app/src/test/.../data/media/`;
- `MediaRepositoryImplTest`;
- `PlayerListenerDelegateTest`;
- `PlaybackSessionSnapshotTest`;
- Media-Controller-Instrumentationstests unter
  `app/src/androidTest/.../data/media/`;
- betroffene ViewModel-/Use-Case-Tests, wenn sich sichtbare Fehlerzustände
  ändern.

## 4. Nicht Bestandteil

- keine neue Media3-Version;
- keine Änderung am Room-Schema;
- keine Änderung am Playlist-Backupformat;
- keine sichtbare Neugestaltung von Player oder Settings;
- kein Compose-Tracing- oder Macrobenchmark-Ausbau;
- keine neue Wiedergabefunktion;
- kein vollständiges Aufteilen von `IMediaRepository`;
- keine grundlegende Änderung von Audio-Fokus, Notification oder Service-
  Prozessmodell;
- kein repo-weites automatisches Reformatting;
- keine Einführung von Detekt/Ktlint im selben Hardening, sofern dadurch
  hunderte fachfremde Dateien geändert werden müssten.

## 5. Priorisierte Befunde

### P1 – Controller bleibt nach Disconnect im Gateway gespeichert

`MediaControllerGateway` verwendet `controller != null` als Bereitschafts-
kriterium. Ein Disconnect nach erfolgreicher Initialisierung setzt den Cache,
den Player-Listener und dessen Scope derzeit nicht zurück. Der nächste Zugriff
kann deshalb einen nicht mehr verbundenen Controller erhalten.

### P1 – Playlist-/Sessionwechsel sind nicht konsistent bei Playerfehlern

Session, Queue und App-State werden teilweise verändert, bevor alle geplanten
Playerkommandos erfolgreich waren. Bei `setMediaItems`, `prepare`, `seekTo`
oder `play/pause` kann eine halbe Transition zurückbleiben.

### P1 – Audio-/Video-Typpriorität ist inkonsistent

`Media3ItemMapper.isVideo()` kann eine explizite Audio-Deklaration durch eine
Video-Dateiendung überschreiben. Library-Synchronizer und Listener können
dasselbe Item dadurch unterschiedlich klassifizieren.

### P2 – Fehlerklassifikation ist zu breit

Jede Exception aus `connection.await()` wird momentan als temporäre
Nichtverfügbarkeit klassifiziert. Außerdem umfasst `withControllerOrNull()`
auch die Ausführung des übergebenen Blocks. Erwerbsfehler und Operationsfehler
sind damit nicht vollständig getrennt.

### P2 – Delete-Rollback ist nur teilweise und nicht beobachtbar

Der Rollback schützt primär `removeMediaItem()`. Fehler beim Rollback selbst
werden verschluckt. Spätere Fehler bei Seek, Queue-Publikation oder State-
Update besitzen keine vollständige Kompensation. Außerdem wird `isPlaying`
statt Wiedergabeabsicht (`playWhenReady`) gespeichert.

### P2 – `PlayerListenerDelegate` besitzt zu viele Aufgaben

Der Listener kombiniert Event-Übersetzung, State-Sync, Positionsticker,
Snapshot-Persistenz, Preference-Persistenz, Fehlerabbildung und Zeitlogik.
Repeat-/Shuffle-Writes können sich überholen; Lifecycle und Ownership der Jobs
sind nicht dokumentiert.

### P2 – Code-nahe Dokumentation fehlt an wichtigen Verträgen

Besonders unklar oder nur im lokalen Plan dokumentiert sind:

- Gateway-Scope- und Controller-Lebensdauer;
- Disconnect-/Reconnect-Verhalten;
- Bedeutung des Boolean-Resultats von `synchronize()`;
- Consume-once-Semantik der Sessionmethoden;
- Pfadvertrag der Delete-Entscheidung;
- Fehler- und Null-Verträge von `IMediaRepository`;
- Reihenfolge und Rollback-Garantie von Playback-Transitionen.

## 6. Leitentscheidungen

### 6.1 Keine echte Transaktion vortäuschen

Media3-Player, In-Memory-State und Queue-State können nicht als echte atomare
Transaktion committed werden. Der Code soll deshalb ausdrücklich eine
**anwendungsseitige, bestmögliche Transition mit Kompensation** implementieren:

1. ursprünglichen Player-, Queue-, Session- und App-State erfassen;
2. Entscheidung ohne Seiteneffekte berechnen;
3. Playerkommandos ausführen;
4. erst danach App-Queue und Playback-State veröffentlichen;
5. bei Fehlern den Player bestmöglich restaurieren;
6. Rollbackfehler loggen und dem ursprünglichen Fehler als `suppressed`
   hinzufügen;
7. nie behaupten, der Vorgang sei vollständig atomar.

### 6.2 Controller-Erwerb und Operationsblock trennen

Das gewünschte Gateway-Muster lautet sinngemäß:

```kotlin
private suspend fun acquireController(): MediaController

suspend fun <T> requireController(
    block: suspend (MediaController) -> T
): T

suspend fun <T> withControllerOrNull(
    block: suspend (MediaController) -> T
): T? {
    val controller = try {
        acquireController()
    } catch (error: TemporarilyUnavailable) {
        return null
    }
    return block(controller)
}
```

Der Catch umschließt nur den Erwerb. Exceptions aus dem Operationsblock werden
nie als Controller-Verbindungsfehler normalisiert.

### 6.3 Deklarierter Medientyp hat Vorrang

Priorität für Audio-/Video-Erkennung:

1. explizites Media-Type-Extra;
2. bestätigter Datenbanktyp;
3. Dateiendung;
4. unbekannt.

Ein explizites Audio-Extra darf niemals durch `.mp4` zu Video werden und
umgekehrt.

### 6.4 Wiedergabeabsicht statt Momentaufnahme

Für Wiederherstellung und Delete wird `playWhenReady` als Benutzerabsicht
gespeichert. `isPlaying` bleibt eine abgeleitete Momentaufnahme, die während
Buffering, Audio-Fokus-Verlust oder temporärer Unterdrückung `false` sein kann.

### 6.5 Dokumentation erklärt Verträge, nicht offensichtlichen Code

KDoc ist verpflichtend für:

- öffentliche Interfaces und nicht offensichtliche Rückgabewerte;
- Lifecycle-, Threading-, Retry- und Cancellation-Verträge;
- Consume-once- oder Best-Effort-Rollback-Semantik;
- ungewöhnliche Pfad- und URI-Prioritäten.

Keine Kommentare wie `new`, `removed`, `direct repo instead of use case` oder
Kommentare, die nur die folgende Codezeile wiederholen.

## 7. Sprintübersicht

| Sprint | Schwerpunkt | Risiko | Hauptgate |
|---|---|---:|---|
| 0 | Isolation und Characterization | niedrig | reproduzierbare Baseline |
| 1 | Mapper- und Error-Mapping-Korrektheit | niedrig | Typ-/Lokalisierungsmatrix |
| 2 | Gateway Disconnect, Reconnect und Fehlergrenze | hoch | Lifecycle-/Concurrencytests |
| 3 | Konsistente Playlist- und Sessiontransitionen | hoch | Fehler-in-jeder-Phase-Tests |
| 4 | Delete-Rollback und Pfadvertrag | mittel-hoch | Partial-Mutation-/Rollbacktests |
| 5 | Listener-Persistenz und Lifecycle | mittel | Race-/Ticker-/Cancellationtests |
| 6 | Struktur- und Dokumentationspolitur | niedrig-mittel | Reviewcheckliste und KDoc |
| 7 | Integration, Emulator und Release-Gate | mittel | volle Matrix und AVD-Smoke |

### 7.1 Verbindliches Pflichtgate nach jedem Sprint

Jeder Sprint wird vollständig abgeschlossen und geprüft, bevor der nächste
Sprint beginnt. Ein Sprint gilt nicht als abgeschlossen, nur weil seine neuen
Einzeltests grün sind.

#### Während der Implementierung

Nach jeder zusammenhängenden Änderung werden zuerst ausschließlich die direkt
betroffenen Testklassen ausgeführt. Beispiele:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.newaudio.data.media.controller.MediaControllerGatewayTest"
.\gradlew.bat testDebugUnitTest --tests "com.example.newaudio.data.media.deletion.*"
.\gradlew.bat testDebugUnitTest --tests "com.example.newaudio.data.audio.PlayerListenerDelegateTest"
```

Bei einem Test-first-Fix muss der neue Regressionstest den Befund zunächst
reproduzieren beziehungsweise ohne Fix rot werden. Danach wird nur so viel
Produktionscode geändert, wie für den grünen Vertrag erforderlich ist.

#### Minimales Abschlussgate für jeden Sprint

Nach jedem Sprint sind unabhängig vom jeweiligen Schwerpunkt mindestens diese
Prüfungen verpflichtend:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
git diff --check
```

Erwartetes Ergebnis:

- vollständige JVM-Suite ohne Fehler oder übersprungene Pflichtfälle;
- Android Lint ohne neue Fehler;
- Debug-APK erfolgreich gebaut;
- keine Whitespace-/Patchfehler;
- keine fachfremden Dateien im Sprint-Diff.

#### Erweitertes Gate für risikoreiche Sprints

Nach Sprint 2, 3, 4 und 5 sowie immer dann, wenn DI, Media3, Hilt,
Controller-Lifecycle, Listener-Scope oder neue Produktionsklassen geändert
werden, kommen mindestens hinzu:

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat compileDebugAndroidTestKotlin
```

Zusätzlich gelten folgende fokussierte Gerätegates:

- Sprint 2: echter Gateway-/Service-Verbindungstest und Reconnect-Test auf AVD
  `newaudio`;
- Sprint 3: fokussierter Musik-/Video-/Session-Smoke;
- Sprint 4: fokussierter aktiver Delete-Smoke mit kontrollierten Testmedien;
- Sprint 5: Positionsticker-, Pause-, Persistenz- und Hintergrund-Smoke;
- Sprint 7: vollständige Instrumentation und vollständiger manueller AVD-Smoke.

#### Abbruchregel

Wenn eines der Pflichtgates fehlschlägt:

1. wird der Sprint nicht als erledigt markiert;
2. wird kein Sprintabschluss-Commit erstellt;
3. beginnt der nächste Sprint nicht;
4. wird zuerst geklärt, ob der Fehler durch den Sprint oder durch eine bereits
   vorhandene unabhängige Änderung verursacht wurde;
5. wird der Fehler im aktuellen Sprint korrigiert oder der Sprint gezielt
   zurückgerollt;
6. wird anschließend das vollständige Pflichtgate erneut ausgeführt.

#### Dokumentations- und Commitregel

Vor dem Sprintabschluss werden im Plan dokumentiert:

- tatsächlich ausgeführte Testcommands;
- Testanzahl und Ergebnis;
- Lint-/Build-/Instrumentation-Ergebnis;
- bewusst nicht ausgeführte manuelle Gates mit Begründung;
- verbleibende Abweichungen oder Restrisiken;
- betroffene Dateien und geplanter Commitumfang.

Erst danach wird ein thematisch sauberer Commit erstellt. Der Commit darf nur
Dateien des jeweiligen Sprints enthalten und muss selbst kompilieren. Nach dem
Commit wird mit `git status --short` geprüft, dass keine versehentlich
mitgenommenen oder verlorenen Änderungen vorliegen.

## 8. Sprint 0 – Isolation und Verhaltensbaseline

### Ziel

Eine saubere, reproduzierbare Ausgangslage, bevor erneut Lifecycle- und
Playbackcode verändert wird.

### Arbeitspakete

- unabhängige Playlist-, Settings-, Gradient- und Tracingänderungen isolieren;
- Media-Dateien und Tests als eindeutigen Diff erfassen;
- aktuelle Anzahl und Ergebnis der JVM-Tests dokumentieren;
- aktuellen Lint-, Debug-, Release- und AndroidTest-Compile-Stand sichern;
- bestehendes Verhalten für widersprüchliche Medienmetadaten charakterisieren;
- bestehendes Verhalten für Playerfehler während Start/Resume charakterisieren;
- bestehendes Disconnect-Verhalten mit einem kontrollierten Test-Seam
  reproduzieren;
- Main-/IO-Dispatcher-Verträge der beteiligten Klassen festhalten;
- festlegen, ob der Plan gezielt aus `.gitignore` ausgenommen wird.

### Tests

- neue Characterization Tests, die zunächst den aktuellen Zustand zeigen:
  - explizit Audio plus `.mp4`;
  - explizit Video plus Audioendung;
  - `setMediaItems()` wirft beim Musikstart;
  - `prepare()` wirft beim Videostart;
  - Resume-Session ist nach Fehlschlag weiterhin verfügbar;
  - Controller wird nach erfolgreichem Aufbau getrennt;
  - zweiter Zugriff nach Disconnect verwendet nicht still den alten Controller.
- vollständiger JVM-Lauf;
- `git diff --check`.

### Exit-Kriterien

- Baseline ist reproduzierbar;
- fremde Änderungen sind isoliert;
- jeder bestätigte P1-Befund besitzt einen rot werdenden Test oder einen
  dokumentierten Instrumentation-Test-Seam;
- noch keine produktive Verhaltensänderung.

### Empfohlener Commit

`test: characterize media lifecycle and transition failures`

## 9. Sprint 1 – Mapper- und Error-Mapping-Korrektheit

### Ziel

Kleine, klar abgegrenzte Funktionsfehler vor den risikoreichen Lifecycle-
Änderungen beseitigen.

### Arbeitspakete

- `Media3ItemMapper.isVideo()` auf explizite Prioritätsentscheidung umstellen;
- gemeinsame Funktion für `MediaType?` statt separater, widersprüchlicher
  Boolean-Logik verwenden;
- Listener und State-Synchronizer verwenden dieselbe Klassifikation;
- `ERROR_CODE_IO_FILE_NOT_FOUND` auf `R.string.error_file_not_found` abbilden;
- unbekannten Fehler auf `R.string.unknown_error` abbilden;
- Netzwerkfehlergruppe auf echte Netzwerkcodes begrenzen;
- Error-Mapping möglichst als kleine reine/isoliert testbare Funktion oder
  `PlaybackErrorMapper` modellieren;
- veraltete Inline-Kommentare im Listener entfernen.

### Tests

#### `Media3ItemMapperTest`

- explizit Audio + `.mp4` bleibt Audio;
- explizit Video + `.mp3` bleibt Video;
- kein Extra + `.mp4` wird Video;
- kein Extra + `.flac` wird Audio;
- unbekanntes Extra fällt gemäß Vertrag auf DB/Endung zurück;
- unbekannte Endung bleibt unbekannt;
- Groß-/Kleinschreibung bleibt korrekt.

#### `PlayerListenerDelegateTest` oder `PlaybackErrorMapperTest`

- File-not-found liefert den File-not-found-Text;
- Netzwerkfehler liefern den Netzwerktext;
- Decoderfehler behalten eine vorhandene aussagekräftige Message;
- fehlende Fehlermessage verwendet die lokalisierte Unknown-Message;
- Fehler setzt `isPlaying = false`, verändert aber nicht aktive Queue/Position.

### Exit-Kriterien

- alle Media-Typ-Konsumenten liefern für dieselbe Eingabe dasselbe Ergebnis;
- keine hart codierte sichtbare `Unknown error`-Message im Listener;
- neue Matrixtests und gesamte JVM-Suite grün;
- keine Änderung an Media3-Lifecycle oder Queue-Orchestrierung.

### Empfohlener Commit

`fix: unify media type and playback error mapping`

## 10. Sprint 2 – Gateway Disconnect, Reconnect und Fehlergrenze

### Ziel

Der Gateway besitzt einen vollständigen, dokumentierten Controller-Lifecycle
und verwendet nach Disconnect niemals still eine alte Instanz.

### Arbeitspakete

- Controller-Erwerb aus `requireController(block)` in eine interne
  `acquireController()`-Funktion trennen;
- `withControllerOrNull()` fängt nur den Erwerbsfehler, nicht den
  Operationsblock;
- Fehlerursachen anhand einer expliziten Klassifikation unterscheiden:
  - Cancellation: immer weiterwerfen;
  - bestätigte temporäre Connection Failure: optionaler No-op erlaubt;
  - Connection Rejected/Security/Policy: sichtbar und nicht als temporär
    normalisieren;
  - Setup-, Listener-, Sync- und Programmierfehler: weiterwerfen;
- die Factory erhält einen testbaren Disconnect-Callback oder einen kleinen
  Connection-Handle;
- jede erfolgreiche Verbindung erhält eine Generation/Identität;
- Disconnect einer alten Generation darf keine neuere Verbindung löschen;
- bei Disconnect:
  - gecachten Controller atomar entfernen;
  - Player-Listener und dessen Child-Scope beenden;
  - Controller bestmöglich releasen;
  - `PlaybackState.player` leeren;
  - klaren, dokumentierten Restoring-/Error-State setzen;
  - nächsten Zugriff genau einen neuen Build starten lassen;
- expliziten internen `release()`-/Teardown-Pfad für Tests und kontrollierte
  Lifecycle-Enden ergänzen;
- Cleanup-Fehler loggen und als `suppressed` am Primärfehler erhalten;
- erwartete Verbindungsfehler nur einmal mit angemessenem Level loggen;
- `gatewayScope`-, `gatewayJob`- und Listener-Scope-Ownership per KDoc erklären.

### Unit-Tests

#### Erfolgs- und Idempotenzfälle

- zwei und viele parallele Initialisierungen erzeugen einen Controller;
- erneutes `initialize()` bei aktiver Verbindung baut keinen zweiten;
- `requireController()` verwendet die aktuelle Generation;
- Operationsblock läuft auf dem Main-Testdispatcher.

#### Disconnect und Reconnect

- Disconnect entfernt die aktuelle Instanz;
- Listenerjob wird beendet;
- State enthält keinen alten Player mehr;
- erster Zugriff nach Disconnect baut genau einmal neu;
- parallele Zugriffe nach Disconnect bleiben Single Flight;
- verspäteter Disconnect der alten Generation löscht die neue nicht;
- doppelter Disconnect ist idempotent;
- Disconnect während laufender Initialisierung hinterlässt keinen halben State.

#### Fehlerklassifikation

- temporäre Connection Failure wird nur in optionaler API zu `null`;
- dieselbe Failure wird in `requireController()` weitergegeben;
- Security-/Policy-/Rejected-Fehler werden nicht als temporär behandelt;
- Listener-/Sync-/Programmierfehler propagieren;
- `CancellationException` propagiert aus Factory, Warten und Operationsblock;
- `MediaControllerUnavailableException` aus dem Operationsblock wird nicht
  verschluckt;
- Setupfehler released Teilressourcen;
- Cleanupfehler ist geloggt beziehungsweise `suppressed`;
- Retry nach temporärem Fehler funktioniert;
- Retry nach nicht retrybarem Fehler folgt dem dokumentierten Vertrag.

### Instrumentation-Tests

- echter Controller verbindet sich mit `MediaPlaybackService`;
- Gateway ist nach `initialize()` bereit;
- zweite Initialisierung erzeugt keinen zweiten aktiven Controller;
- kontrollierter Session-Disconnect setzt Gateway-State zurück;
- Reconnect liefert eine neue verbundene Instanz;
- kein doppelter Player-Listener nach Reconnect.

### Exit-Kriterien

- kein Codepfad verwendet einen getrennten Controller weiter;
- Erwerbs- und Operationsfehler sind technisch getrennt;
- alle Disconnect-/Reconnect-/Race-Tests grün;
- KDoc beschreibt Scope, Generation, Retry, Cancellation und Teardown;
- realer Service-Verbindungstest auf AVD `newaudio` grün.

### Empfohlene Commits

1. `refactor: separate media controller acquisition from commands`
2. `fix: reconnect media controller after session disconnect`
3. `test: cover controller disconnect and error taxonomy`

## 11. Sprint 3 – Konsistente Playlist- und Sessiontransitionen

### Ziel

Musikstart, Videostart, Restore und Resume hinterlassen bei jeder synchron
werfenden Playeroperation einen erklärten und konsistenten Zustand.

### Arbeitspakete

- unveränderlichen `PlaybackTransitionSnapshot` definieren:
  - ursprüngliche MediaItems;
  - Index, Position und `playWhenReady`;
  - Queue-Snapshot;
  - Playback-State;
  - relevante Musik-/Videosession;
- reine `PlaybackTransitionPlan`-Berechnung für Musik und Video einführen oder
  vorhandene Sessionentscheidung entsprechend erweitern;
- Session zunächst lesen/reservieren, aber erst nach erfolgreicher Transition
  konsumieren;
- Start-/Restore-Reihenfolge explizit festlegen:
  1. Eingabe validieren;
  2. Preferences lesen/anwenden;
  3. Transition-Snapshot erfassen;
  4. MediaItems setzen;
  5. vorbereiten und gewünschte Wiedergabeabsicht setzen;
  6. Queue und Playback-State final veröffentlichen;
  7. passende Session committen/konsumieren;
- bei Fehlern vor dem Commit:
  - App-Queue und Playback-State unverändert lassen;
  - Session nicht verlieren;
  - teilweise veränderten Player bestmöglich restaurieren;
  - Rollbackfehler beobachten und an Primärfehler anhängen;
- Audio-/Video-Duplikation nur soweit reduzieren, wie ein gemeinsamer Helfer
  die fachliche Lesbarkeit verbessert;
- `runRequiredControllerAction` so benennen/dokumentieren, dass der Null-
  beziehungsweise Error-State-Vertrag verständlich ist;
- klar definieren, ob ein manueller Start immer spielt und ein explizites
  Resume den gespeicherten `playWhenReady`-Zustand übernimmt.

### Tests

#### Erfolgsfälle

- Musikstart mit gültigem und ungültigem Index;
- Videostart symmetrisch;
- Preferences werden vor Start angewendet;
- manuell geklickter Start spielt gemäß Produktvertrag;
- Resume erhält Index, Position, Queue, Ordner und `playWhenReady`;
- pausierte Session bleibt beim Resume pausiert;
- Session wird nach Erfolg genau einmal konsumiert;
- Musik- und Videosession bleiben getrennt.

#### Fehler pro Phase

- `setMediaItems()` wirft;
- `prepare()` wirft;
- `setPlayWhenReady()` wirft;
- `play()` wirft;
- `pause()` wirft;
- State-Commit-Helfer wirft im Test-Seam;
- Player-Rollback wirft zusätzlich.

Für jeden Fehlerfall prüfen:

- ursprüngliche App-Queue bleibt erhalten;
- ursprünglicher Playback-State bleibt erhalten;
- Session bleibt wiederverwendbar;
- Controller-Rollback wurde mit ursprünglichen Items, Index und Position
  versucht;
- Rollbackfehler ersetzt nicht den Primärfehler;
- Cancellation wird nicht als normaler Transitionfehler behandelt.

#### Listener-Race

- synchroner `onMediaItemTransition` während `setMediaItems()` führt nach
  Abschluss zu einem korrekten finalen State;
- fehlgeschlagene Transition veröffentlicht keine neue Queue dauerhaft;
- Song und Video sind im finalen State gegenseitig exklusiv.

### Exit-Kriterien

- kein Sessionverlust bei fehlgeschlagenem Start/Resume;
- Queue, Player und App-State folgen einem dokumentierten Commit-Punkt;
- sämtliche Fehlerphasen sind getestet;
- Fassade bleibt lesbar und enthält keine neue Low-Level-Lifecyclelogik;
- vollständige JVM-Suite grün.

### Empfohlene Commits

1. `refactor: model playback transitions explicitly`
2. `fix: preserve queue and sessions on player command failure`
3. `test: cover playback transition rollback phases`

## 12. Sprint 4 – Delete-Rollback und Pfadvertrag

### Ziel

Die gute pure Delete-Planung bleibt erhalten, bildet aber ihre Invarianten
präziser ab und macht jeden Rollbackfehler sichtbar.

### Arbeitspakete

- `DeletedMediaSnapshot`, `DeletedMediaDecision` und `ActiveMediaKind` auf die
  kleinste notwendige Sichtbarkeit (`internal`) begrenzen;
- widersprüchliche Audio-/Videozustände durch ein stärkeres Modell vermeiden,
  zum Beispiel eine sealed Queue-/Active-Media-Struktur;
- `currentIndex` in `originalCurrentIndex` umbenennen;
- `paths` in `deletedPaths` umbenennen;
- bei ungültigem Playerindex zuerst den bekannten aktiven Pfad in der
  Controllerqueue suchen, erst danach auf Index `0` fallen;
- `playWhenReady` statt `isPlaying` erfassen und restaurieren;
- Reconciler in klar benannte Phasen teilen:
  - Snapshot erfassen;
  - Entscheidung berechnen;
  - Controlleränderung anwenden;
  - Controllerposition/Wiedergabeabsicht finalisieren;
  - Queue/State veröffentlichen;
  - bei jedem Vor-Commit-Fehler kompensieren;
- Rollbackfehler loggen und als `suppressed` am Originalfehler anhängen;
- Methodenname und KDoc ausdrücklich als Best-Effort-Kompensation formulieren;
- Pfadvertrag dokumentieren:
  - Slash-/Backslash-Normalisierung;
  - wiederholte und trailing separators;
  - case-sensitive Android-Vergleich;
  - echte Pfadgrenzen;
  - Verhalten für Root, URI, UNC, `.` und `..`;
  - leere/blanke Eingaben als No-op.

### Pure Decision Tests

- exakte Datei;
- echter Unterordner;
- Präfix-Geschwister `A` und `AB`;
- Windows- und Androidseparatoren;
- wiederholte und trailing separators;
- Groß-/Kleinschreibung;
- Rootpfad gemäß festgelegtem Vertrag;
- URI-Eingaben gemäß Vertrag;
- UNC-Pfad gemäß Vertrag;
- Dot-Segmente gemäß Vertrag;
- leerer/blanker Pfad;
- Mehrfachlöschung und deduplizierte Eingabe;
- ungültiger Index plus bekannter Aktivpfad;
- aktuelles mittleres und letztes Element;
- alle Elemente gelöscht;
- widersprüchlicher Snapshot wird abgelehnt oder deterministisch behandelt.

### Reconciler Tests

- erste Entfernung schlägt fehl;
- zweite Entfernung schlägt nach einer erfolgreichen ersten fehl;
- Rollback stellt Originalitems, Index, Position und `playWhenReady` wieder her;
- Rollback selbst schlägt fehl und wird sichtbar/suppressed;
- `seekTo()` nach Removals schlägt fehl;
- `play()` oder `pause()` schlägt fehl;
- Queue-/State-Commit erfolgt erst nach erfolgreicher Controllerphase;
- `playWhenReady == true` bei `isPlaying == false` bleibt Wiedergabeabsicht true;
- pausierter Zustand bleibt pausiert;
- Cancellation wird nach Best-Effort-Cleanup weitergereicht;
- Queue-/Controller-Mismatch folgt dem dokumentierten Vertrag.

### Exit-Kriterien

- pure Entscheidung ist weiterhin Player-/Android-frei;
- ungültiger Index nutzt nach Möglichkeit den Aktivpfad;
- Wiedergabeabsicht bleibt bei Buffering erhalten;
- kein Rollbackfehler verschwindet still;
- Pfadvertrag ist in KDoc und Tests identisch;
- Delete-Tests und gesamte JVM-Suite grün.

### Empfohlene Commits

1. `refactor: strengthen deleted media decision invariants`
2. `fix: preserve playback intent during media deletion`
3. `fix: expose delete rollback failures`

## 13. Sprint 5 – Listener-Persistenz und Lifecycle

### Ziel

`PlayerListenerDelegate` wird wieder zu einem schmalen Event-Adapter. Zeit-,
Ticker- und Persistenzlogik erhalten klare Owner und deterministische Tests.

### Zielkomponenten

Pragmatischer, nicht zwingend package-verändernder Schnitt:

```text
PlayerListenerDelegate
├── PlaybackPositionTracker
├── PlaybackSnapshotWriter
├── PlaybackPreferenceWriter
└── PlaybackErrorMapper
```

Nicht jede Komponente muss eine große Klasse werden. Kleine interne Klassen
oder Interfaces sind ausreichend, sofern Lifecycle und Tests klarer werden.

### Arbeitspakete

- vorhandenen conflated Snapshot-Writer aus dem Listener extrahieren;
- `PlaybackPreferenceWriter` mit serialisierter/conflated Verarbeitung für
  Repeat und Shuffle einführen;
- garantieren, dass ein älterer Write keinen neueren Wert überschreibt;
- gewöhnliche Persistenzfehler loggen, Cancellation immer weiterreichen;
- Positionsticker in `PlaybackPositionTracker` verschieben;
- monotone/testbare Zeitquelle injizieren statt direktem
  `System.currentTimeMillis()`;
- Ticker nur bei relevanten Playing-/Playback-State-Events starten/stoppen;
- Repeat-/Shuffle-Event darf den Positionsticker nicht unnötig neu starten;
- Listener-Scope wird vom Gateway erzeugt und bei Disconnect/Release beendet;
- Channel-/Worker-Lifecycle klar schließen oder an Scope-Cancellation binden;
- `PlayerListenerDelegate` konzentriert sich auf:
  - Events erkennen;
  - Mapper/Queue konsultieren;
  - State-Update auslösen;
  - spezialisierte Writer/Tracker informieren;
- Constructor-Abhängigkeiten durch kleine gebündelte Kollaboratoren reduzieren;
- KDoc für Scope-Ownership und Threading ergänzen.

### Tests

#### `PlaybackPreferenceWriterTest`

- schnelle Repeatfolge persistiert final nur/zuletzt den neuesten Wert;
- schnelle Shufflefolge analog;
- ältere langsame Anfrage überschreibt neuere nicht;
- Repeat und Shuffle beeinflussen einander nicht;
- gewöhnlicher Repositoryfehler wird behandelt;
- Cancellation beendet den Worker und wird nicht als normaler Fehler geloggt.

#### `PlaybackSnapshotWriterTest`

- conflated Reihenfolge bleibt erhalten;
- letzter Song/Position/Ordner gewinnt;
- geschlossener/cancelled Scope akzeptiert keine still verlorene Pflichtanfrage;
- Fehler und Cancellation folgen dem Vertrag.

#### `PlaybackPositionTrackerTest`

- startet nur bei gewünschter aktiver Wiedergabe;
- stoppt bei Pause;
- doppeltes Startsignal erzeugt nur einen Job;
- Repeat-/Shuffle-Event startet nicht neu;
- Position wird im Intervall aktualisiert;
- Auto-Save verwendet monotone Zeit und Positionsdelta;
- Scope-Cancellation beendet den Job.

#### `PlayerListenerDelegateTest`

- Audio-/Video-Transition über gemeinsamen Mapper;
- widersprüchliche Extra-/Extension-Klassifikation bleibt korrekt;
- State-Exklusivität Song/Video;
- Pause fordert sofortiges Snapshot-Save an;
- Playerfehler verwendet `PlaybackErrorMapper`;
- Listener delegiert Repeat/Shuffle genau einmal;
- keine direkte Zeit-/Persistenzschleife mehr im Listener.

### Exit-Kriterien

- Listener ist primär Event-Adapter;
- kein unabhängiger Preference-Write kann einen neueren Wert überschreiben;
- Ticker-/Writer-Lifecycle ist an genau einen Scope gebunden;
- Zeitlogik ist deterministisch testbar;
- Listener-Konstruktor und Methoden bleiben überschaubar;
- Listener-, Writer- und Tracker-Tests grün.

### Empfohlene Commits

1. `refactor: extract playback persistence workers`
2. `refactor: extract playback position tracker`
3. `test: cover listener lifecycle and persistence races`

## 14. Sprint 6 – Struktur- und Dokumentationspolitur

### Ziel

Nicht offensichtliche Verträge stehen am Code, Duplikation wird nur dort
reduziert, wo Lesbarkeit tatsächlich steigt.

### Arbeitspakete

#### Öffentlicher Vertrag

- `IMediaRepository` mit Klassen-KDoc versehen;
- `PlaybackState`-Invarianten dokumentieren:
  - Song und Video gegenseitig exklusiv;
  - Bedeutung von `isRestoring`;
  - Einheit von Position/Dauer;
  - Rolle des optionalen `player`-Handles;
- Rückgabevertrag von Resume-Methoden erklären;
- Fehler-/No-op-Vertrag für optionale Commands erklären;
- Kommentare `new` und `REMOVED` entfernen.

#### Komponenten-KDoc

- `MediaControllerGateway`: Scope, Generation, Single Flight, Retry,
  Disconnect, Main Dispatcher und Release;
- `MediaControllerFactory`: welche Fehler typisiert werden dürfen;
- `PlaybackSessionCoordinator`: Capture-vor-Wechsel und Consume-once;
- `Media3PlaybackStateSynchronizer`: Bedeutung `true`/`false`;
- `Media3ItemMapper`: MediaId-/Playback-URI- und Typpriorität;
- `MediaLibraryRepository`: IO-, Scan-, Lookup- und Fallbackvertrag;
- `DeletedMediaDecisionCalculator`: Pfad- und Indexvertrag;
- `DeletedMediaReconciler`: Best-Effort-Commit/Rollback-Grenze;
- Listener-Helfer: Scope- und Cancellation-Ownership.

#### Lesbarkeit

- Audio-/Video-Duplikation im Synchronizer über kleine gemeinsame
  State-Bausteine reduzieren;
- Restore-Audio/-Video-Helfer nur dann vereinheitlichen, wenn Typklarheit nicht
  durch Generics/Sealed-Abstraktion schlechter wird;
- lange Orchestrierungsmethoden in benannte Phasen aufteilen;
- öffentliche interne Datenmodelle auf `internal` begrenzen;
- Magic Numbers für Positionsdelta und Intervalle sprechend benennen;
- Codekommentare einheitlich auf Englisch oder gemäß bestehender
  Projektkonvention schreiben;
- keine reinen Zeilenlimit-Refactorings.

#### Dauerhafte Architekturdokumentation

- eine versionierte Datei `docs/media-playback-architecture.md` anlegen oder
  diesen Plan gezielt aus `.gitignore` ausnehmen;
- enthalten sein müssen:
  - Komponentendiagramm;
  - Controller-Lifecycle und Reconnect-Sequenz;
  - Playback-Transition/Commit/Rollback-Sequenz;
  - Delete-Entscheidung und Best-Effort-Kompensation;
  - Threading-/Dispatcher-Matrix;
  - bekannte Grenzen und manuelle Release-Gates.

### Review- und Testaufgaben

- KDoc-Review durch mindestens eine zweite Person/einen zweiten Agenten;
- prüfen, dass Dokumentation tatsächlichem Verhalten und Tests entspricht;
- Testnamen auf fachliche Aussage statt Implementierungsdetail prüfen;
- keine neuen Reflection-Tests;
- keine Kommentare, die veraltete Änderungshistorie konservieren;
- `git diff --check`;
- `lintDebug`;
- gesamte JVM-Suite.

### Optionale statische Qualitätswerkzeuge

Ktlint/Detekt nur ergänzen, wenn:

- keine repo-weite Massendiff entsteht;
- eine Baseline oder klarer Scope für Bestandsbefunde definiert ist;
- CI denselben Task ausführt;
- das Tool in einem eigenen Commit eingeführt wird.

Andernfalls bleibt die Einführung ein separater Folgeplan.

### Exit-Kriterien

- alle nicht offensichtlichen öffentlichen/Lifecycle-Verträge besitzen KDoc;
- `IMediaRepository` enthält keine Migrationsarchäologie-Kommentare;
- eine langlebige Architekturdokumentation ist versioniert;
- Dokumentation, Tests und Implementierung widersprechen einander nicht;
- keine unnötige Abstraktion nur zur Zeilenreduktion.

### Empfohlene Commits

1. `docs: document media playback contracts and lifecycle`
2. `refactor: polish media component readability`

## 15. Sprint 7 – Integration, Emulator und Release-Gate

### Ziel

Die Komponenten funktionieren gemeinsam mit echtem Service, Activity-
Lifecycle und kontrollierten Testmedien.

### Automatische Gradle-Matrix

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat connectedDebugAndroidTest
```

Falls parallele Projekte denselben Gradle-Daemon verwenden, für den finalen
isolierten Lauf:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin
```

### Instrumentation-Suite

- echte Verbindung zum `MediaPlaybackService`;
- wiederholte Initialisierung bleibt idempotent;
- kontrollierter Disconnect und Reconnect;
- keine doppelte Listenerreaktion;
- vorhandene Servicequeue wird rekonstruiert;
- Shuffle, Repeat, Position und Medienart werden übernommen;
- Activity-Neustart verwendet den bestehenden verbundenen Controller;
- Prozess-/Servicefehler führt zu erklärtem State statt altem Playerhandle;
- kontrollierter aktiver Delete-Fall mit ausschließlich Testfixtures, sofern
  ohne Benutzerdaten möglich.

### Manueller AVD-Smoke auf `newaudio`

#### Start und Controller

1. App frisch installieren und starten.
2. Kein `Unknown error` beim normalen Start.
3. Serviceverbindung und Notification prüfen.
4. App schließen/öffnen, ohne doppelten Player.
5. Hintergrund/Vordergrund bei laufender Wiedergabe.
6. kontrollierten Service-Disconnect auslösen.
7. nächste Wiedergabeaktion muss reconnecten.
8. kein doppelter Listener, keine doppelte Audiowiedergabe.

#### Musik und Video

1. Musikqueue in der Mitte starten.
2. Pause, Play, Seek, Next, Previous.
3. Shuffle und Repeatmodi.
4. Musik -> Video -> Musik mit Position und Pausezustand.
5. Video -> Musik -> Video analog.
6. App während Buffering/Prepare in Hintergrund/Vordergrund bewegen.
7. UI zeigt nie Song und Video gleichzeitig aktiv.

#### Fehler- und Rollbackfälle

1. kontrolliert nicht verfügbare Datei starten;
2. File-not-found-Text muss korrekt sein, nicht Netzwerktext;
3. Service zeitweise nicht verfügbar;
4. Retry nach Wiederverfügbarkeit;
5. fehlgeschlagener Start darf vorherige Queue/Session nicht verlieren;
6. keine dauerhaft hängende Restoring-Anzeige.

#### Delete

1. nicht aktives Element vor aktuellem;
2. nicht aktives Element nach aktuellem;
3. aktuelles mittleres;
4. aktuelles letztes;
5. mehrere Elemente mit partieller Indexverschiebung;
6. komplette Testqueue;
7. Präfix-Geschwisterordner;
8. während Playing, Pause und kontrolliertem Buffering;
9. nach Delete Hintergrund/Vordergrund und Notification prüfen.

### P30-Release-Gate

Vor produktiv signierter Auslieferung:

- Release-APK auf P30 installieren;
- Musik-/Video-Wechsel;
- Hintergrund/Notification;
- Datei nicht gefunden;
- Service-Reconnect;
- aktiver Delete;
- längerer Playbacktest auf Hersteller-Firmware;
- keine neuen Crash-/ANR-/StrictMode-Auffälligkeiten.

Wenn das P30 nicht verbunden ist, bleibt dieser Punkt ausdrücklich offen und
darf nicht als durchgeführt markiert werden.

### Exit-Kriterien

- volle automatische Matrix grün;
- relevante Instrumentationstests auf AVD `newaudio` grün;
- manueller AVD-Smoke dokumentiert und erfolgreich;
- keine Regression in Appstart, Playback, Sessionwechsel oder Delete;
- P30-Smoke erfolgreich oder klar als offenes Release-Gate dokumentiert;
- aktuelle APK nach Instrumentationstest wieder auf dem AVD installiert.

### Empfohlener Commit

`test: complete media hardening integration coverage`

## 16. Testmatrix

| Bereich | Unit-Test | Integration/Instrumentation | Hauptrisiko |
|---|---|---|---|
| Media-Typ | `Media3ItemMapperTest` | Service-State-Restore | Audio/Video-Widerspruch |
| Error-Mapping | `PlaybackErrorMapperTest` | sichtbarer AVD-Fehler | falsche Meldung |
| Gateway-Erwerb | `MediaControllerGatewayTest` | echte Serviceverbindung | falsche Fehlernormalisierung |
| Gateway-Concurrency | Gateway-Race-Tests | Reconnect | Doppelcontroller |
| Disconnect | Generation-/Teardown-Tests | kontrollierter Disconnect | alter Controllercache |
| Playliststart | `MediaRepositoryImplTest` | Musik-/Video-Smoke | halbe Transition |
| Session-Resume | Coordinator-/Facade-Tests | Moduswechsel | Sessionverlust |
| Delete-Entscheidung | Calculator-Tests | optional Fixture-Test | falscher Pfad/Index |
| Delete-Anwendung | Reconciler-Tests | aktiver Delete | inkonsistenter Player/State |
| Listener | Delegate-Tests | Serviceevent-Smoke | falscher State |
| Persistenz | Writer-Tests | App-Neustart | ältere Werte gewinnen |
| Position | Tracker-Tests | längerer Playback-Smoke | Jobleck/Drift |
| Dokumentation | Reviewcheckliste | – | veralteter Vertrag |

## 17. Risikoanalyse und Gegenmaßnahmen

### 17.1 Gateway-/Service-Lifecycle – hoch

**Risiko:** Reconnect erzeugt Doppelcontroller oder ein alter Disconnect löscht
eine neue Verbindung.

**Gegenmaßnahmen:**

- Connection-Generation;
- Single-Flight beibehalten;
- Disconnect-/Reconnect-Racetests;
- Listener-/Job-Teardown idempotent;
- echter Service-Test auf dem AVD.

### 17.2 Playback-Transition – hoch

**Risiko:** Listenercallbacks und Playerkommandos erzeugen Zwischenzustände.

**Gegenmaßnahmen:**

- reiner Plan und expliziter Commit-Punkt;
- ursprünglichen Player-/App-State erfassen;
- State am Ende ausdrücklich final setzen;
- Fehler in jeder Playerphase testen;
- Best-Effort-Rollback statt falscher Atomaritätsbehauptung.

### 17.3 Delete-Rollback – mittel bis hoch

**Risiko:** Mehrfachentfernung mutiert Player teilweise, danach scheitert die
Kompensation.

**Gegenmaßnahmen:**

- Test, bei dem erst die zweite Entfernung scheitert;
- Rollbackfehler als `suppressed`;
- Queue/State erst nach Controllerphase publizieren;
- kontrollierte Testmedien im Emulator.

### 17.4 Listener-Aufteilung – mittel

**Risiko:** Zu viele kleine Klassen oder geändertes Persistenz-Timing.

**Gegenmaßnahmen:**

- nur vier klar benannte Verantwortlichkeiten;
- vorhandene Conflation semantisch erhalten;
- Zeitquelle und Scheduler injizieren;
- Race- und Cancellationtests vor Umschaltung.

### 17.5 Dokumentation wird sofort wieder veraltet – mittel

**Gegenmaßnahmen:**

- Dokumentation gemeinsam mit dem jeweiligen Produktionscommit ändern;
- Tests als Vertragsbeispiele verlinken;
- keine exakten, schnell veraltenden Zeilenzahlen in Architekturdocs;
- finaler Dokumentationsabgleich nach Sprint 7.

## 18. Verbotene Abkürzungen

- kein neues globales `MediaManager`-God-Object;
- kein Catch-All, das unbekannte Fehler in `null` umwandelt;
- kein Verschlucken von Cancellation;
- kein nacktes `runCatching` für Cleanup ohne Logging/suppressed Fehler;
- kein Aktualisieren von App-Queue/State vor einem definierten Commit-Punkt
  ohne Kompensationspfad;
- keine öffentliche mutable Queue oder `MutableStateFlow`;
- keine Reflection auf private MediaRepository-/Gateway-Felder in neuen Tests;
- keine Tests ausschließlich mit `relaxed` Mocks, wenn Interaktionsreihenfolge
  Teil des Vertrags ist;
- kein Threading-/Lifecycle-Kommentar ohne entsprechenden Test;
- keine repo-weite Formatierungsänderung zusammen mit Lifecycle-Fixes;
- keine Behauptung echter Atomarität über Player und In-Memory-Stores.

## 19. Empfohlene Commitfolge

1. `test: characterize media lifecycle and transition failures`
2. `fix: unify media type and playback error mapping`
3. `refactor: separate media controller acquisition from commands`
4. `fix: reconnect media controller after session disconnect`
5. `test: cover controller disconnect and error taxonomy`
6. `refactor: model playback transitions explicitly`
7. `fix: preserve queue and sessions on player command failure`
8. `refactor: strengthen deleted media decision invariants`
9. `fix: preserve playback intent during media deletion`
10. `fix: expose media rollback failures`
11. `refactor: extract playback persistence workers`
12. `refactor: extract playback position tracker`
13. `docs: document media playback contracts and lifecycle`
14. `refactor: polish media component readability`
15. `test: complete media hardening integration coverage`

Jeder Commit muss kompilieren und seine relevanten Tests bestehen. Fremde
Playlist-, Settings-, Gradient- oder Tracingänderungen dürfen nicht in diese
Commitfolge gelangen.

## 20. Definition of Done

### Funktion und Robustheit

- [ ] expliziter Medientyp hat überall Vorrang vor Dateiendung;
- [ ] File-not-found und Unknown Error sind korrekt lokalisiert;
- [ ] Controller-Disconnect entfernt die alte Instanz und deren Listenerjob;
- [ ] erster Zugriff nach Disconnect erzeugt genau eine neue Verbindung;
- [ ] alter Disconnect kann keine neuere Generation löschen;
- [ ] temporäre, permanente, unerwartete und Cancellationfehler sind getrennt;
- [ ] optionale Gateway-API verschluckt keine Fehler aus dem Operationsblock;
- [ ] Cleanupfehler werden geloggt und/oder als `suppressed` erhalten;
- [ ] fehlgeschlagener Playliststart verändert App-Queue/State nicht dauerhaft;
- [ ] fehlgeschlagenes Resume verliert die gespeicherte Session nicht;
- [ ] Wiedergabeabsicht verwendet `playWhenReady`;
- [ ] Delete-Rollback deckt partielle Mehrfachmutation ab;
- [ ] Rollbackfehler verschwinden nicht still;
- [ ] Queue, aktives Medium und Player folgen dokumentierten Commit-Punkten.

### Struktur

- [ ] Gateway besitzt klaren Acquire-, Disconnect-, Retry- und Release-Vertrag;
- [ ] `PlayerListenerDelegate` ist primär Event-Adapter;
- [ ] Ticker, Snapshot- und Preference-Persistenz besitzen klare Owner;
- [ ] keine neue God-Class wurde eingeführt;
- [ ] interne Decision-/Snapshotmodelle sind nicht unnötig öffentlich;
- [ ] Audio-/Video-Duplikation ist pragmatisch reduziert;
- [ ] `MediaRepositoryImpl` bleibt eine verständliche Fassade.

### Dokumentation

- [ ] `IMediaRepository` dokumentiert State-, Fehler- und No-op-Verträge;
- [ ] Gateway-KDoc dokumentiert Scope, Threading, Retry und Disconnect;
- [ ] Session-KDoc dokumentiert Capture und Consume-once;
- [ ] Mapper-KDoc dokumentiert Typ- und URI-Priorität;
- [ ] Delete-KDoc dokumentiert Pfadvertrag und Best-Effort-Rollback;
- [ ] Synchronizer dokumentiert das Boolean-Resultat;
- [ ] veraltete `new`-/`REMOVED`-/Implementierungskommentare sind entfernt;
- [ ] langlebige Media-Architekturdokumentation ist versioniert;
- [ ] Plan/Dokumentation und tatsächliches Verhalten stimmen überein.

### Tests und Release

- [ ] alle neuen Unit- und Regressionstests sind grün;
- [ ] vollständige JVM-Suite ist grün;
- [ ] `lintDebug` ist grün;
- [ ] Debug- und Release-Build sind grün;
- [ ] AndroidTest-Kompilierung ist grün;
- [ ] Disconnect-/Reconnect-Instrumentationstest ist auf `newaudio` grün;
- [ ] manueller AVD-Smoke ist dokumentiert und erfolgreich;
- [ ] aktuelle APK ist nach Instrumentation wieder installiert;
- [ ] P30-Smoke ist erfolgreich oder als offenes Release-Gate dokumentiert;
- [ ] Media-Commits enthalten keine fachfremden Änderungen.

## 21. Abschlusskriterium

Der Hardening-Plan ist erst abgeschlossen, wenn nicht nur alle Tests grün sind,
sondern die wichtigsten nicht offensichtlichen Verträge direkt am Code stehen:

- Wer besitzt und beendet welchen Scope?
- Wann gilt ein Controller als verwendbar?
- Welche Fehler dürfen zu einem No-op werden?
- Wann wird eine Playback-/Delete-Transition committed?
- Was wird bei einem Fehler garantiert zurückgerollt und was nur bestmöglich?
- Welche Quelle entscheidet Audio versus Video?

Erst wenn Implementierung, Tests und Dokumentation darauf dieselbe Antwort
geben, ist der Media-Code robust, gut strukturiert und nachhaltig wartbar.
