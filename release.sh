#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

# Version aus gradle.properties auslesen
VERSION=$(grep "^mod_version=" gradle.properties | cut -d'=' -f2 | tr -d '[:space:]')
TAG="v${VERSION}"
JAR="Fertige Builds/browars-${VERSION}.jar"

echo "================================================="
echo "   BroWars Release Publisher"
echo "   Ziel: justus-gif/BroWars ($TAG)"
echo "================================================="

# Prüfen, ob GitHub CLI eingeloggt ist
if ! gh auth status &>/dev/null; then
    echo "FEHLER: GitHub CLI ist noch nicht eingeloggt."
    echo "Bitte führe einmalig im Terminal aus: gh auth login"
    exit 1
fi

# Prüfen, ob Repo existiert, wenn nicht erstellen
if ! gh repo view "justus-gif/BroWars" &>/dev/null; then
    echo "Erstelle GitHub-Repository justus-gif/BroWars..."
    gh repo create "justus-gif/BroWars" --public --confirm || true
fi

echo "Baue Mod Version ${VERSION}..."
./gradlew copyFinishedBuild

if [ ! -f "$JAR" ]; then
    echo "FEHLER: Datei $JAR wurde nach dem Build nicht gefunden!"
    exit 1
fi

CLEAN_JAR="Fertige Builds/BroWars.jar"
cp "$JAR" "$CLEAN_JAR"

echo "Veröffentliche Release $TAG auf GitHub (als BroWars.jar)..."
# Falls Release bereits existiert, Datei aktualisieren, sonst neu erstellen
if gh release view "$TAG" --repo "justus-gif/BroWars" &>/dev/null; then
    echo "Release $TAG existiert bereits - aktualisiere BroWars.jar..."
    gh release upload "$TAG" "$CLEAN_JAR" --repo "justus-gif/BroWars" --clobber
else
    gh release create "$TAG" "$CLEAN_JAR" \
        --repo "justus-gif/BroWars" \
        --title "BroWars $TAG" \
        --notes "Automatisches Update für BroWars ($TAG)"
fi

echo "================================================="
echo "   ERFOLG: Version $TAG ist online!"
echo "   Alle Freunde erhalten ab sofort das Update."
echo "================================================="
