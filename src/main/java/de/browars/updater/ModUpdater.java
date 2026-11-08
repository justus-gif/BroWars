package de.browars.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.browars.BroWarsMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class ModUpdater {
    public static final String GITHUB_REPO = "justus-gif/BroWars";
    private static volatile String updateNotification = null;
    private static boolean updateChecked = false;

    public static void init() {
        if (updateChecked) return;
        updateChecked = true;

        Thread updaterThread = new Thread(ModUpdater::runCheck, "BroWars-AutoUpdater");
        updaterThread.setDaemon(true);
        updaterThread.start();
    }

    private static void runCheck() {
        try {
            String currentVersion = FabricLoader.getInstance()
                    .getModContainer(BroWarsMod.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("1.0.0");

            BroWarsMod.LOGGER.info("[BroWars Updater] Pruefe auf Updates (Aktuelle Version: v{})...", currentVersion);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest"))
                    .header("User-Agent", "BroWars-Mod-Updater")
                    .header("Accept", "application/vnd.github.v3+json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                BroWarsMod.LOGGER.info("[BroWars Updater] Noch keine Releases auf GitHub ({}) vorhanden.", GITHUB_REPO);
                return;
            }

            if (response.statusCode() != 200) {
                BroWarsMod.LOGGER.warn("[BroWars Updater] GitHub API antwortete mit Status: {}", response.statusCode());
                return;
            }

            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!release.has("tag_name")) return;

            String tagName = release.get("tag_name").getAsString().trim();
            String remoteVersion = tagName.startsWith("v") || tagName.startsWith("V") ? tagName.substring(1) : tagName;

            if (!isNewer(remoteVersion, currentVersion)) {
                BroWarsMod.LOGGER.info("[BroWars Updater] BroWars ist auf dem neuesten Stand (v{}).", currentVersion);
                return;
            }

            BroWarsMod.LOGGER.info("[BroWars Updater] Neue Version v{} gefunden! Starte automatischen Download...", remoteVersion);

            if (!release.has("assets") || !release.get("assets").isJsonArray()) {
                BroWarsMod.LOGGER.warn("[BroWars Updater] Release hat keine Assets.");
                return;
            }

            JsonArray assets = release.getAsJsonArray("assets");
            String downloadUrl = null;
            String targetFileName = null;

            for (JsonElement elem : assets) {
                if (!elem.isJsonObject()) continue;
                JsonObject asset = elem.getAsJsonObject();
                String name = asset.has("name") ? asset.get("name").getAsString() : "";
                if (name.endsWith(".jar") && !name.contains("-dev") && !name.contains("-sources") && !name.contains("-api")) {
                    downloadUrl = asset.get("browser_download_url").getAsString();
                    targetFileName = name;
                    break;
                }
            }

            if (downloadUrl == null) {
                BroWarsMod.LOGGER.warn("[BroWars Updater] Keine gueltige .jar in den Release-Assets gefunden.");
                return;
            }

            // Lokalisieren der aktuellen JAR-Datei
            Path currentJarPath = null;
            try {
                var codeSource = BroWarsMod.class.getProtectionDomain().getCodeSource();
                if (codeSource != null && codeSource.getLocation() != null) {
                    currentJarPath = Path.of(codeSource.getLocation().toURI());
                }
            } catch (Exception e) {
                BroWarsMod.LOGGER.warn("[BroWars Updater] Konnte Pfad der aktuellen JAR nicht ermitteln: {}", e.getMessage());
            }

            if (currentJarPath == null || !Files.isRegularFile(currentJarPath) || !currentJarPath.toString().endsWith(".jar")) {
                BroWarsMod.LOGGER.info("[BroWars Updater] Entwicklungsumgebung aktiv (keine JAR). Download wird uebersprungen.");
                return;
            }

            Path modsDir = currentJarPath.getParent();
            Path targetFile = modsDir.resolve(targetFileName);
            Path tempFile = modsDir.resolve(targetFileName + ".download.tmp");

            // Datei herunterladen
            HttpRequest dlRequest = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("User-Agent", "BroWars-Mod-Updater")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<InputStream> dlResponse = client.send(dlRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (dlResponse.statusCode() != 200) {
                BroWarsMod.LOGGER.warn("[BroWars Updater] Download fehlgeschlagen (HTTP {})", dlResponse.statusCode());
                return;
            }

            try (InputStream in = dlResponse.body()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            final Path oldJar = currentJarPath;
            final Path finalTarget = targetFile;

            if (isWindows) {
                Path updateFile = modsDir.resolve("BroWars.jar.update");
                Files.move(tempFile, updateFile, StandardCopyOption.REPLACE_EXISTING);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        new ProcessBuilder("cmd", "/c",
                                "timeout /t 2 /nobreak >nul & del /f /q \"" + oldJar.toAbsolutePath() + "\" & move /y \"" + updateFile.toAbsolutePath() + "\" \"" + finalTarget.toAbsolutePath() + "\"")
                                .start();
                    } catch (Exception ignored) {}
                }));
            } else {
                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                if (!oldJar.equals(targetFile)) {
                    Files.deleteIfExists(oldJar);
                }
            }

            updateNotification = "§a[BroWars] Update auf v" + remoteVersion + " wurde automatisch heruntergeladen! Beim naechsten Spielstart ist es aktiv.";
            BroWarsMod.LOGGER.info("[BroWars Updater] Update v{} erfolgreich heruntergeladen!", remoteVersion);

        } catch (Exception e) {
            BroWarsMod.LOGGER.warn("[BroWars Updater] Fehler beim Pruefen auf Updates: {}", e.getMessage());
        }
    }

    public static boolean isNewer(String remote, String current) {
        try {
            String[] rParts = remote.split("[.-]");
            String[] cParts = current.split("[.-]");
            int len = Math.max(rParts.length, cParts.length);
            for (int i = 0; i < len; i++) {
                int rVal = i < rParts.length ? parseSafeInt(rParts[i]) : 0;
                int cVal = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
                if (rVal > cVal) return true;
                if (rVal < cVal) return false;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getUpdateNotification() {
        return updateNotification;
    }

    public static void clearNotification() {
        updateNotification = null;
    }

    public static void onServerPlayerJoin(ServerPlayer player) {
        if (updateNotification != null) {
            player.sendSystemMessage(Component.literal(updateNotification));
        }
    }
}
