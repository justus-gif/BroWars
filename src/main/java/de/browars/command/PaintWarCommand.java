package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.paintwar.PaintWarArenaData;
import de.browars.minigame.paintwar.PaintWarGame;
import de.browars.minigame.paintwar.PaintWarSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PaintWarCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var node = Commands.literal("paintwar")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(PaintWarCommand::executeSetArena))
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
                                .executes(PaintWarCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(PaintWarCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(PaintWarCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(PaintWarCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(PaintWarCommand::executeStatus));

        dispatcher.register(Commands.literal("browars").then(node));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Paint War] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Arena!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof PaintWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Paint War] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        PaintWarSavedData saved = PaintWarSavedData.get(player.level());
        saved.getData().setArenaBounds(p1, p2, player.level().dimension());
        PaintWarSavedData.save(player.level());

        BlockPos min = saved.getData().getMinPos();
        BlockPos max = saved.getData().getMaxPos();
        int width = max.getX() - min.getX() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        player.sendSystemMessage(Component.literal("§a[Paint War] Arena-Bereich erfolgreich festgelegt: §e"
                + min.getX() + ", " + min.getZ() + " §abis §e" + max.getX() + ", " + max.getZ()
                + " §7(" + width + "×" + depth + " Grundfläche • gesamte Y-Höhe & Zufalls-Spawns aktiv)"), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorArg = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = MinigameCommandSupport.color(colorArg);

        PaintWarSavedData saved = PaintWarSavedData.get(player.level());
        if (teamNumber == 1) {
            saved.getData().setTeam1Color(color.id);
        } else {
            saved.getData().setTeam2Color(color.id);
        }
        PaintWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Paint War] Team " + teamNumber + " Farbe auf " + color.chatPrefix + color.displayName + " §agesetzt."), false);
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
        PaintWarArenaData arena = PaintWarSavedData.get(level).getData();
        String error = arena.validationError(level);
        if (error != null) {
            source.sendFailure(Component.literal("§c[Paint War] " + error));
            return 0;
        }

        TeamColor team1 = c1 != null ? c1 : arena.getTeam1ColorEnum();
        TeamColor team2 = c2 != null ? c2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() -> new PaintWarGame(source.getServer(), level, arena, first, second, team1, team2));

        if (second == null) {
            source.sendSuccess(() -> Component.literal("§a[Paint War] Solo-Übungsrunde gestartet für " + first.getPlainTextName() + "!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§a[Paint War] Duell gestartet (Best-of-5 / First-to-3): " + first.getPlainTextName() + " (" + team1.displayName + ") vs. "
                    + second.getPlainTextName() + " (" + team2.displayName + ")!"), true);
        }
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (MinigameManager.getInstance().getActiveGame() instanceof PaintWarGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl beendet");
            ctx.getSource().sendSuccess(() -> Component.literal("§6[Paint War] Match abgebrochen."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Paint War] Es läuft gerade kein Paint War Match."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PaintWarArenaData data = PaintWarSavedData.get(player.level()).getData();

        player.sendSystemMessage(Component.literal("§6=== [Paint War Arena Status] ==="), false);
        player.sendSystemMessage(Component.literal("§7Arena: " + (data.hasArena() ? "§a" + data.getMinPos().toShortString() + " bis " + data.getMaxPos().toShortString() + " (volle Y-Höhe)" : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§7Spawns: §aAutomatisch zufällig auf allen Ebenen der Map"), false);
        player.sendSystemMessage(Component.literal("§7Team 1 Farbe: " + data.getTeam1ColorEnum().chatPrefix + data.getTeam1ColorEnum().displayName), false);
        player.sendSystemMessage(Component.literal("§7Team 2 Farbe: " + data.getTeam2ColorEnum().chatPrefix + data.getTeam2ColorEnum().displayName), false);
        return 1;
    }
}
