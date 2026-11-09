package de.browars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class GamemodeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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

        // Top-level /gm <mode> [targets]
        dispatcher.register(Commands.literal("gm")
                .requires(GamemodeCommand::hasGmPermission)
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(modeSuggestion)
                        .executes(ctx -> executeGmSelf(ctx, StringArgumentType.getString(ctx, "mode")))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> executeGmTargets(ctx, StringArgumentType.getString(ctx, "mode"), EntityArgument.getPlayers(ctx, "targets"))))));

        // Top-level shortcuts /gmc, /gms, /gma, /gmsp
        dispatcher.register(Commands.literal("gmc")
                .requires(GamemodeCommand::hasGmPermission)
                .executes(ctx -> executeGmSelf(ctx, "c"))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> executeGmTargets(ctx, "c", EntityArgument.getPlayers(ctx, "targets")))));

        dispatcher.register(Commands.literal("gms")
                .requires(GamemodeCommand::hasGmPermission)
                .executes(ctx -> executeGmSelf(ctx, "s"))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> executeGmTargets(ctx, "s", EntityArgument.getPlayers(ctx, "targets")))));

        dispatcher.register(Commands.literal("gma")
                .requires(GamemodeCommand::hasGmPermission)
                .executes(ctx -> executeGmSelf(ctx, "a"))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> executeGmTargets(ctx, "a", EntityArgument.getPlayers(ctx, "targets")))));

        dispatcher.register(Commands.literal("gmsp")
                .requires(GamemodeCommand::hasGmPermission)
                .executes(ctx -> executeGmSelf(ctx, "sp"))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> executeGmTargets(ctx, "sp", EntityArgument.getPlayers(ctx, "targets")))));
    }

    public static boolean hasGmPermission(CommandSourceStack source) {
        if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) return true;
        if (source.getServer() != null && source.getServer().isSingleplayer()) return true;
        if (source.getEntity() instanceof ServerPlayer sp && (sp.isCreative() || de.browars.util.PermissionsHelper.isOp(sp))) return true;
        return false;
    }

    public static int executeGmSelf(CommandContext<CommandSourceStack> ctx, String modeInput) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        return applyGamemode(ctx.getSource(), List.of(player), modeInput);
    }

    public static int executeGmTargets(CommandContext<CommandSourceStack> ctx, String modeInput, Collection<ServerPlayer> targets) {
        return applyGamemode(ctx.getSource(), targets, modeInput);
    }

    public static int applyGamemode(CommandSourceStack source, Collection<ServerPlayer> targets, String modeInput) {
        GameType type = parseGameType(modeInput);
        if (type == null) {
            source.sendFailure(Component.literal("§c[BroWars] Ungültiger Spielmodus: '" + modeInput + "'. (Gültig: c, s, a, sp, creative, survival, adventure, spectator)"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : targets) {
            if (player.setGameMode(type)) {
                count++;
                player.sendSystemMessage(Component.literal("§a[BroWars] Dein Spielmodus wurde auf §e" + getGameTypeName(type) + " §agesetzt!"));
            }
        }

        final int finalCount = count;
        if (finalCount > 0) {
            source.sendSuccess(() -> Component.literal("§6[BroWars] Spielmodus von §e" + finalCount + " §6Spieler(n) auf §e" + getGameTypeName(type) + " §6geändert!"), true);
        } else {
            source.sendSuccess(() -> Component.literal("§7[BroWars] Keine Änderungen am Spielmodus durchgeführt."), false);
        }
        return count;
    }

    public static GameType parseGameType(String input) {
        if (input == null) return null;
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "c", "creative", "1", "kreativ" -> GameType.CREATIVE;
            case "s", "survival", "0", "überleben", "ueberleben" -> GameType.SURVIVAL;
            case "a", "adventure", "2", "abenteuer" -> GameType.ADVENTURE;
            case "sp", "spectator", "spec", "3", "zuschauer" -> GameType.SPECTATOR;
            default -> null;
        };
    }

    public static String getGameTypeName(GameType type) {
        if (type == null) return "Unbekannt";
        return switch (type) {
            case CREATIVE -> "Kreativ (Creative)";
            case SURVIVAL -> "Überleben (Survival)";
            case ADVENTURE -> "Abenteuer (Adventure)";
            case SPECTATOR -> "Zuschauer (Spectator)";
        };
    }
}
