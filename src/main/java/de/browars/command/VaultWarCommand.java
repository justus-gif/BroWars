package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.item.ArenaWandItem;
import de.browars.minigame.MinigameManager;
import de.browars.minigame.TeamColor;
import de.browars.minigame.vaultwar.VaultWarArenaData;
import de.browars.minigame.vaultwar.VaultWarGame;
import de.browars.minigame.vaultwar.VaultWarSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class VaultWarCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var teamColorSuggestion = MinigameCommandSupport.COLORS;

        var node = Commands.literal("vaultwar")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("arena")
                        .executes(VaultWarCommand::executeSetArena))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("1")
                                .executes(ctx -> executeSetSpawn(ctx, 1)))
                        .then(Commands.literal("2")
                                .executes(ctx -> executeSetSpawn(ctx, 2))))
                .then(Commands.literal("fence")
                        .executes(VaultWarCommand::executeSetFence))
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
                                .executes(VaultWarCommand::executeStartSolo)
                                .then(Commands.argument("spieler2", EntityArgument.player())
                                        .executes(VaultWarCommand::executeStartDefaultColors)
                                        .then(Commands.argument("farbe1", StringArgumentType.word())
                                                .suggests(teamColorSuggestion)
                                                .then(Commands.argument("farbe2", StringArgumentType.word())
                                                        .suggests(teamColorSuggestion)
                                                        .executes(VaultWarCommand::executeStartCustomColors))))))
                .then(Commands.literal("stop")
                        .executes(VaultWarCommand::executeStop))
                .then(Commands.literal("status")
                        .executes(VaultWarCommand::executeStatus));

        dispatcher.register(Commands.literal("browars").then(node));
    }

    private static int executeSetArena(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Bitte markiere zuerst mit dem Arena-Auswahlstab Punkt 1 (Linksklick) und Punkt 2 (Rechtsklick) der Arena!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof VaultWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Stoppe das laufende Match vor Änderungen an der Arena."), false);
            return 0;
        }

        VaultWarSavedData saved = VaultWarSavedData.get(player.level());
        saved.getData().setArenaBounds(p1, p2, player.level().dimension());
        VaultWarSavedData.save(player.level());

        BlockPos min = saved.getData().getMinPos();
        BlockPos max = saved.getData().getMaxPos();
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        player.sendSystemMessage(Component.literal("§a[Vault War] Arena erfolgreich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()
                + " §7(" + width + "×" + height + "×" + depth + ")"), false);
        return 1;
    }

    private static int executeSetSpawn(CommandContext<CommandSourceStack> ctx, int spawnNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (MinigameManager.getInstance().getActiveGame() instanceof VaultWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Stoppe das laufende Match vor Änderungen an den Spawns."), false);
            return 0;
        }

        VaultWarSavedData saved = VaultWarSavedData.get(player.level());
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
        VaultWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Vault War] Spawn " + spawnNumber + " auf deine aktuelle Position gesetzt!"), false);
        return 1;
    }

    private static int executeSetFence(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos p1 = ArenaWandItem.POS1_MAP.get(player.getUUID());
        BlockPos p2 = ArenaWandItem.POS2_MAP.get(player.getUUID());

        if (p1 == null || p2 == null || !ArenaWandItem.isSelectionInCurrentDimension(player)) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Markiere mit dem Arena-Auswahlstab zuerst die Begrenzung des Trennzauns (Punkt 1 & Punkt 2)!"), false);
            return 0;
        }

        if (MinigameManager.getInstance().getActiveGame() instanceof VaultWarGame active && active.isRunning()) {
            player.sendSystemMessage(Component.literal("§c[Vault War] Stoppe das laufende Match vor Änderungen am Trennzaun."), false);
            return 0;
        }

        VaultWarSavedData saved = VaultWarSavedData.get(player.level());
        saved.getData().setFenceBounds(p1, p2);
        VaultWarSavedData.save(player.level());

        BlockPos min = saved.getData().getFenceMin();
        BlockPos max = saved.getData().getFenceMax();
        int width = max.getX() - min.getX() + 1;
        int height = max.getY() - min.getY() + 1;
        int depth = max.getZ() - min.getZ() + 1;

        player.sendSystemMessage(Component.literal("§a[Vault War] Trennzaun erfolgreich festgelegt: "
                + min.toShortString() + " bis " + max.toShortString()
                + " §7(" + width + "×" + height + "×" + depth + ")"), false);
        return 1;
    }

    private static int executeSetTeamColor(CommandContext<CommandSourceStack> ctx, int teamNumber) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String colorArg = StringArgumentType.getString(ctx, "farbe");
        TeamColor color = MinigameCommandSupport.color(colorArg);

        VaultWarSavedData saved = VaultWarSavedData.get(player.level());
        if (teamNumber == 1) {
            saved.getData().setTeam1Color(color.id);
        } else {
            saved.getData().setTeam2Color(color.id);
        }
        VaultWarSavedData.save(player.level());

        player.sendSystemMessage(Component.literal("§a[Vault War] Team " + teamNumber + " Farbe auf " + color.chatPrefix + color.displayName + " §agesetzt."), false);
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
        VaultWarArenaData arena = VaultWarSavedData.get(level).getData();
        String error = arena.validationError(level);
        if (error != null) {
            source.sendFailure(Component.literal("§c[Vault War] " + error));
            return 0;
        }

        TeamColor team1 = c1 != null ? c1 : arena.getTeam1ColorEnum();
        TeamColor team2 = c2 != null ? c2 : arena.getTeam2ColorEnum();

        MinigameManager.getInstance().startMinigame(() -> new VaultWarGame(source.getServer(), level, arena, first, second, team1, team2));

        if (second == null) {
            source.sendSuccess(() -> Component.literal("§a[Vault War] Solo-Übungsrunde gestartet für " + first.getPlainTextName() + "!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§a[Vault War] Duell gestartet: " + first.getPlainTextName() + " (" + team1.displayName + ") vs. "
                    + second.getPlainTextName() + " (" + team2.displayName + ")!"), true);
        }
        return 1;
    }

    private static int executeStop(CommandContext<CommandSourceStack> ctx) {
        if (MinigameManager.getInstance().getActiveGame() instanceof VaultWarGame) {
            MinigameManager.getInstance().stopCurrentMinigame("Durch Befehl beendet");
            ctx.getSource().sendSuccess(() -> Component.literal("§a[Vault War] Spiel erfolgreich gestoppt."), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal("§c[Vault War] Es läuft derzeit kein Vault War Minispiel."));
        return 0;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        VaultWarArenaData arena = VaultWarSavedData.get(player.level()).getData();

        player.sendSystemMessage(Component.literal("§6=== Vault War Arena-Status ==="), false);
        player.sendSystemMessage(Component.literal("§eArena: " + (arena.hasArena() ? "§a" + arena.getMinPos().toShortString() + " bis " + arena.getMaxPos().toShortString() : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§eSpawn 1: " + (arena.hasSpawn1() ? String.format(Locale.ROOT, "§a(%.1f, %.1f, %.1f)", arena.getSpawn1X(), arena.getSpawn1Y(), arena.getSpawn1Z()) : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§eSpawn 2: " + (arena.hasSpawn2() ? String.format(Locale.ROOT, "§a(%.1f, %.1f, %.1f)", arena.getSpawn2X(), arena.getSpawn2Y(), arena.getSpawn2Z()) : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§eTrennzaun: " + (arena.hasFence() ? "§a" + arena.getFenceMin().toShortString() + " bis " + arena.getFenceMax().toShortString() : "§cnicht gesetzt")), false);
        player.sendSystemMessage(Component.literal("§eTeam 1 Farbe: " + arena.getTeam1ColorEnum().chatPrefix + arena.getTeam1ColorEnum().displayName), false);
        player.sendSystemMessage(Component.literal("§eTeam 2 Farbe: " + arena.getTeam2ColorEnum().chatPrefix + arena.getTeam2ColorEnum().displayName), false);

        String error = arena.validationError(player.level());
        if (error == null) {
            player.sendSystemMessage(Component.literal("§a✔ Arena ist startbereit!"), false);
        } else {
            player.sendSystemMessage(Component.literal("§c✘ Nicht bereit: " + error), false);
        }
        return 1;
    }
}
