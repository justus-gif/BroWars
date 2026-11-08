# BroWars

Fabric-Mod für Minecraft 26.3 mit Color Floor, Sumo, Anvil Rain und konfigurierbaren Teleportern.
Benötigt Java 25, Fabric Loader und Fabric API.

## Sumo einrichten

1. Mit dem Arena-Auswahlstab die beiden Ecken der Todeszone markieren: Linksklick setzt Punkt 1, Rechtsklick Punkt 2.
2. `/browars sumo zone` speichert die Auswahl und ersetzt die bisherige Zone vollständig.
3. Auf der sicheren Ringfläche `/browars sumo spawn 1` und `/browars sumo spawn 2` ausführen.
4. `/browars sumo start <Spieler1> <Spieler2>` startet das Match; mit nur einem Spieler startet der Solo-Modus.
5. `/browars sumo status` zeigt gespeicherte Zone und Spawns. `/browars sumo stop` beendet Sumo.

Die Todeszone gehört auf den Boden außerhalb beziehungsweise unterhalb des Rings.
Schon das Stehen auf den ausgewählten Blöcken zählt als Ausscheiden. Unterhalb ihrer Oberkante
zählt die Zone innerhalb ihrer horizontalen Grenzen ebenfalls, damit schnelles Fallen durch
eine dünne Zone erkannt wird. Die Ringfläche und die Spawns müssen oberhalb der Zone liegen.
Eine neue Zone in einer anderen Dimension entfernt die bisherigen Spawns; diese müssen neu gesetzt werden.
Arena und Spawns lassen sich während eines laufenden Matches nicht verändern.

## Anvil Rain einrichten

1. Eine durchgehende, ebene Bodenfläche bauen (mindestens 5×5, höchstens 48×48 Blöcke). Darüber müssen 22 Blöcke frei sein.
2. Mit dem Arena-Auswahlstab zwei gegenüberliegende **Bodenblöcke auf derselben Höhe** markieren.
3. `/browars anvilrain arena` speichert die Fläche und ersetzt die alte Auswahl.
4. Auf der Fläche `/browars anvilrain spawn 1` und `/browars anvilrain spawn 2` setzen.
5. `/browars anvilrain start <Spieler1> <Spieler2>` startet ein Duell bis fünf Punkte.

Mit nur einem Spielernamen startet eine Solo-Übung. `status`, `stop` und `team 1 <Farbe>` / `team 2 <Farbe>`
funktionieren wie bei den anderen Spielen. Beim Start können hinter beiden Spielernamen zwei Teamfarben angegeben werden.

Ambosse starten 20 Blöcke über dem Boden, gleiten zunächst langsam und beschleunigen zum Boden hin.
Innerhalb einer Runde werden Fallzeit und Spawnabstand über zwei Minuten kürzer. Die Ambosse spawnen
völlig zufällig innerhalb der Spielfläche, es gibt keine Schatten. Beim Aufprall
verschwindet der Amboss mit wenigen Rauch-/Wölkchenpartikeln und leisem Geräusch. Es entstehen weder
Explosionsschaden noch Blöcke oder Items. Ein Treffer oder das Verlassen der Spielfläche gibt dem Gegner
einen Punkt. Gleichzeitiges Ausscheiden wiederholt die Runde ohne Punkt. Zwischen Runden wird das Tempo zurückgesetzt.

## Color Floor einrichten

Mit dem Auswahlstab eine Bodenfläche auf einer Höhe markieren (maximal 48×48), danach
`/browars colorfloor arena`, `spawn 1` und `spawn 2` verwenden. Zwei Blöcke Kopffreiheit vorsehen.
Start, Stop, Status und Teamfarben funktionieren entsprechend den anderen Spielen.
Der ursprüngliche Boden wird nach Matchende oder Spielwechsel wiederhergestellt; Kisten und andere
Blöcke mit gespeicherten Daten sind als Spielfläche nicht erlaubt.

## Änderungen in 1.0.1

- Anvil Rain mit beschleunigtem, aber ausweichbarem Fall, stetig steigender Schwierigkeit, völlig zufälligen Spawnpositionen und ohne Bodenschatten.
- Sumo-Stick ohne Angriffs-Cooldown: schnelle aufeinanderfolgende Klicks treffen direkt; der feste Rückstoß beträgt 0,28.
- Gemeinsamer Ablauf für alle drei Spiele: Countdown, Punkte bis fünf, Gleichstand, Gewinnsound, Solo-Ende und Aufräumen.
- Einheitliche HUD-Anzeige mit Spielname, Teamfarben und angepasster Breite für kleine Fenster/lange Namen.
- Color Floor wählt nur vorhandene Farben und erkennt Fallen, bevor eine neue Bodenfläche Spieler retten kann.
- Inventar, Effekte, Spielmodus und Angriffsgeschwindigkeit werden bei Spielwechsel und Spielende wiederhergestellt.
- Prüfung von Arena-Größe, Spawnpunkten, Spielern und Teamfarben bei der Einrichtung.
- Temporäre Ambosse werden aufgeräumt und nicht in die Welt gespeichert.
- Teleporter-Eingaben und Boost-Stärken werden validiert; Blockeinstellungen prüfen Berechtigung und Entfernung.
- Teleportzonen räumen Hologramme in ihrer zugehörigen Dimension auf; Partikelmenge und Aktualisierungen sind begrenzt.

## Frühere Änderungen

- Gemeinsame Gewinnsoundfolge für Sumo und Color Floor; jeder Teilnehmer hört sie einmal.
- Aktivierte Trefferhilfe mit 0,55 Blöcken Ziel-Toleranz, normaler Reichweite und Wandprüfung.
- Sumo erkennt Bodenkontakt, Randkontakt, Durchfallen und das Void anhand der Welthöhe.
- Solo-Ende startet keine neue Runde mehr; Gleichstand wiederholt weiterhin die Runde.
- Spielwechsel beendet das vorherige Spiel, bevor das nächste seine Spieler vorbereitet.
- Inventar, Spielmodus, Effekte und Angriffsgeschwindigkeit werden vorübergehend gesichert und danach wiederhergestellt.
- Spielausrüstung ist markiert; normale Stöcke und Lederpanzer werden nicht als Spielausrüstung gelöscht.
- Abmelden und Server-Stopp räumen das laufende Spiel auf; fremde Spieler respawnen weiterhin am Weltspawn.
- Countdown und Sieganzeige halten die Spieler am Spawn; Rundenwechsel setzt Bewegung, Feuer und Fallstrecke zurück.
- Arena-Auswahl berücksichtigt die Dimension; Client-HUD und Auswahl werden beim Abmelden zurückgesetzt.
- Teleportzonen verwenden eine stabile Spielerliste bei Dimensionswechseln; fehlende Zieldimensionen lösen keinen Teleport in die falsche Welt aus.
- Verzögerte Teleporteffekte werden ohne Kopieren der gesamten Liste entfernt und beim Server-Stopp aufgeräumt.
- Fertige Builds werden erst nach erfolgreichen Prüfungen kopiert.

## Entwickeln und prüfen

- `./gradlew minigameRegressionTest`: Geometrie, Arena-Speicherung, Punkte, Anvil-Fallkurve, Treffer, Eingabevalidierung und Spielwechsel.
- `./gradlew runGametest`: Minecraft-Testserver mit echten Display-Entities und simulierten Spielern; prüft Aufprall, Aufräumen, alle drei Spielabläufe, Inventarwiederherstellung und Anvil Rain bis fünf Punkte.
- `./gradlew build`: kompiliert und führt die Regressionstests aus. Die fertige JAR wird nach `Fertige Builds` und `../BroWars-FertigeBuilds` kopiert.

Die Minecraft-Integrationstests stehen unter `src/gametest`, die eigenständigen Regressionstests unter
`src/regression`. Der Test-Mod wird nicht in die ausgelieferte JAR aufgenommen.
Eine alte BroWars-JAR im Minecraft-Mods-Ordner beim Aktualisieren entfernen.
Automatische Prüfungen ersetzen keinen visuellen Spieltest mit zwei Minecraft-Clients.
