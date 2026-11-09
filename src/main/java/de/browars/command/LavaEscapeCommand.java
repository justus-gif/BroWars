package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.lavaescape.LavaEscapeArenaData;
import de.browars.minigame.lavaescape.LavaEscapeGame;
import de.browars.minigame.lavaescape.LavaEscapeSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class LavaEscapeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var node = Commands.literal("lavaescape")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(LavaEscapeCommand::executeSetArena))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawn(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawn(ctx, 2))))
                .then(Commands.literal("goal")
                        .executes(LavaEscapeCommand::executeSetGoal))
                .then(Commands.literal("chestarea")
                        .executes(LavaEscapeCommand::executeSetChestArea))
                .then(Commands.literal("team")
                        .then(Commands.literal("1")
                                .then(Commands.argument("farbe", StringArgumentType.word())
                                        .suggests(teamColorSuggestion)
                                        .executes(ctx -> executeSetTeamColor(ctx, 1))))
                        .then(Commands.literal("2")
                                .then(Commands.argument("farbe", StringArgumentType.word())
                                        .suggests(teamColorSuggestion)
                                        .executes(ctx -> executeSetTeamColor(ctx, 2)))))
                .then(Commands.literal("targetscore")
                        .executes(LavaEscapeCommand::executeShowTargetScore)
                        .then(Commands.argument("punkte", IntegerArgumentType.integer(1, 10))
                                .executes(LavaEscapeCommand::executeSetTargetScore)))
                .then(Commands.literal("start")
                        .then(Commands.argument("spieler1", EntityArgument.player())
                                .executes(LavaEscapeCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(LavaEscapeCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(LavaEscapeCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(LavaEscapeCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(LavaEscapeCommand::executeStatus));

        dispatcher.register(Commands.literal("browars").then(node));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Arena!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        saved.getData().setArenaBounds(p1, p2, player.level().dimension());
        LavaEscapeSavedData.save(player.level());

        BlockPos min = saved.getData().getMinPos();
        BlockPos max = saved.getData().getMaxPos();
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        player.sendSystemMessage(Component.literal("§a[Lava Escape] Arena erfolgreich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()
                + " §7(" + width + "×" + height + "×" + depth + ")"), false);
        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Stoppe das laufende Match vor Änderungen an den Spawns."), false);
            return 0;
        }

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
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
        LavaEscapeSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Lava Escape] Spawn " + spawnNumber + " auf deine aktuelle Position gesetzt!"), false);
        return 1;
    }

    private static int executeSetGoal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Bitte markiere zuerst mit dem Arena-Auswahlstab den Ziel-Bereich (Punkt 1 und Punkt 2)!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Stoppe das laufende Match vor Änderungen am Ziel."), false);
            return 0;
        }

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        saved.getData().setGoalBounds(p1, p2);
        LavaEscapeSavedData.save(player.level());

        BlockPos min = saved.getData().getGoalMin();
        BlockPos max = saved.getData().getGoalMax();
        player.sendSystemMessage(Component.literal("§a[Lava Escape] Ziel-Bereich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()), false);
        return 1;
    }

    private static int executeSetChestArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Bitte markiere zuerst mit dem Arena-Auswahlstab den Kisten-Bereich (Punkt 1 und Punkt 2)!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Lava Escape] Stoppe das laufende Match vor Änderungen am Kistenbereich."), false);
            return 0;
        }

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        saved.getData().setChestAreaBounds(p1, p2);
        LavaEscapeSavedData.save(player.level());

        BlockPos min = saved.getData().getChestAreaMin();
        BlockPos max = saved.getData().getChestAreaMax();
        player.sendSystemMessage(Component.literal("§a[Lava Escape] Kisten-Bereich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorName = StringArgumentType.getString(ctx, "farbe");

        TeamColor color = TeamColor.fromString(colorName, null);
        if (color == null) {
            player.sendSystemMessage(Component.literal("§cUngültige Teamfarbe: " + colorName), false);
            return 0;
        }

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        if (teamNumber == 1) {
            saved.getData().setTeam1Color(color.name().toLowerCase(Locale.ROOT));
        } else {
            saved.getData().setTeam2Color(color.name().toLowerCase(Locale.ROOT));
        }
        LavaEscapeSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Lava Escape] Team " + teamNumber + " Standardfarbe auf "
                + color.chatPrefix + color.displayName + " §agesetzt!"), false);
        return 1;
    }

    private static int executeShowTargetScore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        int score = saved.getData().getTargetScore();
        player.sendSystemMessage(Component.literal("§6[Lava Escape] Aktuelle Ziel-Punkteanzahl: §e" + score + " §7(First to " + score + ")"), false);
        player.sendSystemMessage(Component.literal("§7Ändern mit: §e/browars lavaescape targetscore <1-10>"), false);
        return 1;
    }

    private static int executeSetTargetScore(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int score = IntegerArgumentType.getInteger(ctx, "punkte");

        LavaEscapeSavedData saved = LavaEscapeSavedData.get(player.level());
        saved.getData().setTargetScore(score);
        LavaEscapeSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Lava Escape] Ziel-Punkteanzahl auf " + score + " gesetzt (First to " + score + ")!"), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        return startGame(ctx.getSource(), p1, null, null, null);
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        return startGame(ctx.getSource(), p1, p2, null, null);
    }

    private static int executeStartCustomColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        TeamColor c1 = TeamColor.fromString(StringArgumentType.getString(ctx, "farbe1"), null);
        TeamColor c2 = TeamColor.fromString(StringArgumentType.getString(ctx, "farbe2"), null);
        return startGame(ctx.getSource(), p1, p2, c1, c2);
    }

    private static int startGame(CommandSourceStack source, ServerPlayer p1, ServerPlayer p2,
                                 TeamColor color1, TeamColor color2) {
        LavaEscapeSavedData saved = LavaEscapeSavedData.get(source.getLevel());
        LavaEscapeArenaData arena = saved.getData();

        if (!arena.hasArena()) {
            source.sendFailure(Component.literal("§c[Lava Escape] Keine Arena definiert! Nutze /browars lavaescape arena"));
            return 0;
        }

        if (p2 == null) {
            if (!arena.isReady()) {
                source.sendFailure(Component.literal("§c[Lava Escape] Spawn 1 muss gesetzt sein! Nutze /browars lavaescape spawn 1"));
                return 0;
            }
        } else {
            if (!arena.isReadyForDuel()) {
                source.sendFailure(Component.literal("§c[Lava Escape] Beide Spawns müssen gesetzt sein! Nutze /browars lavaescape spawn 1 & 2"));
                return 0;
            }
        }

        TeamColor t1 = color1 != null ? color1 : arena.getTeam1ColorEnum();
        TeamColor t2 = color2 != null ? color2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() ->
                new LavaEscapeGame(source.getServer(), source.getLevel(), arena, p1, p2, t1, t2)
        );

        String vs = p2 != null ? " §7gegen " + t2.chatPrefix + p2.getPlainTextName() : " §7(Solo-Modus)";
        source.sendSuccess(() -> Component.literal("§a[Lava Escape] Spiel gestartet: "
                + t1.chatPrefix + p1.getPlainTextName() + vs), true);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Per Befehl abgebrochen");
            ctx.getSource().sendSuccess(() -> Component.literal("§a[Lava Escape] Spiel abgebrochen und Arena wiederhergestellt."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Lava Escape] Es läuft aktuell kein Lava Escape Match!"));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        LavaEscapeSavedData saved = LavaEscapeSavedData.get(ctx.getSource().getLevel());
        LavaEscapeArenaData arena = saved.getData();

        ctx.getSource().sendSuccess(() -> Component.literal("§6--- Lava Escape Status ---"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eArena: " + (arena.hasArena()
                ? "§a" + arena.getMinPos().toShortString() + " bis " + arena.getMaxPos().toShortString()
                : "§cnicht gesetzt")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eSpawn 1: " + (arena.getSpawn1() != null ? "§agesetzt" : "§cnicht gesetzt")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eSpawn 2: " + (arena.getSpawn2() != null ? "§agesetzt" : "§cnicht gesetzt")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eZiel-Zone: " + (arena.hasGoal()
                ? "§a" + arena.getGoalMin().toShortString() + " bis " + arena.getGoalMax().toShortString()
                : "§7Standard (oberste Arena-Ebene)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eKisten-Bereich: " + (arena.hasChestArea()
                ? "§a" + arena.getChestAreaMin().toShortString() + " bis " + arena.getChestAreaMax().toShortString()
                : "§7Standard (gesamte Arena)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§ePunkte für Sieg: §a" + arena.getTargetScore()), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§eStandard-Farben: "
                + arena.getTeam1ColorEnum().chatPrefix + arena.getTeam1Color() + " §7vs "
                + arena.getTeam2ColorEnum().chatPrefix + arena.getTeam2Color()), false);
        boolean running = MinigameManager.getInstance().getActiveGame() instanceof LavaEscapeGame game && game.isRunning();
        ctx.getSource().sendSuccess(() -> Component.literal("§eLaufendes Spiel: " + (running ? "§aJA" : "§7NEIN")), false);

        return 1;
    }
}
