# Minecraft 1.21.4+ Modding Richtlinien

## Item-Modell Definitionen (`assets/<namespace>/items/`)
- In Minecraft 1.21.4+ benötigt **jedes Item** (auch `BlockItem`s) zwingend eine Item-Definitions-Datei unter `assets/<namespace>/items/<item_id>.json`.
- Fehlt diese Datei, rendert das Item im Inventar und in der Hand als fehlende Textur (schwarz-lila).

### Syntax-Beispiel (`assets/<namespace>/items/<item_id>.json`):
```json
{
  "model": {
    "type": "minecraft:model",
    "model": "<namespace>:item/<item_id>"
  }
}
```

### Block-Item-Modelle (`assets/<namespace>/models/item/<item_id>.json`):
- Für Block-Items sollte das Modell auf das entsprechende Block-Modell verweisen, damit der Block 3D gerendert wird:
```json
{
  "parent": "<namespace>:block/<item_id>"
}
```

## Einheitliches Befehlssystem (Keine Alternativ-Befehle)
- **Niemals** doppelte Befehle oder Alternativ-Aliase für denselben Befehl registrieren (z. B. keine zusätzlichen Varianten wie `snow`, `layer`, `zone`, `kill area` neben dem eigentlichen Befehl).
- Jede Aktion hat **genau einen** klaren, einheitlichen Befehlspfad unter `/browars <game> ...`.

## Auto-Updater & GitHub Release-Workflow
- **Integrierter Auto-Updater (`de.browars.updater.ModUpdater`):**
  - Die Mod fragt beim Spielstart anonym die GitHub-API unter `api.github.com/repos/justus-gif/BroWars/releases/latest` ab.
  - Findet sie eine neuere Version, lädt sie `BroWars.jar` automatisch im Hintergrund in den `mods/`-Ordner herunter und ersetzt die alte Version (auch unter Windows über Shutdown-Hook sicher gelöst).
  - Zeigt im Chat beim Welt-Beitritt eine Benachrichtigung an, dass die neue Version beim nächsten Spielstart aktiv wird.
- **Universeller Download-Link für Freunde:**
  - `https://github.com/justus-gif/BroWars/releases/latest/download/BroWars.jar`
  - Freunde müssen diesen Link nur 1x im Browser öffnen und `BroWars.jar` in ihren `mods/`-Ordner legen.
- **Workflow für neue Updates (wenn User sagt "Lade Update hoch" / "Release"):**
  1. Versionsnummer in `gradle.properties` anpassen (`mod_version=...`).
  2. `./release.sh` im Terminal ausführen. Das Skript baut die Mod, kopiert sie zu `Fertige Builds/BroWars.jar` und lädt das Release via `gh` vollautomatisch auf GitHub hoch.
- **Lokaler Entwickler-Deploy (auf dem eigenen PC):**
  - `./gradlew quickDeploy` baut die Mod in ~1 Sekunde und kopiert sie direkt in den NoRisk-Client-Ordner (`~/.local/share/noriskclientv3/profiles/BroWars/mods/`).
  - `./gradlew quickDeploy -t` überwacht Dateiänderungen live.
- **Wichtig zur Laufzeit:**
  - Minecraft lädt Mod-Bytecode beim Booten in den JVM-Speicher. Daher muss Minecraft nach jedem Update (sowohl lokal als auch bei Freunden) einmal neu gestartet werden, damit die neue Version wirksam wird.

