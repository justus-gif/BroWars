package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.crosswar.CrossWarArenaData;
import de.browars.minigame.crosswar.CrossWarGame;
import de.browars.minigame.crosswar.CrossWarSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class CrossWarCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var node = Commands.literal("crosswar")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(CrossWarCommand::executeSetArena))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawn(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawn(ctx, 2))))
                .then(Commands.literal("spawnkammer")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawnChamber(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawnChamber(ctx, 2))))
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
                                .executes(CrossWarCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(CrossWarCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(CrossWarCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(CrossWarCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(CrossWarCommand::executeStatus));

        dispatcher.register(Commands.literal("browars").then(node));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Cross War] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Arena!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof CrossWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Cross War] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        CrossWarSavedData saved = CrossWarSavedData.get(player.level());
        saved.getData().setArenaBounds(p1, p2, player.level().dimension());
        CrossWarSavedData.save(player.level());

        BlockPos min = saved.getData().getMinPos();
        BlockPos max = saved.getData().getMaxPos();
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        player.sendSystemMessage(Component.literal("§a[Cross War] Arena erfolgreich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()
                + " §7(" + width + "×" + height + "×" + depth + ")"), false);
        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (MinigameManager.getInstance().getActiveGame() instanceof CrossWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Cross War] Stoppe das laufende Match vor Änderungen an den Spawns."), false);
            return 0;
        }

        CrossWarSavedData saved = CrossWarSavedData.get(player.level());
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        if (spawnNumber == 1) {
            saved.getData().setSpawn1(x, y, z, yaw, pitch, player.level().dimension());
        } else {
            saved.getData().setSpawn2(x, y, z, yaw, pitch, player.level().dimension());
        }
        CrossWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Cross War] Spawn " + spawnNumber + " auf deine aktuelle Position gesetzt!"), false);
        return 1;
    }

    private static int executeSetSpawnChamber(CommandContext<CommandSourceStack> ctx, int spawnNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Cross War] Markiere mit dem Arena-Auswahlstab zuerst die Begrenzung für die Spawnkammer " + spawnNumber + "!"), false);
            return 0;
        }

        CrossWarSavedData saved = CrossWarSavedData.get(player.level());
        if (spawnNumber == 1) {
            saved.getData().setSpawnChamber1(p1, p2);
        } else {
            saved.getData().setSpawnChamber2(p1, p2);
        }
        CrossWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Cross War] Spawnkammer " + spawnNumber + " (Anti-Spawn-Camp Zone) erfolgreich gespeichert!"), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorArg = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = MinigameCommandSupport.color(colorArg);

        CrossWarSavedData saved = CrossWarSavedData.get(player.level());
        if (teamNumber == 1) {
            saved.getData().setTeam1Color(color.id);
        } else {
            saved.getData().setTeam2Color(color.id);
        }
        CrossWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Cross War] Team " + teamNumber + " Farbe auf " + color.chatPrefix + color.displayName + " §agesetzt."), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer first = EntityArgument.getPlayer(ctx, "spieler1");
        return startMatch(ctx.getSource(), first, null, null, null);
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer first = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer second = EntityArgument.getPlayer(ctx, "spieler2");
        return startMatch(ctx.getSource(), first, second, null, null);
    }

    private static int executeStartCustomColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer first = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer second = EntityArgument.getPlayer(ctx, "spieler2");
        TeamColor c1 = MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe1"));
        TeamColor c2 = MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe2"));
        return startMatch(ctx.getSource(), first, second, c1, c2);
    }

    private static int startMatch(CommandSourceStack source, ServerPlayer first, ServerPlayer second, TeamColor c1, TeamColor c2) {
        if (!MinigameCommandSupport.validatePlayers(source, first, second)) return 0;

        ServerLevel level = first.level();
        CrossWarArenaData arena = CrossWarSavedData.get(level).getData();
        String error = arena.validationError(level);
        if (error != null) {
            source.sendFailure(Component.literal("§c[Cross War] " + error));
            return 0;
        }

        TeamColor team1 = c1 != null ? c1 : arena.getTeam1ColorEnum();
        TeamColor team2 = c2 != null ? c2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() -> new CrossWarGame(source.getServer(), level, arena, first, second, team1, team2));

        if (second == null) {
            source.sendSuccess(() -> Component.literal("§a[Cross War] Solo-Übungsrunde gestartet für " + first.getPlainTextName() + "!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§a[Cross War] Duell gestartet: " + first.getPlainTextName() + " (" + team1.displayName + ") vs. "
                    + second.getPlainTextName() + " (" + team2.displayName + ")!"), true);
        }
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (MinigameManager.getInstance().getActiveGame() instanceof CrossWarGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl beendet");
            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cross War] Match abgebrochen."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Cross War] Es läuft gerade kein Cross War Match."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CrossWarArenaData data = CrossWarSavedData.get(player.level()).getData();

        player.sendSystemMessage(Component.literal("§6=== [Cross War Arena Status] ==="), false);
        player.sendSystemMessage(Component.literal("§7Arena: " + (data.hasArena() ? "§a" + data.getMinPos().toShortString() + " bis " + data.getMaxPos().toShortString() : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Spawn 1: " + (data.hasSpawn1() ? "§aJa" : "§cNein")), false);
        player.sendSystemMessage(Component.literal("§7Spawn 2: " + (data.hasSpawn2() ? "§aJa" : "§cNein")), false);
        player.sendSystemMessage(Component.literal("§7Spawnkammer 1: " + (data.hasSpawnChamber1() ? "§aDefiniert" : "§eStandard (Radius)")), false);
        player.sendSystemMessage(Component.literal("§7Spawnkammer 2: " + (data.hasSpawnChamber2() ? "§aDefiniert" : "§eStandard (Radius)")), false);
        player.sendSystemMessage(Component.literal("§7Team 1 Farbe: " + data.getTeam1ColorEnum().chatPrefix + data.getTeam1ColorEnum().displayName), false);
        player.sendSystemMessage(Component.literal("§7Team 2 Farbe: " + data.getTeam2ColorEnum().chatPrefix + data.getTeam2ColorEnum().displayName), false);
        return 1;
    }
}
