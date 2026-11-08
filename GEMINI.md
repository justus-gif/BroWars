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

