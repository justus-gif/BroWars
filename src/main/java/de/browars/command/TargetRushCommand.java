package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.targetrush.TargetRushArenaData;
import de.browars.minigame.targetrush.TargetRushGame;
import de.browars.minigame.targetrush.TargetRushSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class TargetRushCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var targetRushNode = Commands.literal("targetrush")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(TargetRushCommand::executeSetTargetArea))
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
                                .executes(TargetRushCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(TargetRushCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(TargetRushCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(TargetRushCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(TargetRushCommand::executeStatus));

        dispatcher.register(Commands.literal("browars")
                .then(targetRushNode));
    }

    private static int executeSetTargetArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Target Rush] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) des 3D-Zielbereichs!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof TargetRushGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Target Rush] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        ServerLevel level = player.level();
        TargetRushSavedData savedData = TargetRushSavedData.get(level);
        savedData.getData().setTargetBounds(p1, p2, level.dimension());
        TargetRushSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;

        player.sendSystemMessage(Component.literal("§a✔ [Target Rush] 3D-Zielbereich erfolgreich gespeichert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7➤ Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d §7(%d×%d×%d Blöcke)", minX, maxX, minY, maxY, minZ, maxZ, w, h, d)), false);
        player.sendSystemMessage(Component.literal("§6Nächster Schritt: Setze die Spieler-Spawns mit §e/browars targetrush spawn 1 §6und §e/browars targetrush spawn 2"), false);

        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnIndex) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MinigameManager.getInstance().getActiveGame() instanceof TargetRushGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Target Rush] Stoppe das laufende Match vor Änderungen an den Spawns."), false);
            return 0;
        }
        ServerLevel level = player.level();
        TargetRushSavedData savedData = TargetRushSavedData.get(level);

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        savedData.getData().setSpawn(spawnIndex, x, y, z, yaw, pitch);
        TargetRushSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Target Rush] Spawn %d gesetzt bei: §e%.1f, %.1f, %.1f (Yaw: %.1f, Pitch: %.1f)",
                spawnIndex, x, y, z, yaw, pitch)), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorName = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = MinigameCommandSupport.color(colorName);
        if (color == null) {
            player.sendSystemMessage(Component.literal("§c[Target Rush] Ungültige Farbe: " + colorName), false);
            return 0;
        }

        ServerLevel level = player.level();
        TargetRushSavedData savedData = TargetRushSavedData.get(level);
        if (teamNumber == 1) {
            savedData.getData().setTeam1Color(color.id);
        } else {
            savedData.getData().setTeam2Color(color.id);
        }
        TargetRushSavedData.save(level);

        player.sendSystemMessage(Component.literal("§a✔ [Target Rush] Team " + teamNumber + " Farbe gesetzt auf: " + color.chatPrefix + color.displayName), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer caller = ctx.getSource().getPlayerOrException();
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, null)) return 0;

        ServerLevel level = caller.level();
        TargetRushSavedData savedData = TargetRushSavedData.get(level);
        TargetRushArenaData arena = savedData.getData();

        String err = arena.validationError(level, true);
        if (err != null) {
            caller.sendSystemMessage(Component.literal("§c[Target Rush] " + err), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Target Rush] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        MinigameManager.getInstance().startMinigame(() -> new TargetRushGame(caller.level().getServer(), arenaLevel, arena, p1, null, arena.getTeam1ColorEnum(), null));
        caller.sendSystemMessage(Component.literal("§a[Target Rush] Solo-Übungsmodus gestartet für " + p1.getPlainTextName() + "!"), false);
        return 1;
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        TargetRushSavedData savedData = TargetRushSavedData.get(ctx.getSource().getPlayerOrException().level());
        TargetRushArenaData arena = savedData.getData();
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
            caller.sendSystemMessage(Component.literal("§c[Target Rush] Spieler 1 und Spieler 2 müssen unterschiedlich sein!"), false);
            return 0;
        }

        ServerLevel level = caller.level();
        TargetRushSavedData savedData = TargetRushSavedData.get(level);
        TargetRushArenaData arena = savedData.getData();

        String err = arena.validationError(level, false);
        if (err != null) {
            caller.sendSystemMessage(Component.literal("§c[Target Rush] " + err), false);
            return 0;
        }

        ServerLevel arenaLevel = caller.level().getServer().getLevel(arena.getDimension());
        if (arenaLevel == null) {
            caller.sendSystemMessage(Component.literal("§c[Target Rush] Arena-Dimension nicht gefunden!"), false);
            return 0;
        }

        MinigameManager.getInstance().startMinigame(() -> new TargetRushGame(caller.level().getServer(), arenaLevel, arena, p1, p2, team1, team2));
        caller.sendSystemMessage(Component.literal("§a[Target Rush] Match gestartet: " + p1.getPlainTextName() + " (" + team1.displayName + ") vs " + p2.getPlainTextName() + " (" + team2.displayName + ")!"), false);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        var active = MinigameManager.getInstance().getActiveGame();
        if (active instanceof TargetRushGame game && game.isRunning()) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl beendet");
            ctx.getSource().sendSuccess(() -> Component.literal("§a[Target Rush] Spiel erfolgreich beendet!"), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Target Rush] Es läuft aktuell kein Target Rush Spiel."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TargetRushSavedData savedData = TargetRushSavedData.get(player.level());
        TargetRushArenaData data = savedData.getData();

        player.sendSystemMessage(Component.literal("§6=== Target Rush Status ==="), false);
        player.sendSystemMessage(Component.literal("§7Zielbereich: " + (data.hasTargetArea() ? "§aGesetzt (" + data.getTargetMin().toShortString() + " bis " + data.getTargetMax().toShortString() + ")" : "§cNicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Spawn 1: " + (data.hasSpawn1() ? String.format(Locale.ROOT, "§a(%.1f, %.1f, %.1f)", data.getSpawn1X(), data.getSpawn1Y(), data.getSpawn1Z()) : "§cNicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Spawn 2: " + (data.hasSpawn2() ? String.format(Locale.ROOT, "§a(%.1f, %.1f, %.1f)", data.getSpawn2X(), data.getSpawn2Y(), data.getSpawn2Z()) : "§cNicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Team 1 Farbe: " + data.getTeam1ColorEnum().chatPrefix + data.getTeam1ColorEnum().displayName), false);
        player.sendSystemMessage(Component.literal("§7Team 2 Farbe: " + data.getTeam2ColorEnum().chatPrefix + data.getTeam2ColorEnum().displayName), false);

        var active = MinigameManager.getInstance().getActiveGame();
        boolean running = (active instanceof TargetRushGame game && game.isRunning());
        player.sendSystemMessage(Component.literal("§7Aktives Spiel: " + (running ? "§aLÄUFT" : "§7Keines")), false);

        return 1;
    }
}
