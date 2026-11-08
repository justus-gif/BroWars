package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.browars.network.SyncClipboardPayload;
import de.browars.system.EnvironmentLockManager;
import de.browars.system.GameshowProtectionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public class BroWarsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Register top-level Gamemode & OP commands
        GamemodeCommand.register(dispatcher);
        OpCommand.register(dispatcher);

        // /copypos
        dispatcher.register(Commands.literal("copypos")
                .executes(BroWarsCommand::executeCopyPos)
        );

        // /protection [true|false]
        dispatcher.register(Commands.literal("protection")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    boolean current = GameshowProtectionManager.isProtectionEnabled();
                    GameshowProtectionManager.setProtectionEnabled(!current);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Gameshow-Schutz (Survival): " + (!current ? "§aAKTIVIERT" : "§cDEAKTIVIERT")), true);
                    return 1;
                })
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "enabled");
                            GameshowProtectionManager.setProtectionEnabled(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Gameshow-Schutz (Survival): " + (val ? "§aAKTIVIERT" : "§cDEAKTIVIERT")), true);
                            return 1;
                        }))
        );

        var modeSuggestion = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (ctx, builder) -> {
            builder.suggest("creative");
            builder.suggest("survival");
            builder.suggest("adventure");
            builder.suggest("spectator");
            builder.suggest("c");
            builder.suggest("s");
            builder.suggest("a");
            builder.suggest("sp");
            return builder.buildFuture();
        };

        // /browars ...
        dispatcher.register(Commands.literal("browars")
                .then(Commands.literal("copypos")
                        .executes(BroWarsCommand::executeCopyPos))
                .then(Commands.literal("copy")
                        .executes(BroWarsCommand::executeCopyPos))
                .then(Commands.literal("protection")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            boolean current = GameshowProtectionManager.isProtectionEnabled();
                            GameshowProtectionManager.setProtectionEnabled(!current);
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Gameshow-Schutz (Survival): " + (!current ? "§aAKTIVIERT" : "§cDEAKTIVIERT")), true);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean val = BoolArgumentType.getBool(ctx, "enabled");
                                    GameshowProtectionManager.setProtectionEnabled(val);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Gameshow-Schutz (Survival): " + (val ? "§aAKTIVIERT" : "§cDEAKTIVIERT")), true);
                                    return 1;
                                })))
                .then(Commands.literal("weatherlock")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            boolean current = EnvironmentLockManager.isWeatherTimeLockEnabled();
                            EnvironmentLockManager.setWeatherTimeLockEnabled(!current);
                            if (!current) {
                                EnvironmentLockManager.applyEnvironmentLock(ctx.getSource().getServer());
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Wetter/Tag-Lock: " + (!current ? "§aAKTIVIERT (Immer Tag & Klar)" : "§cDEAKTIVIERT")), true);
                            return 1;
                        })
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean val = BoolArgumentType.getBool(ctx, "enabled");
                                    EnvironmentLockManager.setWeatherTimeLockEnabled(val);
                                    if (val) {
                                        EnvironmentLockManager.applyEnvironmentLock(ctx.getSource().getServer());
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("§6[BroWars] Wetter/Tag-Lock: " + (val ? "§aAKTIVIERT (Immer Tag & Klar)" : "§cDEAKTIVIERT")), true);
                                    return 1;
                                })))
                .then(Commands.literal("day")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            EnvironmentLockManager.applyEnvironmentLock(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[BroWars] Zeit auf Tag & Wetter auf Klar gesetzt!"), true);
                            return 1;
                        }))
                // /browars gm ...
                .then(Commands.literal("gm")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(modeSuggestion)
                                .executes(ctx -> GamemodeCommand.executeGmSelf(ctx, StringArgumentType.getString(ctx, "mode")))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(ctx -> GamemodeCommand.executeGmTargets(ctx, StringArgumentType.getString(ctx, "mode"), EntityArgument.getPlayers(ctx, "targets"))))))
                .then(Commands.literal("gmc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> GamemodeCommand.executeGmSelf(ctx, "c"))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> GamemodeCommand.executeGmTargets(ctx, "c", EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("gms")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> GamemodeCommand.executeGmSelf(ctx, "s"))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> GamemodeCommand.executeGmTargets(ctx, "s", EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("gma")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> GamemodeCommand.executeGmSelf(ctx, "a"))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> GamemodeCommand.executeGmTargets(ctx, "a", EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("gmsp")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> GamemodeCommand.executeGmSelf(ctx, "sp"))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> GamemodeCommand.executeGmTargets(ctx, "sp", EntityArgument.getPlayers(ctx, "targets")))))
                // /browars op ...
                .then(Commands.literal("op")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(OpCommand::executeOpPlayers))
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(OpCommand::executeOpName)))
                // /browars deop ...
                .then(Commands.literal("deop")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(OpCommand::executeDeopPlayers))
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(OpCommand::executeDeopName)))
                // /browars oplist
                .then(Commands.literal("oplist")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(OpCommand::executeOpList))
                // /browars admin ...
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(OpCommand::executeAdminSelf)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> OpCommand.executeAdminTargets(ctx, EntityArgument.getPlayers(ctx, "targets")))))
        );
    }

    private static int executeCopyPos(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        String dimension = player.level().dimension().identifier().toString();

        // 1. Send sync payload to client
        ServerPlayNetworking.send(player, new SyncClipboardPayload(x, y, z, yaw, pitch, dimension));

        // 2. Feedback in chat
        player.sendSystemMessage(Component.literal("§a✔ [BroWars] Position & Blickrichtung kopiert!"), false);
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§eX: §f%.2f §eY: §f%.2f §eZ: §f%.2f §7| §eYaw: §f%.1f° §ePitch: §f%.1f° §7| §b%s",
                x, y, z, yaw, pitch, dimension)), false);
        player.sendSystemMessage(Component.literal("§7(Öffne einen Teleport-Block und klicke auf '📋 Einfügen')"), false);

        return 1;
    }
}
