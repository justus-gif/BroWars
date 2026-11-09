package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.util.PermissionsHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;

public class OpCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /op <targets>
        dispatcher.register(Commands.literal("op")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(OpCommand::executeOpPlayers))
                .then(Commands.argument("username", StringArgumentType.word())
                        .executes(OpCommand::executeOpName)));

        // /deop <targets>
        dispatcher.register(Commands.literal("deop")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(OpCommand::executeDeopPlayers))
                .then(Commands.argument("username", StringArgumentType.word())
                        .executes(OpCommand::executeDeopName)));

        // /oplist
        dispatcher.register(Commands.literal("oplist")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(OpCommand::executeOpList));

        // /admin [targets] (Grant OP + set Creative mode)
        dispatcher.register(Commands.literal("admin")
                .requires(source -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)
                        || (source.getServer() != null && source.getServer().isSingleplayer())
                        || (source.getEntity() instanceof ServerPlayer sp && PermissionsHelper.isOp(sp)))
                .executes(ctx -> executeAdminSelf(ctx))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> executeAdminTargets(ctx, EntityArgument.getPlayers(ctx, "targets")))));
    }

    public static int executeOpPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        MinecraftServer server = ctx.getSource().getServer();
        int count = 0;
        for (ServerPlayer player : players) {
            if (PermissionsHelper.grantOp(server, player)) {
                count++;
                ctx.getSource().sendSuccess(() -> Component.literal("§a✔ [BroWars] OP-Rechte an §e" + player.getScoreboardName() + " §avergeben!"), true);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("§7[BroWars] §e" + player.getScoreboardName() + " §7hat bereits OP-Rechte."), false);
            }
        }
        return count;
    }

    public static int executeOpName(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "username");
        MinecraftServer server = ctx.getSource().getServer();
        if (PermissionsHelper.grantOpByName(server, name)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a✔ [BroWars] OP-Rechte an §e" + name + " §avergeben!"), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("§c[BroWars] Spieler '" + name + "' konnte nicht ge-opped werden (bereits OP oder unvollständig)."));
            return 0;
        }
    }

    public static int executeDeopPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        MinecraftServer server = ctx.getSource().getServer();
        int count = 0;
        for (ServerPlayer player : players) {
            if (PermissionsHelper.revokeOp(server, player)) {
                count++;
                ctx.getSource().sendSuccess(() -> Component.literal("§c✔ [BroWars] OP-Rechte von §e" + player.getScoreboardName() + " §centzogen!"), true);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("§7[BroWars] §e" + player.getScoreboardName() + " §7hat keine OP-Rechte."), false);
            }
        }
        return count;
    }

    public static int executeDeopName(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "username");
        MinecraftServer server = ctx.getSource().getServer();
        if (PermissionsHelper.revokeOpByName(server, name)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§c✔ [BroWars] OP-Rechte von §e" + name + " §centzogen!"), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("§c[BroWars] Spieler '" + name + "' hatte keine OP-Rechte."));
            return 0;
        }
    }

    public static int executeOpList(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<String> list = PermissionsHelper.getOpList(server);
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== [BroWars OP-Liste (" + list.size() + ")] ==="), false);
        if (list.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7(Keine OP-Spieler eingetragen)"), false);
        } else {
            for (String entry : list) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + entry), false);
            }
        }
        return list.size();
    }

    public static int executeAdminSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return applyAdminMode(ctx.getSource(), List.of(player));
    }

    public static int executeAdminTargets(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        return applyAdminMode(ctx.getSource(), targets);
    }

    private static int applyAdminMode(CommandSourceStack source, Collection<ServerPlayer> targets) {
        MinecraftServer server = source.getServer();
        int count = 0;
        for (ServerPlayer player : targets) {
            PermissionsHelper.grantOp(server, player);
            player.setGameMode(GameType.CREATIVE);
            count++;
            player.sendSystemMessage(Component.literal("§a✔ [BroWars] Admin-Status aktiviert (OP + Kreativmodus)!"));
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.literal("§6✔ [BroWars] Admin-Status für §e" + finalCount + " §6Spieler aktiviert!"), true);
        return count;
    }
}
