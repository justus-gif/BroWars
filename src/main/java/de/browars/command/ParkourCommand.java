package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.parkour.ParkourManager;
import de.browars.parkour.ParkourSavedData;
import de.browars.parkour.ParkourScore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

public class ParkourCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var parkourNode = Commands.literal("parkour")
                .then(Commands.literal("top")
                        .executes(ParkourCommand::executeTop))
                .then(Commands.literal("setstart")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ParkourCommand::executeSetStart))
                .then(Commands.literal("setend")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ParkourCommand::executeSetEnd))
                .then(Commands.literal("resetleaderboard")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ParkourCommand::executeResetLeaderboard));

        dispatcher.register(Commands.literal("browars")
                .then(parkourNode));
    }

    private static int executeSetStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Parkour] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Startzone!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        ParkourSavedData savedData = ParkourSavedData.get(level);
        savedData.getData().setStartBounds(p1, p2, level.dimension());
        ParkourSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;

        player.sendSystemMessage(Component.literal("§a[Parkour] Startzone erfolgreich gespeichert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d §7(%d×%d×%d Blöcke)", minX, maxX, minY, maxY, minZ, maxZ, w, h, d)), false);
        return 1;
    }

    private static int executeSetEnd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Parkour] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Zielzone!"), false);
            return 0;
        }

        ServerLevel level = player.level();
        ParkourSavedData savedData = ParkourSavedData.get(level);
        savedData.getData().setEndBounds(p1, p2, level.dimension());
        ParkourSavedData.save(level);

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;

        player.sendSystemMessage(Component.literal("§a[Parkour] Zielzone erfolgreich gespeichert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§7Bereich: §eX: %d..%d, Y: %d..%d, Z: %d..%d §7(%d×%d×%d Blöcke)", minX, maxX, minY, maxY, minZ, maxZ, w, h, d)), false);
        return 1;
    }

    private static int executeTop(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        ParkourSavedData savedData = ParkourSavedData.get(level);
        List<ParkourScore> scores = savedData.getData().getScores();

        ctx.getSource().sendSuccess(() -> Component.literal("§6§l=== PARKOUR BESTZEITEN ==="), false);
        if (scores.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7(Noch keine Zeiten aufgezeichnet)"), false);
            return 1;
        }

        int max = Math.min(scores.size(), 10);
        for (int i = 0; i < max; i++) {
            final int index = i;
            ParkourScore score = scores.get(i);
            String medal = switch (i) {
                case 0 -> "§e#1";
                case 1 -> "§f#2";
                case 2 -> "§6#3";
                default -> "§7#" + (index + 1);
            };
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format(Locale.ROOT, "%s §f%s §7- §a%s", medal, score.playerName(), ParkourScore.formatTime(score.timeMillis()))
            ), false);
        }
        return 1;
    }

    private static int executeResetLeaderboard(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        ParkourSavedData savedData = ParkourSavedData.get(level);
        savedData.getData().clearScores();
        ParkourSavedData.save(level);

        ParkourManager.getInstance().updateAllHolograms(ctx.getSource().getServer());

        ctx.getSource().sendSuccess(() -> Component.literal("§a[Parkour] Alle Bestzeiten wurden erfolgreich zurückgesetzt!"), true);
        return 1;
    }
}
