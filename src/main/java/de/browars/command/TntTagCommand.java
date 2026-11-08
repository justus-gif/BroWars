package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.tnttag.TntTagArenaData;
import de.browars.minigame.tnttag.TntTagGame;
import de.browars.minigame.tnttag.TntTagSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class TntTagCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var tntTagNode = Commands.literal("tnttag")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(TntTagCommand::executeSetArena))
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
                                .executes(TntTagCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(TntTagCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(TntTagCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(TntTagCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(TntTagCommand::executeStatus));

        dispatcher.register(Commands.literal("browars")
                .then(tntTagNode));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[TNT Tag] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Arena!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof TntTagGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[TNT Tag] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        ServerLevel level = player.level();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        savedData.getData().setArenaBounds(p1, p2, level.dimension());
        TntTagSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [TNT Tag] Arena-Bereich gespeichert: (%d, %d, %d) bis (%d, %d, %d) [%d×%d×%d]",
                minX, minY, minZ, maxX, maxY, maxZ, width, height, depth)), false);
        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        TntTagArenaData data = savedData.getData();

        if (number == 1) {
            data.setSpawn1(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        } else {
            data.setSpawn2(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), level.dimension());
        }
        TntTagSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [TNT Tag] Spawn %d gespeichert: %.2f, %.2f, %.2f (Yaw: %.1f°, Pitch: %.1f°)",
                number, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot())), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String inputColor = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = TeamColor.fromString(inputColor, null);

        if (color == null) {
            player.sendSystemMessage(Component.literal("§c[TNT Tag] Unbekannte Farbe: " + inputColor + ". Erlaubt: rot, blau, gruen, gelb, etc."), false);
            return 0;
        }

        ServerLevel level = player.level();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        TntTagArenaData data = savedData.getData();

        if (number == 1) {
            data.setTeam1Color(color.id);
        } else {
            data.setTeam2Color(color.id);
        }
        TntTagSavedData.save(level);

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a✔ [TNT Tag] Team %d Standardfarbe: %s%s",
                number, color.chatPrefix, color.displayName)), false);
        return 1;
    }

    private static int executeStartSolo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        return startMatch(ctx.getSource(), p1, null, null, null);
    }

    private static int executeStartDefaultColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        return startMatch(ctx.getSource(), p1, p2, null, null);
    }

    private static int executeStartCustomColors(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p1 = EntityArgument.getPlayer(ctx, "spieler1");
        ServerPlayer p2 = EntityArgument.getPlayer(ctx, "spieler2");
        String color1Str = StringArgumentType.getString(ctx, "farbe1");
        String color2Str = StringArgumentType.getString(ctx, "farbe2");

        TeamColor c1 = TeamColor.fromString(color1Str, TeamColor.RED);
        TeamColor c2 = TeamColor.fromString(color2Str, TeamColor.BLUE);
        return startMatch(ctx.getSource(), p1, p2, c1, c2);
    }

    private static int startMatch(CommandSourceStack source, ServerPlayer p1, ServerPlayer p2,
                                  TeamColor overrideC1, TeamColor overrideC2) {
        ServerLevel level = source.getLevel();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        TntTagArenaData arena = savedData.getData();

        String error = arena.validationError(level);
        if (error != null) {
            source.sendFailure(Component.literal("§c[TNT Tag] " + error));
            return 0;
        }

        if (p2 != null && p1.getUUID().equals(p2.getUUID())) {
            source.sendFailure(Component.literal("§c[TNT Tag] Spieler 1 und Spieler 2 müssen unterschiedlich sein."));
            return 0;
        }

        TeamColor c1 = overrideC1 != null ? overrideC1 : arena.getTeam1ColorEnum();
        TeamColor c2 = overrideC2 != null ? overrideC2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() -> new TntTagGame(source.getServer(), level, arena, p1, p2, c1, c2));

        source.sendSuccess(() -> Component.literal(p2 != null
                ? "§a✔ [TNT Tag] Match gestartet: " + c1.chatPrefix + p1.getPlainTextName() + " §7vs. " + c2.chatPrefix + p2.getPlainTextName()
                : "§a✔ [TNT Tag] Solo-Runde gestartet für " + c1.chatPrefix + p1.getPlainTextName()), true);
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (MinigameManager.getInstance().getActiveGame() instanceof TntTagGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl abgebrochen");
            source.sendSuccess(() -> Component.literal("§a✔ [TNT Tag] Spiel gestoppt."), true);
            return 1;
        }
        source.sendFailure(Component.literal("§c[TNT Tag] Es läuft aktuell kein TNT Tag Spiel."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        TntTagArenaData data = savedData.getData();

        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("§6=== [TNT Tag Status] ==="), false);
        source.sendSuccess(() -> Component.literal("§7Arena-Bereich: " + (data.hasArena() ? "§aDefiniert (Optional)" : "§7Nicht markiert (Optional)")), false);
        source.sendSuccess(() -> Component.literal("§7Spawn 1 eingerichtet: " + (data.hasSpawn1() ? "§aJa" : "§cNein")), false);
        source.sendSuccess(() -> Component.literal("§7Spawn 2 eingerichtet: " + (data.hasSpawn2() ? "§aJa" : "§cNein")), false);
        source.sendSuccess(() -> Component.literal("§7Team 1 Farbe: " + data.getTeam1ColorEnum().chatPrefix + data.getTeam1ColorEnum().displayName), false);
        source.sendSuccess(() -> Component.literal("§7Team 2 Farbe: " + data.getTeam2ColorEnum().chatPrefix + data.getTeam2ColorEnum().displayName), false);
        source.sendSuccess(() -> Component.literal("§7Spawnkammer 1: " + (data.hasSpawnChamber1() ? "§aDefiniert" : "§7Standard-Radius (2.5m)")), false);
        source.sendSuccess(() -> Component.literal("§7Spawnkammer 2: " + (data.hasSpawnChamber2() ? "§aDefiniert" : "§7Standard-Radius (2.5m)")), false);
        source.sendSuccess(() -> Component.literal("§7Bereit: " + (data.isFullyReady() ? "§aJa" : "§cNein (Spawns 1 & 2 fehlen)")), false);
        return 1;
    }

    private static int executeSetSpawnChamber(CommandContext<CommandSourceStack> ctx, int number) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[TNT Tag] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 und 2 der Spawnkammer " + number + "!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof TntTagGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[TNT Tag] Stoppe das laufende Match vor Änderungen an der Spawnkammer."), false);
            return 0;
        }

        ServerLevel level = player.level();
        TntTagSavedData savedData = TntTagSavedData.get(level);
        TntTagArenaData data = savedData.getData();

        if (number == 1) {
            data.setSpawnChamber1(p1, p2);
        } else {
            data.setSpawnChamber2(p1, p2);
        }
        TntTagSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§a[TNT Tag] Spawnkammer %d gespeichert: (%d, %d, %d) bis (%d, %d, %d) [%d×%d×%d]",
                number, minX, minY, minZ, maxX, maxY, maxZ, width, height, depth)), false);
        return 1;
    }
}
