package de.browars.gameshow;

import de.browars.minigame.AbstractDuelGame;
import de.browars.minigame.TeamColor;
import de.browars.minigame.anvilrain.AnvilRainArenaData;
import de.browars.minigame.anvilrain.AnvilRainGame;
import de.browars.minigame.anvilrain.AnvilRainSavedData;
import de.browars.minigame.colorfloor.ColorFloorArenaData;
import de.browars.minigame.colorfloor.ColorFloorGame;
import de.browars.minigame.colorfloor.ColorFloorSavedData;
import de.browars.minigame.crosswar.CrossWarArenaData;
import de.browars.minigame.crosswar.CrossWarGame;
import de.browars.minigame.crosswar.CrossWarSavedData;
import de.browars.minigame.lavaescape.LavaEscapeArenaData;
import de.browars.minigame.lavaescape.LavaEscapeGame;
import de.browars.minigame.lavaescape.LavaEscapeSavedData;
import de.browars.minigame.paintwar.PaintWarArenaData;
import de.browars.minigame.paintwar.PaintWarGame;
import de.browars.minigame.paintwar.PaintWarSavedData;
import de.browars.minigame.spleef.SpleefArenaData;
import de.browars.minigame.spleef.SpleefGame;
import de.browars.minigame.spleef.SpleefSavedData;
import de.browars.minigame.sumo.SumoArenaData;
import de.browars.minigame.sumo.SumoGame;
import de.browars.minigame.sumo.SumoSavedData;
import de.browars.minigame.targetrush.TargetRushArenaData;
import de.browars.minigame.targetrush.TargetRushGame;
import de.browars.minigame.targetrush.TargetRushSavedData;
import de.browars.minigame.tnttag.TntTagArenaData;
import de.browars.minigame.tnttag.TntTagGame;
import de.browars.minigame.tnttag.TntTagSavedData;
import de.browars.minigame.vaultwar.VaultWarArenaData;
import de.browars.minigame.vaultwar.VaultWarGame;
import de.browars.minigame.vaultwar.VaultWarSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public enum GameshowGameType {
    COLOR_FLOOR(
            "colorfloor",
            "Color Floor",
            "Stehe rechtzeitig auf der gesuchten Farbe, bevor der Boden verschwindet!",
            0xFF38BDF8
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            ColorFloorArenaData arena = ColorFloorSavedData.get(overworld).getData();
            if (!arena.hasArena()) return "Spielfeld nicht gesetzt";
            if (!arena.hasSpawn1() || !arena.hasSpawn2()) return "Spawns 1/2 nicht gesetzt";
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            String err = arena.validationError(level);
            if (err != null) return err;
            if (!arena.containsFloorSpawn(arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z())
                    || !arena.containsFloorSpawn(arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z())) {
                return "Spawnpunkte außerhalb der Spielfläche";
            }
            return null;
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            ColorFloorArenaData arena = ColorFloorSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new ColorFloorGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    SUMO(
            "sumo",
            "Sumo Ringen",
            "Stoße deinen Gegner mit dem Knockback-Stick von der Plattform!",
            0xFFEF4444
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            SumoArenaData arena = SumoSavedData.get(overworld).getData();
            if (!arena.hasLossZone()) return "Todeszone nicht gesetzt";
            if (!arena.hasSpawn1() || !arena.hasSpawn2()) return "Spawns 1/2 nicht gesetzt";
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            if (arena.isSpawnInLossZone(1, level.getMinY()) || arena.isSpawnInLossZone(2, level.getMinY())) {
                return "Spawnpunkt liegt in der Todeszone";
            }
            return null;
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            SumoArenaData arena = SumoSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new SumoGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    ANVIL_RAIN(
            "anvilrain",
            "Anvil Rain",
            "Weiche dem immer dichter werdenden Amboss-Hagel aus!",
            0xFF94A3B8
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            AnvilRainArenaData arena = AnvilRainSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            String err = arena.validationError(level);
            if (err != null) return err;
            if (!arena.containsSpawn(1) || !arena.containsSpawn(2)) return "Spawns 1/2 nicht gültig";
            return null;
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            AnvilRainArenaData arena = AnvilRainSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new AnvilRainGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    TNT_TAG(
            "tnttag",
            "TNT Tag",
            "Gib das tickende TNT rechtzeitig an deinen Gegner weiter!",
            0xFFF97316
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            TntTagArenaData arena = TntTagSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return arena.validationError(level);
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            TntTagArenaData arena = TntTagSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new TntTagGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    CROSS_WAR(
            "crosswar",
            "Cross War",
            "Scharfschützen-Duell mit der Schnellfeuer-Armbrust über den Abgrund!",
            0xFF10B981
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            CrossWarArenaData arena = CrossWarSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return arena.validationError(level);
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            CrossWarArenaData arena = CrossWarSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new CrossWarGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    PAINT_WAR(
            "paintwar",
            "Paint War",
            "Färbe so viele Blöcke der Arena wie möglich in deiner Teamfarbe!",
            0xFFA855F7
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            PaintWarArenaData arena = PaintWarSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return arena.validationError(level);
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            PaintWarArenaData arena = PaintWarSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new PaintWarGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    SPLEEF(
            "spleef",
            "Spleef",
            "Grabe dem Gegner mit der Schaufel den Schneeboden unter den Füßen weg!",
            0xFF06B6D4
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            SpleefArenaData arena = SpleefSavedData.get(overworld).getData();
            if (!arena.hasSpawn1() || !arena.hasSpawn2()) return "Spawns 1/2 nicht gesetzt";
            if (!arena.hasSnowArea(1)) return "Snow Area 1 nicht gesetzt";
            if (!arena.hasKillArea()) return "Kill Area nicht gesetzt";
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            if (arena.isSpawnInKillArea(1, level.getMinY()) || arena.isSpawnInKillArea(2, level.getMinY())) {
                return "Spawn liegt in der Kill Area";
            }
            return null;
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            SpleefArenaData arena = SpleefSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new SpleefGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    TARGET_RUSH(
            "targetrush",
            "Target Rush",
            "Triff die auftauchenden Zielblöcke mit Pfeil und Bogen für Punkte!",
            0xFFF59E0B
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            TargetRushArenaData arena = TargetRushSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return arena.validationError(level, false);
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            TargetRushArenaData arena = TargetRushSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new TargetRushGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    VAULT_WAR(
            "vaultwar",
            "Vault War",
            "Plündere die Tresore, rüste dich aus und entscheide den Kampf für dich!",
            0xFFEAB308
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            VaultWarArenaData arena = VaultWarSavedData.get(overworld).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return arena.validationError(level, false);
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            VaultWarArenaData arena = VaultWarSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new VaultWarGame(server, level, arena, p1, p2, t1, t2);
        }
    },

    LAVA_ESCAPE(
            "lavaescape",
            "Lava Escape",
            "Sammle Blöcke und klettere zum Ziel, bevor die steigende Lava dich erreicht!",
            0xFFDC2626
    ) {
        @Override
        public String validationError(MinecraftServer server) {
            ServerLevel overworld = server.overworld();
            LavaEscapeArenaData arena = LavaEscapeSavedData.get(overworld).getData();
            if (!arena.hasArena()) return "Arena nicht gesetzt";
            if (!arena.isReadyForDuel()) return "Spawns 1/2 nicht gesetzt";
            ServerLevel level = server.getLevel(arena.getDimension());
            if (level == null) return "Dimension nicht gefunden";
            return null;
        }

        @Override
        public AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2) {
            LavaEscapeArenaData arena = LavaEscapeSavedData.get(server.overworld()).getData();
            ServerLevel level = server.getLevel(arena.getDimension());
            return new LavaEscapeGame(server, level, arena, p1, p2, t1, t2);
        }
    };

    public final String id;
    public final String displayName;
    public final String tagline;
    public final int accentColorHex;

    GameshowGameType(String id, String displayName, String tagline, int accentColorHex) {
        this.id = id;
        this.displayName = displayName;
        this.tagline = tagline;
        this.accentColorHex = accentColorHex;
    }

    public boolean isReady(MinecraftServer server) {
        return validationError(server) == null;
    }

    public abstract String validationError(MinecraftServer server);

    public abstract AbstractDuelGame<?> createGame(MinecraftServer server, ServerPlayer p1, ServerPlayer p2, TeamColor t1, TeamColor t2);

    public static GameshowGameType fromId(String id) {
        if (id == null) return null;
        for (GameshowGameType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
