package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.spleef.SpleefArenaData;
import de.browars.minigame.spleef.SpleefGame;
import de.browars.minigame.spleef.SpleefSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class SpleefCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var spleefNode = Commands.literal("spleef")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawn(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawn(ctx, 2))))
                .then(Commands.literal("snowarea")
                        .then(Commands.literal("1").executes(ctx -> executeSetSnowArea(ctx, 1)))
                        .then(Commands.literal("2").executes(ctx -> executeSetSnowArea(ctx, 2)))
                        .then(Commands.literal("3").executes(ctx -> executeSetSnowArea(ctx, 3)))
                        .then(Commands.literal("4").executes(ctx -> executeSetSnowArea(ctx, 4)))
                        .then(Commands.literal("5").executes(ctx -> executeSetSnowArea(ctx, 5)))
                        .then(Commands.literal("6").executes(ctx -> executeSetSnowArea(ctx, 6)))
                        .then(Commands.literal("7").executes(ctx -> executeSetSnowArea(ctx, 7))))
                .then(Commands.literal("killarea")
                        .executes(SpleefCommand::executeSetKillArea))
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
                                .executes(SpleefCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(SpleefCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(SpleefCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(SpleefCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(SpleefCommand::executeStatus));

        dispatcher.register(Commands.literal("browars").then(spleefNode));
    }

    private static boolean checkNotRunning(ServerPlayer player) {
        if (MinigameManager.getInstance().getActiveGame() instanceof SpleefGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Spleef] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return false;
        }
        return true;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnIndex) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!checkNotRunning(player)) return 0;

        ServerLevel level = player.level();
        SpleefSavedData savedData = SpleefSavedData.get(level);
        SpleefArenaData arena = savedData.getData();

        if (arena.hasKillArea() && level.dimension().equals(arena.getDimension()) && arena.isPlayerInKillArea(player)) {
            player.sendSystemMessage(Component.literal("§c[Spleef] Dieser Spawn liegt in der Kill Area. Wähle einen sicheren Punkt auf dem obersten Schneelayer."), false);
            return 0;
        }

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        if (spawnIndex == 1) {
            arena.setSpawn1(x, y, z, yaw, pitch, level.dimension());
        } else {
            arena.setSpawn2(x, y, z, yaw, pitch, level.dimension());
        }

        SpleefSavedData.save(level);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Spleef] Spawn %d dauerhaft gespeichert bei: §eX: %.2f Y: %.2f Z: %.2f §7(Yaw: %.1f°)",
                spawnIndex, x, y, z, yaw)), false);

        notifyIfReady(player, arena);
        return 1;
    }

    private static int executeSetSnowArea(CommandContext<CommandSourceStack> ctx, int layerIndex) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!checkNotRunning(player)) return 0;

        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Spleef] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) für Snow Area " + layerIndex + "!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        SpleefSavedData savedData = SpleefSavedData.get(level);
        SpleefArenaData arena = savedData.getData();
        arena.setSnowArea(layerIndex, p1, p2, level.dimension());
        SpleefSavedData.save(level);

        SpleefArenaData.ArenaBox box = arena.getSnowArea(layerIndex);
        int filled = SpleefGame.fillSnowArea(level, box);

        player.sendSystemMessage(Component.literal("§a✔ [Spleef] Snow Area " + layerIndex + " dauerhaft gespeichert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7➤ Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d §7(%d Schneeblöcke aktiv)",
                box.min().getX(), box.max().getX(), box.min().getY(), box.max().getY(), box.min().getZ(), box.max().getZ(), filled)), false);

        notifyIfReady(player, arena);
        return 1;
    }

    private static int executeSetKillArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!checkNotRunning(player)) return 0;

        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Spleef] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Kill Area!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        SpleefSavedData savedData = SpleefSavedData.get(level);
        SpleefArenaData arena = savedData.getData();
        arena.setKillArea(p1, p2, level.dimension());
        SpleefSavedData.save(level);

        SpleefArenaData.ArenaBox box = arena.getKillArea();
        player.sendSystemMessage(Component.literal("§a✔ [Spleef] Kill Area dauerhaft gespeichert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7➤ Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d",
                box.min().getX(), box.max().getX(), box.min().getY(), box.max().getY(), box.min().getZ(), box.max().getZ())), false);

        notifyIfReady(player, arena);
        return 1;
    }

    private static void notifyIfReady(ServerPlayer player, SpleefArenaData arena) {
        if (arena.isFullyReady()) {
            player.sendSystemMessage(Component.literal("§6★ [Spleef] Alles fertig eingerichtet (2 Spawns, " + arena.getSnowAreas().size() + " Snow Areas, Kill Area)! Starte das Spiel mit: §e/browars spleef start <Spieler1> <Spieler2>"), false);
        }
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNum) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorStr = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = TeamColor.fromString(colorStr, null);

        if (color == null) {
            player.sendSystemMessage(Component.literal("§c[Spleef] Unbekannte Farbe! Verfügbar: brown, red, orange, yellow, lime, green, light_blue, blue, purple, magenta, pink"), false);
            return 0;
        }

        SpleefSavedData savedData = SpleefSavedData.get(player.level());
        if (teamNum == 1) {
            savedData.getData().setTeam1Color(color.id);
        } else {
            savedData.getData().setTeam2Color(color.id);
        }
        SpleefSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a✔ [Spleef] Team " + teamNum + " Farbe gesetzt auf: " + color.chatPrefix + color.displayName), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        return executeStartInternal(ctx, p1, null, null, null);
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        return executeStartInternal(ctx, p1, p2, null, null);
    }

    private static int executeStartCustomColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        TeamColor t1 = MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe1"));
        TeamColor t2 = MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe2"));
        return executeStartInternal(ctx, p1, p2, t1, t2);
    }

    private static int executeStartInternal(CommandContext<CommandSourceStack> ctx, ServerPlayer p1, ServerPlayer p2,
                                            TeamColor customTeam1, TeamColor customTeam2) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, p2)) return 0;

        SpleefArenaData arena = SpleefSavedData.get(caller.level()).getData();

        if (!arena.hasSpawn1()) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Spawn 1 ist noch nicht gesetzt! Nutze /browars spleef spawn 1"), false);
            return 0;
        }
        if (p2 != null && !arena.hasSpawn2()) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Spawn 2 ist noch nicht gesetzt! Nutze /browars spleef spawn 2"), false);
            return 0;
        }
        if (!arena.hasSnowArea(1)) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Mindestens Snow Area 1 muss gesetzt sein! Markiere sie mit dem Stab und nutze /browars spleef snowarea 1"), false);
            return 0;
        }
        if (!arena.hasKillArea()) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Kill Area ist noch nicht gesetzt! Markiere sie mit dem Stab und nutze /browars spleef killarea"), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        if (arena.isSpawnInKillArea(1, arenaLevel.getMinY()) || (p2 != null && arena.isSpawnInKillArea(2, arenaLevel.getMinY()))) {
            caller.sendSystemMessage(Component.literal("§c[Spleef] Ein Spawn liegt in der Kill Area! Setze die Spawns auf dem obersten Schneelayer neu."), false);
            return 0;
        }

        TeamColor team1 = customTeam1 != null ? customTeam1 : arena.getTeam1ColorEnum();
        TeamColor team2 = customTeam2 != null ? customTeam2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() -> new SpleefGame(caller.level().getServer(), arenaLevel, arena, p1, p2, team1, team2));

        if (p2 == null) {
            caller.sendSystemMessage(Component.literal("§a[Spleef] Solo-Übungsmodus gestartet für " + p1.getPlainTextName() + "!"), false);
        } else {
            caller.sendSystemMessage(Component.literal("§a[Spleef] Duell gestartet (Best-of-5 / First-to-3): "
                    + team1.chatPrefix + p1.getPlainTextName() + " §avs. "
                    + team2.chatPrefix + p2.getPlainTextName() + "§a!"), false);
        }
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (MinigameManager.getInstance().getActiveGame() instanceof SpleefGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl beendet");
            ctx.getSource().sendSuccess(() -> Component.literal("§6[Spleef] Match abgebrochen und Schneelayer wiederhergestellt."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Spleef] Es läuft gerade kein Spleef Match."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SpleefArenaData data = SpleefSavedData.get(player.level()).getData();

        player.sendSystemMessage(Component.literal("§6=== [Spleef Arena Status] ==="), false);
        player.sendSystemMessage(Component.literal("§7Dimension: §e" + data.getDimension().identifier()), false);
        player.sendSystemMessage(Component.literal("§7Spawn 1: " + (data.hasSpawn1()
                ? String.format(Locale.ROOT, "§a%.1f, %.1f, %.1f", data.getSpawn1X(), data.getSpawn1Y(), data.getSpawn1Z())
                : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Spawn 2: " + (data.hasSpawn2()
                ? String.format(Locale.ROOT, "§a%.1f, %.1f, %.1f", data.getSpawn2X(), data.getSpawn2Y(), data.getSpawn2Z())
                : "§cnicht gesetzt")), false);
        for (int i = 1; i <= 7; i++) {
            SpleefArenaData.ArenaBox box = data.getSnowArea(i);
            if (box != null || i <= 3) {
                player.sendSystemMessage(Component.literal("§7Snow Area " + i + ": " + (box != null
                        ? "§a" + box.min().toShortString() + " bis " + box.max().toShortString()
                        : "§cnicht gesetzt")), false);
            }
        }
        SpleefArenaData.ArenaBox kill = data.getKillArea();
        player.sendSystemMessage(Component.literal("§7Kill Area: " + (kill != null
                ? "§a" + kill.min().toShortString() + " bis " + kill.max().toShortString()
                : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Team 1 Farbe: " + data.getTeam1ColorEnum().chatPrefix + data.getTeam1ColorEnum().displayName), false);
        player.sendSystemMessage(Component.literal("§7Team 2 Farbe: " + data.getTeam2ColorEnum().chatPrefix + data.getTeam2ColorEnum().displayName), false);
        return 1;
    }
}
