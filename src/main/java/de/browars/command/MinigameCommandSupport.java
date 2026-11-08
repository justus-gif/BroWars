package de.browars.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.browars.minigame.TeamColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MinigameCommandSupport {
    private MinigameCommandSupport() {}
    private static final DynamicCommandExceptionType INVALID_COLOR = new DynamicCommandExceptionType(
            value -> Component.literal("Unbekannte Teamfarbe: " + value));
    public static final SuggestionProvider<CommandSourceStack> COLORS = (ctx, builder) -> {
        for (TeamColor color : TeamColor.values()) builder.suggest(color.id);
        return builder.buildFuture();
    };
    public static TeamColor color(String value) throws CommandSyntaxException {
        TeamColor color = TeamColor.fromString(value, null);
        if (color == null) throw INVALID_COLOR.create(value);
        return color;
    }
    public static boolean validatePlayers(CommandSourceStack source, ServerPlayer first, ServerPlayer second) {
        if (second != null && first.getUUID().equals(second.getUUID())) {
            source.sendFailure(Component.literal("Spieler 1 und Spieler 2 müssen unterschiedlich sein.")); return false;
        }
        if (!first.isAlive() || (second != null && !second.isAlive())) {
            source.sendFailure(Component.literal("Alle Teilnehmer müssen vor dem Spielstart respawnen.")); return false;
        }
        return true;
    }
}
