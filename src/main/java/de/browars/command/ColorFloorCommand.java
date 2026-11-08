package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.colorfloor.ColorFloorArenaData;
import de.browars.minigame.colorfloor.ColorFloorGame;
import de.browars.minigame.colorfloor.ColorFloorSavedData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class ColorFloorCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var colorFloorNode = Commands.literal("colorfloor")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(ColorFloorCommand::executeSetArena))
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
                                .executes(ColorFloorCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(ColorFloorCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(ColorFloorCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(ColorFloorCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(ColorFloorCommand::executeStatus));

        // ONLY register under /browars colorfloor ... (per user instruction)
        dispatcher.register(Commands.literal("browars")
                .then(colorFloorNode));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MinigameManager.getInstance().getActiveGame() instanceof ColorFloorGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Color Floor] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Color Floor] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick)!"), false);
            return 0;
        }

        if (p1.getY() != p2.getY() || Math.abs((long) p1.getX() - p2.getX()) >= 48
                || Math.abs((long) p1.getZ() - p2.getZ()) >= 48) {
            ctx.getSource().sendFailure(Component.literal("Markiere eine ebene Bodenfläche mit maximal 48×48 Blöcken."));
            return 0;
        }
        ServerLevel level = player.level();
        ColorFloorSavedData savedData = ColorFloorSavedData.get(level);
        savedData.getData().setArenaBounds(p1, p2, level.dimension());
        ColorFloorSavedData.saveAndSync(level);

        int dx = Math.abs(p1.getX() - p2.getX()) + 1;
        int dz = Math.abs(p1.getZ() - p2.getZ()) + 1;
        int floorY = savedData.getData().getFloorY();

        player.sendSystemMessage(Component.literal("§a✔ [Color Floor] Spielfeld-Bereich dauerhaft gespeichert!"), false);
        player.sendSystemMessage(Component.literal("§7➤ Spielfläche: §e" + dx + "x" + dz + " Blöcke §7auf Höhe §eY=" + floorY), false);
        player.sendSystemMessage(Component.literal("§6Nächster Schritt: Setze die Spawnpunkte mit §e/browars colorfloor spawn 1 §6und §e/browars colorfloor spawn 2"), false);

        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnIndex) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MinigameManager.getInstance().getActiveGame() instanceof ColorFloorGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Color Floor] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }
        ServerLevel level = player.level();
        ColorFloorSavedData savedData = ColorFloorSavedData.get(level);

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        var arena = savedData.getData();
        if (!arena.hasArena() || !arena.getDimension().equals(level.dimension()) || !arena.containsFloorSpawn(x, y, z)) {
            ctx.getSource().sendFailure(Component.literal("Stelle dich für den Spawn auf die gespeicherte Spielfläche."));
            return 0;
        }

        if (spawnIndex == 1) {
            savedData.getData().setSpawn1(x, y, z, yaw, pitch);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a✔ [Color Floor] Spawn 1 dauerhaft gespeichert bei: §eX: %.2f Y: %.2f Z: %.2f §7(Yaw: %.1f°)", x, y, z, yaw)), false);
        } else {
            savedData.getData().setSpawn2(x, y, z, yaw, pitch);
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§a✔ [Color Floor] Spawn 2 dauerhaft gespeichert bei: §eX: %.2f Y: %.2f Z: %.2f §7(Yaw: %.1f°)", x, y, z, yaw)), false);
        }

        ColorFloorSavedData.saveAndSync(level);

        if (savedData.getData().isFullyReady()) {
            player.sendSystemMessage(Component.literal("§6★ [Color Floor] Alles fertig eingerichtet und gespeichert! Starte das Spiel mit: §e/browars colorfloor start <Spieler1> <Spieler2>"), false);
        }

        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNum) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorStr = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = TeamColor.fromString(colorStr, null);

        if (color == null) {
            player.sendSystemMessage(Component.literal("§c[Color Floor] Unbekannte Farbe! Verfügbar: brown, red, orange, yellow, lime, green, light_blue, blue, purple, magenta, pink"), false);
            return 0;
        }

        ColorFloorSavedData savedData = ColorFloorSavedData.get(player.level());
        if (teamNum == 1) {
            savedData.getData().setTeam1Color(color.id);
        } else {
            savedData.getData().setTeam2Color(color.id);
        }
        ColorFloorSavedData.saveAndSync(player.level());

        player.sendSystemMessage(Component.literal("§a✔ [Color Floor] Team " + teamNum + " Farbe dauerhaft gespeichert als: " + color.chatPrefix + color.displayName + " (" + color.id + ")"), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, null)) return 0;
        ColorFloorSavedData savedData = ColorFloorSavedData.get(caller.level());
        TeamColor t1 = savedData.getData().getTeam1ColorEnum();

        ServerLevel level = caller.level();
        ColorFloorArenaData arena = savedData.getData();

        if (!arena.hasArena()) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Das Spielfeld wurde noch nicht gesetzt! Wähle es mit dem Arena-Auswahlstab aus und nutze /browars colorfloor arena."), false);
            return 0;
        }

        if (!arena.hasSpawn1()) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Spawn 1 ist noch nicht gesetzt! Nutze /browars colorfloor spawn 1"), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        String error = arena.validationError(arenaLevel);
        if (error != null || !arena.containsFloorSpawn(arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z())
                || (arena.hasSpawn2() && !arena.containsFloorSpawn(arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z()))) {
            ctx.getSource().sendFailure(Component.literal(error != null ? error : "Setze die Spawnpunkte auf der aktuellen Spielfläche neu."));
            return 0;
        }
        MinigameManager.getInstance().startMinigame(() -> new ColorFloorGame(caller.level().getServer(), arenaLevel, arena, p1, null, t1, TeamColor.BLUE));

        caller.sendSystemMessage(Component.literal("§a[Color Floor] Solo-Übungsrunde gestartet für: "
                + t1.chatPrefix + p1.getPlainTextName() + " (" + t1.displayName + ")! Überlebe so viele Runden wie möglich!"), false);
        return 1;
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        ColorFloorSavedData savedData = ColorFloorSavedData.get(caller.level());
        TeamColor t1 = savedData.getData().getTeam1ColorEnum();
        TeamColor t2 = savedData.getData().getTeam2ColorEnum();
        return executeStartInternal(ctx, t1, t2);
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
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Spieler 1 und Spieler 2 müssen unterschiedlich sein!"), false);
            return 0;
        }

        ServerLevel level = caller.level();
        ColorFloorSavedData savedData = ColorFloorSavedData.get(level);
        ColorFloorArenaData arena = savedData.getData();

        if (!arena.hasArena()) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Das Spielfeld wurde noch nicht gesetzt! Wähle es mit dem Arena-Auswahlstab aus und nutze /browars colorfloor arena."), false);
            return 0;
        }

        if (!arena.hasSpawn1()) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Spawn 1 ist noch nicht gesetzt! Nutze /browars colorfloor spawn 1"), false);
            return 0;
        }

        if (!arena.hasSpawn2()) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Spawn 2 ist noch nicht gesetzt! Nutze /browars colorfloor spawn 2"), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Color Floor] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        String error = arena.validationError(arenaLevel);
        if (error != null || !arena.containsFloorSpawn(arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z())
                || (arena.hasSpawn2() && !arena.containsFloorSpawn(arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z()))) {
            ctx.getSource().sendFailure(Component.literal(error != null ? error : "Setze die Spawnpunkte auf der aktuellen Spielfläche neu."));
            return 0;
        }
        MinigameManager.getInstance().startMinigame(() -> new ColorFloorGame(caller.level().getServer(), arenaLevel, arena, p1, p2, team1, team2));

        caller.sendSystemMessage(Component.literal("§a[Color Floor] Match gestartet: "
                + team1.chatPrefix + p1.getPlainTextName() + " (" + team1.displayName + ") §7vs "
                + team2.chatPrefix + p2.getPlainTextName() + " (" + team2.displayName + ")!"), false);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (!(MinigameManager.getInstance().getActiveGame() instanceof ColorFloorGame active) || !active.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("§c[Color Floor] Es läuft aktuell kein Color Floor-Spiel."));
            return 0;
        }

        MinigameManager.getInstance().stopCurrentMinigame("Durch Spielleiter abgebrochen");
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Color Floor] Minispiel gestoppt."), true);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ColorFloorSavedData savedData = ColorFloorSavedData.get(player.level());
        ColorFloorArenaData arena = savedData.getData();

        player.sendSystemMessage(Component.literal("§d=== [Color Floor Status & Gespeicherte Daten] ==="), false);
        if (arena.hasArena()) {
            int dx = arena.getMaxPos().getX() - arena.getMinPos().getX() + 1;
            int dz = arena.getMaxPos().getZ() - arena.getMinPos().getZ() + 1;
            player.sendSystemMessage(Component.literal("§7Spielfeld: §aGespeichert ✔ §7(" + dx + "x" + dz + " Blöcke auf Y=" + arena.getFloorY() + ", " + arena.getDimension().identifier().getPath() + ")"), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Spielfeld: §cNicht gesetzt (§e/browars colorfloor arena§7)"), false);
        }

        if (arena.hasSpawn1()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "§7Spawn 1: §aGespeichert ✔ §7(%.1f, %.1f, %.1f | Yaw: %.1f°)", arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z(), arena.getSpawn1Yaw())), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Spawn 1: §cNicht gesetzt (§e/browars colorfloor spawn 1§7)"), false);
        }

        if (arena.hasSpawn2()) {
            player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "§7Spawn 2: §aGespeichert ✔ §7(%.1f, %.1f, %.1f | Yaw: %.1f°)", arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z(), arena.getSpawn2Yaw())), false);
        } else {
            player.sendSystemMessage(Component.literal("§7Spawn 2: §cNicht gesetzt (§e/browars colorfloor spawn 2§7)"), false);
        }

        TeamColor t1 = arena.getTeam1ColorEnum();
        TeamColor t2 = arena.getTeam2ColorEnum();
        player.sendSystemMessage(Component.literal("§7Teamfarben: Team 1: " + t1.chatPrefix + t1.displayName + " §7| Team 2: " + t2.chatPrefix + t2.displayName), false);

        boolean running = MinigameManager.getInstance().isGameRunning();
        player.sendSystemMessage(Component.literal("§7Aktives Spiel: " + (running ? "§aJa (" + MinigameManager.getInstance().getActiveGame().getDisplayName() + ")" : "§7Nein")), false);

        return 1;
    }

}
