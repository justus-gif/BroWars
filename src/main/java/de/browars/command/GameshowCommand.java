package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.gameshow.GameshowData;
import de.browars.gameshow.GameshowGameType;
import de.browars.gameshow.GameshowManager;
import de.browars.gameshow.GameshowSavedData;
import de.browars.item.ArenaWandItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class GameshowCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var gameshowNode = Commands.literal("gameshow")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("spawn")
                        .executes(GameshowCommand::executeSetMainSpawn))
                .then(Commands.literal("startarea")
                        .executes(GameshowCommand::executeSetStartArea))
                .then(Commands.literal("blackroom")
                        .executes(GameshowCommand::executeSetBlackRoom))
                .then(Commands.literal("lobbyspawn")
                        .executes(GameshowCommand::executeSetLobbySpawn))
                .then(Commands.literal("lobbyarea")
                        .executes(GameshowCommand::executeSetLobbyArea))
                .then(Commands.literal("podium1")
                        .executes(GameshowCommand::executeSetPodium1))
                .then(Commands.literal("podium2")
                        .executes(GameshowCommand::executeSetPodium2))
                .then(Commands.literal("podiumspawn")
                        .executes(GameshowCommand::executeSetPodiumSpawn))
                .then(Commands.literal("start")
                        .then(Commands.argument("spieler1", EntityArgument.player())
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(GameshowCommand::executeStart))))
                .then(Commands.literal("stop")
                        .executes(GameshowCommand::executeStop))
                .then(Commands.literal("skip")
                        .executes(GameshowCommand::executeSkip))
                .then(Commands.literal("status")
                        .executes(GameshowCommand::executeStatus));

        dispatcher.register(Commands.literal("browars")
                .then(gameshowNode));
    }

    private static int executeSetMainSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel overworld = player.level().getServer().overworld();
        GameshowSavedData saved = GameshowSavedData.get(overworld);
        saved.getData().setMainSpawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), player.level().dimension());
        GameshowSavedData.save(overworld);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Hauptlobby- & Welt-Spawn gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f, Pitch: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())), false);
        return 1;
    }

    private static int executeSetStartArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Game Show] Markiere zuerst Punkt 1 und Punkt 2 mit dem Arena-Auswahlstab!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setStartArea(p1, p2, level.dimension());
        GameshowSavedData.save(level);

        GameshowData.AreaBox box = saved.getData().getStartArea();
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Startbereich gespeichert (§e%d..%d, %d..%d, %d..%d§a)",
                box.min().getX(), box.max().getX(), box.min().getY(), box.max().getY(), box.min().getZ(), box.max().getZ())), false);
        return 1;
    }

    private static int executeSetBlackRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setBlackRoomSpawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        GameshowSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Schwarzer Raum gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f, Pitch: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())), false);
        return 1;
    }

    private static int executeSetLobbySpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setLobbySpawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        GameshowSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Zwischen-Lobby Spawn gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f, Pitch: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())), false);
        return 1;
    }

    private static int executeSetLobbyArea(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Game Show] Markiere zuerst Punkt 1 und Punkt 2 mit dem Arena-Auswahlstab!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setLobbyArea(p1, p2, level.dimension());
        GameshowSavedData.save(level);

        GameshowData.AreaBox box = saved.getData().getLobbyArea();
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Zwischen-Lobby Bereich gespeichert (§e%d..%d, %d..%d, %d..%d§a)",
                box.min().getX(), box.max().getX(), box.min().getY(), box.max().getY(), box.min().getZ(), box.max().getZ())), false);
        return 1;
    }

    private static int executeSetPodium1(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setPodium1Pos(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        GameshowSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Podest Platz 1 (Gewinner-Figur) gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot())), false);
        return 1;
    }

    private static int executeSetPodium2(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setPodium2Pos(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        GameshowSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Podest Platz 2 (2. Platz-Figur) gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot())), false);
        return 1;
    }

    private static int executeSetPodiumSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        GameshowSavedData saved = GameshowSavedData.get(level);
        saved.getData().setPodiumViewerSpawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        GameshowSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [Game Show] Podest Spieler-Spawn gesetzt auf §e%.1f, %.1f, %.1f §7(Yaw: %.1f, Pitch: %.1f)",
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())), false);
        return 1;
    }

    private static int executeStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        if (!MinigameCommandSupport.validatePlayers(ctx.getSource(), p1, p2)) return 0;

        boolean ok = GameshowManager.getInstance().startShow(ctx.getSource().getServer(), p1, p2);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("§c[Game Show] Konnte nicht gestartet werden."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§a✔ [Game Show] Gestartet für §e" + p1.getPlainTextName() + " §aund §e" + p2.getPlainTextName()), true);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (!GameshowManager.getInstance().isShowActive()) {
            ctx.getSource().sendFailure(Component.literal("§c[Game Show] Es läuft keine Game Show."));
            return 0;
        }
        GameshowManager.getInstance().stopShow(ctx.getSource().getServer(), "Beendet");
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Game Show] Beendet."), true);
        return 1;
    }

    private static int executeSkip(CommandContext<CommandSourceStack> ctx) {
        if (!GameshowManager.getInstance().skipCurrentStep(ctx.getSource().getServer())) {
            ctx.getSource().sendFailure(Component.literal("§c[Game Show] Aktuell kann nichts übersprungen werden."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§e[Game Show] Phase übersprungen."), true);
        return 1;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        GameshowData data = GameshowSavedData.get(server.overworld()).getData();

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== GAME SHOW STATUS ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Hauptlobby-Spawn: " + (data.getMainSpawn().set() ? "§a✔" : "§c✖ (/browars gameshow spawn)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Startbereich: " + (data.getStartArea().set() ? "§a✔" : "§c✖ (/browars gameshow startarea)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Schwarzer Raum: " + (data.getBlackRoomSpawn().set() ? "§a✔" : "§c✖ (/browars gameshow blackroom)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Zwischen-Lobby Spawn: " + (data.getLobbySpawn().set() ? "§a✔" : "§c✖ (/browars gameshow lobbyspawn)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Zwischen-Lobby Bereich: " + (data.getLobbyArea().set() ? "§a✔" : "§c✖ (/browars gameshow lobbyarea)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Podest Platz 1: " + (data.getPodium1Pos().set() ? "§a✔" : "§c✖ (/browars gameshow podium1)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Podest Platz 2: " + (data.getPodium2Pos().set() ? "§a✔" : "§c✖ (/browars gameshow podium2)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Podest Spawn: " + (data.getPodiumViewerSpawn().set() ? "§a✔" : "§c✖ (/browars gameshow podiumspawn)")), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Boards: §e" + data.getBoardBlocks().size()), false);

        int readyCount = 0;
        for (GameshowGameType type : GameshowGameType.values()) {
            String err = type.validationError(server);
            if (err == null) {
                readyCount++;
                ctx.getSource().sendSuccess(() -> Component.literal(" §a✔ " + type.displayName), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(" §c✖ " + type.displayName + " §8— §7" + err), false);
            }
        }
        final int totalReady = readyCount;
        ctx.getSource().sendSuccess(() -> Component.literal("§eBereite Spiele: §f" + totalReady + " / " + GameshowGameType.values().length), false);
        return 1;
    }
}
