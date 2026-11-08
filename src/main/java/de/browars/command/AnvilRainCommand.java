package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.anvilrain.AnvilRainArenaData;
import de.browars.minigame.anvilrain.AnvilRainGame;
import de.browars.minigame.anvilrain.AnvilRainSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class AnvilRainCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("anvilrain")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena").executes(AnvilRainCommand::setArena))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1").executes(ctx -> setSpawn(ctx, 1)))
                        .then(Commands.literal("2").executes(ctx -> setSpawn(ctx, 2))))
                .then(Commands.literal("team")
                        .then(Commands.literal("1").then(Commands.argument("farbe", StringArgumentType.word())
                                .suggests(MinigameCommandSupport.COLORS).executes(ctx -> setTeam(ctx, 1))))
                        .then(Commands.literal("2").then(Commands.argument("farbe", StringArgumentType.word())
                                .suggests(MinigameCommandSupport.COLORS).executes(ctx -> setTeam(ctx, 2)))))
                .then(Commands.literal("start")
                        .then(Commands.argument("spieler1", EntityArgument.player())
                                .executes(ctx -> start(ctx, false, false))
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(ctx -> start(ctx, true, false))
                                        .then(Commands.argument("farbe1", StringArgumentType.word()).suggests(MinigameCommandSupport.COLORS)
                                                .then(Commands.argument("farbe2", StringArgumentType.word()).suggests(MinigameCommandSupport.COLORS)
                                                        .executes(ctx -> start(ctx, true, true)))))))
                .then(Commands.literal("stop").executes(AnvilRainCommand::stop))
                .then(Commands.literal("status").executes(AnvilRainCommand::status));
        dispatcher.register(Commands.literal("browars").then(root));
    }

    private static boolean editingAllowed(CommandSourceStack source) {
        if (MinigameManager.getInstance().getActiveGame() instanceof AnvilRainGame game && game.isRunning()) {
            source.sendFailure(Component.literal("Stoppe Anvil Rain vor Änderungen an der Arena."));
            return false;
        }
        return true;
    }
    private static int setArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        if (!editingAllowed(ctx.getSource())) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var first = ArenaWandItem.POS1_MAP.get(player.getUUID());
        var second = ArenaWandItem.POS2_MAP.get(player.getUUID());
        if (first == null || second == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            ctx.getSource().sendFailure(Component.literal("Markiere mit dem Arena-Auswahlstab zwei Boden-Ecken in dieser Dimension."));
            return 0;
        }
        var saved = AnvilRainSavedData.get(player.level());
        var current = saved.getData();
        var candidate = new AnvilRainArenaData();
        candidate.setArenaBounds(first, second, player.level().dimension());
        candidate.setTeam1Color(current.getTeam1Color());
        candidate.setTeam2Color(current.getTeam2Color());
        String error = candidate.validationError(player.level());
        if (error != null) { ctx.getSource().sendFailure(Component.literal(error)); return 0; }
        // Preserve only spawns which still fit the newly selected arena.
        if (current.getDimension().equals(candidate.getDimension())) {
            for (int number = 1; number <= 2; number++) {
                if (!(number == 1 ? current.hasSpawn1() : current.hasSpawn2())) continue;
                var spawn = current.spawn(number);
                if (candidate.containsPlayer(spawn.x(), spawn.y(), spawn.z())) {
                    if (number == 1) candidate.setSpawn1(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
                    else candidate.setSpawn2(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
                }
            }
        }
        saved.setData(candidate);
        AnvilRainSavedData.save(player.level());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Anvil Rain] Arena gespeichert: " + first.toShortString() + " bis " + second.toShortString()
                + ". Setze /browars anvilrain spawn 1 und spawn 2."), false);
        return 1;
    }
    private static int setSpawn(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        if (!editingAllowed(ctx.getSource())) return 0;
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var arena = AnvilRainSavedData.get(player.level()).getData();
        if (!arena.hasArena() || !arena.getDimension().equals(player.level().dimension())
                || !arena.containsPlayer(player.getX(), player.getY(), player.getZ()) || Math.abs(player.getY() - arena.getFloorY() - 1) > 0.1) {
            ctx.getSource().sendFailure(Component.literal("Stelle dich auf den Boden innerhalb der gespeicherten Arena."));
            return 0;
        }
        if (number == 1) arena.setSpawn1(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        else arena.setSpawn2(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        AnvilRainSavedData.save(player.level());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Anvil Rain] Spawn " + number + " gespeichert."), false);
        return 1;
    }
    private static int setTeam(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        var color = MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe"));
        var player = ctx.getSource().getPlayerOrException();
        var arena = AnvilRainSavedData.get(player.level()).getData();
        if (number == 1) arena.setTeam1Color(color.id); else arena.setTeam2Color(color.id);
        AnvilRainSavedData.save(player.level());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Anvil Rain] Team " + number + ": " + color.chatPrefix + color.displayName), false);
        return 1;
    }
    private static int start(CommandContext<CommandSourceStack> ctx, boolean duel, boolean customColors) throws CommandSyntaxException {
        var caller = ctx.getSource().getPlayerOrException();
        var first = EntityArgument.getPlayer(ctx, "spieler1");
        var second = duel ? EntityArgument.getPlayer(ctx, "spieler2") : null;
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), first, second)) return 0;
        var arena = AnvilRainSavedData.get(caller.level()).getData();
        var level = ctx.getSource().getServer().getLevel(arena.getDimension());
        String error = level == null ? "Arena-Dimension nicht gefunden." : arena.validationError(level);
        if (error == null && (!arena.containsSpawn(1) || (duel && !arena.containsSpawn(2)))) error = "Setze gültige Spawnpunkte mit /browars anvilrain spawn 1 bzw. 2.";
        if (error != null) { ctx.getSource().sendFailure(Component.literal(error)); return 0; }
        TeamColor firstTeam = customColors ? MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe1")) : arena.getTeam1ColorEnum();
        TeamColor secondTeam = customColors ? MinigameCommandSupport.color(StringArgumentType.getString(ctx, "farbe2")) : arena.getTeam2ColorEnum();
        MinigameManager.getInstance().startMinigame(() -> new AnvilRainGame(ctx.getSource().getServer(), level, arena, first, second, firstTeam, secondTeam));
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Anvil Rain] " + (duel ? "Match – erster zu 5 Punkten!" : "Solo-Übungsmodus gestartet!")), false);
        return 1;
    }
    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!(MinigameManager.getInstance().getActiveGame() instanceof AnvilRainGame game) || !game.isRunning()) {
            ctx.getSource().sendFailure(Component.literal("Es läuft aktuell kein Anvil-Rain-Spiel.")); return 0;
        }
        MinigameManager.getInstance().stopCurrentMinigame("Durch Spielleiter abgebrochen");
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Anvil Rain] Spiel beendet; Ambosse entfernt."), false);
        return 1;
    }
    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var arena = AnvilRainSavedData.get(ctx.getSource().getPlayerOrException().level()).getData();
        ctx.getSource().sendSuccess(() -> Component.literal("§d[Anvil Rain] §7Arena: " + (arena.hasArena()
                ? arena.getMinPos().toShortString() + " bis " + arena.getMaxPos().toShortString() + " (" + arena.getDimension().identifier() + ")" : "nicht gesetzt")), false);
        for (int number = 1; number <= 2; number++) {
            var spawn = arena.spawn(number);
            String text = "§7Spawn " + number + ": " + ((number == 1 ? arena.hasSpawn1() : arena.hasSpawn2())
                    ? String.format(Locale.ROOT, "%.1f, %.1f, %.1f", spawn.x(), spawn.y(), spawn.z()) : "nicht gesetzt");
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§7Teams: " + arena.getTeam1ColorEnum().displayName + " / " + arena.getTeam2ColorEnum().displayName
                + " • Erster zu 5 Punkten • Fallhöhe: 20 Blöcke"), false);
        return 1;
    }
}
