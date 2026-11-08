package de.browars.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaWandItem extends Item {
    public static final Map<UUID, BlockPos> POS1_MAP = new ConcurrentHashMap<>();
    public static final Map<UUID, BlockPos> POS2_MAP = new ConcurrentHashMap<>();

    private static final Map<UUID, net.minecraft.resources.ResourceKey<Level>> DIMENSIONS = new ConcurrentHashMap<>();

    public static void clearSelection(ServerPlayer player) {
        POS1_MAP.remove(player.getUUID());
        POS2_MAP.remove(player.getUUID());
        DIMENSIONS.remove(player.getUUID());
    }

    private static void prepareSelection(ServerPlayer player) {
        var previous = DIMENSIONS.put(player.getUUID(), player.level().dimension());
        if (previous != null && !previous.equals(player.level().dimension())) {
            POS1_MAP.remove(player.getUUID());
            POS2_MAP.remove(player.getUUID());
        }
    }

    public static boolean isSelectionInCurrentDimension(ServerPlayer player) {
        return player.level().dimension().equals(DIMENSIONS.get(player.getUUID()));
    }

    public ArenaWandItem(Properties properties) {
        super(properties);
    }

    public static void setPos1(ServerPlayer player, BlockPos pos) {
        prepareSelection(player);
        POS1_MAP.put(player.getUUID(), pos.immutable());
        player.sendSystemMessage(
                Component.literal("§d[BroWars Arena] §aPunkt 1 gesetzt bei: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                true
        );
        ServerPlayNetworking.send(player, new de.browars.network.SyncArenaSelectionPayload(
                java.util.Optional.ofNullable(POS1_MAP.get(player.getUUID())),
                java.util.Optional.ofNullable(POS2_MAP.get(player.getUUID()))
        ));
        sendSelectionSummary(player);
    }

    public static void setPos2(ServerPlayer player, BlockPos pos) {
        prepareSelection(player);
        POS2_MAP.put(player.getUUID(), pos.immutable());
        player.sendSystemMessage(
                Component.literal("§d[BroWars Arena] §bPunkt 2 gesetzt bei: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                true
        );
        ServerPlayNetworking.send(player, new de.browars.network.SyncArenaSelectionPayload(
                java.util.Optional.ofNullable(POS1_MAP.get(player.getUUID())),
                java.util.Optional.ofNullable(POS2_MAP.get(player.getUUID()))
        ));
        sendSelectionSummary(player);
    }

    private static void sendSelectionSummary(ServerPlayer player) {
        BlockPos p1 = POS1_MAP.get(player.getUUID());
        BlockPos p2 = POS2_MAP.get(player.getUUID());
        if (p1 != null && p2 != null) {
            int dx = Math.abs(p1.getX() - p2.getX()) + 1;
            int dy = Math.abs(p1.getY() - p2.getY()) + 1;
            int dz = Math.abs(p1.getZ() - p2.getZ()) + 1;
            long volume = (long) dx * dy * dz;
            player.sendSystemMessage(Component.literal("§7➤ Bereich: §f" + dx + "x" + dy + "x" + dz + " §7(" + volume + " Blöcke) §8| §e/browars paintwar arena §8oder §e/browars colorfloor arena"), false);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        if (player instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                setPos2(serverPlayer, clickedPos);
                return InteractionResult.SUCCESS;
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (de.browars.util.PermissionsHelper.isOpOrCreative(serverPlayer)) {
                BlockPos p1 = POS1_MAP.get(serverPlayer.getUUID());
                BlockPos p2 = POS2_MAP.get(serverPlayer.getUUID());
                serverPlayer.sendSystemMessage(Component.literal("§d=== [BroWars Arena-Auswahl] ==="), false);
                serverPlayer.sendSystemMessage(Component.literal("§7Punkt 1 (Linksklick): " + (p1 != null ? "§a" + p1.toShortString() : "§cnicht gesetzt")), false);
                serverPlayer.sendSystemMessage(Component.literal("§7Punkt 2 (Rechtsklick): " + (p2 != null ? "§b" + p2.toShortString() : "§cnicht gesetzt")), false);
                if (p1 != null && p2 != null) {
                    int dx = Math.abs(p1.getX() - p2.getX()) + 1;
                    int dy = Math.abs(p1.getY() - p2.getY()) + 1;
                    int dz = Math.abs(p1.getZ() - p2.getZ()) + 1;
                    serverPlayer.sendSystemMessage(Component.literal("§7Größe: §e" + dx + "x" + dy + "x" + dz + " §7(" + ((long) dx * dy * dz) + " Blöcke)"), false);
                    serverPlayer.sendSystemMessage(Component.literal("§6Tipp: Nutze §e/browars paintwar arena§6, §e/browars colorfloor arena§6 oder §e/browars sumo zone§6, um diesen Bereich zu übernehmen."), false);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
