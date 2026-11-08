package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.sumo.SumoArenaData;
import de.browars.minigame.sumo.SumoGame;
import de.browars.minigame.sumo.SumoSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class SumoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var sumoNode = Commands.literal("sumo")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(SumoCommand::executeSetLossZone))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawn(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawn(ctx, 2))))
                .then(Commands.literal("team")
                        .then(Commands.literal("1")
                                .then(Commands.argument("farbe", StringArgumentType.word())
                                        .suggests(teamColorSuggestion)
                                        .executes(ctx -> executeSetTeamColor(ctx, 1))))
                        .then(Commands.literal("2")
                                .then(Commands.argument("farbe", StringArgumentType.word())
                                        .suggests(teamColorSuggestion)
                                        .executes(ctx -> executeSetTeamColor(ctx, 2)))))
                .then(Commands.literal("start")
                        .then(Commands.argument("spieler1", EntityArgument.player())
                                .executes(SumoCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(SumoCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(SumoCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(SumoCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(SumoCommand::executeStatus));

        dispatcher.register(Commands.literal("browars")
                .then(sumoNode));
    }

    private static int executeSetLossZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) des Aus-Bereichs / der Todeszone!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof SumoGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }
        ServerLevel level = player.level();
        SumoSavedData savedData = SumoSavedData.get(level);
        savedData.getData().setLossZone(p1, p2, level.dimension());
        SumoSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        player.sendSystemMessage(Component.literal("§a✔ [Sumo Ringen] Todeszone / Aus-Bereich dauerhaft gespeichert! Die bisherige Zone wurde ersetzt."), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7➤ Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d", minX, maxX, minY, maxY, minZ, maxZ)), false);
        player.sendSystemMessage(Component.literal("§6Nächster Schritt: Setze die Spawnpunkte mit §e/browars sumo spawn 1 §6und §e/browars sumo spawn 2"), false);

        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnIndex) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MinigameManager.getInstance().getActiveGame() instanceof SumoGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }
        ServerLevel level = player.level();
        SumoSavedData savedData = SumoSavedData.get(level);

        if (!savedData.getData().hasLossZone() || !level.dimension().equals(savedData.getData().getDimension())) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Setze zuerst die Todeszone in dieser Dimension."), false);
            return 0;
        }
        if (savedData.getData().isPlayerInLossZone(player)) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Dieser Spawn liegt in der Todeszone. Wähle einen sicheren Punkt im Ring."), false);
            return 0;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        if (spawnIndex == 1) {
            savedData.getData().setSpawn1(x, y, z, yaw, pitch);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a✔ [Sumo Ringen] Spawn 1 dauerhaft gespeichert bei: §eX: %.2f Y: %.2f Z: %.2f §7(Yaw: %.1f°)", x, y, z, yaw)), false);
        } else {
            savedData.getData().setSpawn2(x, y, z, yaw, pitch);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a✔ [Sumo Ringen] Spawn 2 dauerhaft gespeichert bei: §eX: %.2f Y: %.2f Z: %.2f §7(Yaw: %.1f°)", x, y, z, yaw)), false);
        }

        SumoSavedData.save(level);

        if (savedData.getData().isFullyReady()) {
            player.sendSystemMessage(Component.literal("§6★ [Sumo Ringen] Alles fertig eingerichtet und gespeichert! Starte das Spiel mit: §e/browars sumo start <Spieler1> <Spieler2>"), false);
        }

        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNum) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorStr = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = TeamColor.fromString(colorStr, null);

        if (color == null) {
            player.sendSystemMessage(Component.literal("§c[Sumo Ringen] Unbekannte Farbe! Verfügbar: brown, red, orange, yellow, lime, green, light_blue, blue, purple, magenta, pink"), false);
            return 0;
        }

        SumoSavedData savedData = SumoSavedData.get(player.level());
        if (teamNum == 1) {
            savedData.getData().setTeam1Color(color.id);
            player.sendSystemMessage(Component.literal("§a✔ [Sumo Ringen] Team 1 Farbe gesetzt auf: " + color.chatPrefix + color.displayName), false);
        } else {
            savedData.getData().setTeam2Color(color.id);
            player.sendSystemMessage(Component.literal("§a✔ [Sumo Ringen] Team 2 Farbe gesetzt auf: " + color.chatPrefix + color.displayName), false);
        }

        SumoSavedData.save(player.level());
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, null)) return 0;
        SumoSavedData savedData = SumoSavedData.get(caller.level());
        SumoArenaData arena = savedData.getData();

        if (!arena.hasLossZone()) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Todeszone ist noch nicht gesetzt! Wähle sie mit dem Auswahlstab aus und nutze /browars sumo zone."), false);
            return 0;
        }
        if (!arena.hasSpawn1()) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Spawn 1 ist noch nicht gesetzt! Nutze /browars sumo spawn 1"), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        if (arena.isSpawnInLossZone(1, arenaLevel.getMinY())) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Ein Spawn liegt in der Todeszone. Setze ihn neu oder korrigiere /browars sumo zone."), false);
            return 0;
        }
        MinigameManager.getInstance().startMinigame(() -> new SumoGame(caller.level().getServer(), arenaLevel, arena, p1, null, arena.getTeam1ColorEnum(), null));

        caller.sendSystemMessage(Component.literal("§a[Sumo Ringen] Solo-Übungsmodus gestartet für " + p1.getPlainTextName() + "!"), false);
        return 1;
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        SumoSavedData savedData = SumoSavedData.get(ctx.getSource().getPlayerOrException().level());
        SumoArenaData arena = savedData.getData();
        return executeStartInternal(ctx, arena.getTeam1ColorEnum(), arena.getTeam2ColorEnum());
    }

    private static int executeStartCustomColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String c1Str = StringArgumentType.getString(ctx, "farbe1");
        String c2Str = StringArgumentType.getString(ctx, "farbe2");

        TeamColor t1 = MinigameCommandSupport.color(c1Str);
        TeamColor t2 = MinigameCommandSupport.color(c2Str);

        return executeStartInternal(ctx, t1, t2);
    }

    private static int executeStartInternal(CommandContext<CommandSourceStack> ctx, TeamColor team1, TeamColor team2) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, null)) return 0;
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, p2)) return 0;

        if (p1.equals(p2)) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Spieler 1 und Spieler 2 müssen unterschiedlich sein!"), false);
            return 0;
        }

        ServerLevel level = caller.level();
        SumoSavedData savedData = SumoSavedData.get(level);
        SumoArenaData arena = savedData.getData();

        if (!arena.hasLossZone()) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Todeszone ist noch nicht gesetzt! Wähle sie mit dem Auswahlstab aus und nutze /browars sumo zone."), false);
            return 0;
        }

        if (!arena.hasSpawn1()) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Spawn 1 ist noch nicht gesetzt! Nutze /browars sumo spawn 1"), false);
            return 0;
        }

        if (!arena.hasSpawn2()) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Spawn 2 ist noch nicht gesetzt! Nutze /browars sumo spawn 2"), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        if (arena.isSpawnInLossZone(1, arenaLevel.getMinY())
                || (arena.hasSpawn2() && arena.isSpawnInLossZone(2, arenaLevel.getMinY()))) {
            caller.sendSystemMessage(Component.literal("§c[Sumo Ringen] Ein Spawn liegt in der Todeszone. Setze ihn neu oder korrigiere /browars sumo zone."), false);
            return 0;
        }
        MinigameManager.getInstance().startMinigame(() -> new SumoGame(caller.level().getServer(), arenaLevel, arena, p1, p2, team1, team2));

        caller.sendSystemMessage(Component.literal("§a[Sumo Ringen] Match gestartet: "
                + team1.chatPrefix + p1.getPlainTextName() + " (" + team1.displayName + ") §7vs "
                + team2.chatPrefix + p2.getPlainTextName() + " (" + team2.displayName + ")!"), false);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (!(MinigameManager.getInstance().getActiveGame() instanceof SumoGame active) || !active.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("§c[Sumo Ringen] Es läuft aktuell kein Sumo-Spiel."));
            return 0;
        }

        MinigameManager.getInstance().stopCurrentMinigame("Durch Spielleiter abgebrochen");
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Sumo Ringen] Minispiel gestoppt."), true);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SumoSavedData savedData = SumoSavedData.get(player.level());
        SumoArenaData arena = savedData.getData();

        player.sendSystemMessage(Component.literal("§d=== [Sumo Ringen Status & Gespeicherte Daten] ==="), false);
        if (arena.hasLossZone()) {
            BlockPos min = arena.getLossZoneMin();
            BlockPos max = arena.getLossZoneMax();
            int dx = max.getX() - min.getX() + 1;
            int dy = max.getY() - min.getY() + 1;
            int dz = max.getZ() - min.getZ() + 1;
            player.sendSystemMessage(Component.literal("§7Todeszone: §aGespeichert ✔ §7(" + dx + "x" + dy + "x" + dz + " Blöcke von Y=" + min.getY() + " bis Y=" + max.getY() + ", " + arena.getDimension().identifier().getPath() + ")"), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Todeszone: §cNicht gesetzt (§e/browars sumo zone§7)"), false);
        }

        if (arena.hasSpawn1()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "§7Spawn 1: §aGespeichert ✔ §7(%.1f, %.1f, %.1f | Yaw: %.1f°)", arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z(), arena.getSpawn1Yaw())), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Spawn 1: §cNicht gesetzt (§e/browars sumo spawn 1§7)"), false);
        }

        if (arena.hasSpawn2()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "§7Spawn 2: §aGespeichert ✔ §7(%.1f, %.1f, %.1f | Yaw: %.1f°)", arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z(), arena.getSpawn2Yaw())), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Spawn 2: §cNicht gesetzt (§e/browars sumo spawn 2§7)"), false);
        }

        TeamColor t1 = arena.getTeam1ColorEnum();
        TeamColor t2 = arena.getTeam2ColorEnum();
        player.sendSystemMessage(Component.literal("§7Teamfarben: Team 1: " + t1.chatPrefix + t1.displayName + " §7| Team 2: " + t2.chatPrefix + t2.displayName), false);

        boolean running = MinigameManager.getInstance().isGameRunning();
        player.sendSystemMessage(Component.literal("§7Aktives Spiel: " + (running ? "§aJa (" + MinigameManager.getInstance().getActiveGame().getDisplayName() + ")" : "§7Nein")), false);

        return 1;
    }
}
