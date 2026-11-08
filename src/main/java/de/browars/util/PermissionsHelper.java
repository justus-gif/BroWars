package de.browars.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class PermissionsHelper {

    /**
     * Returns true if the player is in Creative mode OR has OP/Admin permissions.
     */
    public static boolean isOpOrCreative(Player player) {
        if (player == null) return false;
        if (player.isCreative()) return true;
        return isOp(player);
    }

    /**
     * Returns true if the player has OP permissions.
     */
    public static boolean isOp(Player player) {
        if (player == null) return false;
        if (player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = serverPlayer.level().getServer();
            if (server != null && server.getPlayerList() != null) {
                NameAndId nameAndId = new NameAndId(serverPlayer.getGameProfile());
                return server.getPlayerList().isOp(nameAndId);
            }
        }
        return false;
    }

    /**
     * Grants OP permissions to a player.
     */
    public static boolean grantOp(MinecraftServer server, ServerPlayer target) {
        if (server == null || target == null) return false;
        NameAndId nameAndId = new NameAndId(target.getGameProfile());
        if (!server.getPlayerList().isOp(nameAndId)) {
            server.getPlayerList().op(nameAndId);
            target.sendSystemMessage(Component.literal("§a[BroWars] Du hast Operator-Rechte (OP) erhalten!"));
            return true;
        }
        return false;
    }

    /**
     * Revokes OP permissions from a player.
     */
    public static boolean revokeOp(MinecraftServer server, ServerPlayer target) {
        if (server == null || target == null) return false;
        NameAndId nameAndId = new NameAndId(target.getGameProfile());
        if (server.getPlayerList().isOp(nameAndId)) {
            server.getPlayerList().deop(nameAndId);
            target.sendSystemMessage(Component.literal("§c[BroWars] Deine Operator-Rechte (OP) wurden entfernt!"));
            return true;
        }
        return false;
    }

    /**
     * Grants OP permissions by username (online player).
     */
    public static boolean grantOpByName(MinecraftServer server, String username) {
        if (server == null || username == null || username.isBlank()) return false;
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player != null) {
            return grantOp(server, player);
        }
        return false;
    }

    /**
     * Revokes OP permissions by username (online player).
     */
    public static boolean revokeOpByName(MinecraftServer server, String username) {
        if (server == null || username == null || username.isBlank()) return false;
        ServerPlayer player = server.getPlayerList().getPlayerByName(username);
        if (player != null) {
            return revokeOp(server, player);
        }
        return false;
    }

    /**
     * Returns a list of formatted strings representing all OP players.
     */
    public static List<String> getOpList(MinecraftServer server) {
        List<String> opList = new ArrayList<>();
        if (server == null) return opList;
        for (String opName : server.getPlayerList().getOpNames()) {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(opName);
            if (onlinePlayer != null) {
                opList.add("§a● " + opName + " §7(Online)");
            } else {
                opList.add("§7○ " + opName + " §7(Offline)");
            }
        }
        return opList;
    }
}
