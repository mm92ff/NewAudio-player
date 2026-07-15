# NewAudio: Gradient-Richtungswahl mit Vektorpfeilen

**Stand:** 14. Juli 2026
**Status:** Umgesetzt und auf dem AVD `newaudio` abgenommen; P30-Smoke extern ausstehend
**Referenzprojekt:** `C:\Users\jemi\Desktop\Github\pocastcloni`
**Zielgerät für die lokale Abnahme:** dedizierter AVD `newaudio` (`emulator-5554`)

## 1. Ziel

NewAudio erhält im Settings-Tab **Design** eine Auswahl für die Richtung des
Hintergrundverlaufs. Die Bedienung und die inneren Größenverhältnisse werden aus
`pocastcloni` übernommen:

```text
↖   ↑   ↗
←       →
↙   ↓   ↘
```

Es stehen genau acht Richtungen zur Verfügung:

1. Oben nach unten
2. Unten nach oben
3. Links nach rechts
4. Rechts nach links
5. Oben links nach unten rechts
6. Unten rechts nach oben links
7. Oben rechts nach unten links
8. Unten links nach oben rechts

Die bisherige Darstellung bleibt nach dem Update unverändert, solange der Nutzer
nichts umstellt: **Oben nach unten** ist der Standard und entspricht dem heutigen
`Brush.verticalGradient`.

## 2. Bestätigte Ausgangslage

### NewAudio

- Der Gradient ist bereits über `backgroundGradientEnabled` aktivierbar.
- Die Stärke/Farbtönung wird über `backgroundTintFraction` gesteuert.
- Der Verlauf ist in `ui/theme/Theme.kt` fest auf vertikal, oben nach unten,
  eingestellt.
- Die Gradient-Option befindet sich bereits im Tab **Design**.
- Settings werden über `UserPreferences`, DataStore, UseCases und
  `SettingsViewModel` durchgereicht.
- `UserPreferences` ist serialisierbar und Bestandteil von Backup/Restore.
- Settings-Karten besitzen in NewAudio einen eigenen, vom Nutzer einstellbaren
  Transparenz- und Rahmenstil.

### Referenz `pocastcloni`

- Es existieren keine separaten SVG- oder VectorDrawable-Dateien für die Pfeile.
- Der Pfeil wird als auflösungsunabhängige Canvas-Vektorgrafik gezeichnet.
- Eine Grundform zeigt nach oben; die sieben weiteren Richtungen entstehen durch
  Rotation.
- Die Gradient-Endpunkte werden aus der tatsächlichen Root-Größe berechnet.

## 3. Verbindlicher visueller Vertrag

Die interne Geometrie wird exakt aus `pocastcloni` übernommen. Symbolische
`Dimens`-Namen werden nicht blind kopiert, da `PaddingSmall` in beiden Projekten
unterschiedliche Werte besitzt.

| Element | Wert | Verhältnis |
|---|---:|---:|
| Richtungsbutton | 64 dp × 64 dp | Referenzgröße |
| Horizontaler/vertikaler Abstand | 8 dp | Button : Abstand = 8 : 1 |
| Pfeil-Canvas | 30 dp × 30 dp | 46,875 % der Buttonkante |
| Pfeilstrich | 2,4 dp | 8 % der Pfeilgröße |
| Button-Rundung | 8 dp | 12,5 % der Buttonkante |
| Button-Rahmen | 2 dp | wie Referenz |
| Karten-Innenabstand | 12 dp | wie Referenz |
| Rasterbreite | 208 dp | `3 × 64 + 2 × 8` |
| Rasterhöhe | 208 dp | `3 × 64 + 2 × 8` |

### Vektorpfeil

Die Grundform wird als Canvas-Vektor übernommen:

- Schaftstart: `(50 % Breite, 78 % Höhe)`
- Schaftende: `(50 % Breite, 22 % Höhe)`
- Pfeilkopfgröße: `22 %` der Canvas-Breite
- Linienenden: rund (`StrokeCap.Round`)
- keine Bitmap-Assets
- keine neue Grafikbibliothek
- keine acht duplizierten XML- oder SVG-Dateien

Rotationsvertrag:

| Richtung | Rotation der nach oben zeigenden Grundform |
|---|---:|
| Unten nach oben | 0° |
| Unten links nach oben rechts | 45° |
| Links nach rechts | 90° |
| Oben links nach unten rechts | 135° |
| Oben nach unten | 180° |
| Oben rechts nach unten links | −135° |
| Rechts nach links | −90° |
| Unten rechts nach oben links | −45° |

### Farben und Zustand

- Ausgewählter Hintergrund: `primary` mit Alpha `0,22`.
- Ausgewählter Rahmen und Pfeil: `primary`.
- Nicht ausgewählter Hintergrund: transparent.
- Nicht ausgewählter Rahmen: `outlineVariant` mit Alpha `0,78`.
- Nicht ausgewählter Pfeil: `onBackground`.
- Farbwechsel dürfen animiert werden, dürfen aber keine neue Abhängigkeit aus
  `pocastcloni` übernehmen.
- Der äußere Container verwendet weiterhin NewAudios `SettingsCard`, damit die
  nutzerdefinierten Kartenoptionen für Transparenz, Rahmenbreite und Rahmenfarbe
  respektiert werden.

## 4. Verbindliche Architekturentscheidungen

1. `GradientDirection` wird im NewAudio-Domainmodell typisiert abgebildet; keine
   frei interpretierbaren Strings im UI.
2. Standard und Fallback sind `TOP_TO_BOTTOM`.
3. DataStore speichert `enum.name` unter einem neuen String-Key.
4. Ungültige oder zukünftige DataStore-Werte fallen ohne Crash auf den Standard
   zurück.
5. Das neue serialisierte Feld erhält einen Defaultwert, damit ältere Backups ohne
   Richtungsfeld importierbar bleiben.
6. Die Richtungsauswahl wird nur angezeigt, wenn der Gradient aktiviert ist.
7. Ein Richtungswechsel aktualisiert das Theme sofort und benötigt keinen
   App-Neustart.
8. Die Farbfolge bleibt semantisch unverändert: Start = untönte Theme-Grundfarbe,
   Ende = mit Primärfarbe getönte Hintergrundfarbe.
9. Bei deaktiviertem Gradient bleibt der bisherige solide getönte Hintergrund
   unverändert; die gespeicherte Richtung wird nicht gelöscht.
10. Bei `backgroundTintFraction <= 0` bleibt der bestehende No-op-/Solid-Fallback
    erhalten.
11. Start- und Endpunkte werden aus der tatsächlichen Root-Größe berechnet.
12. Die Brush-Erzeugung erfolgt größenabhängig über `drawWithCache` oder eine
    gleichwertige Lösung ohne `onSizeChanged`-Recomposition-Schleife.
13. Es werden keine Änderungen an Compose Tracing, Macrobenchmarks oder
    Release-Signing vorgenommen.

## 5. Gradient-Mathematik

Für eine Root-Fläche mit `left = 0`, `top = 0`, `right = width`,
`bottom = height`, `centerX = width / 2` und `centerY = height / 2` gilt:

| Richtung | Startpunkt | Endpunkt |
|---|---|---|
| `TOP_TO_BOTTOM` | `(centerX, top)` | `(centerX, bottom)` |
| `BOTTOM_TO_TOP` | `(centerX, bottom)` | `(centerX, top)` |
| `LEFT_TO_RIGHT` | `(left, centerY)` | `(right, centerY)` |
| `RIGHT_TO_LEFT` | `(right, centerY)` | `(left, centerY)` |
| `TOP_LEFT_TO_BOTTOM_RIGHT` | `(left, top)` | `(right, bottom)` |
| `BOTTOM_RIGHT_TO_TOP_LEFT` | `(right, bottom)` | `(left, top)` |
| `TOP_RIGHT_TO_BOTTOM_LEFT` | `(right, top)` | `(left, bottom)` |
| `BOTTOM_LEFT_TO_TOP_RIGHT` | `(left, bottom)` | `(right, top)` |

Die Funktion für diese Zuordnung wird als kleine, deterministische und möglichst
plattformunabhängig testbare Funktion extrahiert.

## 6. Geplanter Datei- und Komponenten-Scope

### Domain und Persistenz

- `app/src/main/java/com/example/newaudio/domain/model/UserPreferences.kt`
  - Enum und Preference-Feld mit rückwärtskompatiblem Default ergänzen.
- `app/src/main/java/com/example/newaudio/domain/repository/ISettingsRepository.kt`
  - Setter für Gradient-Richtung ergänzen.
- `app/src/main/java/com/example/newaudio/data/repository/SettingsRepositoryImpl.kt`
  - DataStore-Key, Lesen, Schreiben und Restore ergänzen.
- `app/src/main/java/com/example/newaudio/domain/usecase/settings/`
  - `SetBackgroundGradientDirectionUseCase` ergänzen.
- `app/src/test/java/com/example/newaudio/fake/FakeSettingsRepository.kt`
  - neuen Setter realistisch im Fake abbilden.

### Settings-Pipeline

- `app/src/main/java/com/example/newaudio/feature/settings/SettingsViewModel.kt`
  - UseCase injizieren und Callback anbieten.
- `app/src/main/java/com/example/newaudio/feature/settings/SettingsScreen.kt`
  - Callback an die Design-Actions weiterreichen.
- `app/src/main/java/com/example/newaudio/feature/settings/SettingsTabs.kt`
  - `DesignSettingsActions` erweitern.
  - Picker direkt nach dem Gradient-Schalter einfügen.
- `app/src/main/java/com/example/newaudio/feature/settings/composables/SettingsGroupTheme.kt`
  - Direction-Card, 3×3-Picker, Button und Canvas-Pfeil ergänzen.
- `app/src/main/res/values/strings.xml`
  - Titel und acht lokalisierbare Richtungsbeschriftungen ergänzen.
  - Die bisher fest auf „Top/Bottom“ formulierte Gradient-Beschreibung neutral
    formulieren.

### Theme

- `app/src/main/java/com/example/newaudio/ui/theme/Theme.kt`
  - feste `Brush.verticalGradient`-Erzeugung ersetzen.
  - pure Start-/Endpunkt-Zuordnung ergänzen.
  - größenabhängigen `Brush.linearGradient` im Root-Drawpfad erzeugen.
  - aktuelles Farb- und Fehlerfallback erhalten.

### Tests

- `app/src/test/java/com/example/newaudio/domain/usecase/settings/`
- `app/src/test/java/com/example/newaudio/feature/settings/SettingsViewModelTest.kt`
- `app/src/test/java/com/example/newaudio/feature/settings/BackupExportImportTest.kt`
- `app/src/test/java/com/example/newaudio/feature/settings/SettingsTabTest.kt`
- `app/src/androidTest/java/com/example/newaudio/feature/settings/SettingsTabsTest.kt`
- neuer fokussierter Test für die Richtungs-/Offset-Zuordnung

## 7. Sprintplan

## Sprint 0 – Baseline und Verträge

### Ziel

Den heutigen Gradient-Zustand und die Referenzgeometrie festhalten, bevor
Produktionscode geändert wird.

### Aufgaben

- Aktuellen Default `TOP_TO_BOTTOM` dokumentieren.
- Bestehende Farbfolge und `backgroundTintFraction`-Semantik sichern.
- Referenzmaße und Rotationstabellen als Testvertrag festschreiben.
- Bestehende Settings-, Backup- und Theme-Tests erfassen.
- Sicherstellen, dass der Worktree vor der Umsetzung bewusst geprüft wird.
- Keine Produktlogik in diesem Sprint ändern.

### Tests/Baseline

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

### Abnahmekriterien

- Baseline ist grün oder bekannte, nicht featurebezogene Abweichungen sind
  dokumentiert.
- Visueller und mathematischer Vertrag ist eindeutig.
- Keine neue Dependency ist erforderlich.

## Sprint 1 – Domainmodell und sichere Persistenz

### Ziel

Die Richtungswahl wird typsicher und rückwärtskompatibel gespeichert.

### Aufgaben

- Achtwertiges `GradientDirection`-Enum ergänzen.
- Preference-Feld mit Default `TOP_TO_BOTTOM` ergänzen.
- DataStore-Key `background_gradient_direction` ergänzen.
- Lesen über die vorhandene sichere Enum-Konvertierung implementieren.
- Repository-Setter ergänzen.
- `restoreUserPreferences` um das neue Feld erweitern.
- Fake-Repository anpassen.
- Keine Room-Migration anlegen; DataStore benötigt keine Schema-Migration.

### Tests

- Default ist `TOP_TO_BOTTOM`.
- Alle acht Werte durchlaufen einen DataStore-Roundtrip.
- Ungültiger String fällt auf `TOP_TO_BOTTOM` zurück.
- Fehlender Key fällt auf `TOP_TO_BOTTOM` zurück.
- Restore schreibt die gewählte Richtung.
- Bestehende Preferences bleiben unverändert.

### Abnahmekriterien

- Kein Crash bei fehlenden oder ungültigen Daten.
- Bestehende Installationen zeigen nach dem Update weiterhin oben → unten.
- Repository- und Fake-Vertrag stimmen überein.

## Sprint 2 – Gradient-Renderer für acht Richtungen

### Ziel

Das Theme kann alle Richtungen korrekt über die gesamte Root-Fläche zeichnen.

### Aufgaben

- Pure Funktion für Start-/Endpunkte implementieren.
- Null-/Minimalgrößen auf mindestens einen sicheren Pixel begrenzen.
- `Brush.verticalGradient` durch größenabhängigen `Brush.linearGradient` ersetzen.
- Brush mit `drawWithCache` oder gleichwertigem Size-aware Drawpfad cachen.
- Defaultpfad visuell identisch halten.
- Ungültige Primärfarbe weiterhin über den vorhandenen Theme-Fallback abfangen.
- Soliden Hintergrundpfad bei deaktiviertem Gradient unverändert lassen.

### Unit-Tests

- Alle acht Start-/Endpunktpaare bei einer nicht quadratischen Größe prüfen,
  beispielsweise `1080 × 2400`; dadurch werden vertauschte Achsen sichtbar.
- Default entspricht exakt oben → unten.
- Gegenrichtungen tauschen Start und Ende exakt.
- Diagonalen verwenden die tatsächlichen vier Ecken.
- Größe `0 × 0` verursacht keinen Crash oder ungültigen Brush.
- Farbreihenfolge bleibt Grundfarbe → getönte Farbe.

### Abnahmekriterien

- Alle acht Richtungen sind mathematisch korrekt.
- Kein Layout-State und keine Recomposition-Schleife wird zur Größenmessung
  eingeführt.
- Gradient und Solid-Mode bleiben klar getrennt.

## Sprint 3 – UseCase-, ViewModel- und Action-Pipeline

### Ziel

Eine UI-Auswahl erreicht DataStore ausschließlich über den bestehenden
Settings-Architekturpfad.

### Aufgaben

- `SetBackgroundGradientDirectionUseCase` implementieren.
- UseCase in `SettingsViewModel` injizieren.
- `onBackgroundGradientDirectionChange` ergänzen.
- `DesignSettingsActions` um einen typisierten Callback erweitern.
- Callback in `SettingsScreen` verdrahten.
- Keine direkte Repository-Verwendung aus Composables einführen.

### Tests

- UseCase delegiert exakt den gewählten Enum-Wert.
- ViewModel delegiert alle acht Richtungen.
- Fehler im Repository läuft über den bestehenden Settings-Fehlerkanal.
- Änderung erscheint im `settingsState`.
- Bestehende ViewModel-Tests kompilieren mit dem neuen Konstruktorvertrag.

### Abnahmekriterien

- Vollständige Kette UI → ViewModel → UseCase → Repository ist vorhanden.
- Kein paralleler lokaler UI-Wahrheitszustand für die gespeicherte Richtung.
- Schnelle Mehrfachauswahl endet deterministisch beim letzten Wert.

## Sprint 4 – Picker und Canvas-Vektorgrafik

### Ziel

Die Referenzbedienung wird mit identischen inneren Proportionen in NewAudio
integriert.

### Aufgaben

- Direction-Card mit Titel, Raster und zentriertem Ergebnistext erstellen.
- Exaktes 3×3-Raster mit leerem Mittelpunkt umsetzen.
- Canvas-Pfeil mit Referenzgeometrie und runden Linienenden übernehmen.
- Rotationen gemäß Vertrag zuordnen.
- Button-, Gap-, Pfeil-, Strich-, Rahmen- und Rundungsmaße exakt übernehmen.
- Selected-/Unselected-Farben exakt übernehmen.
- Äußere Card über NewAudios `SettingsCard` rendern.
- Picker nur bei aktiviertem Gradient als eigenes LazyColumn-Item einfügen.
- Richtungsauswahl direkt unter dem Gradient-Schalter platzieren.
- Neutrale Gradient-Beschreibung verwenden, die nicht mehr fest „oben/unten“
  behauptet.
- Stabile Test-Tags für Picker und alle acht Buttons ergänzen.

### Semantik und Accessibility

- Gesamtes Raster als zusammengehörige Auswahl markieren.
- Jeder Button erhält `Role.RadioButton`.
- `selected`-Semantik entspricht dem Preference-Wert.
- Content Description verwendet die lokalisierte Richtungsbezeichnung.
- Leerer Mittelpunkt ist nicht fokussierbar und nicht klickbar.
- Ergebnistext nennt die aktuelle Richtung.
- 64-dp-Fläche erfüllt das Mindest-Touchziel deutlich.

### Compose-/Instrumentationstests

- Bei deaktiviertem Gradient existiert der Picker nicht.
- Bei aktiviertem Gradient existiert genau ein Picker.
- Es existieren genau acht auswählbare Richtungsbuttons.
- Der Mittelpunkt besitzt keine Click-/Selection-Semantik.
- Defaultbutton ist ausgewählt.
- Klick auf jede Richtung löst den richtigen Callback aus.
- Nach Zustandsupdate ist genau ein Button ausgewählt.
- Ergebnistext entspricht der Auswahl.
- Buttonbreite und -höhe betragen mindestens/exakt 64 dp.
- Picker passt bei 320 dp und 360 dp Breite ohne horizontales Scrollen.

### Abnahmekriterien

- Optik und Proportionen entsprechen der Referenz.
- NewAudio-Kartenstil bleibt wirksam.
- Bedienung funktioniert per Touch, Tastatur und Accessibility-Service.

## Sprint 5 – Backup-, Restore- und Regression-Härtung

### Ziel

Die neue Preference darf bestehende Backups und bestehende Design-Settings nicht
beschädigen.

### Aufgaben

- Sicherstellen, dass der Defaultwert beim Import alter JSON-Dateien angewendet
  wird.
- Export der neuen Richtung prüfen.
- Import/Restore aller acht Werte prüfen.
- Bestehende Backup-Validierung unverändert sicher halten.
- Tests mit manuell konstruiertem Legacy-JSON ohne neues Feld ergänzen.
- Settings-Tab-Scrolltests an das zusätzliche bedingte Item anpassen.
- Sicherstellen, dass Tabzustand und Scrollzustand weiterhin erhalten bleiben.

### Tests

- Legacy-Backup ohne Richtungsfeld importiert erfolgreich mit
  `TOP_TO_BOTTOM`.
- Neues Backup exportiert den gewählten Enum-Wert.
- Export → Import erhält die Richtung.
- Deaktivieren des Gradients löscht die gespeicherte Richtung nicht.
- Erneutes Aktivieren stellt die vorherige Richtung wieder her.
- Theme-, Primärfarbe-, Tint-, Transparenz- und Kartenstilwerte bleiben beim
  Restore unverändert.
- Alle bisherigen Backup- und Settings-Tests bleiben grün.

### Abnahmekriterien

- Volle Rückwärtskompatibilität mit bisherigen NewAudio-Backups.
- Kein Datenverlust beim Toggle oder Restore.
- Keine Regression der vier Settings-Tabs.

## Sprint 6 – Gesamtprüfung und Geräteabnahme

### Ziel

Die Implementierung auf JVM, Android-Test und realer UI vollständig abnehmen.

### Automatisierte Prüfung

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest

$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :app:connectedDebugAndroidTest
```

### Emulatorprüfung auf `newaudio`

1. Neueste Debug-APK installieren.
2. App kalt starten.
3. Settings → Design öffnen.
4. Gradient deaktiviert: Picker ist verborgen.
5. Gradient aktivieren: Picker erscheint ohne Layoutsprung außerhalb der Liste.
6. Alle acht Richtungen nacheinander auswählen.
7. Prüfen, dass der Verlauf sofort und korrekt reagiert.
8. App beenden und erneut starten; letzte Richtung bleibt erhalten.
9. Light-, Dark- und System-Theme prüfen.
10. Mehrere Primärfarben und Tint-Stärken prüfen.
11. Transparente und gefüllte Settings-Karten prüfen.
12. Schriftgröße 100 % und erhöhtes Font-Scale-Profil prüfen.
13. Screenshotvergleich mit der Referenz für Raster und Proportionen durchführen.
14. Logcat auf Crash, SerializationException und Compose/Layout-Fehler prüfen.

### P30-Prüfung

- Erst nach grüner Emulatorabnahme eine aktuelle signierte Release-APK verwenden.
- Acht Richtungen im Hochformat prüfen.
- Persistenz nach Force-Stop prüfen.
- Touchziele, Kontrast und flüssige Farbumschaltung prüfen.
- Edge-to-edge-Bereiche und Systemleisten auf sichtbare Gradient-Brüche prüfen.

### Abnahmekriterien

- Unit-, Lint-, Debug-, Release- und Instrumentation-Gates sind grün.
- Kein `Unknown error`, Crash oder sichtbarer Brush-Sprung.
- Default ist visuell identisch zum bisherigen NewAudio-Verlauf.
- Alle acht Richtungen sind auf AVD und P30 eindeutig unterscheidbar.
- Keine horizontale Überbreite bei der kleinsten unterstützten Testbreite.

## 8. Testmatrix

| Ebene | Schwerpunkt | Mindestnachweis |
|---|---|---|
| Pure Unit | Richtungsgeometrie | 8 Richtungen, Gegenpaare, Nullgröße |
| Repository | DataStore | Default, Roundtrip, ungültiger Wert, Restore |
| UseCase | Delegation | alle Enum-Werte |
| ViewModel | Zustandsfluss | Änderung, Fehlerpfad, letzter Wert gewinnt |
| Backup | Kompatibilität | Legacy-Import, neuer Roundtrip |
| Compose UI | Raster | 8 Buttons, Mittelpunkt leer, Auswahl eindeutig |
| Accessibility | Semantik | Rolle, Selected-State, Labels, Touchgröße |
| Integration | Theme | Live-Update ohne Neustart |
| Emulator | visuelle Abnahme | 8 Richtungen, Themes, Farben, Persistenz |
| P30 | Release-Smoke | Layout, Touch, Edge-to-edge, Force-Stop |

## 9. Risiken und Gegenmaßnahmen

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| Default ändert die bestehende Optik | sichtbare Regression nach Update | `TOP_TO_BOTTOM` und Pixel-/Screenshotvergleich |
| Diagonalpunkte sind vertauscht | Pfeil und realer Gradient widersprechen sich | pure Tests für alle acht Start-/Endpunkte |
| Alter Backup-Import scheitert | Nutzer kann Sicherung nicht wiederherstellen | serialisierter Default und Legacy-JSON-Test |
| DataStore enthält ungültigen String | Start-/Settings-Crash | vorhandenen sicheren Enum-Fallback verwenden |
| Symbolische Dimens werden blind kopiert | falsche Proportionen | konkrete Referenzwerte verwenden |
| Fremder Kartenstil überschreibt NewAudio | Design-Settings wirken inkonsistent | äußeren NewAudio-`SettingsCard` beibehalten |
| Größenmessung erzeugt Recompositions | unnötige UI-Arbeit | Brush im Drawpfad über `drawWithCache` erzeugen |
| Mittelpunkt wird fokussierbar | Accessibility-Verwirrung | reiner Platzhalter ohne Semantik/Click |
| Beschreibung bleibt „Top/Bottom“ | Text widerspricht Auswahl | neutrale Beschreibung und dynamisches Label |
| Testindex im Design-Tab verschiebt sich | bestehender UI-Test wird rot | semantische Tags statt fragile Indizes bevorzugen |

## 10. Nicht-Ziele

- Keine Änderung der verfügbaren Primärfarben.
- Keine neue Gradient-Stärke zusätzlich zu `backgroundTintFraction`.
- Keine radialen, Sweep- oder Mehrfachfarbverläufe.
- Keine frei drehbare Winkelsteuerung.
- Keine Bitmap-, SVG-Datei- oder Drittanbieter-Icon-Abhängigkeit.
- Keine Änderung an Audio-/Video-Playback.
- Keine Performance-Baseline-Neukalibrierung allein wegen dieses Features.
- Kein automatischer Push, Tag oder Release.

## 11. Definition of Done

Die Funktion gilt erst als vollständig umgesetzt, wenn alle folgenden Punkte erfüllt
sind:

- [x] Acht typisierte Richtungen sind vorhanden.
- [x] Standard und Fehlerfallback sind `TOP_TO_BOTTOM`.
- [x] DataStore, Restore und Backup unterstützen die Richtung.
- [x] Legacy-Backups bleiben importierbar.
- [x] Theme zeichnet alle acht Richtungen über die gesamte Root-Fläche korrekt.
- [x] Defaultdarstellung entspricht dem bisherigen NewAudio-Verlauf.
- [x] Picker erscheint im Design-Tab nur bei aktiviertem Gradient.
- [x] Canvas-Vektorpfeil und alle Maße entsprechen der Referenz.
- [x] NewAudio-Kartenstil bleibt wirksam.
- [x] Accessibility-Semantik ist vollständig.
- [x] Fokussierte und vollständige Unit-Tests sind grün.
- [x] Lint, Debug-, Release- und AndroidTest-Build sind grün.
- [x] Instrumentationstests auf `newaudio` sind grün.
- [x] Visuelle Emulatorprüfung der Richtungswahl und eines persistierten
      Diagonalverlaufs ist durchgeführt.
- [x] Der P30-Smoke ist bewusst als externer Schritt ausstehend dokumentiert;
      dafür wurde in diesem Schritt kein Gerät verbunden.
- [x] Git-Status und Commit-Scope wurden geprüft; ein Commit ist nicht Teil dieses
      Umsetzungsschritts.

## 12. Umsetzungs- und Prüfnachweis

**Abnahme:** 14. Juli 2026

- `:app:testDebugUnitTest`: erfolgreich.
- `:app:lintDebug`: erfolgreich.
- `:app:assembleDebug`: erfolgreich.
- `:app:assembleRelease`: erfolgreich.
- Aktuelle Release-APK lokal signiert und mit `apksigner` verifiziert (v1, v2
  und v3): `apks/newaudio-v2.41-beta-release-signed.apk`.
- `:app:assembleDebugAndroidTest`: erfolgreich.
- `:app:connectedDebugAndroidTest` auf `newaudio` / `emulator-5554`:
  17 von 17 Tests erfolgreich.
- Schnelle Richtungsfolgen werden über einen einzelnen conflated Update-Consumer
  serialisiert; ein blockierter erster Schreibvorgang plus mehrere Folgeauswahlen
  wurde mit „letzter Wert gewinnt“ getestet.
- ViewModel-StateFlow und Repository-Fehlerkanal sind für Richtungsänderungen
  fokussiert getestet.
- Picker-Semantik ist automatisiert geprüft: acht RadioButtons, lokalisierte
  Beschreibungen, genau eine Auswahl, nicht klickbarer Mittelpunkt und dynamischer
  Ergebnistext.
- Alle acht Buttons sind auf 64 dp geprüft; eigene Layouttests bei 320 dp und
  360 dp bestätigen, dass kein Button den Viewport horizontal verlässt.
- Debug-APK auf dem dedizierten AVD installiert.
- Picker im Design-Tab visuell geprüft: 3×3-Raster, leerer Mittelpunkt,
  Canvas-Pfeile und ausgewählter Zustand entsprechen dem Vertrag.
- Eine diagonale Richtung wurde gewählt und nach Force-Stop erneut gestartet;
  Auswahl und diagonaler Root-Gradient blieben erhalten.
